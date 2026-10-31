# features/leaderboard/leaderboard_ranking.feature
# 來源：PRD §5 leaderboard（US-LB-001，P1）；RTM TC-INT-LB-001
# 上游 API：API.md §3 BC: leaderboard（GET /leaderboard、POST /leaderboard）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030
# 核心：公開 Top N 查詢（不需登入）；上榜歸戶寫入，以 run_id upsert 冪等。

@leaderboard @p1
Feature: 全域排行榜查詢與上榜
  作為代表玩家的客戶端整合工程師
  我希望公開查詢全域排行榜並以歸戶方式上榜且重複提交只更新一筆
  以便支撐社交參與同時確保分數由伺服器權威歸戶

  Background:
    Given 資料庫已初始化為乾淨狀態
    And 排行榜已有若干筆沙盒模式的既有分數

  # ─── 正常路徑：公開查詢 Top N ──────────────────────────
  @p1 @smoke @contract @api @TC-INT-LB-001-S01 @US-LB-001-AC1
  Scenario: 未登入也能依分數由高到低查詢排行榜
    Given 使用者未提供 session token
    When 使用者查詢排行榜前 20 名
    Then 系統回應 HTTP 200 並依分數由高到低回傳上榜清單
    And 每筆上榜資料包含場次 ID、顯示名稱、模式與分數

  # ─── 邊界條件：limit 夾擠 ───────────────────────────────
  @p1 @regression @api @TC-INT-LB-002-B01 @US-LB-001-AC1
  Scenario Outline: 查詢筆數被夾擠於合法範圍內
    Given 排行榜已有超過 100 筆分數
    When 使用者以筆數 "<limit>" 查詢排行榜
    Then 系統回傳的筆數不超過 "<max_returned>"

    Examples:
      | limit | max_returned |
      | 1     | 1            |
      | 20    | 20           |
      | 500   | 100          |

  # ─── 正常路徑：上榜歸戶寫入 ─────────────────────────────
  @p1 @smoke @contract @api @TC-INT-LB-003-S01 @US-LB-001-AC1
  Scenario: 已登入者上榜時以帳號顯示名稱歸戶寫入
    Given 使用者持有有效的 session token
    When 使用者以場次 "run-7781" 提交分數 982300
    Then 系統回應 HTTP 200 並確認上榜成功
    And 該場次以使用者的顯示名稱歸戶記錄

  # ─── 冪等：同 run_id upsert ─────────────────────────────
  @p1 @regression @api @TC-INT-LB-004-S01 @US-LB-001-AC1
  Scenario: 以相同場次 ID 再次提交時只更新同一筆排行記錄
    Given 使用者持有有效的 session token
    And 使用者已以場次 "run-7781" 提交過分數
    When 使用者以相同場次 "run-7781" 提交更高的分數
    Then 該場次的排行記錄更新為最新分數
    And 排行榜中該場次仍只有一筆記錄

  # ─── 邊界條件：缺場次 ID（400）─────────────────────────
  @p1 @regression @contract @api @TC-INT-LB-005-E01 @US-LB-001-AC1
  Scenario: 未提供場次 ID 上榜時被拒絕
    Given 使用者持有有效的 session token
    When 使用者在未提供場次 ID 的情況下提交分數
    Then 系統回應 HTTP 400 並提示「缺 runId」
    And 系統不寫入任何排行記錄

  # ─── 錯誤路徑：未認證上榜（401）────────────────────────
  @p1 @regression @contract @api @TC-INT-LB-006-E01 @US-LB-001-AC1
  Scenario: 無有效 token 提交分數時拒絕存取
    Given 使用者未提供有效的 session token
    When 使用者嘗試提交分數上榜
    Then 系統回應 HTTP 401 並提示「請先登入」
    And 系統不寫入任何排行記錄
