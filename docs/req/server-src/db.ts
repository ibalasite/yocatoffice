// DB 連線層：連到 k8s 內的 Postgres（經 NodePort 30432）
import path from "node:path";
import { fileURLToPath } from "node:url";
import pg from "pg";

// 載入 server/.env.local（DATABASE_URL 等）
const SRV = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
try { process.loadEnvFile(path.join(SRV, ".env.local")); } catch { /* 沒有就用環境變數 */ }

const DATABASE_URL = process.env.DATABASE_URL ?? "postgres://yocat:yocat@localhost:30432/yocat";

export const pool = new pg.Pool({
  connectionString: DATABASE_URL,
  max: 8,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
});

pool.on("error", (e) => console.error("[db] pool error:", e.message));

export async function q<T = any>(text: string, params: any[] = []): Promise<T[]> {
  const r = await pool.query(text, params);
  return r.rows as T[];
}

// 交易包裝：fn 內用傳入的 client 執行多個查詢，自動 COMMIT/ROLLBACK
export async function tx<T>(fn: (c: pg.PoolClient) => Promise<T>): Promise<T> {
  const c = await pool.connect();
  try {
    await c.query("BEGIN");
    const out = await fn(c);
    await c.query("COMMIT");
    return out;
  } catch (e) {
    try { await c.query("ROLLBACK"); } catch { /* ignore */ }
    throw e;
  } finally {
    c.release();
  }
}

// 啟動時確認連得到
export async function dbPing(): Promise<boolean> {
  try { await pool.query("SELECT 1"); return true; }
  catch (e: any) { console.error("[db] ping failed:", e?.message ?? e); return false; }
}
