// yoCat Office — 角色大腦後端（Node TS，零依賴）
// 路由 client 的 /character/think、/character/chat 到本地 vLLM(OpenAI 相容)。
// 跑法：node src/index.ts （Node 24 原生剝除 TS 型別，無需編譯）
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { handleApi } from "./accounts.ts";
import { dbPing } from "./db.ts";

// ===== 設定 =====
const PORT = Number(process.env.PORT ?? 3000);
// 專案根目錄（server/src/index.ts → 上兩層）＝ <project-root>
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const LLM_BASE = process.env.LLM_BASE ?? "http://localhost:8000/v1";
const MODEL = process.env.LLM_MODEL ?? "Qwen/Qwen2.5-7B-Instruct-AWQ";
const LLM_TIMEOUT_MS = Number(process.env.LLM_TIMEOUT_MS ?? 20000);

const DEPT_NAME: Record<string, string> = {
  "總": "總經理辦公室", "產": "產品部", "銷": "銷售部",
  "人": "人事部", "發": "研發部", "財": "財務部",
};

// ===== 簡體→繁體安全網（模型偶爾漏出簡體字時轉回；皆為一對一安全對應）=====
const S2T_PAIRS =
  "这這们們个個产產销銷务務会會办辦实實单單报報团團问問题題应應该該没沒见見现現议議认認识識让讓师師专專业業严嚴东東车車书書买買卖賣货貨质質价價钱錢资資总總经經营營户戶开開关關门門内內处處场場规規标標测測试試验驗证證据據库庫订訂购購构構环環节節设設计計项項类類级級统統网網页頁码碼数數软軟维維简簡优優势勢态態样樣边邊风風险險两兩为為从從众眾乐樂习習写寫对對导導寻尋层層属屬举舉亲親仅僅传傳伤傷体體来來党黨兴興农農区區医醫华華协協卫衛厂廠压壓县縣参參双雙变變号號听聽启啟员員响響园園围圍图圖圆圓坚堅坏壞块塊声聲备備够夠头頭夺奪奋奮奖獎妇婦妈媽学學宁寧宝寶审審宽寬宾賓寿壽将將尘塵尝嘗岁歲岛島币幣帅帥带帶帮幫广廣庆慶废廢异異弃棄张張弹彈归歸当當录錄忆憶忧憂怀懷恋戀恶惡惊驚惧懼愤憤懒懶戏戲战戰执執扩擴扫掃扬揚抚撫抢搶护護担擔拟擬挣掙揽攬摄攝摆擺摇搖敌敵断斷无無旧舊显顯晓曉暂暫机機杀殺权權条條杨楊极極枪槍柜櫃栏欄树樹桥橋梦夢检檢楼樓欢歡欧歐残殘毕畢气氣汇匯汉漢汤湯沟溝泪淚泽澤洁潔浅淺济濟浓濃涛濤润潤涨漲渐漸湾灣湿濕溃潰满滿滤濾滥濫滩灘灭滅灯燈灵靈灾災点點炼煉热熱爱愛爷爺牵牽状狀独獨狭狹猫貓献獻玛瑪电電画畫畅暢疗療疯瘋瘫癱瘾癮皱皺盏盞盐鹽监監盖蓋盘盤瞒瞞矫矯矿礦砖磚础礎确確碍礙礼禮祸禍离離种種积積称稱稳穩穷窮窃竊竞競笔筆笼籠筹籌签簽篮籃紧緊纪紀纯純纲綱纳納纷紛纸紙纹紋线線练練组組细細织織终終结結绕繞绘繪给給络絡绝絕继繼绩績绪緒续續绳繩绵綿综綜绿綠缓緩编編缘緣缩縮缴繳罗羅罚罰罢罷职職联聯聪聰肃肅肠腸肤膚肾腎肿腫胀脹胆膽脉脈脑腦舰艦舱艙艰艱苏蘇荣榮药藥获獲蓝藍虽雖补補装裝裤褲观觀觉覺览覽触觸誉譽讨討训訓讯訊记記讲講许許论論诉訴词詞译譯诗詩诚誠话話询詢详詳语語误誤说說请請诸諸读讀课課谁誰调調谈談谊誼谋謀谎謊谜謎谢謝谦謙谨謹谱譜贝貝负負贡貢财財责責贤賢败敗贩販贪貪贯貫贱賤贴貼贵貴贷貸贸貿费費贺賀贼賊赁賃赋賦赌賭赏賞赐賜赔賠赚賺赛賽赠贈赢贏趋趨轧軋轨軌转轉轮輪轰轟轴軸轻輕载載较較辅輔辆輛辈輩辉輝输輸辑輯辖轄辞辭辩辯达達迁遷过過迈邁运運还還进進远遠违違连連迟遲适適选選逊遜递遞逻邏遗遺邓鄧邮郵郑鄭邻鄰释釋针針钓釣钢鋼钩鉤钮鈕钻鑽铁鐵铜銅铝鋁银銀铭銘链鏈锁鎖锅鍋锋鋒锐銳错錯锦錦键鍵镇鎮镜鏡长長闪閃闭閉闯闖闲閒间間闷悶闹鬧闻聞阁閣阅閱阳陽阴陰阵陣阶階际際陆陸陈陳隐隱难難雾霧静靜顶頂顺順须須顾顧顿頓颂頌预預领領颈頸频頻颗顆颜顏额額飘飄飞飛饥飢饭飯饮飲饰飾饱飽饲飼饺餃饼餅饿餓馆館馈饋马馬驰馳驱驅驳駁驶駛驾駕驻駐骂罵骄驕骑騎骗騙骤驟鱼魚鲁魯鲜鮮鸟鳥鸡雞鸣鳴鸭鴨鸿鴻鹅鵝鹰鷹麦麥齐齊齿齒龄齡龙龍龟龜国國赶趕韩韓临臨乱亂争爭丢丟唤喚挡擋挤擠挥揮损損撑撐滚滾潜潛脱脫荡蕩萧蕭贬貶跃躍轿轎辐輻酱醬锈鏽锣鑼锤錘锯鋸镀鍍钙鈣钠鈉铅鉛铺鋪锌鋅锥錐锻鍛奥奧妆妝孙孫宫宮屿嶼岗崗恳懇悬懸昼晝枣棗桩樁椭橢汹洶渗滲牺犧犹猶猪豬琐瑣痒癢盗盜窝窩竖豎筛篩纵縱绣繡缝縫耸聳聋聾腊臘腻膩茧繭虏虜衔銜衬襯谬謬谭譚谴譴贮貯贿賄赡贍践踐酝釀锚錨镁鎂镍鎳吗嗎厨廚卧臥咏詠唇脣啮嚙时時动動术術么麼勋勳务務别別决決约約丽麗丝絲侧側虑慮虚虛挂掛荐薦绍紹乡鄉乔喬乌烏仓倉伟偉伞傘侦偵侨僑俭儉债債倾傾偿償储儲刚剛凤鳳剂劑厅廳叠疊恼惱卢盧养養兽獸";
const S2T = new Map<string, string>();
for (let i = 0; i < S2T_PAIRS.length; i += 2) S2T.set(S2T_PAIRS[i], S2T_PAIRS[i + 1]);
function toTrad(s: string): string {
  let out = "";
  for (const ch of s) out += S2T.get(ch) ?? ch;
  return out;
}

// ===== 型別 =====
interface ThinkReq {
  name: string; dept: string; persona?: string[]; task?: string;
  announce?: string; gameTime?: string; mood?: number; energy?: number; style?: string;
}
interface ChatReq { name: string; dept: string; persona?: string[]; message: string; memory?: string[]; style?: string; }
interface Peer { name: string; dept: string; persona?: string[]; mem?: string[]; style?: string; }
interface ConvReq { a: Peer; b: Peer; gameTime?: string; announce?: string; rel?: string; events?: string[]; }

interface ChatMsg { role: "system" | "user" | "assistant"; content: string; }

// ===== 呼叫本地 vLLM =====
async function callLLM(messages: ChatMsg[], opts: { max_tokens: number; temperature: number }): Promise<string> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), LLM_TIMEOUT_MS);
  try {
    const r = await fetch(`${LLM_BASE}/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "Authorization": "Bearer local" },
      body: JSON.stringify({ model: MODEL, messages, ...opts, stream: false }),
      signal: ctrl.signal,
    });
    if (!r.ok) throw new Error(`LLM ${r.status}: ${await r.text()}`);
    const j: any = await r.json();
    return (j?.choices?.[0]?.message?.content ?? "").trim();
  } finally {
    clearTimeout(timer);
  }
}

// 去掉多餘引號/換行，取第一行，限長
function oneLine(s: string, max = 40): string {
  let t = s.replace(/^["「『]+|["」』]+$/g, "").split(/\r?\n/)[0].trim();
  if (t.length > max) t = t.slice(0, max);
  return t;
}

// ===== 業務邏輯 =====
async function think(b: ThinkReq): Promise<string> {
  const deptName = DEPT_NAME[b.dept] ?? b.dept;
  const persona = (b.persona ?? []).join("、");
  const sys = [
    `你是「${b.name}」，${deptName}的員工。個性：${persona || "一般"}。`,
    `這是一家電商公司的辦公室模擬。你此刻正在上班。`,
    b.task ? `目前任務：${b.task}。` : `目前沒有被指派特定任務，做本職工作即可。`,
    b.announce ? `老闆最新公告：「${b.announce}」。` : ``,
    b.gameTime ? `現在遊戲時間：${b.gameTime}。` : ``,
    `你的狀態：心情 ${b.mood ?? 60}/100、精力 ${b.energy ?? 60}/100。`,
    b.style ? `你說話的習慣：${b.style}。要自然融入，不要生硬硬套。` : ``,
    `【語言規定】務必使用台灣繁體中文與台灣用語，嚴禁出現任何簡體字。`,
    `以第一人稱說出你此刻心裡的一句話或正在做的事，要符合個性與狀態，20字以內，只回這一句，不要解釋、不要加引號。`,
  ].filter(Boolean).join("\n");
  const out = await callLLM(
    [{ role: "system", content: sys }, { role: "user", content: "（說出此刻的一句話）" }],
    { max_tokens: 60, temperature: 0.9 },
  );
  return toTrad(oneLine(out, 40));
}

function extractJSON(s: string): string {
  const i = s.indexOf("{"), j = s.lastIndexOf("}");
  return (i >= 0 && j > i) ? s.slice(i, j + 1) : s;
}
// 清洗對話：去英數亂碼、殘留名字/meta 前綴、前後雜訊與內部空白；限長；太短則捨棄
function cleanLine(s: string, names: string[]): string {
  let t = oneLine(s, 48);
  t = t.split(/["{}\\]/)[0];                                          // 遇引號/大括號即截斷（擋壞 JSON 殘留）
  t = t.replace(/[A-Za-z_]+/g, "");                                   // 英數/底線亂碼
  t = t.replace(/[#&*=+<>~`^|@$%/\[\]]/g, "");                        // 雜散符號
  for (const n of names) if (n) t = t.replace(new RegExp("^" + n + "[^\\u4e00-\\u9fff]*"), "");
  t = t.replace(/^[^，。！？一-鿿]{0,2}[^，。！？]{0,8}[:：]/, ""); // 「乙的回答是：」之類 meta 前綴
  t = t.replace(/^[\s_\-—.,，。、:：!！?？'"「」（）()]+/, "");          // 開頭雜訊
  t = t.replace(/[\s_\-—.]+$/, "");                                   // 句尾半形雜訊
  t = t.replace(/\s+/g, "");                                          // 內部空白
  if (t.length > 20) t = t.slice(0, 20);
  if (t.length < 2) return "";
  return t;
}
async function converse(req: ConvReq): Promise<{ a: string; b: string }> {
  const A = req.a, B = req.b;
  const dn = (d: string) => DEPT_NAME[d] ?? d;
  const sys = [
    `這是一家電商公司的辦公室，兩位同事在工位旁短暫交談。`,
    `甲：${dn(A.dept)}，個性：${(A.persona ?? []).join("、") || "一般"}。${A.style ? "說話習慣：" + A.style + "。" : ""}`,
    `乙：${dn(B.dept)}，個性：${(B.persona ?? []).join("、") || "一般"}。${B.style ? "說話習慣：" + B.style + "。" : ""}`,
    req.rel ? `甲乙的關係：${req.rel}。講話語氣要符合這個關係。` : ``,
    (A.mem && A.mem.length) ? `甲最近的經歷：${A.mem.join("、")}。` : ``,
    (B.mem && B.mem.length) ? `乙最近的經歷：${B.mem.join("、")}。` : ``,
    req.announce ? `老闆最新公告：「${req.announce}」。` : ``,
    (req.events && req.events.length) ? `公司最近發生這些事：${req.events.join("；")}。兩人可以私下聊這些、八卦一下。` : ``,
    req.gameTime ? `現在時間：${req.gameTime}。` : ``,
    `請讓甲、乙各說一句自然、口語、符合個性與關係的話（閒聊、聊工作、或八卦上面的公司大事皆可，乙要回應甲）。`,
    `重要：句子裡不要出現任何名字或稱呼，直接講要說的內容。每句務必 14 個字以內，不可說到一半。`,
    `【語言規定】務必使用台灣繁體中文與台灣用語，嚴禁任何簡體字或英文。`,
    `只用 JSON 回覆，不要任何多餘文字：{"a":"甲說的話","b":"乙說的話"}`,
  ].filter(Boolean).join("\n");
  const out = await callLLM(
    [{ role: "system", content: sys }, { role: "user", content: "（甲、乙各說一句）" }],
    { max_tokens: 140, temperature: 0.7 },
  );
  const names = [A.name, B.name];
  let a = "", b = "";
  try {
    const j: any = JSON.parse(extractJSON(out));
    a = cleanLine(String(j.a ?? ""), names); b = cleanLine(String(j.b ?? ""), names);
  } catch {
    const parts = out.split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
    a = cleanLine(parts[0] ?? "", names); b = cleanLine(parts[1] ?? parts[0] ?? "", names);
  }
  return { a: toTrad(a), b: toTrad(b) };
}

interface ReqReq { name: string; dept: string; persona?: string[]; type: string; coworker?: string; style?: string; }
const REQ_DESC: Record<string,string> = {
  raise:    "向老闆開口要求加薪（你覺得自己表現很好、能力強，值得更高薪水）",
  dayoff:   "向老闆請假休息（你最近太累了，想喘口氣）",
  complaint:"向老闆抱怨（工作量太大、或某件讓你不爽的事）",
  proposal: "向老闆提出一個能幫公司賺更多/更順的點子",
  quit:     "向老闆暗示你做得很不開心、在考慮離職",
};
async function request(r: ReqReq): Promise<string> {
  const deptName = DEPT_NAME[r.dept] ?? r.dept;
  const persona = (r.persona ?? []).join("、");
  const sys = [
    `你是「${r.name}」，這家電商公司${deptName}的員工。個性：${persona || "一般"}。`,
    r.coworker ? `（這跟同事「${r.coworker}」有關）` : ``,
    r.style ? `你說話的習慣：${r.style}。` : ``,
    `你現在要主動${REQ_DESC[r.type] ?? "跟老闆說一件事"}。對象是公司老闆（玩家）。`,
    `用第一人稱、口語、台灣繁體中文，帶一點你個性的情緒，25 字以內，只回這一句，不要加引號。`,
  ].filter(Boolean).join("\n");
  const out = await callLLM(
    [{ role: "system", content: sys }, { role: "user", content: "（對老闆說出這件事）" }],
    { max_tokens: 60, temperature: 0.9 },
  );
  let t = oneLine(out, 48).replace(/[A-Za-z_]+/g, "").replace(/\s+/g, " ").trim();
  return toTrad(t).slice(0, 34);
}

interface RomanceReq { name: string; dept: string; persona?: string[]; target?: string; type: string; style?: string; }
const ROM_DESC: Record<string,string> = {
  confess: "你喜歡上同事「{t}」很久了，現在鼓起勇氣跟對方告白、表達你的心意",
  accept:  "同事「{t}」剛剛跟你告白，你其實也喜歡對方，你害羞又開心地答應了",
  reject:  "同事「{t}」剛剛跟你告白，但你對對方只有同事情誼，你委婉地拒絕對方",
  sweet:   "你跟交往中的另一半「{t}」在辦公室偷偷講一句甜蜜的悄悄話",
  breakup: "你跟交往中的「{t}」最近處得不好，你說出想分手或感到難過的心情",
};
async function romance(r: RomanceReq): Promise<string> {
  const deptName = DEPT_NAME[r.dept] ?? r.dept;
  const persona = (r.persona ?? []).join("、");
  const desc = (ROM_DESC[r.type] ?? "你跟同事說一句話").replace("{t}", r.target ?? "對方");
  const sys = [
    `你是「${r.name}」，這家電商公司${deptName}的員工。個性：${persona || "一般"}。`,
    `情境：${desc}。`,
    r.style ? `你說話的習慣：${r.style}。` : ``,
    `用第一人稱、口語、台灣繁體中文，帶真實的情緒（害羞/心動/難過都好），20 字以內，只回這一句，不要加引號，不要出現對方的名字。`,
    `【語言規定】務必台灣繁體中文，嚴禁簡體字或英文。`,
  ].join("\n");
  const out = await callLLM(
    [{ role: "system", content: sys }, { role: "user", content: "（說出這句話）" }],
    { max_tokens: 60, temperature: 0.95 },
  );
  let t = oneLine(out, 42).replace(/[A-Za-z_]+/g, "").replace(/\s+/g, " ").trim();
  return toTrad(t).slice(0, 28);
}

async function chat(b: ChatReq): Promise<string> {
  const deptName = DEPT_NAME[b.dept] ?? b.dept;
  const persona = (b.persona ?? []).join("、");
  const sys = [
    `你是「${b.name}」，這家電商公司${deptName}的員工。個性：${persona || "一般"}。`,
    `現在跟你說話的是公司的老闆／總經理（也就是玩家），不是客戶。請以「對上司」的口吻自然回應。`,
    (b.memory && b.memory.length) ? `你最近在公司的經歷：${b.memory.join("、")}。如果老闆的話跟這些有關，可以自然帶到。` : ``,
    b.style ? `你說話的習慣：${b.style}。自然融入即可。` : ``,
    `【語言規定】務必使用台灣繁體中文與台灣用語，嚴禁出現任何簡體字。`,
    `回答精簡、口語、有人味，2~3句以內，符合你的個性與職務。`,
  ].join("\n");
  const out = await callLLM(
    [{ role: "system", content: sys }, { role: "user", content: b.message }],
    { max_tokens: 200, temperature: 0.85 },
  );
  return toTrad(out) || "…";
}

// ===== HTTP server（含 CORS，供 file:// 開啟的 client 呼叫）=====
function cors(res: http.ServerResponse) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
}
function sendJSON(res: http.ServerResponse, code: number, obj: unknown) {
  cors(res);
  res.writeHead(code, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(obj));
}
function readBody(req: http.IncomingMessage): Promise<any> {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (c) => { data += c; if (data.length > 1e6) req.destroy(); });
    req.on("end", () => { try { resolve(data ? JSON.parse(data) : {}); } catch (e) { reject(e); } });
    req.on("error", reject);
  });
}

// ===== 靜態檔（把遊戲本身也用 HTTP 服務出去）=====
const MIME: Record<string, string> = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8",
  ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif",
  ".svg": "image/svg+xml", ".webp": "image/webp", ".ico": "image/x-icon",
  ".woff": "font/woff", ".woff2": "font/woff2", ".mp3": "audio/mpeg", ".wav": "audio/wav",
};
// 只允許服務 client/ 與 assets/ 底下的檔案，且不得跳出 ROOT（防目錄穿越）
const ALLOW_DIRS = [path.join(ROOT, "client"), path.join(ROOT, "assets")];
function serveStatic(res: http.ServerResponse, rel: string): boolean {
  let clean: string;
  try { clean = decodeURIComponent(rel.split("?")[0]).replace(/^\/+/, ""); } catch { return false; }
  const fp = path.resolve(ROOT, clean);
  // 解析後必須確實落在 client/ 或 assets/ 內（擋編碼過的 ../ 穿越）
  if (!ALLOW_DIRS.some(d => fp === d || fp.startsWith(d + path.sep))) return false;
  let st: fs.Stats;
  try { st = fs.statSync(fp); } catch { return false; }
  if (!st.isFile()) return false;
  const type = MIME[path.extname(fp).toLowerCase()] ?? "application/octet-stream";
  res.writeHead(200, { "Content-Type": type, "Cache-Control": "no-cache" });
  fs.createReadStream(fp).pipe(res);
  return true;
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") { cors(res); res.writeHead(204); res.end(); return; }
  const url = req.url ?? "/";
  const pathname = url.split("?")[0];   // 去掉 query string（如 ?fresh / ?roam）

  try {
    // 會員系統 API（/auth /wallet /entitlements /save /records /leaderboard）→ 交給 accounts 模組
    if (await handleApi(req, res)) return;

    // 首頁 → 直接吐遊戲 HTML（不轉址，經 ngrok 等通道較穩）
    if (req.method === "GET" && (pathname === "/" || pathname === "/index.html")) {
      if (serveStatic(res, "/client/index.html")) return;
      res.writeHead(404); res.end("client/index.html not found"); return;
    }
    // 靜態資源（client/ 與 assets/）
    if (req.method === "GET" && (pathname.startsWith("/client/") || pathname.startsWith("/assets/"))) {
      if (serveStatic(res, pathname)) return;
      res.writeHead(404); res.end("not found"); return;
    }
    if (req.method === "GET" && pathname === "/health") {
      // 順便確認 LLM 端點活著
      let llm = "unknown";
      try {
        const r = await fetch(`${LLM_BASE}/models`, { signal: AbortSignal.timeout(3000) });
        llm = r.ok ? "up" : `http ${r.status}`;
      } catch { llm = "down"; }
      const db = (await dbPing()) ? "up" : "down";
      return sendJSON(res, 200, { ok: true, model: MODEL, llm, db });
    }
    if (req.method === "POST" && url === "/character/think") {
      const b = await readBody(req) as ThinkReq;
      const line = await think(b);
      return sendJSON(res, 200, { line });
    }
    if (req.method === "POST" && url === "/character/converse") {
      const body = await readBody(req) as ConvReq;
      const pair = await converse(body);
      return sendJSON(res, 200, pair);
    }
    if (req.method === "POST" && url === "/character/chat") {
      const b = await readBody(req) as ChatReq;
      const reply = await chat(b);
      return sendJSON(res, 200, { reply });
    }
    if (req.method === "POST" && url === "/character/request") {
      const b = await readBody(req) as ReqReq;
      const msg = await request(b);
      return sendJSON(res, 200, { msg });
    }
    if (req.method === "POST" && url === "/character/romance") {
      const b = await readBody(req) as RomanceReq;
      const msg = await romance(b);
      return sendJSON(res, 200, { msg });
    }
    return sendJSON(res, 404, { error: "not found" });
  } catch (e: any) {
    console.error(`[err] ${req.method} ${url}:`, e?.message ?? e);
    return sendJSON(res, 500, { error: String(e?.message ?? e) });
  }
});

server.listen(PORT, () => {
  console.log(`[yocat-server] listening on http://localhost:${PORT}`);
  console.log(`[yocat-server] LLM → ${LLM_BASE} (model ${MODEL})`);
});
