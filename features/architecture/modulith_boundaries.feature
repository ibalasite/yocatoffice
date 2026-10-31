# features/architecture/modulith_boundaries.feature
# 來源：PRD §5.5 平台底座（US-PLAT-001，AC-3/AC-4）；API §1.1（HC-2）；EDD §3.4
# 上游：Spring Modulith HC-1~HC-5 硬約束（禁跨 BC DB FK、只走 Public API/Event、Redis 命名空間隔離、依賴為 DAG）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030

@architecture @modulith @p0
Feature: Spring Modulith 有界上下文邊界守護
  作為維運團隊的 Engineering Lead
  我希望以可自動驗證的場景守護 7 個有界上下文的模組邊界
  以便讓架構約束不退化為人工 code review，並確保模組可在不改邊界下逐一拆微服務

  Background:
    Given 系統由 7 個有界上下文組成：member、wallet、deposit、entitlement、record、leaderboard、game-save

  # ─── HC-1：Schema 隔離 ──────────────────────────────────
  @p0 @modulith @TC-INT-ARCH-001-S01 @US-PLAT-001-AC4
  Scenario: 每個有界上下文只存取自己擁有的資料庫 schema
    Given 各有界上下文各自擁有獨立的資料庫 schema
    When 執行模組邊界靜態分析
    Then 不存在任何跨 schema 的外鍵或直接查詢
    And member 的錢包資料一律經 wallet 的公開介面取得

  # ─── HC-2：跨模組僅走 Public API ──────────────────────
  @p0 @modulith @TC-INT-ARCH-002-S01 @US-PLAT-001-AC4
  Scenario: 有界上下文之間僅透過公開介面或領域事件互動
    Given entitlement 購買流程需呼叫 wallet 進行扣款
    When 執行模組依賴分析
    Then entitlement 僅透過 wallet 的公開服務介面呼叫扣款
    And 不存在對其他模組內部類別的直接依賴

  # ─── HC-3：領域事件合約 ────────────────────────────────
  @p0 @modulith @event-contract @TC-INT-ARCH-003-S01 @US-PLAT-001-AC4
  Scenario: 跨模組通訊透過具版本相容性的領域事件
    Given deposit 確認後需通知 wallet 入帳
    When deposit 發布儲值確認領域事件
    Then 該事件透過交易式 Outbox 可靠投遞
    And 事件結構符合既有合約且向後相容

  # ─── HC-4：Redis 命名空間隔離 ─────────────────────────
  @p0 @modulith @TC-INT-ARCH-004-S01 @US-PLAT-001-AC4
  Scenario: 各有界上下文使用隔離的 Redis 命名空間
    Given 多個有界上下文使用 Redis 作為快取與限流後援
    When 檢視各模組寫入的 Redis 鍵
    Then 每個模組的鍵都帶有自己的命名空間前綴
    And 不同模組之間不共用同一組可變快取鍵

  # ─── HC-5：模組依賴為 DAG ──────────────────────────────
  @p0 @modulith @TC-UNIT-ARCH-005-S01 @US-PLAT-001-AC3
  Scenario: 模組依賴關係構成有向無環圖
    Given 7 個有界上下文之間存在依賴關係
    When 執行模組化測試驗證依賴圖
    Then 模組依賴關係為有向無環圖
    And 不存在任何循環依賴

  # ─── 模組隔離冷啟動 ─────────────────────────────────────
  @p0 @module-isolation @TC-INT-ARCH-006-S01 @US-PLAT-001-AC4
  Scenario Outline: 單一有界上下文可在其他模組不可用時獨立冷啟動
    Given 僅啟動 "<bc>" 有界上下文，其餘模組以外部替身隔離
    When 該有界上下文啟動應用情境
    Then 該有界上下文成功啟動
    And 其健康檢查回報就緒

    Examples:
      | bc          |
      | member      |
      | wallet      |
      | entitlement |
      | game-save   |
