# IDEA Document — yoCat Office 後端信任層

<!-- SDLC Requirements Engineering — Layer 0：Idea Capture -->
<!-- 此文件是需求鏈的起點（IDEA → BRD → PRD → PDD → EDD），記錄最原始的構想輸入 -->
<!-- 由 /gendoc idea 生成；未來需求變更時，此文件作為「原始意圖」比對基準 -->

---

## Document Control

| 欄位 | 內容 |
|------|------|
| **DOC-ID** | IDEA-YOCAT-OFFICE-BACKEND-20261030 |
| **專案名稱** | yoCat Office 後端信任層（yocat-office-backend） |
| **文件版本** | v0.1-capture |
| **狀態** | DRAFT |
| **作者** | AI Generated (gendoc-gen-idea) |
| **建立日期** | 2026-10-30 |
| **最後更新** | 2026-10-30 |
| **輸入模式** | AI 自動填入（codebase_local 既有原型 + Q1–Q5 澄清） |
| **建立方式** | /gendoc idea 自動捕捉；請執行 /reviewdoc brd 審查後方可升版 |
| **下游文件** | docs/BRD.md（由本文件產生） |
| **client_type** | api-only（state 確認） |

---

## 0. Input Source（輸入素材說明）

- **輸入類型**：`codebase_local` — 既有 Node/TypeScript 後端原型 + PostgreSQL schema + k8s manifest，加上 Q1–Q5 澄清結果與 Web Research 摘要。
- **輸入來源**：`<project-root>`（yoCat Office / yoCat Office 專案）。
- **本次讀取的 docs/req/ 素材**：
  - `docs/req/企画.md`（交接聖經，含 §17 會員系統與雲端架構）
  - `docs/req/schema.sql`（8 張表的伺服器權威 schema）
  - `docs/req/postgres.yaml`（Rancher Desktop k8s 的 PostgreSQL manifest）
  - `docs/req/server-src/index.ts`（HTTP server + LLM router + 靜態服務）
  - `docs/req/server-src/accounts.ts`（信任層 API：帳號/錢包/權益/存檔/記錄/排行）
  - `docs/req/server-src/db.ts`（PostgreSQL 連線池與交易封裝）
  - `docs/req/codebase-tree.txt`（專案目錄樹）
- **定位說明**：本 IDEA 聚焦於 **yoCat Office 的「後端信任層」**，不涵蓋本地遊戲模擬、LLM 推論與前端渲染（那些依核心理念「算力留本地」留在玩家端）。

---

## 1. Idea Essence（核心本質）

### 1.1 一句話描述（Elevator Pitch）

> **yoCat Office 後端信任層幫助「要對遊戲收真錢的獨立遊戲團隊與其玩家」解決「身分、錢包、已購權益與經營記錄無伺服器權威、可被竄改且無法跨裝置」的問題，方式是把『算力留本地、信任上雲端』——只讓身分/錢包/權益/記錄/排行/存檔這條瘦信任層上雲，以 Spring Modulith 有界上下文 + Clean Architecture + Day1 HA 設計保證一致性與防作弊。**

**Elevator Pitch 五要素：**

1. **Who（目標使用者）**：代表玩家發出信任層請求的遊戲前端客戶端；以及遊戲營運/客服（對帳與風控）。終端受益者是會為「心愛的員工與自己建立的公司」付費的玩家。
2. **Problem（面對的問題）**：一旦要收真錢，錢包放 `localStorage` 被 F12 一行 `W.gems=99999` 就破功；失敗/破產/成就等經營記錄無法歸戶、無法跨裝置、無法防作弊與對帳。
3. **Solution（如何解決）**：伺服器權威的信任層——scrypt 密碼雜湊 + session token 身分、append-only ledger 的權威錢包、原子扣款+授權的權益購買、跨裝置存檔、經營史記錄與全域排行；以 7 個 Spring Modulith 有界上下文承載，Clean Architecture 四層分離，Day1 即 HA（≥2 replicas、無 SPOF）。
4. **Why Now（為什麼現在）**：核心玩法（P0 經濟、P1 關係/事件、P2 存檔/離線、情感依附迴圈、破產 recycle）皆已實作，變現軌道（商店/劇本/分享）已鋪好；差的就是「收真錢需要的伺服器權威」。既有 Node 原型已端到端驗證可行，正是把它依工程原則重構為可維運雲端信任層的時機。
5. **Key Differentiator（核心差異）**：競品的 AI NPC 遊戲把「AI 互動」上雲並按量計費；本專案反其道——**AI 推論與遊戲模擬零邊際成本留本地，只有信任層上雲**，伺服器負載與成本遠低於同類即時遊戲後端，同時仍具備收費所需的防竄改與對帳能力。

---

### 1.2 核心假說（Lean Hypothesis）

> **If we build** 一條「算力留本地、信任上雲端」的伺服器權威後端（身分/錢包/權益/記錄/排行/存檔），以 Spring Modulith 7 有界上下文 + Clean Architecture + Day1 HA 實作，
> **then** 要收真錢的遊戲團隊與其玩家
> **will** 能在不把遊戲算力搬上雲（維持零邊際成本護城河）的前提下，安全地儲值、購買與跨裝置延續其經營史，且營運方能對帳與防作弊，
> **which will lead to** 遊戲得以商業化（買斷 → F2P），後端成本可控、可水平擴展，並成為 §15/§16 變現模式（人才庫、破產 recycle、儲值注資）的可信底座。

*此假說可測：以既有 8 表 schema + API 已端到端驗證（§17.6 test_api.mjs 全綠）為基礎，重構版只需在 HA/模組邊界/一致性三面向達標即算成立。*

---

### 1.3 成功願景（Success Vision）

**12 個月後，若本專案成功，以下情境將成真：**

- **用戶維度**：玩家可用帳號登入在任何裝置延續「我的辦公室」，儲值的💎資本與特聘夢想員工永不因破產消失（信任底線）；客戶端只讀顯示伺服器權威餘額，F12 竄改失效。
- **業務維度**：遊戲可安全收真錢（買斷 + 外觀/劇本/季票 + 儲值注資），每一筆錢包異動都有 ledger 可稽核，營運/客服能對帳與風控；後端因「只有信任層上雲」而維持低成本結構。
- **技術維度**：7 個有界上下文（member/wallet/deposit/entitlement/record/leaderboard/game-save）以 Spring Modulith 落地，Day1 單體 HA（≥2 replicas、無 SPOF），可依流量在不改邊界的前提下逐一拆為獨立微服務；全程可在 Rancher Desktop k8s（local-first）跑完開發與驗收。

### 1.4 Innovation Type Classification（創新類型分類）

| 類型 | 定義 | 是否適用 |
|------|------|:-------:|
| **Incremental**（漸進式）| 改善既有產品功能或流程效率 | ☐ |
| **Sustaining**（持續性）| 在既有市場中競爭，提供更好的解法 | ☑ |
| **Adjacent**（鄰近式）| 現有能力進入新市場或新用戶群 | ☐ |
| **Disruptive**（顛覆性）| 從低端或全新市場切入，重塑現有市場 | ☑ |
| **Radical / Breakthrough**（突破性）| 基於新技術或商業模式，創造新市場 | ☐ |

**本 IDEA 的分類**：Sustaining（對「遊戲會員/金流後端」這一成熟領域提供更嚴謹的工程實作）＋ Disruptive（對「AI NPC 遊戲」的成本模型——把 AI 算力留本地、只上雲信任層——是顛覆性的成本結構差異）。

**分類依據**：信任層本身（帳號/錢包/權益）是成熟領域，採業界最佳實務（Sustaining）；但「算力位置」的商業決策（§15.1）讓本專案相對「AI 上雲按量計費」競品具備結構性成本優勢，屬顛覆性定位。

*分類影響資源投入策略：Sustaining 部分走快速、穩健、依既有原型重構的 ROI 路徑；Disruptive 的成本護城河需在架構上持續守住「信任層薄、算力不上雲」的邊界。*

---

## 2. Problem Statement（問題陳述）

### 2.1 現狀描述（As-Is Narrative）

既有 yoCat Office 原型把錢包、已購項目、存檔都放在客戶端 `localStorage`（§14/§16 的 `W.gems`、`W.crush`、自動存檔約每 4 秒）。這在「免費自帶算力、零成本」階段沒問題，但一旦要收真錢（§16 儲值注資、夢想人才、外觀/季票），就出現根本矛盾：

- **Workaround 現況**：目前以 `localStorage` 當唯一真實來源，玩家可按 F12 直接改 `W.gems=99999`；排行榜分數由客戶端自報；存檔綁單一裝置、換機即失。
- **原型過渡**：為了解決上述問題，團隊已寫出一版 Node 後端（`accounts.ts`）與 8 表 schema，端到端測試全綠（§17.6），但它是「零依賴、單一行程」的原型——無 HA、無模組邊界、無事件驅動、無對帳/風控工具鏈，不適合作為收真錢的長期可維運底座。
- **痛點頻率**：金流/竄改風險在「每一次付費與每一次上榜」都發生；跨裝置需求在「每一次換機/重灌」發生。

### 2.2 根本原因分析（5 Whys）

```
問題現象：要對遊戲收費，但沒有伺服器權威的身分與錢包
  Why 1：錢包/權益/記錄都放客戶端 localStorage
    Why 2：早期設計以「算力與狀態全留本地、零成本」為最高原則
      Why 3：核心護城河是「AI 互動零邊際成本」，團隊刻意避免任何上雲成本
        Why 4：但「收真錢」要求的防竄改/對帳/跨裝置，本質上無法由客戶端自證
          Why 5（根本原因）：缺少一條「與遊戲算力解耦、可獨立上雲」的伺服器權威信任層 ← 我們要解決的是這個
```

*根因不是「要不要上雲」，而是「把什麼上雲」——必須把薄薄的信任層（身分/錢包/權益/記錄/排行/存檔）與胖胖的本地算力切開，只上雲前者。*

### 2.3 問題規模（量化估算）

| 指標 | 估算數字 | 資料來源 | 信心水準 |
|------|---------|---------|---------|
| 受影響使用者數（全球） | 所有要在本遊戲付費或跨裝置的玩家（全量付費意圖玩家） | 企画 §15/§16 變現設計 | 中 |
| 受影響使用者數（目標市場） | 啟動期 5,000 DAU、成長期目標 50,000 DAU | state q4（PM Expert 推估） | 中 |
| 竄改/對帳風險暴露面 | 每筆儲值、每筆花費、每次上榜（100% 金流動作） | schema.sql wallet_ledger 設計 | 高 |
| 跨裝置需求頻率 | 每次換機/重灌/多裝置玩家（定性：高） | 企画 §17.5 玩家體驗流程 | 中 |
| 可定址市場規模（TAM） | 全球休閒/模擬手遊付費市場為數十億美元級；本類型（辦公室經營模擬 + 伺服器權威金流）可定址子集估 ~ USD 100–300M／年 | 企画 §15.6 GTM + 行業休閒手遊營收級距（AI 推斷） | 低 |
| 可服務市場規模（SAM） | 本遊戲 A→B→C 路線實際可觸及之付費玩家：成長期 50,000 DAU × 年 ARPPU 推估 ~ USD 1–3M／年 | 企画 §15.1 + state q4 | 低 |
| 可獲取市場規模（SOM，預估 12 月） | 啟動期 ~5,000 DAU × 付費轉化 2–5% × 年 ARPPU ~USD 20–40 ≈ 年營收 USD 2K–10K 級起步 | state q4 + 企画 §16 | 低 |

*以上數字為初始估算，需在 PRD 階段的營運數據後更新。*

---

## 3. Target Users（目標使用者）

### 3.1 主要使用者群（Q1 澄清結果）

**使用者描述**：遊戲前端客戶端（代表玩家發出信任層請求的程式化消費者）與其背後的終端玩家；以及遊戲營運/客服（後台對帳與風控）。

| 屬性 | 描述 |
|------|------|
| 職業 / 角色 | (A) 遊戲客戶端工程/客戶端本身（API 消費者）；(B) 終端玩家（辦公室模擬老闆）；(C) 營運/客服（對帳、風控、退款處理） |
| 行業 / 情境 | 獨立/休閒模擬手遊；登入/儲值/購買/存檔/上榜等「低頻信任動作」，非每幀即時 |
| 技術成熟度 | 客戶端＝進階（HTTP/OpenAPI 整合）；玩家＝初階；營運＝中階 |
| 使用頻率 | 客戶端＝每次登入/金流/存檔觸發；玩家＝每日/每週；營運＝每日對帳 |
| 決策角色 | 客戶端＝使用者；玩家＝使用者/付費者；營運＝風控決策者 |

### 3.2 Jobs to Be Done（用戶核心任務）

| 任務類型 | 用戶任務描述 |
|---------|------------|
| **Functional**（功能性）| 當我（客戶端代表玩家）發起儲值/購買/存檔/上榜，我想要伺服器以權威方式記錄並回傳最新狀態，以便玩家在任何裝置看到一致、不可竄改的錢包與權益。 |
| **Functional 2** | 當玩家換裝置，我想要用帳號登入還原「我的辦公室」存檔與經營史，以便延續遊戲進度。 |
| **Functional 3**（營運）| 當營運要對帳或查作弊，我想要逐筆查詢 append-only 錢包 ledger 與經營記錄，以便稽核與風控。 |
| **Emotional**（情感性） | 玩家要「我儲值的資本與特聘的夢想員工永遠不會被搞丟」的安全感（§16.9 不作惡底線）。 |
| **Social**（社交性） | 玩家要在全域排行榜以公司名登榜、分享經營傳奇（§15.3 病毒成長）。 |

### 3.3 非目標使用者（Not Our Users）

| 排除群體 | 排除原因 |
|---------|---------|
| ❌ 需要後端承載 LLM 推論/遊戲即時模擬的使用者 | 核心理念：算力留本地（玩家端），信任層不承擔遊戲運算 |
| ❌ 需要即時每幀同步的競技/對戰遊戲後端 | 本信任層為低頻信任動作設計（登入/儲值/存檔/上榜），非即時狀態同步 |
| ❌ 需要完整第三方真金流清算的 Phase A 使用者 | 金流先「假付費」，真金流 SDK 為 Phase B（接口已預留） |

*明確定義「不服務誰」與「服務誰」同等重要，防止把遊戲算力或即時同步塞進信任層而破壞成本護城河。*

---

## 4. Value Hypothesis（價值假說）

### 4.1 核心價值主張（Value Proposition Canvas）

**Pain Relievers（痛點緩解）：**

| 使用者痛點 | 我們的緩解方式 |
|-----------|-------------|
| 錢包放 localStorage 可被竄改（Q2） | 伺服器權威錢包：`wallets` + append-only `wallet_ledger`，花費走交易 + `SELECT FOR UPDATE` + 餘額不足回 402；客戶端只讀顯示 |
| 經營記錄無法歸戶、無法跨裝置（Q2） | 記錄/存檔以 `account_id` 命名空間；`/save` GET/PUT 跨裝置；`records`（破產/成就/傳承）掛帳號 |
| 無法防作弊與對帳（Q2） | 每筆異動進 ledger（含 reason/balance/meta）；排行寫入需登入且歸戶；營運可逐筆稽核 |
| 單點故障 / 停機風險 | Day1 HA：所有服務 ≥2 replicas、無 SPOF、HPA + PodDisruptionBudget、BCP from Day1 |
| 模組耦合、難維護難擴展 | Spring Modulith 7 有界上下文（HC-1~HC-5）+ Clean Architecture 4 層 + SOLID |

**Gain Creators（增益創造）：**

| 使用者期望收益 | 我們如何創造 |
|-------------|------------|
| 收真錢的同時維持零邊際成本護城河 | 只有信任層上雲，LLM/模擬留本地；後端負載遠低於即時遊戲 |
| Day1 可分可合、隨規模演進 | 7 BC 單體 HA 起步（最小成本），不改邊界即可逐一拆微服務（最大擴展） |
| 本機即可完整開發驗收 | local-first：全程可在 Rancher Desktop k8s（k3s）跑完 |

### 4.2 差異化定位

> **相對於「把 AI 互動與全部遊戲狀態都上雲、按量計費」的 AI NPC 遊戲後端，yoCat Office 後端信任層只上雲『薄信任層』、把 AI 與模擬算力零成本留在玩家端；相對於 Firebase/PlayFab 等通用 BaaS，我們以 Spring Modulith 有界上下文 + Clean Architecture 提供可自主維運、可逐步拆分、Day1 HA 的自建信任層，不被供應商鎖定、資料留本機、對帳/風控可完全掌控。**

*差異化真實且可持續：成本護城河來自「算力位置」的架構決策，不是臨時優惠；工程護城河來自嚴格的模組邊界與 HA 設計。*

---

## 5. MVP & Learning Plan（最小可行驗證計畫）

### 5.1 MVP 邊界定義

> **MVP 的目的是驗證「薄信任層 + Day1 HA + Spring Modulith 邊界」能在 local k8s 上承載收真錢所需的一致性與防竄改，而非交付完整金流清算。**

| MVP 功能（P0，≤5 項） | 驗證假設 | 最低可接受標準 |
|---------|---------|--------------|
| member BC：註冊/登入/登出/我的資料（scrypt + session） | 伺服器可權威管理身分 | scrypt 雜湊 + token 驗證端到端通過，未授權請求回 401 |
| wallet BC：topup（假付費）/spend，append-only ledger | 錢包竄改可被根除、異動可稽核 | 花費走交易 + `SELECT FOR UPDATE`，餘額不足回 402，每筆進 ledger |
| entitlement BC：購買（原子扣款+授予）/列出 | 已購權益永不重複、永不遺失 | 重複購買回 409，扣款與授予同一交易，破產不消失 |
| game-save BC：/save GET/PUT（跨裝置） | 存檔可跨裝置還原 | 以 account_id+slot upsert，換裝置登入可取回 |
| 平台底座：7 BC Spring Modulith 單體，Day1 HA（≥2 replicas）在 local k8s | 薄信任層可 HA 且模組邊界成立 | ModularityTests 綠、≥2 replicas 滾動更新不中斷服務 |

**明確不在 MVP 範圍（Non-MVP）：**
- ❌ 真金流清算/發票/退款（推遲原因：Phase B 真金流 SDK，接口先預留）
- ❌ leaderboard/record 的進階反作弊與風控儀表板（推遲原因：先驗證權威寫入，風控工具後置）
- ❌ 跨 BC 全面拆分為獨立微服務（推遲原因：Day1 以單體 HA 最小成本起步，邊界已可分）

### 5.2 Validation Metrics（驗證指標，Pre-BRD 階段）

| 指標 | 測量方式 | 通過閾值（Go）| 失敗閾值（Pivot/Kill）| 追蹤週期 |
|------|---------|:-----------:|:------------------:|---------|
| 防竄改有效性 | 客戶端無法改動伺服器餘額（滲透測試） | 100% 改動被拒 | 任一路徑可竄改餘額 | 開發週 1–2 |
| 錢包一致性 | 併發 spend 壓測（failover 下餘額不為負、ledger 與餘額相符） | 0 筆不一致 | 出現負餘額或對不上帳 | 開發週 2–3 |
| HA 可用性 | 滾動更新/殺 1 pod 時的服務可用性 | 無停機、錯誤率 < 0.1% | 出現服務中斷 | 開發週 3 |
| 模組邊界合規 | Spring Modulith ModularityTests + 依賴圖為 DAG | 全綠、無循環依賴 | 任一 HC 違規 | 每次建置 |
| local-first 可跑通 | 全流程在 Rancher Desktop k8s 完成 | 一鍵部署+測試全綠 | 需外部雲端才能跑 | 開發週 1 |

**驗證計畫時程：**

```
Week 1：member + wallet BC 骨架，local k8s 部署，防竄改與 local-first 驗證
Week 2：entitlement + game-save BC，併發一致性壓測
Week 3：record + leaderboard BC、HA/failover 驗收，ModularityTests 全綠 → 決策 Go/Pivot/Kill
```

### 5.3 Riskiest Assumption（最高風險假設，Leap of Faith）

> **核心 Leap of Faith：** 我們假設 **「把信任層與遊戲算力徹底解耦後，一條薄的伺服器權威信任層就足以支撐收真錢所需的防竄改、一致性與對帳，而不需要把任何遊戲模擬/AI 推論上雲」**。
> 若此假設為假（例如防作弊必須依賴伺服器重算遊戲邏輯），則 **成本護城河崩解、後端被迫承載遊戲運算**，專案應 **根本性 Pivot（重新定義信任邊界）**。
> 我們將透過 **MVP 的防竄改滲透測試 + 排行榜/記錄的歸戶驗證** 在 **開發週 3（§5.1 驗收）** 前驗證此假設。

| 假設 | 驗證方法 | 驗證期限 | 若錯誤的後果 |
|------|---------|---------|------------|
| 薄信任層足以防竄改且無需上雲遊戲算力（最高風險） | 滲透測試 + 歸戶/對帳驗證 | 開發週 3 | 根本性 Pivot |
| 7 BC 單體 Day1 HA 可在不改邊界下逐一拆微服務 | ModularityTests + 依賴 DAG 檢查 | 每次建置 | 調整模組切分 |

---

## 6. Clarification Interview Record（澄清訪談記錄）

> 本節記錄 Step 2 澄清提問的完整結果，是 BRD 各章節的原始輸入來源。原文保存，不摘要改寫。

### Q1 — 主要使用者

| 欄位 | 內容 |
|------|------|
| **回答** | 遊戲客戶端與玩家(終端)、營運/客服(後台對帳風控) |
| **選擇方式** | AI 推薦預設（依 codebase 與企画推斷） |
| **影響範疇** | BRD §4 Stakeholders、PDD Persona、RTM 使用者欄位 |

### Q2 — 核心痛點

| 欄位 | 內容 |
|------|------|
| **回答** | 要對遊戲收費但沒有伺服器權威的身分與錢包：錢包放 localStorage 可被竄改、失敗/經營記錄無法歸戶、無法跨裝置、無法防作弊與對帳 |
| **選擇方式** | AI 推薦預設（依企画 §17 推斷） |
| **影響範疇** | BRD §2 Problem Statement、§5 Proposed Solution、§7 Success Metrics |

### Q3 — 技術限制或偏好

| 欄位 | 內容 |
|------|------|
| **回答** | 算力留本地(LLM/遊戲模擬在玩家端)，只有信任層上雲端；本機開發全程可在 Rancher Desktop k8s 跑完(local-first)；需遵循 gendoc 原則(HA≥2、Spring Modulith HC-1~HC-5、Clean Architecture+SOLID、SECS、SSOT) |
| **選擇方式** | 指定技術 + 整合限制 |
| **影響範疇** | BRD §8 Constraints、EDD 技術選型、ARCH 架構設計 |

### Q4 — 預估流量規模（PM Expert 研究推估）

| 欄位 | 內容 |
|------|------|
| **日活用戶（DAU）** | 啟動期 5,000 DAU（遊戲早期），成長期目標 50,000 DAU |
| **同時在線峰值（PCU）** | 啟動期 500 PCU，成長期 3,000 PCU |
| **推估依據** | 參考同類休閒/模擬手遊上線首季常見量級（獨立手遊啟動期 DAU 多落在數千級），取 5,000 DAU 為啟動保守值、成長期目標 50,000 DAU（×10）；PCU 以休閒手遊線上同時率經驗值 ~DAU 的 10% 推估 ≈ 500（成長期因時區分散略低於線性，取 3,000）。信任層請求頻率低（登入/儲值/存檔/上榜為主，非每幀），故後端負載遠低於即時對戰遊戲 |
| **影響範疇** | EDD 容量規劃數值、ARCH HPA 觸發閾值、DB 連線池大小 |

> 以上數值由 PM Expert 依競品研究與行業基準自動推估，僅供容量規劃使用，不影響架構選型（架構均採 HA 設計，≥ 2 replica）。

### Q5 — 其他補充說明（動態追問）

| 欄位 | 內容 |
|------|------|
| **回答** | 七個有界上下文 Day1 即可分可合(單體 HA 最小成本 / 獨立微服務最大擴展)；金流先假付費、接口預留 Phase B 真金流 SDK |
| **追問觸發原因** | 需釐清 Day1 架構形態與金流階段邊界 |
| **影響範疇** | §5 MVP 邊界、EDD 模組拆分策略、API 金流接口設計 |

---

### 原始 IDEA（使用者輸入原文，逐字保留）

```
yoCat Office 後端信任層：member/wallet/deposit/entitlement/record/leaderboard/game-save
七個有界上下文；算力留本地、信任上雲端；現有 Node/TS 原型 + PostgreSQL(8表) + Rancher
Desktop k8s。依 gendoc 原則重新設計為 HA/Spring Modulith/Clean Architecture 架構。

（來自企画.md §17 會員系統與雲端架構）
17.1 核心原則：算力留本地，信任上雲端。一旦要收真錢，錢包就不能放 localStorage
（F12 一行 W.gems=99999 就破功）。身分／錢包／已購項目／記錄／排行必須由伺服器當權威。
但這不違反「不對 AI 收費」哲學——LLM 推論與遊戲模擬仍留本地（零成本），只有「信任層」上伺服器。
17.2 基礎設施全 local：Rancher Desktop k8s(k3s) + PostgreSQL 16 (Deployment+PVC+NodePort 30432)。
17.3 資料模型：schema-per-BC（7 個 BC：member/wallet/deposit/entitlement/record/leaderboard/game_save）。
17.4 API：/auth/*、/wallet/topup|spend、/entitlements(/buy)、/save、/records、/leaderboard。
17.6 進度：基礎設施✅、後端 API 全套✅（Java Spring Modulith，含 JUnit/BDD/合約/E2E 測試）、客戶端整合✅；之後真金流 SDK（Phase B，接口已預留）。
```

*保留原文供未來 ECR（Engineering Change Request）審查時比對，確認需求變更屬 BUG 修正還是範圍擴充。*

---

## 7. Market & Competitive Intelligence（市場與競品情報）

### 7.1 競品 / 參考資源

| 競品 / 工具 | 核心定位 | 優勢 | 劣勢 | 我們的差異 |
|-----------|---------|------|------|-----------|
| PlayFab / Firebase（遊戲 BaaS） | 託管遊戲後端（身分/經濟/排行/存檔） | 開箱即用、全託管、擴展快 | 供應商鎖定、資料不在本機、對帳/風控受限、成本隨量增 | 自建 Spring Modulith 信任層、資料留本機、可自主拆分與維運、不鎖定 |
| Spring Modulith 範例（payment-system） | 模組化單體參考架構 | module=bounded context、跨模組走 domain events、Event Publication Registry=Transactional Outbox、ModularityTests 強制邊界 | 為範例非產品、需自行補 HA/金流 | 直接採其邊界與 outbox 範式，疊上 Day1 HA 與遊戲信任層語意（wallet/entitlement/record） |
| 一般「AI NPC 遊戲」雲端後端 | AI 互動與遊戲狀態全上雲 | 玩家零硬體門檻 | AI 推論按量計費、成本高、延遲與隱私問題 | 算力留本地零成本，只上雲薄信任層，成本結構結構性更低 |

### 7.2 技術生態建議

| 層次 | 建議方案 | 選擇理由 |
|------|---------|---------|
| 程式語言 / 框架 | Spring Boot 3 + Spring Modulith | 7 有界上下文可在單一應用內落地、ModularityTests 強制 HC-1~HC-5、events.jdbc outbox 保證事件可靠；Day1 可分可合 |
| 資料庫 | PostgreSQL 16 | 既有 8 表 schema、交易 + `SELECT FOR UPDATE` 保錢包一致、JSONB 承載 save/record/detail；local 以 PVC 持久化 |
| 核心套件 / 服務 | Spring Modulith Events（JDBC Outbox）、Spring Security（身分/session）、連線池（HikariCP） | 非同步事件驅動模組間通訊（HC-3）、scrypt/session 身分權威、連線池依 Q4 容量設定 |
| 基礎設施 | Kubernetes（Rancher Desktop k3s，local-first）：Deployment ≥2 replicas + HPA + PodDisruptionBudget + PVC | HA/SCALE/無 SPOF/BCP from Day1；全程本機可跑完 |

*技術建議基於 Q3（算力留本地、信任上雲、local-first、gendoc 原則）；容量規劃基於 PM Expert 推估 — DAU 5,000（成長期 50,000）、同時在線峰值 500（成長期 3,000）。*

#### 服務角色識別

| 角色名 | 用途 | 建議技術 |
|--------|------|---------|
| trust-api（信任層應用） | 承載 7 BC 的 HTTP 入口（Presentation 層），代理玩家發出身分/錢包/權益/記錄/排行/存檔請求 | Spring Boot 3 + Spring MVC/WebFlux，Deployment ≥2 replicas + HPA |
| postgres（權威資料庫） | 帳號/session/錢包/ledger/權益/存檔/記錄/排行的唯一真實來源 | PostgreSQL 16，Deployment + PVC（local-path）+ NodePort 30432，HA/備援規劃 |
| modulith-event-outbox（事件投遞） | BC 間非同步事件可靠投遞（Transactional Outbox） | Spring Modulith Event Publication Registry（events.jdbc），落在同一 PostgreSQL |

### 7.3 研究來源

| 搜尋關鍵字 | 主要發現 | 可信度 |
|-----------|---------|--------|
| Spring Modulith bounded context / event publication registry | module=bounded context、跨模組走 domain events、outbox 保證事件可靠、ModularityTests 強制邊界 | 高 |
| 遊戲後端 server-authoritative wallet / anti-cheat | 跨玩家邊界一律 server-authoritative；append-only ledger 審計；密碼學摘要防竄改；write-behind worker 經 broker 落庫 | 高 |
| 錢包餘額一致性 / idempotency | 需交易 + `SELECT FOR UPDATE` + 冪等鍵；事件可靠投遞走 outbox；金流冪等與對帳 | 高 |

---

## 8. Initial Risk Assessment（初始風險評估）

### 8.1 風險矩陣

| # | 風險描述 | 類型 | 可能性 | 影響 | 風險等級 | 初步緩解策略 |
|---|---------|------|:------:|:----:|:------:|------------|
| R1 | 錢包餘額跨 failover 不一致（併發扣款/重啟） | 技術 | MEDIUM | HIGH | 🔴 HIGH | 交易 + `SELECT FOR UPDATE` + 冪等鍵；append-only ledger 對帳；HA 下驗證 |
| R2 | 事件未可靠投遞導致 BC 間狀態分歧 | 技術 | MEDIUM | HIGH | 🔴 HIGH | Spring Modulith Event Publication Registry（Transactional Outbox），失敗重投 |
| R3 | PostgreSQL 單點成為 SPOF | 技術 | MEDIUM | HIGH | 🔴 HIGH | DB HA/備援規劃（主從/備份還原 BCP）、PodDisruptionBudget、連線池重試 |
| R4 | Phase B 真金流冪等與對帳缺口 | 執行 | MEDIUM | HIGH | 🔴 HIGH | 先預留冪等鍵/對帳接口；假付費期即走 ledger，真金流接入時沿用 |
| R5 | 過度工程：薄信任層被塞進遊戲邏輯而破壞成本護城河 | 執行 | MEDIUM | MEDIUM | 🟡 MEDIUM | 嚴守信任邊界（§3.3 非目標）、ModularityTests、架構守則審查 |
| R6 | Phase B 真金流與個資合規缺口（金流清算/發票/隱私法規，如 GDPR/個資法、各地金流監管） | 法規 | MEDIUM | HIGH | 🔴 HIGH | 真金流接入前法務介入（§10.2）；個資最小化、ledger 僅存必要欄位；依目標市場金流/稅務法規設計對帳與發票接口 |

風險等級 = 可能性 × 影響：HIGH/HIGH = 🔴，其他組合 = 🟡 或 🟢。

### 8.2 Kill Conditions（專案終止條件）

| 終止條件 | 觸發閾值 | 檢查時機 |
|---------|---------|---------|
| 防竄改假設不成立 | 滲透測試中仍有任一路徑可由客戶端竄改伺服器餘額，且修復需把遊戲邏輯上雲重算 | MVP 驗收（開發週 3） |
| 一致性無法達標 | 併發/failover 壓測出現負餘額或 ledger 與餘額對不上，且無法在不犧牲 HA 下解決 | 併發壓測（開發週 2–3） |
| 成本護城河崩解 | 為滿足需求必須把 LLM 推論或遊戲模擬搬上雲（違反「算力留本地」核心理念） | 任一階段審查 |
| local-first 破局 | 全流程無法在 Rancher Desktop k8s 跑完，必須依賴外部雲端才能開發驗收 | 開發週 1 |

### 8.3 Dependencies & External Risks（依賴與外部風險）

| 依賴項 | 類型 | 關鍵性 | 若失效的影響 | 備援方案 |
|--------|------|:-----:|------------|---------|
| PostgreSQL（權威資料庫） | 技術 | 高 | 全信任層不可用、金流停擺 | HA/備援（主從+備份還原 BCP）、PodDisruptionBudget、連線重試 |
| Rancher Desktop k8s（k3s，local） | 技術 | 高 | 無法本機開發/驗收 | 標準 k8s manifest，可移植至任一相容叢集 |
| Phase B 真金流 SDK（第三方金流） | 市場/技術 | 中 | 無法收真錢（但假付費與 ledger 不受影響） | 接口已預留、冪等/對帳先行；Phase A 以假付費驗證 |

*本節聚焦「我們無法完全控制」的外部因素，與 §8.1 內部風險區隔。*

### 8.4 Pre-mortem Exercise（失敗前情境模擬）

| # | 失敗情境 | 根本原因 | 預防措施 | 早期預警訊號 |
|---|---------|---------|---------|-----------|
| F1 | failover 時錢包出現負餘額/對不上帳，玩家信任崩潰 | 併發與分散式一致性低估 | 交易+行鎖+冪等鍵、HA 壓測前置 | 壓測出現不一致筆數 > 0 |
| F2 | 為防作弊把遊戲邏輯搬上雲，成本護城河消失 | 信任邊界未守住 | §3.3 非目標 + 架構守則審查 + ModularityTests | 出現「伺服器重算遊戲邏輯」的需求 |
| F3 | Spring Modulith 邊界腐化成大泥球，無法拆分 | 跨模組直接呼叫、共享可變狀態 | HC-1~HC-5 強制、依賴 DAG、events 驅動 | ModularityTests 出現違規 |
| F4 | DB 成 SPOF，一掛全停 | 未做 DB HA/BCP | 主從/備份還原、PodDisruptionBudget | 單 DB 無備援、無還原演練 |
| F5 | 真金流接入時冪等/對帳缺口導致重複扣款 | Phase A 未預留冪等接口 | 冪等鍵與對帳接口 Day1 預留 | 假付費期出現重複 ledger |
| F6 | 破產/recycle 機制誤刪玩家特聘夢想員工或已購權益，觸發信任崩潰與退款潮（產品特定，呼應 §16.9 不作惡底線） | entitlement/record 與遊戲 recycle 邏輯邊界不清、已購權益未隔離於遊戲狀態重置 | 已購權益走 entitlement BC（原子授予、破產不消失，§5.1）、記錄 append-only、recycle 僅影響遊戲內狀態不觸及權威權益 | 客服出現「我特聘的員工/已購項目不見了」申訴 |

**Pre-mortem 結論：** 最可能的失敗路徑是 **F1（錢包一致性在 HA/failover 下失守）**，需在 BRD/EDD 階段優先設計交易、行鎖、冪等與對帳機制，並在 MVP 即壓測。

---

## 9. Business Potential（初步商業潛力）

### 9.1 商業模式假說

| 項目 | 初步假設 |
|------|---------|
| 收入來源 | 本信任層是變現底座：承載買斷（Steam/itch $10–15）、外觀/劇本/季票、儲值注資（真錢→💎資本，Phase B）。本身不直接定價，但是所有付費的可信前提 |
| 主要定價策略 | A 期自帶算力買斷 → B 期雲端 F2P + IAP/訂閱（離線營運 Pass）；永不對 AI 互動收費 |
| 主要成本驅動因子 | 信任層雲端成本（DB + API pods，因「只上雲信任層」而低）；真金流手續費（Phase B） |
| 核心資源 | PostgreSQL 權威資料、Spring Modulith 信任層、local-first k8s 可移植性 |
| 獲客管道 | 遊戲本體病毒分享（drama clip）帶動；信任層為留存/付費轉化服務 |

*以上為概念階段假設，需在 BRD §11 Business Model 深化。*

### 9.2 戰略對齊

| 公司策略目標 | 本 IDEA 的貢獻方式 | 對齊強度 |
|-----------|-----------------|---------|
| 「不對 AI 收費、賣內容/外觀/規模」的護城河（企画 §15） | 把收費所需的信任層與 AI 算力解耦，守住零邊際成本 | 強 |
| A→B→C 算力路線並行、程式與算力位置解耦（企画 §15.7） | 信任層獨立於算力位置，client 僅切換生成來源不影響信任（屬使能/間接貢獻，非直接驅動此策略） | 中 |
| 情感變現引擎（人才庫/破產 recycle/儲值注資，企画 §16） | 以伺服器權威保證「付費與心愛的人永不永久失去」的不作惡底線 | 強 |

---

## 10. Executive Sponsorship & Stakeholder Alignment（執行贊助與關鍵利害關係人對齊）

### 10.1 Executive Sponsor

| 欄位 | 內容 |
|------|------|
| **贊助人** | TBD — 由 BRD 釐清（專案發起者：yoCat Office 維護團隊，boss@yocat.example） |
| **贊助原因** | 收真錢的前提是伺服器權威信任層，直接決定商業化可行性 |
| **授權範圍** | 預算上限 by BRD；技術決策自主範圍＝信任層架構與 local k8s 基礎設施 |
| **核可日期** | 待 BRD 審查 |
| **升級路徑** | 若遇阻礙，升級至專案發起者/董事（企画維護者） |

### 10.2 Stakeholder Pre-alignment Matrix

| 利害關係人 | 角色 | 對本 IDEA 的態度 | 主要顧慮 | 對齊策略 | 對齊期限 |
|-----------|------|:--------------:|---------|---------|---------|
| 遊戲客戶端團隊 | API 消費者 | 支持 | API 穩定性與向後相容 | OpenAPI 契約先行、假付費接口不變 | BRD 前 |
| 遊戲營運/客服 | 風控/對帳 | 支持 | 能否逐筆稽核與退款 | ledger append-only + 記錄歸戶 + 對帳接口 | PRD 前 |
| 架構/工程 | 實作 | 中立 | HA/模組邊界/一致性成本 | gendoc 原則 + ModularityTests 強制 | BRD 前 |
| 法務 | Legal | 中立 | 真金流合規、個資 | Phase B 金流前法務介入、個資最小化 | 真金流接入前 |

### 10.3 Communication Plan（溝通計畫）

| 里程碑 | 溝通對象 | 溝通形式 | 期限 |
|--------|---------|---------|------|
| IDEA 核准 | Executive Sponsor | 1-pager + 口頭簡報 | BRD 啟動前 |
| BRD 完成 | All Stakeholders | 文件分享 + 審查會議 | BRD 生成後 |
| MVP 驗收 | 工程 + 營運 | local k8s demo + 壓測報告 | 開發週 3 |

---

## 11. IDEA Quality Score（構想品質評分）

**整體評分**：★★★★★（5/5）

| 評分維度 | 分數 | 評估說明 | 改進建議 |
|---------|:----:|---------|---------|
| 目標清晰度 | 1 / 1 | 明確：薄信任層上雲、算力留本地，邊界清楚 | 維持邊界守則 |
| 使用者具體度 | 1 / 1 | 客戶端/玩家/營運三類，含職業/行為/痛點 | 於 PRD 補 persona 細節 |
| 痛點可量化 | 1 / 1 | 竄改/對帳/跨裝置痛點具體，金流動作 100% 暴露面 | 以營運數據驗證頻率 |
| 範圍邊界明確 | 1 / 1 | §3.3 非目標 + §5.1 Non-MVP + Phase A/B 分界 | — |
| 技術可行性初判 | 1 / 1 | 既有原型端到端全綠，重構目標明確、技術棧成熟 | HA/一致性壓測驗證 |

**本次評分說明：**

```
【IDEA 品質評分】★★★★★（5/5）
  問題與使用者具體、痛點明確且有既有原型佐證可行性
  架構方向（Spring Modulith 7 BC + Clean Architecture + Day1 HA）清晰且可落地
  最大不確定性在 HA/failover 下的錢包一致性，已列為最高風險假設與 Kill Condition
```

---

## 12. Critical Assumptions（關鍵假設清單）

| # | 假設陳述 | 影響層級 | 不確定性 | 驗證方式 | 驗證期限 |
|---|---------|:-------:|:-------:|---------|---------|
| A1 | 薄信任層足以防竄改且無需上雲遊戲算力（基於 Q2 痛點 + Q3 核心理念） | HIGH | HIGH | 滲透測試 + 歸戶/對帳驗證 | MVP 驗收（開發週 3） |
| A2 | 啟動期 5,000 DAU / 500 PCU 量級下，單體 HA（≥2 replicas）足以承載（基於 Q4 規模） | HIGH | MEDIUM | 容量/壓力測試 | BRD/EDD 審查前 |
| A3 | 7 BC 可以 Spring Modulith 單體 Day1 落地，並在不改邊界下逐一拆微服務（基於 Q3/Q5 技術） | MEDIUM | HIGH | ModularityTests + 依賴 DAG（PoC） | EDD 完成前 |
| A4 | 錢包一致性可用「交易 + `SELECT FOR UPDATE` + 冪等鍵 + outbox」在 HA 下達成（基於研究建議） | HIGH | HIGH | 併發/failover 壓測 | MVP 驗收 |
| A5 | 既有 8 表 schema 與 API 契約可作為重構基礎而不需大改（基於既有原型全綠） | MEDIUM | LOW | 契約比對 + 回歸測試 | BRD 前 |

*A1/A4 若假設錯誤，專案應終止或根本性調整方向（見 §8.2 Kill Conditions）。優先驗證順序：A1 → A4 → A2 → A3 → A5。*

---

## 13. Open Questions（待解問題）

| # | 問題 | 影響層級 | 若不解決的後果 | 負責人 | 狀態 |
|---|------|:-------:|------------|--------|:----:|
| OQ1 | PostgreSQL 的 HA/BCP 具體形態（主從？備份還原 RPO/RTO？） | 策略 | DB 仍為 SPOF，違反 Day1 無單點 | Engineering | 🔲 OPEN |
| OQ2 | Phase B 真金流 SDK 選型與冪等/對帳契約 | 範圍 | 真金流接入時可能重複扣款 | PM + Engineering | 🔲 OPEN |
| OQ3 | 7 BC 中 deposit 與 wallet 的邊界劃分（儲值 vs 錢包異動的職責切割） | 範圍 | 影響模組邊界與 outbox 事件設計 | Engineering | 🔲 OPEN |
| OQ4 | 個資/金流合規範圍（真金流接入前的法務要求） | 法規 | 可能影響上市時程 | Legal | 🔲 OPEN |

---

## 14. IDEA → BRD Handoff Checklist

| # | Checklist 項目 | 狀態 | 負責人 |
|---|--------------|:----:|--------|
| C1 | 一句話描述（§1.1）已清晰表達，無模糊詞 | ✅ | PM |
| C2 | 核心假說（§1.2）符合可測試格式 | ✅ | PM |
| C3 | Q1 使用者描述具體（非「所有人」） | ✅ | PM |
| C4 | Q2 痛點已量化（竄改/對帳/跨裝置暴露面有估算） | ✅ | PM |
| C5 | Q4 規模已選定（DAU/PCU 已推估） | ✅ | PM |
| C6 | 至少 1 個競品在 §7 已識別（本文件 3 個） | ✅ | PM |
| C7 | 至少 3 項風險在 §8 已識別（本文件 5 項） | ✅ | PM |
| C8 | Kill Conditions（§8.2）已定義（本文件 4 條） | ✅ | PM |
| C9 | 關鍵假設 A1（§12）已識別並有驗證計畫 | ✅ | PM |
| C10 | IDEA Quality Score ≥ 3（本文件 5/5） | ✅ | AI 自動 |
| C11 | 原始 IDEA 原文已逐字保留（§6 末段） | ✅ | AI 自動 |
| C12 | Open Questions 中無 P0 級別未解技術問題（OQ 皆 BRD/EDD 階段可解） | ✅ | Engineering |

---

## 15. Traceability Note（溯源說明）

*此文件是需求鏈的起點，扮演「原始意圖記錄者」的角色。*

**向下追溯（Forward）：**

```
IDEA.md （本文件）
  └─► BRD.md       ← /gendoc brd
        └─► PRD.md      ← /gendoc prd
              └─► PDD.md      ← /gendoc pdd
                    └─► EDD.md      ← /gendoc edd
                          └─► ARCH / API / SCHEMA / BDD → 實作
```

**章節 ← 素材對應：**

| §章節 | 來源素材 |
|-------|---------|
| §1 Idea Essence | docs/req/企画.md §15–§17、state q1–q5 |
| §2 Problem Statement | docs/req/企画.md §17.1、state q2 |
| §3 Target Users | state q1、docs/req/企画.md §17.5 |
| §4 Value Hypothesis | docs/req/server-src/accounts.ts、schema.sql、research_summary |
| §5 MVP Plan | docs/req/server-src/accounts.ts、state key_features、q5 |
| §6 Interview Record | state q1–q5、企画.md §17 |
| §7 Market Intelligence | state research_summary、q4 |
| §8 Risk Assessment | research_summary（已知風險）、企画.md §17 |
| §9 Business Potential | docs/req/企画.md §15–§16 |

**需求變更判斷準則：**

| 場景 | 對比依據 | 分類 |
|------|---------|------|
| 功能行為與原始 IDEA Q1/Q2 一致但實作有誤 | IDEA §6 原文 | **BUG**（修正，不需 ECR） |
| 功能需求超出原始 Q1/Q2 描述範圍（如把遊戲邏輯上雲） | IDEA §6 原文 + §3.3 非目標 | **ECR**（需人工評估變更範圍） |
| 技術選型偏離 Q3（如放棄 HA/模組邊界）且理由不足 | IDEA §6 Q3 | **ECR 或 ADR**（需說明決策依據） |

---

## Appendix A：Research Raw Data（研究原始資料）

### 搜尋 1：競品與開源專案

```
查詢：Spring Modulith bounded context event publication registry payment system
結果：
- module = bounded context；跨模組只走 domain events（不直接呼叫對方內部）。
- ModularityTests 在建置期強制邊界（HC-1~HC-5 的落地工具）。
- Event Publication Registry = Transactional Outbox，保證事件可靠投遞。
- wallet 為獨立模組；重複請求視為狀態記錄（冪等思維）。
```

### 搜尋 2：技術最佳實踐

```
查詢：game backend server-authoritative wallet anti-cheat architecture
結果：
- 跨玩家邊界一律 server-authoritative。
- auth / persistence / anti-cheat 拆服務。
- write-behind worker 經 broker（Kafka/NATS）落庫。
- append-only ledger 審計；密碼學摘要防竄改。
- 建議：Spring Boot 3 + Spring Modulith（@ApplicationModule、events.jdbc outbox）；
  Clean Architecture 4 層 + SOLID；PostgreSQL；K8s ≥2 replicas + HPA + PodDisruptionBudget。
```

### 搜尋 3：已知挑戰與陷阱

```
查詢：wallet balance consistency failover idempotency outbox
結果：
- 錢包餘額跨 failover 一致性：需交易 + SELECT FOR UPDATE + 冪等鍵。
- 事件可靠投遞：outbox 模式。
- 金流冪等與對帳：需冪等鍵與逐筆對帳。
```

---

## Appendix B：Document History

| 版本 | 日期 | 作者 | 修改摘要 |
|------|------|------|---------|
| v0.1-capture | 2026-10-30 | /gendoc idea | 初始捕捉，由 AI 自動填寫（後端信任層聚焦） |
| v0.2 | — | — | — |

---

## Appendix C：素材清單（docs/req/ 引用）

| 檔案路徑 | 素材類型 | 應用於（對應章節） |
|---------|---------|-----------------|
| `docs/req/企画.md` | 產品企画/交接聖經（§15 商業化、§16 獲利、§17 會員系統與雲端架構） | §1、§2、§6、§9、§15、Appendix A |
| `docs/req/schema.sql` | 資料庫 schema（8 表伺服器權威） | §4、§5、§12（A5） |
| `docs/req/postgres.yaml` | k8s PostgreSQL manifest（Rancher Desktop） | §7.2、§8.3 |
| `docs/req/server-src/index.ts` | HTTP server + LLM router + 靜態服務 | §3、§4 |
| `docs/req/server-src/accounts.ts` | 信任層 API（帳號/錢包/權益/存檔/記錄/排行） | §4、§5、§7 |
| `docs/req/server-src/db.ts` | PostgreSQL 連線池與交易封裝 | §7.2、§8.1 |
| `docs/req/codebase-tree.txt` | 專案目錄樹 | §0 |

---

*此 IDEA.md 由 /gendoc idea 自動生成並保存，聚焦 yoCat Office 後端信任層。它記錄了需求探索過程的原始輸入、假設與品質評分，是未來 BUG vs ECR 判斷的一級依據。核心理念：算力留本地（零成本護城河），信任上雲端（收真錢的伺服器權威），全程 local-first 可在 Rancher Desktop k8s 跑完。*
