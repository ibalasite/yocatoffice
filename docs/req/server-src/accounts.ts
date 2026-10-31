// 會員系統 API：帳號 / 會話 / 伺服器權威錢包 / 權益 / 存檔 / 記錄 / 排行榜
// 自含 http 小工具，index.ts 只要 delegate 到 handleApi() 即可。
import type http from "node:http";
import crypto from "node:crypto";
import { q, tx } from "./db.ts";

const SESSION_TTL_DAYS = Number(process.env.SESSION_TTL_DAYS ?? 30);

// ===== 付費貨幣包（伺服器為權威來源）=====
const GEM_PACKS: Record<string, { gems: number; label: string }> = {
  g_small: { gems: 60,  label: "小寶石包" },
  g_mid:   { gems: 330, label: "中寶石包（+10%）" },
  g_big:   { gems: 700, label: "大寶石包（+16%）" },
};
// 資本儲值包（假付費）
const CAP_PACKS: Record<string, { cash: number; label: string }> = {
  cap_1000: { cash: 1000, label: "儲值 1000 資本（測試）" },
};

// ===== http 小工具 =====
function cors(res: http.ServerResponse) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization");
}
function sendJSON(res: http.ServerResponse, code: number, obj: unknown) {
  cors(res);
  res.writeHead(code, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(obj));
}
function readBody(req: http.IncomingMessage): Promise<any> {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (c) => { data += c; if (data.length > 4e6) req.destroy(); });
    req.on("end", () => { try { resolve(data ? JSON.parse(data) : {}); } catch (e) { reject(e); } });
    req.on("error", reject);
  });
}

// ===== 密碼雜湊（scrypt，Node 內建 crypto）=====
function hashPw(pw: string): string {
  const salt = crypto.randomBytes(16);
  const h = crypto.scryptSync(pw, salt, 64);
  return salt.toString("hex") + "$" + h.toString("hex");
}
function verifyPw(pw: string, stored: string): boolean {
  const [s, h] = stored.split("$");
  if (!s || !h) return false;
  const hh = crypto.scryptSync(pw, Buffer.from(s, "hex"), 64);
  const hb = Buffer.from(h, "hex");
  return hh.length === hb.length && crypto.timingSafeEqual(hh, hb);
}

interface Account { id: string; email: string; display_name: string; }

async function authUser(req: http.IncomingMessage): Promise<Account | null> {
  const h = String(req.headers["authorization"] ?? "");
  const m = /^Bearer\s+(.+)$/.exec(h);
  if (!m) return null;
  const rows = await q<Account>(
    `SELECT a.id::text, a.email, a.display_name
       FROM sessions s JOIN accounts a ON a.id = s.account_id
      WHERE s.token = $1 AND s.expires_at > now()`, [m[1]]);
  return rows[0] ?? null;
}

async function newSession(accountId: string): Promise<string> {
  const token = crypto.randomBytes(32).toString("hex");
  await q(
    `INSERT INTO sessions(token, account_id, expires_at)
     VALUES ($1, $2, now() + ($3 || ' days')::interval)`,
    [token, accountId, String(SESSION_TTL_DAYS)]);
  return token;
}

async function walletOf(accountId: string): Promise<{ cash: number; gems: number }> {
  const rows = await q<{ cash: string; gems: string }>(
    `SELECT cash::text, gems::text FROM wallets WHERE account_id = $1`, [accountId]);
  if (!rows[0]) return { cash: 0, gems: 0 };
  return { cash: Number(rows[0].cash), gems: Number(rows[0].gems) };
}
async function entitlementsOf(accountId: string) {
  return q(`SELECT kind, item_id AS "itemId", acquired_at AS "acquiredAt"
              FROM entitlements WHERE account_id = $1 ORDER BY acquired_at`, [accountId]);
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// ===== 主處理器：命中回傳 true =====
export async function handleApi(req: http.IncomingMessage, res: http.ServerResponse): Promise<boolean> {
  const url = req.url ?? "/";
  const method = req.method ?? "GET";
  const pathname = url.split("?")[0];
  const query = new URLSearchParams(url.split("?")[1] ?? "");

  const API_PREFIXES = ["/auth/", "/wallet/", "/entitlements", "/save", "/records", "/leaderboard"];
  if (!API_PREFIXES.some((p) => pathname === p || pathname.startsWith(p))) return false;

  if (method === "OPTIONS") { cors(res); res.writeHead(204); res.end(); return true; }

  try {
    // ---- 註冊 ----
    if (method === "POST" && pathname === "/auth/register") {
      const b = await readBody(req);
      const email = String(b.email ?? "").trim().toLowerCase();
      const name = String(b.displayName ?? "").trim();
      const pw = String(b.password ?? "");
      if (!EMAIL_RE.test(email)) return sendJSON(res, 400, { error: "email 格式不正確" }), true;
      if (name.length < 1 || name.length > 24) return sendJSON(res, 400, { error: "公司/老闆名需 1–24 字" }), true;
      if (pw.length < 6) return sendJSON(res, 400, { error: "密碼至少 6 碼" }), true;
      const dup = await q(`SELECT 1 FROM accounts WHERE email = $1`, [email]);
      if (dup.length) return sendJSON(res, 409, { error: "這個 email 已經註冊過了" }), true;
      const acc = await tx(async (c) => {
        const r = await c.query(
          `INSERT INTO accounts(email, display_name, pass_hash) VALUES ($1,$2,$3) RETURNING id::text, email, display_name`,
          [email, name, hashPw(pw)]);
        const a = r.rows[0];
        await c.query(`INSERT INTO wallets(account_id, cash, gems) VALUES ($1, 0, 0)`, [a.id]);
        await c.query(`INSERT INTO wallet_ledger(account_id, currency, delta, reason, balance) VALUES ($1,'gems',0,'init',0)`, [a.id]);
        return a;
      });
      const token = await newSession(acc.id);
      return sendJSON(res, 200, { token, account: acc, wallet: { cash: 0, gems: 0 } }), true;
    }

    // ---- 登入 ----
    if (method === "POST" && pathname === "/auth/login") {
      const b = await readBody(req);
      const email = String(b.email ?? "").trim().toLowerCase();
      const pw = String(b.password ?? "");
      const rows = await q<{ id: string; email: string; display_name: string; pass_hash: string }>(
        `SELECT id::text, email, display_name, pass_hash FROM accounts WHERE email = $1`, [email]);
      const a = rows[0];
      if (!a || !verifyPw(pw, a.pass_hash)) return sendJSON(res, 401, { error: "email 或密碼錯誤" }), true;
      const token = await newSession(a.id);
      const wallet = await walletOf(a.id);
      return sendJSON(res, 200, {
        token, account: { id: a.id, email: a.email, display_name: a.display_name }, wallet,
      }), true;
    }

    // ---- 排行榜 Top N（公開，不需登入）----
    if (method === "GET" && pathname === "/leaderboard") {
      const mode = query.get("mode") ?? "";
      const limit = Number(query.get("limit") ?? 20);
      return sendJSON(res, 200, { leaderboard: await leaderboardTop(mode, limit) }), true;
    }

    // ---- 以下皆需登入 ----
    const me = await authUser(req);
    if (pathname === "/auth/logout" && method === "POST") {
      const h = String(req.headers["authorization"] ?? "");
      const m = /^Bearer\s+(.+)$/.exec(h);
      if (m) await q(`DELETE FROM sessions WHERE token = $1`, [m[1]]);
      return sendJSON(res, 200, { ok: true }), true;
    }
    if (!me) return sendJSON(res, 401, { error: "請先登入" }), true;

    // ---- 我的資料 ----
    if (method === "GET" && pathname === "/auth/me") {
      return sendJSON(res, 200, { account: me, wallet: await walletOf(me.id), entitlements: await entitlementsOf(me.id) }), true;
    }

    // ---- 儲值（假付費）----
    if (method === "POST" && pathname === "/wallet/topup") {
      const b = await readBody(req);
      let currency = "", amount = 0, label = "";
      if (b.pack && GEM_PACKS[b.pack]) { currency = "gems"; amount = GEM_PACKS[b.pack].gems; label = GEM_PACKS[b.pack].label; }
      else if (b.pack && CAP_PACKS[b.pack]) { currency = "cash"; amount = CAP_PACKS[b.pack].cash; label = CAP_PACKS[b.pack].label; }
      else {
        currency = b.currency === "gems" ? "gems" : "cash";
        amount = Math.floor(Number(b.amount));
        label = "自訂儲值";
      }
      if (!(amount > 0) || amount > 1e7) return sendJSON(res, 400, { error: "金額不正確" }), true;
      const col = currency === "gems" ? "gems" : "cash";
      const bal = await tx(async (c) => {
        const r = await c.query(
          `UPDATE wallets SET ${col} = ${col} + $2, updated_at = now() WHERE account_id = $1 RETURNING ${col}::text AS bal`,
          [me.id, amount]);
        const newBal = Number(r.rows[0].bal);
        await c.query(
          `INSERT INTO wallet_ledger(account_id, currency, delta, reason, balance, meta) VALUES ($1,$2,$3,'topup',$4,$5)`,
          [me.id, currency, amount, newBal, JSON.stringify({ label, fake: true })]);
        return newBal;
      });
      return sendJSON(res, 200, { ok: true, currency, amount, balance: bal, wallet: await walletOf(me.id) }), true;
    }

    // ---- 花費（扣款，餘額不足回 402）----
    if (method === "POST" && pathname === "/wallet/spend") {
      const b = await readBody(req);
      const currency = b.currency === "gems" ? "gems" : "cash";
      const amount = Math.floor(Number(b.amount));
      const reason = String(b.reason ?? "spend").slice(0, 40);
      if (!(amount > 0)) return sendJSON(res, 400, { error: "金額不正確" }), true;
      const col = currency === "gems" ? "gems" : "cash";
      try {
        const bal = await tx(async (c) => {
          const r = await c.query(`SELECT ${col}::text AS bal FROM wallets WHERE account_id = $1 FOR UPDATE`, [me.id]);
          const cur = Number(r.rows[0].bal);
          if (cur < amount) throw new Error("INSUFFICIENT");
          const r2 = await c.query(
            `UPDATE wallets SET ${col} = ${col} - $2, updated_at = now() WHERE account_id = $1 RETURNING ${col}::text AS bal`,
            [me.id, amount]);
          const newBal = Number(r2.rows[0].bal);
          await c.query(
            `INSERT INTO wallet_ledger(account_id, currency, delta, reason, balance, meta) VALUES ($1,$2,$3,$4,$5,$6)`,
            [me.id, currency, -amount, reason, newBal, JSON.stringify(b.meta ?? {})]);
          return newBal;
        });
        return sendJSON(res, 200, { ok: true, currency, balance: bal, wallet: await walletOf(me.id) }), true;
      } catch (e: any) {
        if (e.message === "INSUFFICIENT") return sendJSON(res, 402, { error: "餘額不足", wallet: await walletOf(me.id) }), true;
        throw e;
      }
    }

    // ---- 權益：列出 ----
    if (method === "GET" && pathname === "/entitlements") {
      return sendJSON(res, 200, { entitlements: await entitlementsOf(me.id) }), true;
    }
    // ---- 權益：購買（原子：扣款 + 授予）----
    if (method === "POST" && pathname === "/entitlements/buy") {
      const b = await readBody(req);
      const kind = String(b.kind ?? "");
      const itemId = String(b.itemId ?? "");
      const currency = b.currency === "gems" ? "gems" : "cash";
      const amount = Math.floor(Number(b.amount ?? 0));
      if (!["cosmetic", "dream", "scenario"].includes(kind) || !itemId) return sendJSON(res, 400, { error: "項目不正確" }), true;
      const col = currency === "gems" ? "gems" : "cash";
      try {
        const out = await tx(async (c) => {
          const owned = await c.query(`SELECT 1 FROM entitlements WHERE account_id=$1 AND kind=$2 AND item_id=$3`, [me.id, kind, itemId]);
          if (owned.rowCount) throw new Error("OWNED");
          if (amount > 0) {
            const r = await c.query(`SELECT ${col}::text AS bal FROM wallets WHERE account_id=$1 FOR UPDATE`, [me.id]);
            if (Number(r.rows[0].bal) < amount) throw new Error("INSUFFICIENT");
            const r2 = await c.query(`UPDATE wallets SET ${col}=${col}-$2, updated_at=now() WHERE account_id=$1 RETURNING ${col}::text AS bal`, [me.id, amount]);
            await c.query(`INSERT INTO wallet_ledger(account_id,currency,delta,reason,balance,meta) VALUES ($1,$2,$3,$4,$5,$6)`,
              [me.id, currency, -amount, "buy_" + kind, Number(r2.rows[0].bal), JSON.stringify({ itemId })]);
          }
          await c.query(`INSERT INTO entitlements(account_id, kind, item_id) VALUES ($1,$2,$3)`, [me.id, kind, itemId]);
          return true;
        });
        return sendJSON(res, 200, { ok: out, wallet: await walletOf(me.id), entitlements: await entitlementsOf(me.id) }), true;
      } catch (e: any) {
        if (e.message === "OWNED") return sendJSON(res, 409, { error: "已經擁有這個項目" }), true;
        if (e.message === "INSUFFICIENT") return sendJSON(res, 402, { error: "餘額不足", wallet: await walletOf(me.id) }), true;
        throw e;
      }
    }

    // ---- 存檔 ----
    if (method === "GET" && pathname === "/save") {
      const slot = query.get("slot") ?? "main";
      const rows = await q(`SELECT data, updated_at AS "updatedAt" FROM saves WHERE account_id=$1 AND slot=$2`, [me.id, slot]);
      return sendJSON(res, 200, { save: rows[0] ?? null }), true;
    }
    if (method === "PUT" && pathname === "/save") {
      const b = await readBody(req);
      const slot = String(b.slot ?? "main");
      if (b.data == null) return sendJSON(res, 400, { error: "缺 data" }), true;
      await q(
        `INSERT INTO saves(account_id, slot, data, updated_at) VALUES ($1,$2,$3,now())
         ON CONFLICT (account_id, slot) DO UPDATE SET data=EXCLUDED.data, updated_at=now()`,
        [me.id, slot, JSON.stringify(b.data)]);
      return sendJSON(res, 200, { ok: true }), true;
    }

    // ---- 經營史記錄（破產/成就/傳承）----
    if (method === "POST" && pathname === "/records") {
      const b = await readBody(req);
      const type = String(b.type ?? "");
      if (!["bankruptcy", "achievement", "legacy"].includes(type)) return sendJSON(res, 400, { error: "type 不正確" }), true;
      await q(`INSERT INTO records(account_id, type, payload) VALUES ($1,$2,$3)`, [me.id, type, JSON.stringify(b.payload ?? {})]);
      return sendJSON(res, 200, { ok: true }), true;
    }
    if (method === "GET" && pathname === "/records") {
      const type = query.get("type");
      const rows = type
        ? await q(`SELECT type, payload, created_at AS "createdAt" FROM records WHERE account_id=$1 AND type=$2 ORDER BY created_at DESC LIMIT 100`, [me.id, type])
        : await q(`SELECT type, payload, created_at AS "createdAt" FROM records WHERE account_id=$1 ORDER BY created_at DESC LIMIT 100`, [me.id]);
      return sendJSON(res, 200, { records: rows }), true;
    }

    // ---- 排行榜 ----
    if (method === "POST" && pathname === "/leaderboard") {
      const b = await readBody(req);
      const runId = String(b.runId ?? "");
      const mode = String(b.mode ?? "sandbox");
      const score = Math.floor(Number(b.score ?? 0));
      if (!runId) return sendJSON(res, 400, { error: "缺 runId" }), true;
      await q(
        `INSERT INTO leaderboard(run_id, account_id, display_name, mode, score, grade, detail, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,now())
         ON CONFLICT (run_id) DO UPDATE SET score=EXCLUDED.score, grade=EXCLUDED.grade, detail=EXCLUDED.detail, mode=EXCLUDED.mode, updated_at=now()`,
        [runId, me.id, me.display_name, mode, score, b.grade ?? null, JSON.stringify(b.detail ?? {})]);
      return sendJSON(res, 200, { ok: true }), true;
    }

    return sendJSON(res, 404, { error: "api not found" }), true;
  } catch (e: any) {
    console.error(`[api] ${method} ${pathname}:`, e?.message ?? e);
    return sendJSON(res, 500, { error: String(e?.message ?? e) }), true;
  }
}

// 排行榜 Top N（公開，不需登入）——由 index.ts 在 handleApi 之前或之後都可呼叫
export async function leaderboardTop(mode: string, limit: number) {
  return q(
    `SELECT run_id AS "runId", display_name AS "name", mode, score, grade, updated_at AS "updatedAt"
       FROM leaderboard WHERE ($1 = '' OR mode = $1) ORDER BY score DESC LIMIT $2`,
    [mode, Math.min(Math.max(limit, 1), 100)]);
}
