// features/step_definitions/server_steps.ts
// Auto-generated Step Definition skeleton for yocat-office-backend Server BDD.
// 規則：BDD-server.gen.md §B（Given = db.seed；When = client.request 捕獲 4xx/5xx；Then = 斷言 status + DB）。
// 每個 stub 含 inline 注釋指向對應 API endpoint（見 API.md）或 SCHEMA table（見 SCHEMA.md）。
// stub 不含業務邏輯實作，僅 return 'pending' / TODO，供開發者或 AI codegen 填入。

import { Given, When, Then } from '@cucumber/cucumber';
import assert from 'node:assert/strict';
import { ApiWorld } from '../support/world';

// ───────────────────────────────────────────────────────────────────────────
// Background / 共用前置狀態（Given — 唯一合法模式：db.seed，見 §B.0）
// ───────────────────────────────────────────────────────────────────────────

Given('資料庫已初始化為乾淨狀態', async function (this: ApiWorld) {
  // DB: 清空所有 business tables — 見 SCHEMA.md §3；clean() 於 Before hook 亦呼叫
  await this.db.clean();
});

Given('系統已存在一個啟用中的帳號，email 為 {string}、密碼為 {string}', async function (this: ApiWorld, _email: string, _password: string) {
  // DB: member.accounts + member.sessions — 見 SCHEMA.md §3.1
  return 'pending'; // TODO: this.db.seed({ 'member.accounts': [...] })
});

Given('已存在登入中的帳號，其錢包餘額為 cash {int}、gems {int}', async function (this: ApiWorld, _cash: number, _gems: number) {
  // DB: member.accounts + wallet.wallets — 見 SCHEMA.md §3.1 / §3.3；並 auth.loginAs('USER')
  return 'pending'; // TODO: seed account + wallet, set this.authToken
});

Given('已存在登入中的帳號，其錢包餘額為 cash {int}、gems {int}（可空）', async function (this: ApiWorld, _cash: number, _gems: number) {
  return 'pending'; // TODO
});

Given('已存在一個登入中的帳號', async function (this: ApiWorld) {
  // DB: member.accounts — 見 SCHEMA.md §3.1；auth.loginAs('USER')
  return 'pending'; // TODO
});

Given('使用者持有有效的 session token', async function (this: ApiWorld) {
  // DB: member.sessions（expires_at > now）— 見 SCHEMA.md §3.1；設定 this.authToken
  this.authToken = await this.auth.loginAs('USER');
});

Given('使用者未提供有效的 session token', function (this: ApiWorld) {
  this.authToken = null;
});

Given('使用者未提供 session token', function (this: ApiWorld) {
  this.authToken = null;
});

Given('使用者提供一個已失效的 session token', function (this: ApiWorld) {
  // DB: member.sessions expires_at < now — 見 SCHEMA.md §3.1
  this.authToken = 'expired-token-0000';
});

Given('使用者未提供有效的 session token（公開端點例外）', function (this: ApiWorld) {
  this.authToken = null;
});

Given('email {string} 尚未被註冊', async function (this: ApiWorld, _email: string) {
  // DB: 確認 member.accounts 無該 email — 見 SCHEMA.md §3.1
  return 'pending'; // TODO
});

Given('email {string} 已被註冊', async function (this: ApiWorld, _email: string) {
  // DB: member.accounts 既有該 email — 見 SCHEMA.md §3.1
  return 'pending'; // TODO
});

Given('email {string} 不存在於系統', async function (this: ApiWorld, _email: string) {
  return 'pending'; // TODO
});

Given('帳號 {string} 為啟用狀態', async function (this: ApiWorld, _email: string) {
  return 'pending'; // TODO: seed member.accounts status=active
});

// ─── wallet 前置 ───
Given('使用者的現金餘額不少於欲扣款金額', async function (this: ApiWorld) {
  return 'pending'; // TODO: seed wallet.wallets cash 充足
});

Given('使用者的現金餘額小於欲扣款金額', async function (this: ApiWorld) {
  return 'pending'; // TODO: seed wallet.wallets cash 不足
});

Given('使用者的現金餘額為固定的已知金額', async function (this: ApiWorld) {
  // DB: wallet.wallets cash 設為固定已知值供併發對帳 — 見 SCHEMA.md §3.3
  return 'pending'; // TODO: this.db.seed({ 'wallet.wallets': [...] })
});

Given('該帳號在本時間窗內的錢包請求次數已達上限', async function (this: ApiWorld) {
  // Redis: rate_limit:wallet:{accountId} 預填至閾值 — 見 SCHEMA.md §19 / CONSTANTS §6
  return 'pending'; // TODO: this.redis.set(...)
});

Given('同一來源 IP 在本時間窗內的註冊次數已達上限', async function (this: ApiWorld) {
  // Redis: rate_limit:register:{ip} — 見 CONSTANTS §6
  return 'pending'; // TODO
});

// ─── deposit 前置 ───
Given('儲值目錄已載入，包含寶石包與資本包', async function (this: ApiWorld) {
  // DB: deposit.deposit_pack — 見 SCHEMA.md deposit schema
  return 'pending'; // TODO
});

Given('使用者提供一個全新的冪等鍵', function (this: ApiWorld) {
  this.idempotencyKey = '7d793037-a076-4c05-9a70-b7e8b2e3c4f5';
});

Given('使用者已以某冪等鍵建立過一筆已確認的儲值訂單', async function (this: ApiWorld) {
  // DB: deposit.deposit_order status=confirmed, idempotency_key — 見 SCHEMA.md deposit schema
  return 'pending'; // TODO
});

Given('使用者已以某冪等鍵建立過一筆儲值訂單', async function (this: ApiWorld) {
  return 'pending'; // TODO
});

// ─── entitlement 前置 ───
Given('使用者尚未擁有外觀項目 {string}', async function (this: ApiWorld, _itemId: string) {
  return 'pending'; // TODO: 確認 entitlement.entitlements 無該列
});

Given('使用者尚未擁有劇本項目 {string}', async function (this: ApiWorld, _itemId: string) {
  return 'pending'; // TODO
});

Given('使用者尚未擁有夢想員工 {string}', async function (this: ApiWorld, _itemId: string) {
  return 'pending'; // TODO
});

Given('使用者已擁有外觀項目 {string}', async function (this: ApiWorld, _itemId: string) {
  // DB: entitlement.entitlements UNIQUE(account,kind,item) — 見 SCHEMA.md entitlement schema
  return 'pending'; // TODO
});

Given('使用者已擁有外觀項目 {string} 與夢想員工 {string}', async function (this: ApiWorld, _a: string, _b: string) {
  return 'pending'; // TODO
});

Given('使用者的寶石餘額足以支付該項目', async function (this: ApiWorld) {
  return 'pending'; // TODO
});

Given('使用者的餘額小於該項目價格', async function (this: ApiWorld) {
  return 'pending'; // TODO
});

Given('該帳號曾在遊戲內破產並重經營', async function (this: ApiWorld) {
  // 說明：破產 recycle 僅影響遊戲內狀態，不觸及 entitlement BC（PRD §5.3 AC-5）
  return 'pending'; // TODO
});

// ─── record 前置 ───
Given('該帳號已有破產與成就各一筆經營史記錄', async function (this: ApiWorld) {
  // DB: record.records — 見 SCHEMA.md record schema
  return 'pending'; // TODO
});

// ─── leaderboard 前置 ───
Given('排行榜已有若干筆沙盒模式的既有分數', async function (this: ApiWorld) {
  // DB: leaderboard.leaderboard — 見 SCHEMA.md leaderboard schema
  return 'pending'; // TODO
});

Given('排行榜已有超過 100 筆分數', async function (this: ApiWorld) {
  return 'pending'; // TODO
});

Given('使用者已以場次 {string} 提交過分數', async function (this: ApiWorld, runId: string) {
  this.runId = runId;
  return 'pending'; // TODO: seed leaderboard.leaderboard run_id
});

// ─── game-save 前置 ───
Given('該帳號的預設存檔槽已有一份存檔', async function (this: ApiWorld) {
  // DB: game_save.saves (account_id, slot='main') — 見 SCHEMA.md game_save schema
  return 'pending'; // TODO
});

Given('該帳號的預設存檔槽尚無任何存檔', async function (this: ApiWorld) {
  return 'pending'; // TODO
});

Given('使用者已在裝置 A 上傳了一份辦公室存檔', async function (this: ApiWorld) {
  return 'pending'; // TODO: seed game_save.saves
});

// ─── architecture / event 前置 ───
Given('系統由 7 個有界上下文組成：member、wallet、deposit、entitlement、record、leaderboard、game-save', function () {
  return 'pending'; // TODO: 載入 Spring Modulith ApplicationModules
});

Given('各有界上下文各自擁有獨立的資料庫 schema', function () {
  return 'pending'; // TODO
});

Given('entitlement 購買流程需呼叫 wallet 進行扣款', function () {
  return 'pending'; // TODO
});

Given('deposit 確認後需通知 wallet 入帳', function () {
  return 'pending'; // TODO
});

Given('多個有界上下文使用 Redis 作為快取與限流後援', function () {
  return 'pending'; // TODO
});

Given('7 個有界上下文之間存在依賴關係', function () {
  return 'pending'; // TODO
});

Given('僅啟動 {string} 有界上下文，其餘模組以外部替身隔離', function (_bc: string) {
  return 'pending'; // TODO: WireMock stub 其他 BC
});

Given('跨模組通訊一律透過交易式 Outbox 的領域事件進行', function () {
  return 'pending'; // TODO
});

Given('member 已在同一交易建立新帳號', function () {
  return 'pending'; // TODO
});

Given('wallet 尚未為該帳號建立錢包', function () {
  return 'pending'; // TODO
});

Given('deposit 已建立一筆已確認的儲值訂單', function () {
  return 'pending'; // TODO
});

Given('wallet 尚未處理某筆儲值確認事件', function () {
  return 'pending'; // TODO
});

Given('entitlement 已在同一交易完成扣款與授予', function () {
  return 'pending'; // TODO
});

Given('leaderboard 已以場次 ID upsert 寫入分數', function () {
  return 'pending'; // TODO
});

// ───────────────────────────────────────────────────────────────────────────
// When — 單一觸發動作（client 呼叫 API，捕獲完整 response，見 §B.2）
// ───────────────────────────────────────────────────────────────────────────

When('使用者以合法 email、顯示名稱與密碼送出註冊', async function (this: ApiWorld) {
  // POST /auth/register — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'post', '/auth/register');
});

When('使用者以 email {string} 送出註冊', async function (this: ApiWorld, _email: string) {
  // POST /auth/register — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'post', '/auth/register');
});

When('使用者以 email {string}、顯示名稱 {string}、密碼 {string} 送出註冊', async function (this: ApiWorld, _e: string, _n: string, _p: string) {
  // POST /auth/register — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'post', '/auth/register');
});

When('使用者以正確 email 與密碼登入', async function (this: ApiWorld) {
  // POST /auth/login — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'post', '/auth/login');
});

When('使用者以正確 email 但錯誤密碼登入', async function (this: ApiWorld) {
  // POST /auth/login — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'post', '/auth/login');
});

When('使用者以未註冊的 email 登入', async function (this: ApiWorld) {
  // POST /auth/login — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'post', '/auth/login');
});

When('使用者查詢「我的資料」', async function (this: ApiWorld) {
  // GET /auth/me — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'get', '/auth/me');
});

When('使用者嘗試查詢「我的資料」', async function (this: ApiWorld) {
  // GET /auth/me — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'get', '/auth/me');
});

When('使用者登出', async function (this: ApiWorld) {
  // POST /auth/logout — 見 API.md §3 BC: member（冪等）
  this.lastResponse = await callApi(this, 'post', '/auth/logout');
});

When('使用者再次送出註冊', async function (this: ApiWorld) {
  // POST /auth/register — 見 API.md §3 BC: member
  this.lastResponse = await callApi(this, 'post', '/auth/register');
});

// ─── wallet ───
When('使用者以中寶石包儲值', async function (this: ApiWorld) {
  // POST /wallet/topup {pack:"g_mid"} — 見 API.md §3 BC: wallet
  this.lastResponse = await callApi(this, 'post', '/wallet/topup');
});

When('使用者以現金自訂金額 {int} 儲值', async function (this: ApiWorld, _amount: number) {
  // POST /wallet/topup {currency:"cash", amount} — 見 API.md §3 BC: wallet
  this.lastResponse = await callApi(this, 'post', '/wallet/topup');
});

When('使用者以現金花費 {int}', async function (this: ApiWorld, _amount: number) {
  // POST /wallet/spend — 見 API.md §3 BC: wallet
  this.lastResponse = await callApi(this, 'post', '/wallet/spend');
});

When('使用者嘗試以現金花費超過餘額的金額', async function (this: ApiWorld) {
  // POST /wallet/spend — 見 API.md §3 BC: wallet
  this.lastResponse = await callApi(this, 'post', '/wallet/spend');
});

When('使用者嘗試花費', async function (this: ApiWorld) {
  // POST /wallet/spend — 見 API.md §3 BC: wallet
  this.lastResponse = await callApi(this, 'post', '/wallet/spend');
});

When('使用者以 {string} 操作提交金額 {int}', async function (this: ApiWorld, operation: string, _amount: number) {
  // POST /wallet/topup 或 /wallet/spend — 見 API.md §3 BC: wallet
  const path = operation === '儲值' ? '/wallet/topup' : '/wallet/spend';
  this.lastResponse = await callApi(this, 'post', path);
});

When('使用者再次送出花費請求', async function (this: ApiWorld) {
  // POST /wallet/spend — 見 API.md §3 BC: wallet
  this.lastResponse = await callApi(this, 'post', '/wallet/spend');
});

When('同一帳號同時送出多筆現金花費請求', async function (this: ApiWorld) {
  // 併發 POST /wallet/spend（行鎖序列化，EDD §4.5.6）— 見 API.md §3 BC: wallet
  return 'pending'; // TODO: Promise.all 併發呼叫並收集各筆 status（200/402）
});

// ─── deposit ───
When('使用者查詢儲值包目錄', async function (this: ApiWorld) {
  // GET /deposit/packs — 見 API.md §3 BC: deposit（公開）
  this.lastResponse = await callApi(this, 'get', '/deposit/packs');
});

When('使用者以大寶石包建立儲值訂單', async function (this: ApiWorld) {
  // POST /deposit/checkout {packId:"g_big"} + Idempotency-Key — 見 API.md §3 BC: deposit
  this.lastResponse = await callApi(this, 'post', '/deposit/checkout');
});

When('使用者以相同的冪等鍵再次送出相同的儲值請求', async function (this: ApiWorld) {
  // POST /deposit/checkout（相同 Idempotency-Key）— 見 API.md §7 冪等
  this.lastResponse = await callApi(this, 'post', '/deposit/checkout');
});

When('使用者以相同冪等鍵但不同的儲值包送出請求', async function (this: ApiWorld) {
  // POST /deposit/checkout — 見 API.md §7.3（命中但 body 不同 → 409）
  this.lastResponse = await callApi(this, 'post', '/deposit/checkout');
});

When('使用者以不在目錄中的儲值包建立訂單', async function (this: ApiWorld) {
  // POST /deposit/checkout {packId 非法} — 見 API.md §3 BC: deposit
  this.lastResponse = await callApi(this, 'post', '/deposit/checkout');
});

When('使用者在未提供冪等鍵的情況下建立儲值訂單', async function (this: ApiWorld) {
  // POST /deposit/checkout（缺 Idempotency-Key → 400）— 見 API.md §2.1
  this.idempotencyKey = null;
  this.lastResponse = await callApi(this, 'post', '/deposit/checkout');
});

When('使用者嘗試建立儲值訂單', async function (this: ApiWorld) {
  // POST /deposit/checkout — 見 API.md §3 BC: deposit
  this.lastResponse = await callApi(this, 'post', '/deposit/checkout');
});

// ─── entitlement ───
When('使用者以寶石購買外觀項目 {string}', async function (this: ApiWorld, _itemId: string) {
  // POST /entitlements/buy {kind:"cosmetic",currency:"gems"} — 見 API.md §3 BC: entitlement
  this.lastResponse = await callApi(this, 'post', '/entitlements/buy');
});

When('系統以零金額免費授予該劇本項目', async function (this: ApiWorld) {
  // POST /entitlements/buy {kind:"scenario", amount:0} — 見 API.md §3 BC: entitlement
  this.lastResponse = await callApi(this, 'post', '/entitlements/buy');
});

When('使用者再次購買相同的外觀項目 {string}', async function (this: ApiWorld, _itemId: string) {
  // POST /entitlements/buy — 見 API.md §3 BC: entitlement（UNIQUE → 409）
  this.lastResponse = await callApi(this, 'post', '/entitlements/buy');
});

When('使用者嘗試購買該夢想員工', async function (this: ApiWorld) {
  // POST /entitlements/buy {kind:"dream"} — 見 API.md §3 BC: entitlement
  this.lastResponse = await callApi(this, 'post', '/entitlements/buy');
});

When('使用者以類別 {string} 與項目 {string} 送出購買', async function (this: ApiWorld, _kind: string, _itemId: string) {
  // POST /entitlements/buy — 見 API.md §3 BC: entitlement
  this.lastResponse = await callApi(this, 'post', '/entitlements/buy');
});

When('使用者嘗試購買外觀項目', async function (this: ApiWorld) {
  // POST /entitlements/buy — 見 API.md §3 BC: entitlement
  this.lastResponse = await callApi(this, 'post', '/entitlements/buy');
});

When('使用者查詢已購權益清單', async function (this: ApiWorld) {
  // GET /entitlements — 見 API.md §3 BC: entitlement
  this.lastResponse = await callApi(this, 'get', '/entitlements');
});

When('使用者嘗試查詢已購權益清單', async function (this: ApiWorld) {
  // GET /entitlements — 見 API.md §3 BC: entitlement
  this.lastResponse = await callApi(this, 'get', '/entitlements');
});

// ─── record ───
When('使用者新增一筆破產類型的經營史記錄', async function (this: ApiWorld) {
  // POST /records {type:"bankruptcy"} — 見 API.md §3 BC: record
  this.lastResponse = await callApi(this, 'post', '/records');
});

When('使用者以類型 {string} 新增經營史記錄', async function (this: ApiWorld, _type: string) {
  // POST /records — 見 API.md §3 BC: record
  this.lastResponse = await callApi(this, 'post', '/records');
});

When('使用者嘗試新增經營史記錄', async function (this: ApiWorld) {
  // POST /records — 見 API.md §3 BC: record
  this.lastResponse = await callApi(this, 'post', '/records');
});

When('使用者查詢全部經營史', async function (this: ApiWorld) {
  // GET /records — 見 API.md §3 BC: record
  this.lastResponse = await callApi(this, 'get', '/records');
});

When('使用者以破產類型篩選查詢經營史', async function (this: ApiWorld) {
  // GET /records?type=bankruptcy — 見 API.md §3 BC: record
  this.lastResponse = await callApi(this, 'get', '/records?type=bankruptcy');
});

// ─── leaderboard ───
When('使用者查詢排行榜前 20 名', async function (this: ApiWorld) {
  // GET /leaderboard?limit=20 — 見 API.md §3 BC: leaderboard（公開）
  this.lastResponse = await callApi(this, 'get', '/leaderboard?limit=20');
});

When('使用者以筆數 {string} 查詢排行榜', async function (this: ApiWorld, limit: string) {
  // GET /leaderboard?limit=<n> — 見 API.md §3 BC: leaderboard
  this.lastResponse = await callApi(this, 'get', `/leaderboard?limit=${limit}`);
});

When('使用者以場次 {string} 提交分數 {int}', async function (this: ApiWorld, runId: string, _score: number) {
  // POST /leaderboard — 見 API.md §3 BC: leaderboard
  this.runId = runId;
  this.lastResponse = await callApi(this, 'post', '/leaderboard');
});

When('使用者以相同場次 {string} 提交更高的分數', async function (this: ApiWorld, runId: string) {
  // POST /leaderboard（同 run_id upsert）— 見 API.md §7 冪等
  this.runId = runId;
  this.lastResponse = await callApi(this, 'post', '/leaderboard');
});

When('使用者在未提供場次 ID 的情況下提交分數', async function (this: ApiWorld) {
  // POST /leaderboard（缺 runId → 400）— 見 API.md §3 BC: leaderboard
  this.lastResponse = await callApi(this, 'post', '/leaderboard');
});

When('使用者嘗試提交分數上榜', async function (this: ApiWorld) {
  // POST /leaderboard — 見 API.md §3 BC: leaderboard
  this.lastResponse = await callApi(this, 'post', '/leaderboard');
});

// ─── game-save ───
When('使用者將辦公室存檔上傳至預設存檔槽', async function (this: ApiWorld) {
  // PUT /save {data} — 見 API.md §3 BC: game-save
  this.lastResponse = await callApi(this, 'put', '/save');
});

When('使用者在未提供存檔資料的情況下上傳存檔', async function (this: ApiWorld) {
  // PUT /save（缺 data → 400）— 見 API.md §3 BC: game-save
  this.lastResponse = await callApi(this, 'put', '/save');
});

When('使用者上傳一個內容為空物件的存檔', async function (this: ApiWorld) {
  // PUT /save {data:{}} — 見 API.md §3 BC: game-save
  this.lastResponse = await callApi(this, 'put', '/save');
});

When('使用者嘗試上傳存檔', async function (this: ApiWorld) {
  // PUT /save — 見 API.md §3 BC: game-save
  this.lastResponse = await callApi(this, 'put', '/save');
});

When('使用者查詢預設存檔槽', async function (this: ApiWorld) {
  // GET /save?slot=main — 見 API.md §3 BC: game-save
  this.lastResponse = await callApi(this, 'get', '/save?slot=main');
});

When('使用者在裝置 B 以同一帳號登入並查詢存檔', async function (this: ApiWorld) {
  // GET /save（跨裝置還原）— 見 API.md §3 BC: game-save
  this.lastResponse = await callApi(this, 'get', '/save?slot=main');
});

// ─── architecture / event（建置期 / 事件驗證，非 HTTP）───
When('執行模組邊界靜態分析', function () {
  return 'pending'; // TODO: Spring Modulith ApplicationModules.verify()
});

When('執行模組依賴分析', function () {
  return 'pending'; // TODO
});

When('deposit 發布儲值確認領域事件', function () {
  return 'pending'; // TODO: 觸發並攔截 Outbox 事件
});

When('檢視各模組寫入的 Redis 鍵', function () {
  return 'pending'; // TODO
});

When('執行模組化測試驗證依賴圖', function () {
  return 'pending'; // TODO: ModularityTests
});

When('該有界上下文啟動應用情境', function () {
  return 'pending'; // TODO: 隔離啟動 ApplicationContext
});

When('該交易提交完成', function () {
  return 'pending'; // TODO
});

When('wallet 收到合規的會員註冊事件', function () {
  return 'pending'; // TODO: 投遞 MemberRegistered 至 wallet consumer
});

When('wallet 收到該儲值確認事件並再次收到相同事件', function () {
  return 'pending'; // TODO: 投遞兩次 DepositConfirmed（event_id dedup）
});

When('該訂單交易提交完成', function () {
  return 'pending'; // TODO
});

When('該寫入完成', function () {
  return 'pending'; // TODO
});

// ───────────────────────────────────────────────────────────────────────────
// Then — 斷言 HTTP 狀態 + 業務/持久化結果（見 §B.3）
// ───────────────────────────────────────────────────────────────────────────

Then('系統回應 HTTP {int} 並附帶 session token', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  // TODO: assert body.token 存在且為 32B hex — 見 API.md AuthResponse
});

Then('系統回應 HTTP {int} 並簽發有效期 30 天的新 session token', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO: 驗證 sessions.expires_at ≈ now + 30d
});

Then('系統回應 HTTP {int} 並提示 {string}', function (this: ApiWorld, status: number, _message: string) {
  assert.equal(this.lastResponse.status, status);
  // TODO: assert body.error 包含訊息 — 見 API.md §5 Error Response Schema
});

Then('系統回應 HTTP {int} 並{word}', function (this: ApiWorld, status: number, _rest: string) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並回傳加值後的最新錢包餘額', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO: DB wallet.wallets 餘額已增加
});

Then('系統回應 HTTP {int} 並回傳扣款後的最新錢包餘額', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並回傳扣款後的錢包與最新權益清單', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並回傳狀態為已確認的訂單', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並回傳所有可購買的儲值包', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並回傳該帳號的經營史清單', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並依分數由高到低回傳上榜清單', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並確認記錄已寫入', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO: DB record.records 新增一列
});

Then('系統回應 HTTP {int} 並確認上榜成功', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並確認存檔已寫入', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO: DB game_save.saves upsert
});

Then('系統回應 HTTP {int} 並回傳最新存檔資料與更新時間', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 且存檔內容為空', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO: body.save === null（非 404）
});

Then('系統回應 HTTP {int} 並包含帳號、錢包與已購權益清單', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並完整列出先前購買的所有權益', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並提示冪等鍵衝突', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

Then('系統回應 HTTP {int} 並要求提供冪等鍵', function (this: ApiWorld, status: number) {
  assert.equal(this.lastResponse.status, status);
  return 'pending'; // TODO
});

// ─── 業務語言斷言（非 @contract）───
Then('回應包含新帳號資料與初始錢包（cash 為 {int}、gems 為 {int}）', function (this: ApiWorld, _cash: number, _gems: number) {
  return 'pending'; // TODO
});

Then('回應包含帳號資料與當前錢包餘額', function () {
  return 'pending'; // TODO
});

Then('回應包含帳號、錢包與已購權益清單', function () {
  return 'pending'; // TODO
});

Then('回應包含訂單 ID、包 ID、幣別與入帳數量', function () {
  return 'pending'; // TODO
});

Then('每個儲值包包含包 ID、幣別、入帳數量與顯示名稱', function () {
  return 'pending'; // TODO
});

Then('每筆上榜資料包含場次 ID、顯示名稱、模式與分數', function () {
  return 'pending'; // TODO
});

Then('權益清單在未購買任何項目時為空陣列', function () {
  return 'pending'; // TODO
});

Then('系統為該帳號建立一筆開帳的錢包異動記錄', function () {
  return 'pending'; // TODO: DB wallet.wallet_ledger reason='init'
});

Then('系統在同一交易寫入一筆原因為 topup 的錢包異動記錄', function () {
  return 'pending'; // TODO: DB wallet.wallet_ledger reason='topup'
});

Then('系統在同一交易寫入一筆購買原因的錢包異動記錄', function () {
  return 'pending'; // TODO: reason='buy_<kind>'
});

Then('系統寫入一筆負異動的錢包帳本記錄', function () {
  return 'pending'; // TODO: delta < 0
});

Then('所有成功扣款的總和不超過初始餘額', function () {
  return 'pending'; // TODO: Σ 成功扣款 ≤ 初始餘額
});

Then('最終餘額等於初始餘額減去所有成功扣款的總和', function () {
  return 'pending'; // TODO: DB wallet.wallets.cash == 初始 − Σ 成功扣款
});

Then('錢包餘額與帳本累積完全一致且無負餘額', function () {
  return 'pending'; // TODO: 餘額 == Σ ledger.delta 且 ≥ 0（0 筆不一致）
});

Then('異動後的帳本餘額等於儲值前餘額加上本次金額', function () {
  return 'pending'; // TODO
});

Then('系統不建立重複帳號', function () {
  return 'pending'; // TODO
});

Then('系統不寫入任何帳號資料', function () {
  return 'pending'; // TODO
});

Then('系統不簽發任何 session token', function () {
  return 'pending'; // TODO
});

Then('系統不透露該 email 是否已註冊', function () {
  return 'pending'; // TODO
});

Then('系統不回傳任何帳號資料', function () {
  return 'pending'; // TODO
});

Then('系統不寫入任何錢包異動記錄', function () {
  return 'pending'; // TODO
});

Then('系統不變更任何錢包餘額', function () {
  return 'pending'; // TODO
});

Then('回應附帶使用者當前的錢包餘額', function () {
  return 'pending'; // TODO
});

Then('回應附帶建議的重試等待時間', function () {
  return 'pending'; // TODO: Retry-After header
});

Then('回應的最新餘額反映本次加值', function () {
  return 'pending'; // TODO
});

Then('回傳的最新餘額反映本次加值', function () {
  return 'pending'; // TODO
});

Then('系統確認儲值成功並回傳最新錢包餘額', function () {
  return 'pending'; // TODO
});

Then('系統確認儲值成功', function () {
  return 'pending'; // TODO
});

Then('系統確認登出成功', function () {
  return 'pending'; // TODO
});

Then('原先的 session token 不再能存取需認證的資源', function () {
  return 'pending'; // TODO
});

Then('系統仍確認登出成功', function () {
  return 'pending'; // TODO
});

Then('系統不回應認證錯誤', function () {
  return 'pending'; // TODO
});

Then('系統回放先前那筆訂單的結果', function () {
  return 'pending'; // TODO
});

Then('使用者的錢包僅入帳一次', function () {
  return 'pending'; // TODO
});

Then('系統不建立新的訂單', function () {
  return 'pending'; // TODO
});

Then('系統不建立任何訂單', function () {
  return 'pending'; // TODO
});

Then('新權益出現在使用者的權益清單中', function () {
  return 'pending'; // TODO
});

Then('使用者取得該劇本權益', function () {
  return 'pending'; // TODO
});

Then('使用者取得該權益', function () {
  return 'pending'; // TODO
});

Then('系統不重複扣款也不重複授予', function () {
  return 'pending'; // TODO
});

Then('使用者未取得該權益且餘額維持不變', function () {
  return 'pending'; // TODO
});

Then('系統不授予任何權益', function () {
  return 'pending'; // TODO
});

Then('權益清單依取得時間排序', function () {
  return 'pending'; // TODO
});

Then('系統不回傳任何權益資料', function () {
  return 'pending'; // TODO
});

Then('該筆記錄以 append-only 方式歸屬於該帳號', function () {
  return 'pending'; // TODO
});

Then('系統不新增任何經營史記錄', function () {
  return 'pending'; // TODO
});

Then('清單依建立時間由新到舊排序', function () {
  return 'pending'; // TODO
});

Then('系統僅回傳破產類型的經營史記錄', function () {
  return 'pending'; // TODO
});

Then('回傳筆數不超過單次上限 100 筆', function () {
  return 'pending'; // TODO
});

Then('系統回傳的筆數不超過 {string}', function (_max: string) {
  return 'pending'; // TODO
});

Then('該場次以使用者的顯示名稱歸戶記錄', function () {
  return 'pending'; // TODO
});

Then('該場次的排行記錄更新為最新分數', function () {
  return 'pending'; // TODO
});

Then('排行榜中該場次仍只有一筆記錄', function () {
  return 'pending'; // TODO
});

Then('系統不寫入任何排行記錄', function () {
  return 'pending'; // TODO
});

Then('該存檔的最後更新時間被更新為當下', function () {
  return 'pending'; // TODO
});

Then('系統不寫入任何存檔', function () {
  return 'pending'; // TODO
});

Then('後續查詢可取回該空物件存檔', function () {
  return 'pending'; // TODO
});

Then('系統不回應資源不存在錯誤', function () {
  return 'pending'; // TODO
});

Then('系統回傳裝置 A 寫入的最新存檔內容', function () {
  return 'pending'; // TODO
});

Then('兩台裝置看到的存檔資料一致', function () {
  return 'pending'; // TODO
});

// ─── architecture / event 斷言 ───
Then('不存在任何跨 schema 的外鍵或直接查詢', function () {
  return 'pending'; // TODO
});

Then('member 的錢包資料一律經 wallet 的公開介面取得', function () {
  return 'pending'; // TODO
});

Then('entitlement 僅透過 wallet 的公開服務介面呼叫扣款', function () {
  return 'pending'; // TODO
});

Then('不存在對其他模組內部類別的直接依賴', function () {
  return 'pending'; // TODO
});

Then('該事件透過交易式 Outbox 可靠投遞', function () {
  return 'pending'; // TODO
});

Then('事件結構符合既有合約且向後相容', function () {
  return 'pending'; // TODO
});

Then('每個模組的鍵都帶有自己的命名空間前綴', function () {
  return 'pending'; // TODO
});

Then('不同模組之間不共用同一組可變快取鍵', function () {
  return 'pending'; // TODO
});

Then('模組依賴關係為有向無環圖', function () {
  return 'pending'; // TODO
});

Then('不存在任何循環依賴', function () {
  return 'pending'; // TODO
});

Then('該有界上下文成功啟動', function () {
  return 'pending'; // TODO
});

Then('其健康檢查回報就緒', function () {
  return 'pending'; // TODO
});

Then('系統發布會員註冊事件', function () {
  return 'pending'; // TODO
});

Then('事件內容包含帳號 ID 與 email', function () {
  return 'pending'; // TODO
});

Then('事件結構符合既有合約版本', function () {
  return 'pending'; // TODO
});

Then('wallet 為該帳號建立初始餘額為零的錢包', function () {
  return 'pending'; // TODO
});

Then('wallet 寫入一筆原因為開帳的錢包異動記錄', function () {
  return 'pending'; // TODO
});

Then('整個過程不需呼叫 member 的任何 HTTP 介面', function () {
  return 'pending'; // TODO
});

Then('系統發布儲值確認事件', function () {
  return 'pending'; // TODO
});

Then('事件內容包含帳號 ID、訂單 ID、幣別與金額', function () {
  return 'pending'; // TODO
});

Then('wallet 僅對該帳號入帳一次', function () {
  return 'pending'; // TODO
});

Then('wallet 以事件 ID 去重避免重複入帳', function () {
  return 'pending'; // TODO
});

Then('系統發布權益授予事件', function () {
  return 'pending'; // TODO
});

Then('事件內容包含帳號 ID、類別與項目 ID', function () {
  return 'pending'; // TODO
});

Then('系統發布分數提交事件', function () {
  return 'pending'; // TODO
});

Then('事件內容包含帳號 ID、場次 ID、模式與分數', function () {
  return 'pending'; // TODO
});

// ───────────────────────────────────────────────────────────────────────────
// 錯誤訊息斷言（RegExp stub — 捕獲 zh-TW 「…」 訊息；見 API.md §5 Error Response Schema）
// 說明：Gherkin 以 「…」 標註業務錯誤訊息（非 Cucumber {string}），故以 RegExp 捕獲訊息內容。
// 各 RegExp 前綴互斥（HTTP 回應 / 認證失敗 / 系統拒絕*），不會造成 ambiguous step。
// ───────────────────────────────────────────────────────────────────────────

Then(/^系統回應 HTTP (\d+) 並提示「(.+)」$/, function (this: ApiWorld, status: string, _message: string) {
  assert.equal(this.lastResponse.status, Number(status));
  // TODO: assert body.error 包含訊息 — 見 API.md §5.2 Error Code Registry
});

Then(/^系統回應認證失敗並提示「(.+)」$/, function (this: ApiWorld, _message: string) {
  return 'pending'; // TODO: assert 401 且 body.error 相符（不區分帳號不存在/密碼錯）— API.md §4.3
});

Then(/^系統拒絕(?:存取|寫入|操作|購買|註冊|存檔)[^「]*「(.+)」$/, function (this: ApiWorld, _message: string) {
  return 'pending'; // TODO: assert 對應 4xx + body.error 相符 — 見 API.md §5.2
});

Then('系統拒絕請求並要求提供冪等鍵', function (this: ApiWorld) {
  return 'pending'; // TODO: assert 400 缺 Idempotency-Key — 見 API.md §2.1 / §7
});

Then('系統確認存檔已寫入', function (this: ApiWorld) {
  return 'pending'; // TODO: DB game_save.saves upsert — 見 SCHEMA.md game_save schema
});

// ───────────────────────────────────────────────────────────────────────────
// Helper — 單一 HTTP 呼叫封裝（捕獲 4xx/5xx，不 throw），見 §B.2
// ───────────────────────────────────────────────────────────────────────────

async function callApi(
  world: ApiWorld,
  method: 'get' | 'post' | 'put' | 'delete' | 'patch',
  path: string
): Promise<{ status: number; body: unknown }> {
  const headers: Record<string, string> = {};
  if (world.authToken) headers['Authorization'] = `Bearer ${world.authToken}`;
  if (world.idempotencyKey) headers['Idempotency-Key'] = world.idempotencyKey;
  // TODO: request body 依各 endpoint DTO 由 world 狀態組裝（見 API.md §3 各 Request DTO）
  const res = await world.client.request({ method, url: path, headers });
  return { status: res.status, body: res.data };
}
