# features/wallet/wallet_transactions.feature
# 來源：PRD §5.2 wallet（US-WAL-001，AC-1～AC-5）
# 上游 API：API.md §3 BC: wallet（POST /wallet/topup、POST /wallet/spend）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030
# 核心：伺服器權威錢包；每筆異動寫 append-only ledger；花費以行鎖序列化、餘額不足回 402。

@wallet @p0
Feature: 伺服器權威錢包儲值與花費
  作為代表玩家的客戶端整合工程師
  我希望呼叫儲值與花費使伺服器權威變更餘額，且每筆異動都寫入 append-only 帳本
  以便根除客戶端竄改、讓餘額成為不可偽造的唯一真實來源並可逐筆對帳

  Background:
    Given 資料庫已初始化為乾淨狀態
    And 已存在登入中的帳號，其錢包餘額為 cash 20000、gems 330

  # ─── 正常路徑：以儲值包儲值（AC-1）─────────────────────
  @p0 @smoke @contract @api @TC-E2E-WAL-001-S01 @US-WAL-001-AC1
  Scenario: 以預定義儲值包儲值並寫入 topup 帳本
    Given 使用者持有有效的 session token
    When 使用者以中寶石包儲值
    Then 系統回應 HTTP 200 並回傳加值後的最新錢包餘額
    And 系統在同一交易寫入一筆原因為 topup 的錢包異動記錄

  # ─── 正常路徑：自訂金額儲值 ─────────────────────────────
  @p0 @regression @api @TC-E2E-WAL-001-S02 @US-WAL-001-AC1
  Scenario: 以自訂現金金額儲值使餘額增加
    Given 使用者持有有效的 session token
    When 使用者以現金自訂金額 5000 儲值
    Then 系統確認儲值成功並回傳最新錢包餘額
    And 異動後的帳本餘額等於儲值前餘額加上本次金額

  # ─── 正常路徑：花費行鎖扣款（AC-2）─────────────────────
  @p0 @smoke @contract @api @TC-E2E-WAL-002-S01 @US-WAL-001-AC2
  Scenario: 餘額足夠時以行鎖序列化扣款並寫入負異動帳本
    Given 使用者持有有效的 session token
    And 使用者的現金餘額不少於欲扣款金額
    When 使用者以現金花費 1250
    Then 系統回應 HTTP 200 並回傳扣款後的最新錢包餘額
    And 系統寫入一筆負異動的錢包帳本記錄

  # ─── 錯誤路徑：餘額不足（AC-3）──────────────────────────
  @p0 @regression @contract @api @TC-INT-WAL-003-E01 @US-WAL-001-AC3
  Scenario: 餘額不足時拒絕扣款並回傳現有錢包
    Given 使用者持有有效的 session token
    And 使用者的現金餘額小於欲扣款金額
    When 使用者嘗試以現金花費超過餘額的金額
    Then 系統回應 HTTP 402 並提示「餘額不足」
    And 回應附帶使用者當前的錢包餘額
    And 系統不寫入任何錢包異動記錄

  # ─── 錯誤路徑：未認證（401）─────────────────────────────
  @p0 @regression @contract @api @TC-INT-WAL-006-E01 @US-WAL-001-AC2
  Scenario: 無有效 token 嘗試花費時拒絕存取
    Given 使用者未提供有效的 session token
    When 使用者嘗試花費
    Then 系統回應 HTTP 401 並提示「請先登入」
    And 系統不變更任何錢包餘額

  # ─── 邊界條件：金額驗證（AC-4）──────────────────────────
  @p0 @regression @api @TC-UNIT-WAL-004-B01 @US-WAL-001-AC4
  Scenario Outline: 超出合法範圍的金額被拒絕
    Given 使用者持有有效的 session token
    When 使用者以 "<operation>" 操作提交金額 <amount>
    Then 系統拒絕操作並提示「金額不正確」
    And 系統不變更任何錢包餘額

    Examples:
      | operation | amount     |
      | 花費      | 0          |
      | 花費      | -100       |
      | 儲值      | 0          |
      | 儲值      | 10000001   |

  # ─── 邊界條件：合法金額上下界通過 ──────────────────────
  @p1 @regression @api @TC-UNIT-WAL-004-B02 @US-WAL-001-AC4
  Scenario Outline: 合法邊界金額的儲值被接受
    Given 使用者持有有效的 session token
    When 使用者以現金自訂金額 <amount> 儲值
    Then 系統確認儲值成功
    And 回傳的最新餘額反映本次加值

    Examples:
      | amount    |
      | 1         |
      | 10000000  |

  # ─── 併發一致性（AC-5）──────────────────────────────────
  @p0 @regression @api @TC-INT-WAL-005-S01 @US-WAL-001-AC5
  Scenario: 同帳號併發多筆花費時以行鎖序列化且帳本與餘額零不一致
    Given 使用者持有有效的 session token
    And 使用者的現金餘額為固定的已知金額
    When 同一帳號同時送出多筆現金花費請求
    Then 所有成功扣款的總和不超過初始餘額
    And 最終餘額等於初始餘額減去所有成功扣款的總和
    And 錢包餘額與帳本累積完全一致且無負餘額

  # ─── 速率限制（429）─────────────────────────────────────
  @p1 @regression @contract @api @TC-INT-WAL-007-E01 @US-WAL-001-AC2
  Scenario: 同一帳號短時間內大量花費時觸發速率限制
    Given 使用者持有有效的 session token
    And 該帳號在本時間窗內的錢包請求次數已達上限
    When 使用者再次送出花費請求
    Then 系統回應 HTTP 429 並提示「請求過於頻繁」
    And 回應附帶建議的重試等待時間
