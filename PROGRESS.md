# PROGRESS — yoCat Office 監察儀表板

> 監察人（AI agent）維護。狀態：✅完成 / 🟡進行中 / ⬜待辦 / ❌阻塞。
> 配合 `企画.md` 閱讀。接手者從「下一步」開始。

## 里程碑總覽
| Phase | 項目 | 狀態 | 驗收標準 |
|---|---|---|---|
| 0 | 本地 vLLM 端點 | ✅ | :8000 /v1/models 回 200 |
| 0 | 企画書交接文件 | ✅ | `企画.md` |
| 1 | HTML5 等角辦公室 client 原型 | ✅ | `client/index.html` 可開，6角色走動+4動作 UI（離線內建行為） |
| 1 | Node TS 後端 + LLM router | ✅ | 角色心聲來自本地 LLM（`server/`，零依賴，`/health`+`/character/think`+`/character/chat` 實測回繁中，2026-10-29） |
| 1 | client↔後端串接 | ✅ | `USE_BACKEND=true`；think/chat 走 HTTP。WebSocket 即時世界同步留待 Phase 3 營運期再上 |
| 2 | 角色精靈池（每部門 15 變體） | ✅ | `assets/sprites/pool/{dept}/*.png` 共 90 個，直幅全身、隨機髮/膚/表情/眼鏡/配件（2026-10-29） |
| 2 | 招募變化系統（池 + 客戶端參數） | ✅ | client 載入池貼圖，每角色抽池 + 色相/縮放/鏡像；`drawChar` 改畫精靈（2026-10-29） |
| 1 | 遊戲系統 P0 經濟核心 | ✅ | 生產力→營收/日終結算/過勞虧損張力 |
| 1 | 遊戲系統 P1 關係網+八卦+員工事件 | ✅ | 關係分數/LLM 八卦/五型主動請求卡片 |
| 2 | 遊戲系統 P2 存檔+離線進度 | ✅ | localStorage 存檔 + 「歡迎回來」離線報告 |

## 關鍵決策紀錄
- 首里程碑：Phase 1 可看 MVP + Phase 2 素材管線並行。

## 下一步
1. **（Phase 2 續）把 6 個透明精靈接進 client**：`client/index.html` 的 `drawChar` 改用 `assets/sprites/*.png` 貼圖取代目前的幾何圖形（依 dept 對應：總→boss、產→product、銷→sales、人→hr、發→rnd、財→finance）。



## 動畫（Blender 3D，2026-10-29）
- 決定走 **Blender 3D**（真實步態、跨格一致）；ControlNet 2D 實測會閃爍+姿勢不跟骨架，放棄。SDXL 池改當頭像/UI。
- 可愛 chibi 角色建構器 + 走路動畫：`scratchpad/blender_char.py`（圓角 box+球、卡通 ShaderToRGB+Freestyle 描邊、關節 keyframe、EEVEE 等角正交+透明、8 格/動作）。
- **已完成：走路/待機/坐下打字/講話 各 8 格**（`scratchpad/blender_anim.py`，build 一次→套 clip→各渲；`blender_anim.py -- all` 或指定 clip）。坐姿下半身遊戲中被桌擋,重點上半身打字。
- **雷**：`primitive_cube_add(size=1)` scale 用 size 不是 size/2；Blender 5.1 Action 無 `.fcurves`。
- **已接進遊戲**：`assets/anim/{walk,idle,sit,talk}/*.png`（一致 bbox 裁切），client `drawChar` 改播動畫影格（`animOf()` 狀態→clip、`ANIM_MS=110`、方向翻轉）。截圖/GIF 驗證角色在辦公室走/坐。
- **6 部門配色/配件變體完成**：`blender_anim2.py -- <dept>`（參數化髮/衣/褲/鞋 + 領帶/眼鏡/耳麥），6 部門各 4 動作 ×8 格 → `assets/anim/<dept>/<clip>/`（一致 bbox）。client 依 `DEPT_FOLDER[c.dept]` 選貼圖組，已進遊戲、各部門不同。
- **每員工變化完成**：`blender_anim2.py -- <dept> <variant>`（變體覆寫髮色+膚色，每部門 4 變體）；6×4×4×8=768 格 → `assets/anim/<dept>/v<k>/<clip>/`。client 招募抽 `variant`（0-3）+ `varScale`/`faceDir`；同部門每人不同。debug `?crowd` 同部門多人、`?roam` 持續走動。
- **動畫角色系統完成**：可愛 3D → 走/坐/講話/待機 → 6 部門配色配件 → 每員工變體 → 全進遊戲。

## 遊戲系統（2026-10-29）
- **方向性貼圖（已修正）**：背面走路渲染（`blender_anim2.py -- <dept> <v> back`，camera +Y）。幾何推導：臉在 -Y、相機右=世界(+X,+Y)→ `sx=vx-vy`(右) `sy=vx+vy`(下)；**sx>0 鏡像、sy<0(往上/遠離)用背面**，正/背面同規則。**關鍵修正**：(1) 腳 Y 偏移要負(-0.12)讓腳尖朝 -Y(前)，否則「腳跟往前走」；(2) `walk_back/` 內檔名是 `walk_NN.png`，client preload 要 `fileClip='walk'` 否則 404 掉回正面。debug URL `?dirtest`（4 角色各走一方向驗證朝向）。drawChar 退路改用同角色任何已載入格，不掉回幾何圖。
- **經濟核心循環（P0）**：每人 `skill/salary`→`prod()`=技能×心情×精力×部門權重→工作時段累積營收/訂單；技能隨工作成長；任務 12s 自動完成給獎金；能量/心情動態（休息回、過勞掉）。
- **日終結算**：每天 18:00 `endOfDay()`：營收−薪資=淨利→現金；日報(營收/薪資/淨利/現金/訂單/士氣/MVP)顯示在公司動態。現金<0 警告。`TIME_SCALE=20`（一天≈72秒）。**湧現張力：員工過多→薪資>營收→虧損**。
- **P1 關係網 + 茶水間八卦（2026-10-29）**：`W.rel`(pairKey→分數) + 個性相容性 `compat()`(BOND/CLASH 清單)；`relOf/relAdd/relLabel`。互動(startChat)依相容性拉近/推遠、影響心情;好友被解職→他人心情掉。`W.events`(招募/解職/公告/完成任務)。後端 `converse` 吃 `rel`+`events`→生成**帶公司八卦、語氣隨關係**的對話(好友同情/死對頭互嗆,實測佳)。茶水間/會議室八卦機率提高。員工資訊面板顯示好友/不對盤。
- **P1 員工主動事件（2026-10-29）**：員工依狀態主動向老闆開口——`REQ_CFG` 五型：加薪(技能高心情好)/請假(精力低)/抱怨(心情差)/提案(採納可賺錢)/離職(心情太差)。後端 `/character/request` 生成第一人稱台詞(≤34字、去英文/亂碼、繁中)。client `genRequest/showNextRequest/resolveRequest` + 右上📨徽章 + 卡片(採納/否決)；每 15–37 秒依 `mood/energy/skill/day` 挑人挑型觸發。各型 resolve 後果：加薪=薪資×1.18/心情±15、提案=現金+1500+技能獎金、離職=慰留或離開。實測卡片顯示+按鈕+LLM 文字+自主觸發皆正常。
- **P2 存檔 + 離線進度（2026-10-29）**：`localStorage` 鍵 `yocat_save_v2`，`serialize/saveGame/loadGame`；自動存檔(約每 4 秒 + `beforeunload`)。開局優先讀檔還原（chars 含 id/變體、rel、events），否則新陣容。離線進度 `runOffline()`：依現實離線時間換算遊戲天（封頂 5 天），逐日結算營收/薪資/訂單/MVP + 技能微成長 + 隔夜回復，彈出「🌙 歡迎回來，老闆」報告（含關係亮點）。`?fresh` 清檔、debug 模式不存檔。實測 day 1→6、現金/士氣離線結算、報告文案正確。
- 設計提案見 `企画.md §14`（定位/三支柱/核心循環/為何需要本地 LLM）。

## 去背管線（重要結論）

## 啟動指令（接手者）
- 後端＋遊戲（同一個行程）：`cd <project-root>\server && npm start`（= `node src/index.ts`，:3000，零依賴）。這個後端**同時**服務 LLM API 與遊戲靜態檔。
- 遊戲：瀏覽器開 **`http://localhost:3000`**（會自動導到 `/client/index.html`，`BACKEND_URL` 同源）。也可直接開檔 `client/index.html`（此時 `BACKEND_URL` 自動指向 `http://localhost:3000`）。連上會顯示「LLM：已連線 ✅」。
  - **共存實測（2026-10-29）**：vLLM + SDXL 同時跑，出一張 1024² 約 53s，峰值 VRAM 21.3GB（24GB 內未爆），vLLM 全程存活。

## 更新日誌
- 2026-10-29：**Phase 1 client 原型完成**（`client/index.html`，離線可玩，預留後端鉤子）。
- 2026-10-29：**Phase 1 完成** — Node TS 後端（`server/`，零依賴 http+fetch）上線，三端點實測路由本地 7B，client `USE_BACKEND=true` 串接；角色心聲與對話現由本地 LLM 驅動。
- 2026-10-29：**對話全面 LLM 化** — 移除所有內建短句；新增後端 `/character/converse`（一次產生兩人對話，不給名字避免冠名、降溫 0.7、JSON 防呆、簡→繁安全網 638 對字 + 亂碼/符號/壞 JSON 清洗）；修正招募重名（`uniqueName`）。心聲與討論內容現全由本地 7B 生成。
- 2026-10-29：**泡泡分流** — 區分「心聲泡泡」(獨處，淡紫圓角+小圓圈尾，接 LLM) 與「說話泡泡」(與同事討論，白色尖角)；新增「附近有同事才開口討論」行為；派任務/公告→員工心聲反應，玩家直接聊天→對你說話。
- 2026-10-29：**client 畫面改版（依老闆回饋 8 點）** — 視野放大+自動置中縮放相機；部門改房間化（矮牆+走道+牆上全名標牌）；角色改成坐在辦公桌前工作（有椅子/電腦，偶爾才去開會/茶水間）；2.5D 角色帶明暗立體；對話泡泡改偏正方圓角+自動換行；部門正名為 **產品部/銷售部/人事部/研發部/財務部 + 總經理辦公室**（管→產、老闆→總經理）。後端加強台灣繁體提示 + 簡體→繁體安全網（toTrad），修正對老闆口吻。
- 2026-10-29：**P1 員工主動事件完成** — 後端 `/character/request`（五型、第一人稱、去英文/繁中）；client 請求卡片系統（徽章+佇列+採納/否決+各型後果）；依員工狀態每 15–37 秒自主觸發。截圖+puppeteer 實測通過。
- 2026-10-29：**遊戲性設計定稿**（`企画.md §14`）— 差異化＝湧現敘事 + 人際關係 + 記憶，三者皆靠本地 LLM；核心循環 P0 經濟 → P1 關係/事件 → P2 存檔/離線。
- 2026-10-29：**個人記憶完成（記憶支柱落地）** — 每員工 `c.memory` 累積帶日期的個人經歷（`pushMem()` 埋在加薪/請假/抱怨/提案/慰留/交友/鬧翻/完成任務/MVP/好友離職/被解職/離線 MVP 等事件點）；存進存檔、資訊面板顯示「個人經歷」、`recentMem()` 餵進後端 `chat`(`ChatReq.memory`)／`converse`(`Peer.mem`) 的 prompt，員工會提起自己的過去。puppeteer 實測：經歷累積/面板顯示/存檔還原皆通過。
- 2026-10-29：**toTrad 補字** — 簡體安全網新增 别決约麗絲側慮虛掛薦紹鄉喬烏倉偉傘偵僑儉債傾償儲剛鳳劑廳疊惱盧養獸 等一對一對應（修 LLM 偶漏「特别/解决」等簡體字）。
- 2026-10-30：**辦公室場景大改版（多輪調校）**。道具改用正統 2:1 等角相機(3,-3,2.449)對齊格子；新增冰箱/茶水檯(洗手台+微波)/視訊電視/大辦公桌/皮沙發，移除舊白板/原沙發；盆栽重做(盆+盆緣+樹幹+層次樹冠)。桌面：螢幕加寬移到桌前(背面朝我們)、鍵盤移到員工側；螢幕朝 +Y(員工面向螢幕、我們看背面)。**座位構圖**：角色坐在桌後、桌子貼身蓋住下半身、坐著不畫影子、`animOf` 在座位一律回 'sit'(講話也坐著不站起飄浮)、坐姿一律正面(`face=clip==='sit'?1:faceDir`，修走回來坐成側向)。部門位置多次對調(財/產、人/銷、人/發)。總經理室：大桌+沙發(沙發平行桌、露背面、只有總經理)。會議室：電視左端+長桌。道具比例以 `drawProp {h:目標像素}` 控制(冰箱比人高)。名字移到頭頂、泡泡疊其上不重疊。**員工偶而去跟總經理約談/交報告**(財務報表/招募審核等，走到桌前背對我們面向總經理，LLM 對白，`startBossMeeting`/`bossMeetingTalk`/state goBoss→boss)。
- 2026-10-30：**辦公室場景 Blender 化**。家具道具改用同一套卡通渲染(ShaderToRGB 階梯+Freestyle 描邊)+同角度等角相機：`scratchpad/blender_props.py` 建桌(螢幕+鍵盤)/辦公轉椅(五爪)/會議長桌/飲水機/盆栽/沙發/白板 7 道具 → `crop_props.py`(水平置中對齊原點、垂直貼緊)→ `assets/props/*.png`。**對齊修正**：道具相機原用人物角度(2.8,-3.8,2.0 方位53°/壓縮0.39)→家具方底跟 2:1 格子不合顯歪，改**正統 2:1 等角相機 (3.0,-3.0,2.449 方位45°/仰角30°/壓縮0.5)**，桌面變乾淨平行四邊形對齊格線。client `PROP_IMG`+`drawProp(name,ix,iy,{scale,dy,dx})`,`drawChair/drawDesk/plant` 改畫精靈,會議室=長桌+白板、茶水間=飲水機+沙發+盆栽,取代原本幾何 `cube()`。實測無 404/錯誤,場景與人物風格一致。
- 2026-10-30：**避牆尋路 + 手機清晰度**。(1) **走格子避隔間**：房間上緣/左緣牆＝`navTop`/`navLeft` 邊阻擋，`findPath` BFS(4 向、`canStep` 檢查共用邊)算避牆路徑，`stepChar` 依 `c.path` 走 waypoint、目標改變才重算。實測 `test_path.js` 30 條路徑 0 條穿牆。(2) **手機文字不糊**：canvas 後端解析度 ×`DPR`(上限3)、相機數學維持 CSS 像素、render transform ×DPR；`charAt` 命中不受影響(實測仍對)。(3) 手機縮小邊距、scale cap 1.7→1.9 讓辦公室大一點。
- 2026-10-30：**改名 yoCat Office**（client title/h1、server 註解/log、package.json；`SAVE_KEY` 與資料夾路徑保留以免弄丟存檔）。
- 2026-10-30：**選單化管理 + 招募上下限 + 解職代價 + 幽靈人修復 + 部門位置對調**。(1) 所有管理輸入改「用選的」：通用 `openPicker` 彈窗；招募選部門、公告隨機拉 10 則(`ANNOUNCE_POOL`)、派任務先選人再選任務(`TASK_POOL`)、解職名單下拉(`fireChar`)、對談點一句(`CHAT_POOL` 4 選項+可打字)。(2) 招募上限 `MAX_WORKERS=8`、下限 `MIN_WORKERS=1`(超過/不足用 toast 擋)。(3) 解職代價：`overloadFactor()`(人<5 漸增) → 產出打折、更累心情更差、任務完成變慢(交付/上市 delay)、警示。(4) **幽靈人修復**：`preloadAnim` 開場同發 960 張圖≈96MB，手機/遠端載不完卡成幽靈 → 改 `framesOf()` lazy load + `prefetchChar`，只載在場角色用到的 dept/變體/動作(~100 張)。(5) 部門房間位置 4-cycle 對調(財↔產位、人↔銷位)。puppeteer `test_all.js` 全通過(無幽靈、無失敗資產、上下限/過載/選單皆驗證)。
- 2026-10-29：**AI 代管 + 登場動畫 + 手機自適應**。(1) **🤖 AI 代管**：預設開啟(存檔記 `W.autopilot`，舊檔預設開)，`autopilotTick` 自動回應員工請求卡片(`autoDecideRequest` 策略)、缺人招募/嚴重虧損精簡/派任務給閒置者(`autoManage`)。(2) **材質化登場**：`c.spawnT`+`SPAWN_MS=1500`，drawChar 以空氣人(`ghostFigure` 漸層人形)→實心淡入+地面光環+上浮；初始 6 人同 spawnT 一起登場，貼圖未載入時也畫脈動空氣人。還原的角色 spawnT=0 不重播。(3) **手機自適應**：`@media(max-width:760px)` 側欄變右側抽屜(預設收起,☰ 開合,背景點擊關閉)、HUD 換行縮小；點操作鈕收選單、點角色開選單看資訊。**修**：後端首頁/靜態路由改用 `pathname`(去 query)，否則 `?fresh`/`?roam` 會 404。puppeteer 實測 `test_mobile_ap.js`/`cap_spawn.js` 全通過。
- 2026-10-29：**遊戲改用 HTTP 服務** — Node 後端(:3000)新增靜態檔服務：`/`→302→`/client/index.html`，`/client/*` 與 `/assets/*` 從磁碟送(含 MIME、目錄白名單 client/+assets/、解析後路徑必須落在白名單內擋編碼過的 `../` 穿越)。client `BACKEND_URL` 改為 `location.protocol==='file:'?'http://localhost:3000':''`(HTTP 同源/file 指本機)。開 `http://localhost:3000` 即可玩，與 LLM API 同源免 CORS。puppeteer 實測 `test_http.js`：302 導向、6 角色、精靈載入、同源 health、零失敗請求。
- 2026-10-29：**無縫接續存檔** — 之前 reload 會把時鐘重設 09:00、丟任務/公告，感覺重來。改：存檔多存 `task/taskT/pos/announce/todayRev/todayOrders/reported`；loadGame 分流——離開 <5分現實(`RESUME_SEC`)＝原地接上（保留 day/minutes/全狀態、不跳離線報告），離開夠久才推進世界時鐘(接續非跳 9:00)+跑離線結算。實測 `test_resume.js`：快速 reload 完整保留第2天15:00/任務/公告/情侶/位置；久離才離線+時鐘接續。
- 2026-10-29：**性別外型 + 辦公室戀情完成** — Blender `blender_anim2.py` 加 `gender`：女性長髮+洋裝+睫毛（v1/v3 重渲 480 格），男性短髮+長褲（v0/v2 沿用）；960 格全域 union bbox 重裁（347×576 對齊）。client：性別↔名字↔變體三者一致（男名/女名池、奇偶變體）、初始 3男3女均衡、頭上 💞、資訊面板顯示交往/暗戀。**戀情系統** `romanceTick`：單身異性累積 `W.crush`→告白→交往/被拒→放閃/分手，離職自動解除，全程後端 `/character/romance`(confess/accept/reject/sweet/breakup) LLM 對白，寫進事件+個人記憶。puppeteer 實測 `test_romance.js` 告白→交往→面板/記憶/事件全通過，存檔/記憶/請求回歸測試無退化。

## 商業化（2026-10-30 起）
- **主題商店 MVP（外觀變現地基）**：`THEMES`(7 主題:預設/暖木/純白/森林/賽博/和風/聖誕,各有 floor 雙色+bg 漸層+price)，側欄「🛍️ 主題商店」→`openShop` picker 用現金買/套用，`applyTheme` 換地板色+背景，`W.theme`/`W.ownedThemes` 存檔。沿用現有道具系統，之後接家具皮膚/服裝。實測 `test_shop.js` 購買/換色/存檔還原通過。
- **C 瀏覽器 WebLLM（三算力路線並行）**：`assets/webllm.js` 自足模組(toTrad+清洗+5 個 prompt 建構 think/chat/converse/request/romance，對齊後端)，用 `@mlc-ai/web-llm`(esm.run) 跑 Qwen2.5-1.5B on WebGPU。client `GEN.mode`(auto/backend/browser)+`checkBackend`+`useBrowserGen`，5 個生成函式改路由：瀏覽器模式呼叫 `WLLM.*`、否則 fetch 後端。`?browser` 強制。**雷**：webllm.js 要放 `assets/`(用 `../assets/webllm.js` 引入)才在 HTTP 根路徑服務得到(client/ 下會 404)。零伺服器+免玩家自架，為大規模發行鋪路。

## 成長系統（2026-10-30，企畫判斷：補玩家目標感）
- **公司估值 `valuation()`**：單一成長指標（現金+累積營收×0.4+人才[技能×120+薪×1.5]+士氣+好友/死對頭+情侶），HUD 顯示「📈 估值」。
- **里程碑 `MILESTONES`**（8 個，皆往前成長才達成）：首次獲利/第一對情侶/現金40k/撐7天/估值150k/滿編8人/撐20天/估值300k；達成給**現金獎勵+士氣+🏆慶祝橫幅**(`celebrate`)。
- **成就 `ACHIEVEMENTS`**（6 個，獎勵湧現玩法）：紅娘/鐵血老闆(解職5)/散財老闆(加薪10)/點子工廠(提案5)/穩健經營(連獲利3天)/八點檔(分手)。計數器 `W.stat`(fired/raises/proposals/breakups/profitStreak/everProfit/bestVal)掛在對應事件。
- 側欄「🎯 目標與成就」按鈕→`openGoals` picker(里程碑✅/🎯/🔒 + 成就)。`checkProgress()` 每~2 秒掃。`W.milestones/achv/stat` 存檔。實測 `test_goals.js` 達成/獎勵/成就/慶祝/面板/還原通過。

## 角色深度 + 內容引擎 + 新手引導（2026-10-30）
- **口頭禪 + 說話風格**：`CATCHPHRASES`(22,約40%無)+`SPEAK_STYLES`(台味/文青/直球/囉嗦/可愛/冷淡/熱情/正經/無)，addChar 指派 `c.catchphrase`/`c.speakStyle`，`styleHint(c)` 組字串經 `style` 欄餵進全部生成(think/chat/converse/request/romance)→後端 + webllm.js prompt 都注入。資訊面板顯示「說話：風格・口頭禪」。存檔。casual 對話明顯吃到風格(converse 實測)。
- **📰 公司大事記/分享圖**：`W.highlights` 收集精華(交往/分手/交友/鬧翻/離職/里程碑)，`pushHighlight` 埋在各事件。側欄「📰公司大事記」→`openDigest` 模態列清單，`shareDigest` 用 canvas 畫成 PNG 下載分享(病毒+留存引擎)。存檔。
- **🎲 隨機事件/危機**：`EVENTS`(訂單暴增/系統當機/客訴/媒體/慶功/投資人注資(估值門檻)/競對挖角)加權隨機，loop 每 50~95s `triggerEvent`。surge/crash 改 loop 經濟 eff(×1.9/×0.4)。挖角 `eventPoach` 代管自動決定、手動開 picker 留人/放人。
- **🧭 新手引導**：`#welcome` 歡迎卡(4 點:AI員工/四權力/代管/目標)，首次遊玩顯示(localStorage `yocat_onboarded` 一次)，開始經營關閉。

## 📜 劇本挑戰模式（2026-10-30・內容變現驗證）
- **`SCENARIOS`**(5)：`sandbox` 自由經營(無時限)、`ipo90` 90天衝上市(估值≥$180k,winEarly)、`turnaround` 絕地求生(現金$4k+士氣低,30天轉虧為盈,到期評判)、`franchise` 💎連鎖帝國(120天·滿編8人+估值≥$400k雙條件,winEarly,開局$18k)、`startup2` 💎兩人創業(60天·只留總+產2人+現金$8k·估值≥$120k,winEarly)。
- **付費劇本**：`premium:true` 兩款現為**可玩的真實關卡**(各有 setup/check/hud)。依「先不鎖、兩路都留」：保留 💎 標籤與「付費劇本・免費體驗中」文案(開局 toast)，日後上架只要加回 gate。startup2 的 setup 會把 6 人裁成 2 人。
- **開場旁白 + 結算成績單**(`#scen-modal`)：每個劇本有 `intro`(情境引言)+`goal`。開局 `showScenIntro` 顯示旁白+目標+期限+💎標籤，按「開始經營 →」關閉。結束 `showScenResult` 顯示 S/A/B/C/X 評級(`scenGrade`：達成天數/期限 ratio≤0.5→S、≤0.7→A、≤0.9→B、其餘 C、敗→X)+評語+5 項數據(天數/估值/人數/現金/獎勵)，按「接下來 →」進 `offerReplay`。BGM 改 lo-fi 和弦進行(Cmaj7→Am7→Fmaj7→G7+貝斯+低通+輕旋律)，預設關。
- **實測**(test_narr.js)：旁白顯示/標題/narr、S/A/B/C/X 評級、成績卡 5 數據、接下來→replay picker — 全通過零錯誤。
- **成績分享圖**(`shareScenResult`)：成績單加「📷 存成成績分享圖」鈕(僅結算顯示，開場隱藏)，canvas 620×560 畫品牌抬頭+劇本名+成功/結束+評級彩色圓環(顏色隨 S/A/B/C/X)+評語+4 數據+slogan，`toDataURL`→PNG 下載。檔名含評級與天數(炫耀式病毒傳播)。實測(test_scenshare.js/grab_scenshare.js)：開場隱藏/結算顯示/canvas 生成/點擊不丟錯/實圖產出 — 全通過零錯誤。
- **最佳成績牆**：`W.bestScores{id:{g,day,val}}`(存檔)，`recordBest`(GRADE_RANK S>A>B>C，只存更高評級)於 endScenario win 寫入；`sc._newBest` 旗標讓成績卡顯示「⭐ 刷新最佳成績！」。`openScenarios` 頂部加「🏅 戰績牆：通關 X/4」header、各劇本 label 掛「🏅評級」獎牌、sub 顯示「最佳 N 級（第 X 天）」→給重玩長期目標(刷更高評級)。實測(test_bestwall.js)：B→C不覆蓋→S刷新、新紀錄旗標、header 計數、獎牌數、ipo 列顯示 — 全通過零錯誤。
- **跨劇本成就 + 戰績牆整合進目標面板**：ACHIEVEMENTS 加 3 個(`firstclear` 初次通關/`sgrade` S 級達人/`allclear` 劇本全通關，`countTimedCleared()` 計數)，`checkProgress` 自動解鎖(共 9 成就)。`openGoals`(主選單「🎯目標與成就」)末段加「🏅劇本戰績牆 通關 X/4」+ 各劇本最高評級列(第X天/估值)——不另設冗餘按鈕，戰績與既有成就系統統一入口。實測(test_achv.js)：三成就依序達成、checkProgress 自動解鎖、面板含戰績牆 4/4、各關評級列 — 全通過零錯誤。
- **劇本難度（簡單/普通/困難）**：`DIFFS`(easy dl×1.3 cash×1.3 / normal ×1 / hard dl×0.7 cash×0.8，rank 0/1/2)。`startScenario(id,diffKey)` 依難度縮放期限(存 `W.scenario.deadline`)與起始資金；`scenGrade`/`checkScenario`/HUD 改用 `sc.deadline`。選劇本→`pickDifficulty` 三難度(即確認)→開局。`offerReplay` 加「🔁同難度再玩／🎮換難度」。最佳成績改**綜合排名** `composite=評級×10+難度rank`，困難 S(42) > 普通 S(41)，戰績牆/成績卡/highlight 都顯示難度名。實測(test_diff.js)：期限/資金縮放、評級用縮放期限、困難 S 覆蓋普通 S 且反向不覆蓋、到期用縮放期限判定 — 全通過零錯誤。
- **🪑 座位 bug 修復**：讀檔時 `c.seat` 用目前版面重算、但 `c.pos` 還原成舊存檔座標 → 部門位置換過的舊檔會坐錯位。改為讀檔一律 `c.pos=c.target={...seat}; state='work'` 貼齊目前座位。實測(test_seatfix.js)：7 人全歸位、零錯誤。
- **👔 人才庫原型（獲利 §16.10 第 2 步，先用遊戲現金）**：`W.alumni`(校友庫)+`W.hiredDreams`，**跨 recycle 保留**(resetWorld 不清)。解職/被挖角→`toAlumnus` 進校友庫(保留記憶/人格/外觀)。招募改成**招募中心**：🧑‍💼一般招募(免費隨機)／🎓校友招回(`rehireCost=skill*40+salary*3`，回鍋 mood+15)／🌟夢想人才市場(`DREAM_TALENTS` 3 隻手工精品靈魂：沈墨/艾莉/羅文，各有 title/bio/高技能/價格)。`c.dream` key 標記;市場排除在職與校友中的夢想人才;夢想人才離職進校友庫可再招回。`serialize`/`addChar` 補 `dream`/`title` 欄位。實測(test_talent.js)：解職→校友、再招回扣現金/回鍋/離庫、夢想特聘帶 title、市場排除、夢想離職進校友、現金不足擋下、跨 recycle 保留 — 全通過零錯誤。
- **💖 最愛角色依附迴圈（獲利 §16.10 第 1 步・情感引擎）**：每個員工有 `c.bond`(0-100 與老闆羈絆)、`c.fav`(最愛，單選)、`c.arc`({id,stage} 個人故事線)。`ARCS`(5 條：獨當一面/走出內向/家庭與工作/超越宿敵/創業夢，各 3 beats+thinks+達成 payoff)。`bondAdd` 掛鉤：聊天+2、答應請求+4/拒絕-3、慰留(quit/poach)+8、派任務+1；`newDay` 疏於互動 -1。`checkArc`：羈絆跨 `ARC_THRESH[20,45,70,90]` 推進階段→故事 beat(log/心聲泡泡/最愛者 celebrate+highlight)，達成→夢想 payoff(技能/心情獎勵+🌈慶祝+彩虹 highlight)。inspect 面板：💖羈絆條(bondbar/bondLabel)+🎯夢想進度(x/4)+⭐最愛鈕(setFavorite 單選)；`updateBondUI` 聊天即時更新。最愛者頭上顯示 ⭐(金色名)。**修**：quit/no 自主離職改走 toAlumnus(進校友庫可再招回)。存讀帶 bond/fav/arc。實測(test_bond.js)：欄位初始、最愛單選、故事 1→2→3→達成、達成給獎勵+夢想 highlight、答應加/拒絕扣羈絆、自主離職進校友庫、serialize 保留 fav/bond/arc、面板有羈絆條+最愛鈕 — 全通過零錯誤。
- **💎 資本貨幣 + 儲值 + 羈絆變現 + 羈絆雙向化（2026-10-30）**：
  - **💎 資本(premium 貨幣)**：`W.gems`(持久、跨 recycle 不清)。遊戲內賺(里程碑+2/成就+3/劇本通關+5 `gainGems`)或儲值取得。`openRecharge`(選單「💎資本/儲值」)：**付費先做假的**——「💎儲值1000（測試）」與三檔 GEM_PACKS 點擊**直接入帳**(未接真實金流，標註測試)。
  - **虧損抉擇視窗 + 倒閉記錄**(依使用者要求)：`checkBankruptcy` 現金<`-15000` **一律跳窗**(含代管)。`showBankruptcy` 兩選：「💎繼續投資救公司(花20資本→現金保證≥8000跑道)」/「🔚讓公司倒閉」。倒閉→`recordBankruptcy` 寫 `localStorage yocat_bankruptlog`(持久 Top20)，`openGoals` 顯示「💀倒閉記錄」。
  - **羈絆接變現**：角色專屬外觀 `ACCESSORIES`(👑/😇/🌟/💐/🐱，花資本，`openCosmetics`，頭上顯示 icon)；羈絆≥70 解鎖 `deepTalk` 專屬深度對談(揭露夢想、+心情/羈絆)。
  - **羈絆雙向化**：`prod` ×(0.92+0.16·bond/100)(高羈絆更投入)；`eventPoach` 低羈絆先被盯上、羈絆≥75 員工自己婉拒挖角(忠誠)；低心情時羈絆≥55 只抱怨不離職。
  - 實測(test_monet.js)：儲值1000入帳、注資救公司(扣20資本/保跑道)、資本不足擋下、倒閉留記錄+現金重置、高羈絆高產能、外觀購買、深聊<70擋/≥70開、忠誠婉拒挖角、高羈絆不離職、gems 存檔 — 全通過零錯誤。
  - **劇本模式也啟用破產抉擇 + 資金告急條(2026-10-30)**：`checkBankruptcy` 移除劇本跳過守衛，所有模式現金<-15000 都跳抉擇窗；`showBankruptcy` 判 `inScenario`：繼續投資→注資續跑挑戰；倒閉→`recordBankruptcy`+`endScenario('lost')`(進結算卡/offerReplay)。checkScenario 移除原 -12000 直接判敗。新增 `#danger` 警示條(現金<0 時底部紅色脈動顯示「資金告急 $X / 破產線 $-15000」，updateHUD 控制)。實測(test_scenbankrupt.js)：劇本破產跳窗不自動判敗、繼續投資續跑、倒閉記錄+判敗+結算卡、警示條顯示/隱藏 — 全通過零錯誤。
- **🕸️ 員工社交連動（2026-10-30・讓戀愛/八卦/羈絆/關係互相咬合）**：`closeFriends`(rel≥25)、`socialRipple`(情緒波及好友圈)、`favInvolved`(最愛放大)。情侶綜效 `prod ×1.05`+每 tick 心情微升。交往：公司士氣+3、好友+3、最愛再+3+celebrate。分手：士氣-3、好友-4、最愛再-3+celebrate。開除戀人：另一半 mood-12/bond-8+記恨記憶，最愛額外士氣-3+celebrate。實測(test_social.js)：好友網、情侶綜效 prod、分手降士氣+波及好友+最愛慶祝、開除戀人心碎記恨 — 全通過零錯誤。
- **🪑 飄浮人口修復（2026-10-30）**：根因＝`takeSeat` 用單調遞增 `z.seatIdx`，解職不釋放→招募/解職幾輪後 index 暴增→新人被塞到無桌的溢位位置飄浮。改為**依目前實際占用找空位**(解職自動釋出)；渲染改成桌椅畫在「固定座位 ∪ 每位員工實際座位」，連真正溢位(>4)也補桌。實測(test_seat2.js)：4人全在固定座位、churn 後重用不溢位、無重複座位、第5人溢位不崩潰、render 正常 — 全通過零錯誤。
- **🏢 favicon 404 修復**：`<head>` 加 inline SVG emoji favicon(🏢)，消除 `/favicon.ico` 404。
- **🎭 辦公室八點檔分享海報（2026-10-30・病毒傳播）**：大事記模態加「🎭八點檔海報」鈕。`DRAMA_ICONS`(💞💔👋💪🌈⚡🫶🎣🔴😠⭐📖)過濾 highlights→`shareDrama` 畫粉紫聳動海報(只挑人際/感情 drama，排除里程碑等)，canvas→PNG。空則 toast 不產圖。實測(test_drama.js)：drama 過濾(6/7 排除里程碑)、海報生成、空不產圖、鈕存在 — 全通過零錯誤。
- **💥 破產 Recycle（獲利 §16.6/16.7）**：`BANKRUPT_CASH=-15000`。`checkBankruptcy`(在 checkProgress，僅沙盒模式；劇本有自己的破產判定)：現金負但未破產→警告 toast；低於門檻→手動跳 `showBankruptcy` modal(復用 #scen-modal，含「💎緊急注資〔付費・即將開放〕」次要鈕＝真金流接點 + 「🔄重新開始」)、autopilot 則自動 `recycleCompany`。`recycleCompany`：lbSubmit→員工進校友庫→保存 highlights/achv→resetWorld(現金回 20000、新 runId、alumni/dreams/排行榜保留)→還原 highlights/achv→重發總/產/銷 3 人精簡班底。**落實「公司會倒但心愛的人與傳承不奪走、帶老班底東山再起」**。實測(test_bankrupt.js)：溶劑時不跳、破產跳 modal、重整後現金歸零+老班底全進校友庫可招回+大事記保留+flag reset、autopilot 自動重整 — 全通過零錯誤。
- **📊 公司排行榜（跨局 Top）**：`localStorage yocat_leaderboard`(獨立於存檔)。每局有 `W.runId`(存檔；新局/reset 換新)，`lbSubmit()` 以 runId upsert——**本局只佔一名次並即時更新較高估值**，top 10 保留。checkProgress 每 ~2s 提交、startScenario 先提交離場局再 reset。`modeLabel()` 顯示 🏢自由經營 或 劇本+難度。整合進 `openGoals` 頂部：目前估值→「🏆公司排行榜 Top 5」🥇🥈🥉+估值+模式+天數，本局標「◀ 本局」。實測(test_lb.js)：同局一名次並更新、跨局排序、離場登榜+換 runId、面板顯示 — 全通過零錯誤。
- **流程**：`startScenario(id)`→`resetWorld()`(清 chars/rel/crush/events/highlights/milestones/achv/stat/reqQueue，保留 theme/ownedThemes/autopilot)+重新發 6 人+`def.setup()`→`W.scenario={id,deadline,status:'running'}`。`checkScenario()`(在 checkProgress 內)：winEarly 達標即勝、到期評判勝負、現金<-$12k 破產出局。`endScenario`→`celebrate`+獎勵$5k+`offerReplay`(再玩/自由/換劇本)。
- **HUD**：`#scen-hud` 頂部膠囊顯示「劇本名・Day X/上限・即時進度・剩 N 天」。按鈕「📜劇本挑戰」→`openScenarios`(選單→二次確認重開局)。存檔(`serialize`/`loadGame` 帶 `W.scenario`)。
- **實測**(test_scenario.js)：5 劇本/2 付費、開局重置6人、HUD 顯示、winEarly 勝、turnaround 低現金 setup、到期勝/敗、破產出局、付費擋下、回自由 HUD 隱藏、存檔帶 scenario — 10/10 通過零錯誤。

## 🔊 音效 / BGM（2026-10-30・Web Audio 程序化合成）
- **`SOUND`**(IIFE)：lazy `AudioContext`(首次互動解鎖，遵守 autoplay 政策)；5 cue(`good` 升階琶音/`levelup` 四音+尾音/`cash` 收銀雙擊/`bad` 下滑鋸齒/`coin`)；生成式 BGM(五聲音階隨機柔和 sine 音墊+偶發五度，`setInterval` 1.5s 排程，`bgmGain` 淡入淡出)。偏好 `localStorage yocat_sound`(預設 sfx:on, bgm:off)。
- **接線**：事件 surge/investor→cash、media/party→good、crash/complaint/poach→bad；里程碑/成就→good；劇本勝→levelup、敗→bad；招募→coin。按鈕「🔊音效與音樂」→`openSound` 面板切換 SFX/BGM。
- **實測**(test_sound.js)：引擎存在、預設偏好、5 cue 不丟錯、BGM 開關、SFX 關閉、localStorage 持久化、面板兩選項、按鈕存在 — 全通過零錯誤。

## 🔐 會員系統地基完成（2026-10-30）
玩家提出「要收費卻沒會員系統，人人都看得到很奇怪，應該每人有自己的辦公室、失敗記錄記在帳號上」。
確立架構：**算力留本地、信任上雲端**（Rancher Desktop k8s 當本地雲端）。
- 🏗️ 基礎設施：k8s namespace `yocat` + PostgreSQL 16（PVC 持久化 + NodePort 30432）。manifests 在 docs/req/postgres.yaml 與 java-backend/k8s/overlays/local/。
- 🗄️ Schema：schema-per-BC（7 BC：member/wallet/deposit/entitlement/record/leaderboard/game_save）＋ platform/modulith 橫切，Flyway 遷移。
- 🔌 後端 API：帳號(scrypt+session)、**伺服器權威錢包**(topup/spend 走交易、餘額不足 402)、權益(原子扣款+授予)、存檔、**破產記錄**、排行榜。拆成 server/src/db.ts + accounts.ts，不汙染原 LLM 邏輯。
- ✅ 端到端測試全綠（Java E2E/BDD 套件）：註冊/登入/me/儲值/花費/402/購買/409/存檔往返/破產記錄/排行/登出失效。
- ⏳ 下一步：客戶端整合（帳號畫面＋把錢包/記錄/存檔/排行從 localStorage 改走伺服器）。

## 🔐 會員系統客戶端整合完成（2026-10-30）
把遊戲接上後端，玩家請求「每人有自己的辦公室、失敗記在帳號上、標準會員流程」全部實現。
- 🖥️ 啟動帳號畫面：登入 / 建立辦公室 / 訪客試玩（guest-first，之後可註冊保留進度）。側欄加「👤 帳號」按鈕（顯示登入狀態、切換/登出）。
- 🧩 client/index.html：新增 ACCT client 層（register/login/logout/me/topup/spend/pullSave/pushSave 防抖/addRecord/pushLeaderboard）。
- 💾 存檔以 account_id 命名空間（saveKey()），loadGame 拆出 applySave()，登入即載入雲端辦公室、登出回訪客本機存檔。afterAuthLoad：有雲端存檔→載入；沒有→目前辦公室收歸此帳號。切換帳號 newCompany() 開新公司。
- 💎 錢包上雲：gainGems/openRecharge/角色外觀/破產注資 全部改走伺服器（登入時），W.gems 鏡射伺服器權威餘額。
- 💀 破產記錄 recordBankruptcy→ACCT.addRecord('bankruptcy')；排行 lbSubmit→ACCT.pushLeaderboard()。
- ✅ 端到端測試 test_member.mjs 全綠（零 pageerror、零 404）：帳號畫面顯示、註冊、儲值1000/花費900/超額402、登出重登入載回 day15+錢包900、破產記錄歸戶、切 account2 獨立 day1、回 account1 仍 day15。兩間辦公室完全獨立。

→ 玩家原始需求全部達成：會員管理系統 ✅、每人自己的辦公室 ✅、失敗記錄記在帳號上 ✅、標準會員流程 ✅。真金流 SDK 為 Phase B（接口已預留 /wallet/topup）。

## 📐 後端架構工程文件（gendoc 流水線，2026-10-30）
依 gendoc 設計原則（HA≥2/SPOF/BCP、Spring Modulith 7 BC HC-1~HC-5、Clean Architecture 4層+SOLID、SSOT、SECS）為 yoCat Office 後端信任層產出完整工程文件集。
- 技術棧：Java 21 + Spring Boot 3.3 + Spring Modulith 1.3 + PostgreSQL 16 + Kubernetes（client_type=api-only）。
- 23 個流水線步驟全通過（每份 gen + review loop，審查零 CRITICAL/HIGH 殘留）。
- 產物：docs/ 17 份 .md（17,805 行：IDEA/BRD/PRD/CONSTANTS/EDD/ARCH/DRYRUN/API/SCHEMA/test-plan/RTM/runbook/LOCAL_DEPLOY/CICD/DEVELOPER_GUIDE/ALIGN_REPORT/CONTRACTS）、31 張 Mermaid UML、9 個 BDD .feature（65 scenario）、34 個 JSON Schema + OpenAPI 3.1（18 ops）+ 3 Pact、70 個 Java seed 骨架（7 BC Spring Modulith）、Helm/NetworkPolicy IaC、49 頁 HTML 文件網站。
- 文件網站入口：docs/pages/index.html。SSOT 基準：.gendoc-rules/（34 個）。
- 跨文件對齊（ALIGN-VERIFY）：0 CRITICAL / 0 HIGH。共 51 個 gendoc commit。
