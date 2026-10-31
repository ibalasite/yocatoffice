# features/cross-module/event/domain_event_contracts.feature
# 來源：EDD §4.6.1 Domain Events / §3.4；API §1.3（Outbox 投遞）
# 跨 BC Domain Event consumer pair：
#   MemberRegistered（member → wallet）、DepositConfirmed（deposit → wallet）、
#   WalletBalanceChanged（wallet → 下游）、EntitlementGranted（entitlement → 下游）、RunScoreSubmitted（leaderboard → 下游）
# DOC-ID: BDD-SERVER-YOCAT-OFFICE-BACKEND-20261030

@event-contract @modulith @p0 @api
Feature: 跨有界上下文領域事件合約
  作為各有界上下文的生產者與消費者
  我希望發布並消費符合合約的領域事件
  以便跨模組狀態保持一致且事件結構升版時不破壞消費端

  Background:
    Given 跨模組通訊一律透過交易式 Outbox 的領域事件進行

  # ─── MemberRegistered 生產者 ───────────────────────────
  @TC-INT-EVT-001-S01 @US-PLAT-001-AC4
  Scenario: 註冊完成時 member 發布合規的會員註冊事件
    Given member 已在同一交易建立新帳號
    When 該交易提交完成
    Then 系統發布會員註冊事件
    And 事件內容包含帳號 ID 與 email
    And 事件結構符合既有合約版本

  # ─── MemberRegistered 消費者（wallet 開帳）─────────────
  @TC-INT-EVT-002-S01 @US-WAL-001-AC1
  Scenario: wallet 消費會員註冊事件後建立初始錢包與開帳帳本
    Given wallet 尚未為該帳號建立錢包
    When wallet 收到合規的會員註冊事件
    Then wallet 為該帳號建立初始餘額為零的錢包
    And wallet 寫入一筆原因為開帳的錢包異動記錄
    And 整個過程不需呼叫 member 的任何 HTTP 介面

  # ─── DepositConfirmed 生產者 ───────────────────────────
  @TC-INT-EVT-003-S01 @US-DEP-001-AC2
  Scenario: 儲值確認後 deposit 發布合規的儲值確認事件
    Given deposit 已建立一筆已確認的儲值訂單
    When 該訂單交易提交完成
    Then 系統發布儲值確認事件
    And 事件內容包含帳號 ID、訂單 ID、幣別與金額

  # ─── DepositConfirmed 消費者（wallet 入帳，冪等）───────
  @TC-INT-EVT-004-S01 @US-WAL-001-AC1
  Scenario: wallet 消費儲值確認事件後入帳且重複投遞只入帳一次
    Given wallet 尚未處理某筆儲值確認事件
    When wallet 收到該儲值確認事件並再次收到相同事件
    Then wallet 僅對該帳號入帳一次
    And wallet 以事件 ID 去重避免重複入帳

  # ─── EntitlementGranted 生產者 ─────────────────────────
  @TC-INT-EVT-005-S01 @US-ENT-001-AC1
  Scenario: 授予權益後 entitlement 發布合規的權益授予事件
    Given entitlement 已在同一交易完成扣款與授予
    When 該交易提交完成
    Then 系統發布權益授予事件
    And 事件內容包含帳號 ID、類別與項目 ID

  # ─── RunScoreSubmitted 生產者 ──────────────────────────
  @TC-INT-EVT-006-S01 @US-LB-001-AC1
  Scenario: 上榜後 leaderboard 發布合規的分數提交事件
    Given leaderboard 已以場次 ID upsert 寫入分數
    When 該寫入完成
    Then 系統發布分數提交事件
    And 事件內容包含帳號 ID、場次 ID、模式與分數
