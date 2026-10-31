# features/member/member_auth.feature
# 來源：PRD §5.1 member（US-MEM-001，AC-1～AC-6）
# 上游 API：API.md §3 BC: member（POST /auth/register、/auth/login、/auth/logout、GET /auth/me）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030
# 註：本系統 C 端信任層為 self-scoped，SSOT（API §5.2）刻意不使用 403 / 422；越權等同無效 token → 401。

@member @p0
Feature: 會員身分與 session 認證
  作為代表玩家的客戶端整合工程師
  我希望以 email 與密碼註冊、登入取得 session token，並查詢「我的資料」
  以便讓伺服器以權威方式識別並歸戶每一位玩家，作為後續錢包與存檔的可信身分錨點

  Background:
    Given 資料庫已初始化為乾淨狀態
    And 系統已存在一個啟用中的帳號，email 為 "boss@example.com"、密碼為 "s3cret!"

  # ─── 正常路徑：註冊（AC-1）───────────────────────────────
  @p0 @smoke @contract @api @TC-E2E-MEM-001-S01 @US-MEM-001-AC1
  Scenario: 以合法資料註冊新帳號並同時開立錢包
    Given email "newboss@example.com" 尚未被註冊
    When 使用者以合法 email、顯示名稱與密碼送出註冊
    Then 系統回應 HTTP 200 並附帶 session token
    And 回應包含新帳號資料與初始錢包（cash 為 0、gems 為 0）
    And 系統為該帳號建立一筆開帳的錢包異動記錄

  # ─── 錯誤路徑：email 重複（AC-2）────────────────────────
  @p0 @regression @contract @api @TC-INT-MEM-002-E01 @US-MEM-001-AC2
  Scenario: 以已註冊的 email 再次註冊時回應衝突
    Given email "boss@example.com" 已被註冊
    When 使用者以 email "boss@example.com" 送出註冊
    Then 系統回應 HTTP 409 並提示「這個 email 已經註冊過了」
    And 系統不建立重複帳號

  # ─── 邊界條件：欄位格式（AC-3）──────────────────────────
  @p0 @regression @api @TC-UNIT-MEM-003-B01 @US-MEM-001-AC3
  Scenario Outline: 格式或長度不合法的註冊欄位被拒絕
    Given email "<email>" 尚未被註冊
    When 使用者以 email "<email>"、顯示名稱 "<display_name>"、密碼 "<password>" 送出註冊
    Then 系統拒絕註冊並回應「<message>」
    And 系統不寫入任何帳號資料

    Examples:
      | email               | display_name | password | message          |
      |                     | 喵喵科技     | s3cret!  | email 格式不正確 |
      | notanemail          | 喵喵科技     | s3cret!  | email 格式不正確 |
      | ok@example.com      |              | s3cret!  | 公司/老闆名需 1–24 字 |
      | ok2@example.com     | 喵喵科技     | 12345    | 密碼至少 6 碼    |

  # ─── 正常路徑：登入（AC-4）───────────────────────────────
  @p0 @smoke @contract @api @TC-E2E-MEM-004-S01 @US-MEM-001-AC4
  Scenario: 以正確帳密登入並簽發新 session
    Given 帳號 "boss@example.com" 為啟用狀態
    When 使用者以正確 email 與密碼登入
    Then 系統回應 HTTP 200 並簽發有效期 30 天的新 session token
    And 回應包含帳號資料與當前錢包餘額

  # ─── 錯誤路徑：帳密錯誤（AC-5）──────────────────────────
  @p0 @regression @contract @api @TC-INT-MEM-005-E01 @US-MEM-001-AC5
  Scenario: 以錯誤密碼登入時回應認證失敗且不洩露帳號是否存在
    Given 帳號 "boss@example.com" 為啟用狀態
    When 使用者以正確 email 但錯誤密碼登入
    Then 系統回應 HTTP 401 並提示「email 或密碼錯誤」
    And 系統不簽發任何 session token

  @p0 @regression @api @TC-INT-MEM-005-E02 @US-MEM-001-AC5
  Scenario: 以未註冊的 email 登入時回應與帳密錯誤相同的訊息
    Given email "ghost@example.com" 不存在於系統
    When 使用者以未註冊的 email 登入
    Then 系統回應認證失敗並提示「email 或密碼錯誤」
    And 系統不透露該 email 是否已註冊

  # ─── 正常路徑：我的資料 ─────────────────────────────────
  @p0 @smoke @contract @api @TC-E2E-MEM-007-S01 @US-MEM-001-AC1
  Scenario: 已登入者查詢我的資料彙整帳號、錢包與權益
    Given 使用者持有有效的 session token
    When 使用者查詢「我的資料」
    Then 系統回應 HTTP 200 並包含帳號、錢包與已購權益清單
    And 權益清單在未購買任何項目時為空陣列

  # ─── 錯誤路徑：未認證（AC-6）────────────────────────────
  @p0 @regression @contract @api @TC-INT-MEM-006-E01 @US-MEM-001-AC6
  Scenario: 無有效 token 查詢我的資料時拒絕存取
    Given 使用者未提供有效的 session token
    When 使用者嘗試查詢「我的資料」
    Then 系統回應 HTTP 401 並提示「請先登入」
    And 系統不回傳任何帳號資料

  # ─── 冪等：登出 ─────────────────────────────────────────
  @p0 @regression @api @TC-INT-MEM-008-S01 @US-MEM-001-AC4
  Scenario: 登出刪除當前 session 且為冪等操作
    Given 使用者持有有效的 session token
    When 使用者登出
    Then 系統確認登出成功
    And 原先的 session token 不再能存取需認證的資源

  @p0 @regression @api @TC-INT-MEM-008-E01 @US-MEM-001-AC4
  Scenario: 以無效或已失效的 token 登出仍回應成功（冪等）
    Given 使用者提供一個已失效的 session token
    When 使用者登出
    Then 系統仍確認登出成功
    And 系統不回應認證錯誤

  # ─── 速率限制（429）─────────────────────────────────────
  @p0 @regression @contract @api @TC-INT-MEM-009-E01 @US-MEM-001-AC1
  Scenario: 同一來源短時間內大量註冊時觸發速率限制
    Given 同一來源 IP 在本時間窗內的註冊次數已達上限
    When 使用者再次送出註冊
    Then 系統回應 HTTP 429 並提示「請求過於頻繁」
    And 回應附帶建議的重試等待時間
