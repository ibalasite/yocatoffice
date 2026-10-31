# features/game-save/game_save_sync.feature
# 來源：PRD §5.4 game-save（US-SAVE-001，AC-1～AC-5）
# 上游 API：API.md §3 BC: game-save（PUT /save、GET /save）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030
# 核心：以 (account_id, slot) upsert 跨裝置存檔；查無回 {save:null}（非 404）；body 上限 4MB。

@game-save @p0
Feature: 跨裝置辦公室存檔同步
  作為透過客戶端的辦公室模擬老闆
  我希望以帳號上傳辦公室存檔並在任何裝置還原
  以便換手機或重灌後延續公司經營進度，不再因綁單一裝置而全失

  Background:
    Given 資料庫已初始化為乾淨狀態
    And 已存在一個登入中的帳號

  # ─── 正常路徑：上傳存檔（AC-1）──────────────────────────
  @p0 @smoke @contract @api @TC-E2E-SAVE-001-S01 @US-SAVE-001-AC1
  Scenario: 上傳存檔時以帳號與存檔槽 upsert 寫入
    Given 使用者持有有效的 session token
    When 使用者將辦公室存檔上傳至預設存檔槽
    Then 系統回應 HTTP 200 並確認存檔已寫入
    And 該存檔的最後更新時間被更新為當下

  # ─── 邊界條件：缺 data（AC-4）───────────────────────────
  @p0 @regression @contract @api @TC-UNIT-SAVE-004-B01 @US-SAVE-001-AC4
  Scenario: 上傳存檔缺少資料欄位時被拒絕
    Given 使用者持有有效的 session token
    When 使用者在未提供存檔資料的情況下上傳存檔
    Then 系統回應 HTTP 400 並提示「缺 data」
    And 系統不寫入任何存檔

  # ─── 邊界條件：空物件視為合法存檔 ──────────────────────
  @p1 @regression @api @TC-INT-SAVE-006-B01 @US-SAVE-001-AC1
  Scenario: 上傳空物件存檔時仍視為合法寫入
    Given 使用者持有有效的 session token
    When 使用者上傳一個內容為空物件的存檔
    Then 系統確認存檔已寫入
    And 後續查詢可取回該空物件存檔

  # ─── 錯誤路徑：未認證（401）─────────────────────────────
  @p0 @regression @contract @api @TC-INT-SAVE-007-E01 @US-SAVE-001-AC1
  Scenario: 無有效 token 上傳存檔時拒絕存取
    Given 使用者未提供有效的 session token
    When 使用者嘗試上傳存檔
    Then 系統回應 HTTP 401 並提示「請先登入」
    And 系統不寫入任何存檔

  # ─── 正常路徑：還原存檔（AC-2）──────────────────────────
  @p0 @smoke @contract @api @TC-E2E-SAVE-002-S01 @US-SAVE-001-AC2
  Scenario: 查詢已存在的存檔時回傳最新內容
    Given 使用者持有有效的 session token
    And 該帳號的預設存檔槽已有一份存檔
    When 使用者查詢預設存檔槽
    Then 系統回應 HTTP 200 並回傳最新存檔資料與更新時間

  # ─── 邊界條件：無存檔回 null（AC-3）────────────────────
  @p0 @regression @contract @api @TC-INT-SAVE-003-E01 @US-SAVE-001-AC3
  Scenario: 查詢尚無存檔的存檔槽時回傳空存檔而非找不到
    Given 使用者持有有效的 session token
    And 該帳號的預設存檔槽尚無任何存檔
    When 使用者查詢預設存檔槽
    Then 系統回應 HTTP 200 且存檔內容為空
    And 系統不回應資源不存在錯誤

  # ─── 正常路徑：跨裝置還原（AC-5）───────────────────────
  @p0 @smoke @api @TC-E2E-SAVE-005-S01 @US-SAVE-001-AC5
  Scenario: 在一台裝置上傳後於另一台裝置登入可還原同一存檔
    Given 使用者已在裝置 A 上傳了一份辦公室存檔
    When 使用者在裝置 B 以同一帳號登入並查詢存檔
    Then 系統回傳裝置 A 寫入的最新存檔內容
    And 兩台裝置看到的存檔資料一致
