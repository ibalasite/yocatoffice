# features/entitlement/entitlement_purchase.feature
# 來源：PRD §5.3 entitlement（US-ENT-001，AC-1～AC-5）
# 上游 API：API.md §3 BC: entitlement（POST /entitlements/buy、GET /entitlements）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030
# 核心：原子扣款 + 授予同一交易；UNIQUE(account,kind,item) 防重複；破產不消失。

@entitlement @p0
Feature: 已購權益的原子購買與查詢
  作為透過客戶端的辦公室模擬老闆
  我希望以原子交易購買外觀、夢想員工或劇本，且已購權益不重複、不因破產消失
  以便確保花錢買的與特聘的夢想員工永遠不會被搞丟

  Background:
    Given 資料庫已初始化為乾淨狀態
    And 已存在登入中的帳號，其錢包餘額為 cash 18750、gems 330

  # ─── 正常路徑：購買（AC-1）──────────────────────────────
  @p0 @smoke @contract @api @TC-E2E-ENT-001-S01 @US-ENT-001-AC1
  Scenario: 餘額足夠且未擁有時原子扣款並授予權益
    Given 使用者尚未擁有外觀項目 "suit_gold"
    And 使用者的寶石餘額足以支付該項目
    When 使用者以寶石購買外觀項目 "suit_gold"
    Then 系統回應 HTTP 200 並回傳扣款後的錢包與最新權益清單
    And 系統在同一交易寫入一筆購買原因的錢包異動記錄
    And 新權益出現在使用者的權益清單中

  # ─── 正常路徑：免費授予（amount = 0）───────────────────
  @p1 @regression @api @TC-INT-ENT-006-S01 @US-ENT-001-AC1
  Scenario: 以零金額免費授予活動權益時僅插入權益不寫帳本
    Given 使用者尚未擁有劇本項目 "event_welcome"
    When 系統以零金額免費授予該劇本項目
    Then 使用者取得該劇本權益
    And 系統不寫入任何錢包異動記錄

  # ─── 錯誤路徑：已擁有（AC-2）────────────────────────────
  @p0 @regression @contract @api @TC-INT-ENT-002-E01 @US-ENT-001-AC2
  Scenario: 重複購買已擁有的項目時回應衝突且不重複扣款
    Given 使用者已擁有外觀項目 "suit_gold"
    When 使用者再次購買相同的外觀項目 "suit_gold"
    Then 系統回應 HTTP 409 並提示「已經擁有這個項目」
    And 系統不重複扣款也不重複授予

  # ─── 錯誤路徑：餘額不足（AC-3）──────────────────────────
  @p0 @regression @contract @api @TC-INT-ENT-003-E01 @US-ENT-001-AC3
  Scenario: 餘額不足時交易回滾不扣款也不授予
    Given 使用者尚未擁有夢想員工 "soul_einstein"
    And 使用者的餘額小於該項目價格
    When 使用者嘗試購買該夢想員工
    Then 系統回應 HTTP 402 並提示「餘額不足」
    And 使用者未取得該權益且餘額維持不變

  # ─── 邊界條件：項目驗證（AC-4）──────────────────────────
  @p0 @regression @api @TC-UNIT-ENT-004-B01 @US-ENT-001-AC4
  Scenario Outline: 類別非法或缺項目 ID 的購買被拒絕
    Given 使用者持有有效的 session token
    When 使用者以類別 "<kind>" 與項目 "<item_id>" 送出購買
    Then 系統拒絕購買並提示「項目不正確」
    And 系統不授予任何權益

    Examples:
      | kind      | item_id    |
      | weapon    | suit_gold  |
      | cosmetic  |            |
      |           | suit_gold  |

  # ─── 錯誤路徑：未認證（401）─────────────────────────────
  @p0 @regression @contract @api @TC-INT-ENT-007-E01 @US-ENT-001-AC1
  Scenario: 無有效 token 購買權益時拒絕存取
    Given 使用者未提供有效的 session token
    When 使用者嘗試購買外觀項目
    Then 系統回應 HTTP 401 並提示「請先登入」
    And 系統不授予任何權益

  # ─── 正常路徑：查詢權益、破產不消失（AC-5）─────────────
  @p0 @smoke @contract @api @TC-E2E-ENT-005-S01 @US-ENT-001-AC5
  Scenario: 帳號破產重經營後已購權益仍完整列出
    Given 使用者已擁有外觀項目 "suit_gold" 與夢想員工 "soul_einstein"
    And 該帳號曾在遊戲內破產並重經營
    When 使用者查詢已購權益清單
    Then 系統回應 HTTP 200 並完整列出先前購買的所有權益
    And 權益清單依取得時間排序

  # ─── 錯誤路徑：查詢未認證（401）────────────────────────
  @p1 @regression @api @TC-INT-ENT-008-E01 @US-ENT-001-AC5
  Scenario: 無有效 token 查詢權益時拒絕存取
    Given 使用者未提供有效的 session token
    When 使用者嘗試查詢已購權益清單
    Then 系統拒絕存取並提示「請先登入」
    And 系統不回傳任何權益資料
