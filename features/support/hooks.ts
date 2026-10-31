// features/support/hooks.ts
// Cucumber.js 生命週期 hooks — 依 BDD-server.gen.md §A.3 Before Hook（具體版）。
// 確保每個 scenario 獨立 seed、乾淨狀態，無跨 scenario 狀態殘留。

import { BeforeAll, AfterAll, Before, After, setDefaultTimeout } from '@cucumber/cucumber';
import { ApiWorld } from './world';

setDefaultTimeout(30_000);

BeforeAll(async () => {
  // 1. 建立 DB 連線 pool（連線字串從 ENV 讀取，指向測試 DB）
  // 2. 執行 Flyway migration（若 test DB 未 up）
  // 3. flush 測試用 Redis DB（DB 15，非生產）
  // TODO: implement — 見 SCHEMA.md §底層遷移 / EDD §11.3
});

AfterAll(async () => {
  // 關閉 DB pool + Redis client
  // TODO: implement — close connections
});

Before(async function (this: ApiWorld) {
  // 每個 scenario 前 TRUNCATE 所有 business tables，保證 clean state（§A.3）
  await this.db.clean();
  this.authToken = null;
  this.accountId = null;
  this.orderId = null;
  this.idempotencyKey = null;
  this.runId = null;
  this.lastResponse = { status: 0, body: null };
});

After(async function (this: ApiWorld) {
  // 清掉本 scenario 寫入的測試 Redis 鍵（限流/session），避免污染下一個 scenario
  // TODO: implement — 逐 key 清理或 flushDb（測試 DB）
  this.lastResponse = { status: 0, body: null };
});
