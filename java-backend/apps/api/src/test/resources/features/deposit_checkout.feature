# features/deposit/deposit_checkout.feature
# 來源：PRD §4.4 / §5 deposit（US-DEP-001，P1）
# 上游 API：API.md §3 BC: deposit（GET /deposit/packs、POST /deposit/checkout）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030
# 核心：Phase A 假付費直接確認，經 DepositConfirmed 事件由 wallet BC 入帳；以 Idempotency-Key 冪等。

@deposit @p1
Feature: 儲值訂單建立與目錄查詢
  作為代表玩家的客戶端整合工程師
  我希望查詢可購買的儲值包並以冪等方式建立儲值訂單
  以便讓玩家的儲值動作可信、可對帳且重試不重複入帳

  Background:
    Given 資料庫已初始化為乾淨狀態
    And 儲值目錄已載入，包含寶石包與資本包

  # ─── 正常路徑：公開查詢目錄 ─────────────────────────────
  @p1 @smoke @contract @api @TC-E2E-DEP-001-S01 @US-DEP-001-AC1
  Scenario: 未登入也能查詢儲值包目錄
    Given 使用者未提供 session token
    When 使用者查詢儲值包目錄
    Then 系統回應 HTTP 200 並回傳所有可購買的儲值包
    And 每個儲值包包含包 ID、幣別、入帳數量與顯示名稱

  # ─── 正常路徑：建立訂單（假付費確認）───────────────────
  @p1 @smoke @contract @api @TC-E2E-DEP-002-S01 @US-DEP-001-AC2
  Scenario: 以有效儲值包與冪等鍵建立已確認的儲值訂單
    Given 使用者持有有效的 session token
    And 使用者提供一個全新的冪等鍵
    When 使用者以大寶石包建立儲值訂單
    Then 系統回應 HTTP 200 並回傳狀態為已確認的訂單
    And 回應包含訂單 ID、包 ID、幣別與入帳數量

  # ─── 冪等：重放相同鍵 ───────────────────────────────────
  @p1 @regression @api @TC-INT-DEP-003-S01 @US-DEP-001-AC2
  Scenario: 以相同冪等鍵重放儲值請求時回放原訂單且只入帳一次
    Given 使用者持有有效的 session token
    And 使用者已以某冪等鍵建立過一筆已確認的儲值訂單
    When 使用者以相同的冪等鍵再次送出相同的儲值請求
    Then 系統回放先前那筆訂單的結果
    And 使用者的錢包僅入帳一次

  # ─── 錯誤路徑：冪等鍵衝突（409）────────────────────────
  @p1 @regression @contract @api @TC-INT-DEP-004-E01 @US-DEP-001-AC2
  Scenario: 相同冪等鍵但請求內容不同時回應衝突
    Given 使用者持有有效的 session token
    And 使用者已以某冪等鍵建立過一筆儲值訂單
    When 使用者以相同冪等鍵但不同的儲值包送出請求
    Then 系統回應 HTTP 409 並提示冪等鍵衝突
    And 系統不建立新的訂單

  # ─── 錯誤路徑：儲值包不在目錄（400）───────────────────
  @p1 @regression @contract @api @TC-INT-DEP-005-E01 @US-DEP-001-AC2
  Scenario: 以不存在的儲值包建立訂單時被拒絕
    Given 使用者持有有效的 session token
    And 使用者提供一個全新的冪等鍵
    When 使用者以不在目錄中的儲值包建立訂單
    Then 系統回應 HTTP 400 並提示「儲值包不正確」
    And 系統不建立任何訂單

  # ─── 錯誤路徑：缺冪等鍵（400）──────────────────────────
  @p1 @regression @api @TC-INT-DEP-006-E01 @US-DEP-001-AC2
  Scenario: 未提供冪等鍵時拒絕建立儲值訂單
    Given 使用者持有有效的 session token
    When 使用者在未提供冪等鍵的情況下建立儲值訂單
    Then 系統拒絕請求並要求提供冪等鍵
    And 系統不建立任何訂單

  # ─── 錯誤路徑：未認證（401）─────────────────────────────
  @p1 @regression @contract @api @TC-INT-DEP-007-E01 @US-DEP-001-AC2
  Scenario: 無有效 token 建立儲值訂單時拒絕存取
    Given 使用者未提供有效的 session token
    When 使用者嘗試建立儲值訂單
    Then 系統回應 HTTP 401 並提示「請先登入」
    And 系統不建立任何訂單
