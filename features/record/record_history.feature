# features/record/record_history.feature
# 來源：PRD §4.1 / §5 record（US-REC-001，P1）；RTM TC-INT-REC-001
# 上游 API：API.md §3 BC: record（POST /records、GET /records）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030
# 核心：經營史 append-only（破產 / 成就 / 傳承），破產記錄歸戶、不因重經營消失。

@record @p1
Feature: 經營史記錄寫入與查詢
  作為透過客戶端的辦公室模擬老闆
  我希望把破產、成就與傳承等經營史以 append-only 方式記錄並可查詢
  以便保留公司的情感軌跡並供營運逐筆對帳

  Background:
    Given 資料庫已初始化為乾淨狀態
    And 已存在一個登入中的帳號

  # ─── 正常路徑：寫入經營史 ───────────────────────────────
  @p1 @smoke @contract @api @TC-INT-REC-001-S01 @US-REC-001-AC1
  Scenario: 以合法類型新增一筆破產經營史
    Given 使用者持有有效的 session token
    When 使用者新增一筆破產類型的經營史記錄
    Then 系統回應 HTTP 200 並確認記錄已寫入
    And 該筆記錄以 append-only 方式歸屬於該帳號

  # ─── 邊界條件：類型驗證 ─────────────────────────────────
  @p1 @regression @api @TC-INT-REC-002-B01 @US-REC-001-AC1
  Scenario Outline: 類型非法的經營史記錄被拒絕
    Given 使用者持有有效的 session token
    When 使用者以類型 "<type>" 新增經營史記錄
    Then 系統拒絕寫入並提示「type 不正確」
    And 系統不新增任何經營史記錄

    Examples:
      | type      |
      | victory   |
      | refund    |
      |           |

  # ─── 錯誤路徑：未認證（401）─────────────────────────────
  @p1 @regression @contract @api @TC-INT-REC-003-E01 @US-REC-001-AC1
  Scenario: 無有效 token 寫入經營史時拒絕存取
    Given 使用者未提供有效的 session token
    When 使用者嘗試新增經營史記錄
    Then 系統回應 HTTP 401 並提示「請先登入」
    And 系統不新增任何經營史記錄

  # ─── 正常路徑：查詢全部經營史 ──────────────────────────
  @p1 @smoke @contract @api @TC-INT-REC-004-S01 @US-REC-001-AC1
  Scenario: 查詢經營史時依建立時間由新到舊回傳
    Given 使用者持有有效的 session token
    And 該帳號已有破產與成就各一筆經營史記錄
    When 使用者查詢全部經營史
    Then 系統回應 HTTP 200 並回傳該帳號的經營史清單
    And 清單依建立時間由新到舊排序

  # ─── 正常路徑：依類型篩選 ───────────────────────────────
  @p1 @regression @api @TC-INT-REC-005-S01 @US-REC-001-AC1
  Scenario: 依類型篩選經營史時只回傳符合的記錄
    Given 使用者持有有效的 session token
    And 該帳號已有破產與成就各一筆經營史記錄
    When 使用者以破產類型篩選查詢經營史
    Then 系統僅回傳破產類型的經營史記錄
    And 回傳筆數不超過單次上限 100 筆
