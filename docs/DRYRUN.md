---
doc-type: DRYRUN
version: 1.0.0
description: Pipeline Execution Manifest — 流水線執行摘要，記錄本次 gendoc 執行的量化錨點、條件分支結果與每步完整性標準
---

# MANIFEST — Pipeline Execution Manifest
<!-- SDLC 流水線管控 — Pre-flight Dryrun：在任何文件生成之前，先量化上游文件、決定哪些步驟啟用、並固化每步的品質驗收閾值 -->
<!-- 由 gendoc-flow DRYRUN 步驟自動生成；gate-check 將引用本文件的量化錨點驗收每份輸出文件 -->

---

## Document Control

| 欄位 | 內容 |
|------|------|
| **Generated Date** | 2026-10-30 |
| **Pipeline Version** | 4.0 |
| **EDD Version** | v1.0.0 |
| **Client Type** | api-only |
| **Has Admin Backend** | false |
| **Active Steps Count** | 23 |
| **Skipped Steps Count** | 11 |

---

## §1 文件說明

本文件（MANIFEST.md）是 gendoc pipeline 的**執行前清單**，在任何文件實際生成之前產出。

它回答三個問題：
1. **量化錨點是什麼？**（§2.1）— 從上游文件萃取的數字，成為所有下游品質規則的基礎
2. **哪些步驟要跑？**（§2.2）— 依 pipeline.json 條件篩選，明確標示 Active / Skipped
3. **每步驗收標準是什麼？**（§4）— gate-check 將逐步核對，不符即失敗

> 修改本文件等同於修改驗收標準。若需調整某步驟的量化門檻，應在 DRYRUN 步驟重新執行，而非直接手動編輯本文件的 `{{PLACEHOLDER}}` 外的值。

---

## §2 Execution Context（執行環境）

### §2.1 System Parameters（系統量化錨點）

以下數值由 DRYRUN 生成步驟從上游文件自動計算，是**所有下游步驟 gate-check 的量化基準**。

| 參數 | 值 | 說明 |
|------|----|------|
| `entity_count` | 38 | EDD.md classDiagram 中的 class 數量（grep -c '^\s*class '）；影響 SCHEMA min_table_count |
| `rest_endpoint_count` | 16 | EDD.md 中的 REST 端點數（<<REST>>、<<Interface>>、HTTP 動詞）；若計算結果 < 5，使用保守預設值 10（避免過低門檻無意義）；影響 API min_endpoint_count |
| `user_story_count` | 8 | PRD.md 中 US- 標題數量（grep -c '^## US-\|^### US-'）；影響 RTM min_row_count、BDD min_scenario_count |
| `arch_layer_count` | 18 | ARCH.md §3 Tech Stack 表格非標頭列數（最小值 4）；影響 test-plan min_h2_sections |

> **量化錨點說明**：以上數字在生成後不得手動修改。若上游文件更新（如新增 Entity 或 User Story），需重新執行 DRYRUN 步驟以更新錨點，所有下游 gate-check 閾值將自動聯動更新。

---

### §2.2 Active Conditional Steps（條件步驟啟用狀態）

以下步驟依 `client_type` 和 `has_admin_backend` 狀態決定啟用與否。

| Step ID | 條件 | Active Y/N | 原因 |
|---------|------|:----------:|------|
| PDD | `client_type != none` | false | client_type = api-only |
| VDD | `client_type != none` | false | client_type = api-only |
| FRONTEND | `client_type != none` | false | client_type = api-only |
| AUDIO | `client_type == game` | false | client_type = api-only |
| ANIM | `client_type == game` | false | client_type = api-only |
| CLIENT_IMPL | `client_type != none` | false | client_type = api-only |
| ADMIN_IMPL | `has_admin_backend` | false | has_admin_backend = false |
| RESOURCE | `client_type != none` | false | client_type = api-only |
| BDD-client | `client_type != none` | false | client_type = api-only |
| MOCK | `client_type != api-only` | false | client_type = api-only |
| PROTOTYPE | `client_type != api-only` | false | client_type = api-only |

---

## §3 Mandatory Steps Checklist（強制步驟清單）

以下步驟在所有 `client_type` 條件下**永遠啟用**（condition: always）。

| Step ID | Output Files | Layer | Status |
|---------|-------------|-------|--------|
| IDEA | docs/IDEA.md | 需求層 | active |
| BRD | docs/BRD.md | 需求層 | active |
| PRD | docs/PRD.md | 需求層 | active |
| CONSTANTS | docs/CONSTANTS.md | 需求層 | active |
| EDD | docs/EDD.md | 設計層 | active |
| ARCH | docs/ARCH.md | 設計層 | active |
| API | docs/API.md | 設計層 | active |
| SCHEMA | docs/SCHEMA.md | 設計層 | active |
| UML | docs/diagrams/*.md | 設計層 | active |
| test-plan | docs/test-plan.md | 品質層 | active |
| BDD-server | features/*.feature | 品質層 | active |
| RTM | docs/RTM.md | 品質層 | active |
| runbook | docs/runbook.md | 運維層 | active |
| LOCAL_DEPLOY | docs/LOCAL_DEPLOY.md | 運維層 | active |
| CICD | docs/CICD.md | 運維層 | active |
| DEVELOPER_GUIDE | docs/DEVELOPER_GUIDE.md | 運維層 | active |
| UML-CICD | docs/diagrams/cicd-*.md | 運維層 | active |
| ALIGN | docs/ALIGN_REPORT.md | 稽核層 | active |
| CONTRACTS | docs/blueprint/contracts/ | 實作層 | active |
| HTML | docs/pages/ | 稽核層 | active |

> Status 值：`PENDING`（尚未執行）、`DONE`（已完成）、`FAILED`（未達品質門檻）

---

## §4 Per-Step Completeness Standards（逐步完整性標準）

以下每行定義 gate-check 在驗收該步驟輸出時的最低要求。所有帶 `{{PLACEHOLDER}}` 的欄位在 DRYRUN 生成時由量化錨點計算填入。

| Step ID | Output File | Min §Sections | Quantitative Rules | Anti-Fake Rules |
|---------|-------------|:-------------:|--------------------|-----------------|
| IDEA | docs/IDEA.md | 3 | 至少 1 個 Persona 卡片；Elevator Pitch 不得為空 | no_bare_placeholder；no_trivial_entity_names |
| BRD | docs/BRD.md | 5 | MoSCoW 表格含 P0/P1/P2/Out 四分類；KPI 含具體數字 | no_bare_placeholder；min_section_words_30 |
| PRD | docs/PRD.md | 6 | US 數量 ≥ 1；每個 US 含 AC；NFR 數字不得為「TBD」 | no_bare_placeholder；no_trivial_entity_names |
| CONSTANTS | docs/CONSTANTS.md | 3 | 至少 5 個常數條目；每條含 name/value/unit/source/note | no_bare_placeholder |
| PDD | docs/PDD.md | 4 | 至少 3 個 Screen；每個 Screen 含欄位清單 | no_bare_placeholder；min_section_words_30 |
| VDD | docs/VDD.md | 4 | 至少 5 個 Design Token；Color Palette 不得為空 | no_bare_placeholder |
| EDD | docs/EDD.md | 8 | §3.3 不含 placeholder；§7 SCALE 含具體數字；UML 9 大圖已生成 | no_bare_placeholder；no_duplicate_paragraphs_150；min_section_words_30 |
| ARCH | docs/ARCH.md | 6 | C4 L1/L2/L3 三圖均存在；§14 ADR ≥ 1 條目；§15 12 項 NFR 均已驗證 | no_bare_placeholder；no_duplicate_paragraphs_150 |
| API | docs/API.md | 5 | min_endpoint_count = max(16, 5)；含 Authentication / Error Codes / Rate Limiting 章節 | no_bare_placeholder；required_keywords_per_section |
| SCHEMA | docs/SCHEMA.md | 4 | min_table_count = max(38, 3)；含 Indexes / Migration 章節 | no_bare_placeholder；no_trivial_entity_names |
| FRONTEND | docs/FRONTEND.md | 4 | 至少 3 個 Component；E2E 覆蓋範圍不得為空 | no_bare_placeholder |
| AUDIO | docs/AUDIO.md | 3 | BGM/SFX 清單至少各 1 項；音效觸發邏輯不得為空 | no_bare_placeholder |
| ANIM | docs/ANIM.md | 3 | 至少 3 個動畫定義；效能預算不得為空 | no_bare_placeholder |
| CLIENT_IMPL | docs/CLIENT_IMPL.md | 4 | 場景結構已定義；資源載入策略不得為空 | no_bare_placeholder |
| ADMIN_IMPL | docs/ADMIN_IMPL.md | 4 | RBAC 角色 ≥ 2；路由清單不得為空 | no_bare_placeholder；no_trivial_entity_names |
| RESOURCE | docs/RESOURCE.md | 3 | 資產條目 ≥ 5；每條含 ID/檔名/type/prompt | no_bare_placeholder |
| UML | docs/diagrams/ | — | 9 大圖全部存在；class-inventory.md 已生成 | no_bare_placeholder |
| test-plan | docs/test-plan.md | 20 | 覆蓋率目標 ≥ 80%；Unit/Integration/E2E 三層均有策略 | no_bare_placeholder；min_section_words_30 |
| BDD-server | features/*.feature | — | min_scenario_count = ceil(8 × 0.8)；每個 Scenario 含 @TC-ID tag | no_bare_placeholder；required_keywords_per_section |
| BDD-client | features/client/*.feature | — | min_scenario_count = ceil(8 × 0.6)；每個 Scenario 含 @TC-ID tag | no_bare_placeholder |
| RTM | docs/RTM.md | 4 | min_row_count = 8（RTM 列數 ≥ US 數量）；需求覆蓋率 ≥ 100% P0 | no_bare_placeholder；no_duplicate_paragraphs_150 |
| runbook | docs/runbook.md | 4 | 至少 3 個 Incident 場景；每個場景含 診斷步驟 + 修復命令 | no_bare_placeholder；min_section_words_30 |
| LOCAL_DEPLOY | docs/LOCAL_DEPLOY.md | 4 | API Server replica ≥ 2；docker-compose 命令可執行 | no_bare_placeholder |
| CICD | docs/CICD.md | 4 | Pipeline stages ≥ 3；Secret 注入策略不得為空 | no_bare_placeholder |
| DEVELOPER_GUIDE | docs/DEVELOPER_GUIDE.md | 3 | 日常操作命令 ≥ 5 條；Make targets 不得為空 | no_bare_placeholder |
| UML-CICD | docs/diagrams/cicd-*.md | — | 5 張 CI/CD UML 圖全部存在 | no_bare_placeholder |
| ALIGN | docs/ALIGN_REPORT.md | 3 | CRITICAL 問題數 = 0；HIGH 問題數 = 0（或已全部 FIX） | no_bare_placeholder |
| CONTRACTS | docs/blueprint/contracts/ | — | openapi.yaml 通過 openapi-spec-validator；path 數量 ≥ max(16, 5) | no_bare_placeholder |
| MOCK | docs/blueprint/mock/ | — | main.py --check 可成功啟動；Mock endpoint 數量 ≥ max(16, 5) | no_bare_placeholder |
| PROTOTYPE | docs/pages/prototype/ | — | index.html 存在且可瀏覽；至少 3 個 Screen 頁面 | no_bare_placeholder |
| HTML | docs/pages/ | — | index.html 存在；所有 docs/*.md 均有對應連結 | no_bare_placeholder |

---

## §5 Anti-Fake Guard Summary（防偽造規則說明）

gate-check 在驗收每份輸出文件時，除量化規則外，還會執行以下五條**防偽造規則**。任一規則失敗即視為 FAILED。（JSON key 使用底線+數字格式，如 `min_section_words_30`）

| 規則名稱 | 觸發條件 | 說明 |
|---------|---------|------|
| **no_bare_placeholder** | 文件中殘留未替換的 `{{...}}` 語法 | 所有 `{{PLACEHOLDER}}` 必須在生成時替換為真實內容。殘留 placeholder 代表生成不完整，等同於「空白填表」。 |
| **min_section_words_30** | 任何 `##` 或 `###` 章節的正文字數不足 30 字 | 每個非標題章節的實質內容不得少於 30 個字（中英文均計）。避免「章節標題存在但內容為空」的偽完成。 |
| **no_duplicate_paragraphs_150** | 文件中存在兩段以上完全相同且長度 ≥ 150 字元的段落 | 禁止跨章節複製貼上。相同段落出現 ≥ 2 次代表 AI 以重複填充代替實質內容，屬偽造。 |
| **required_keywords_per_section** | 特定章節缺少必要關鍵字（依步驟類型定義） | 例如：API.md 的 Authentication 章節必須包含 `Bearer` 或 `API Key`；Error Codes 章節必須包含 `4xx` 或 `5xx`。缺少關鍵字代表章節為空洞佔位。 |
| **no_trivial_entity_names** | 文件中出現 `FooBar`、`TestEntity`、`SampleClass`、`MyModel`、`Example` 等顯然虛假的實體名稱 | 所有 Entity、Class、Table、Endpoint 名稱必須來自真實業務語境（從 PRD/EDD 上游文件衍生）。虛假命名代表 AI 未讀取上游文件即自行杜撰。 |

> **重要**：防偽造規則的目的不是追求「形式正確」，而是確保 AI 在生成每份文件時確實**讀取了上游文件**並**用上游內容填充**，而非以 placeholder 或重複段落濫竽充數。
