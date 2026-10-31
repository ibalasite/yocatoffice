# Launch Report — 2026-10-31T10:29:50+0800

> **Pipeline 最終 launch 步**（Phase 12 Trace / `trace-launch-local`, artifact O-50）.
> client_engine = **none (api-only)**：無前端，故不做 Playwright UI 截圖；launch 驗收 = API endpoint smoke。
> Runner（唯一真理）：[`scripts/launch-local.sh`](../scripts/launch-local.sh)；停止：[`scripts/launch-teardown.sh`](../scripts/launch-teardown.sh)

## 部署環境
| 項目 | 值 |
|------|-----|
| 叢集 | context `rancher-desktop`（非 prod），k3s runtime `docker://29.1.3` |
| App image | `yocat-office-backend/api:local`（multi-stage docker build, imagePullPolicy: Never） |
| DB | 既有 `yocat-pg`（namespace `yocat`；跨 namespace DNS `yocat-pg.yocat.svc.cluster.local:5432/yocat`） |
| App namespace | `yocat-office-backend-local`（自有 namespace，與既有 membership `public` schema 隔離） |
| ready replicas | 2（HA ≥2） |

## Server（k8s）— Pod 狀態
| Pod | Status |
|-----|--------|
| redis-6d5dd87b8c-hf9fn | Running |
| yocat-office-backend-flyway-migrate-7bmml | Completed |
| yocat-office-backend-api-544bfd9dd6-hr5rv | Running |
| yocat-office-backend-api-544bfd9dd6-ppdns | Running |

## API 存取（api-only 入口）
| 入口 | URL / 指令 | Health |
|------|-----------|--------|
| **Service NodePort（預設，經 Service LB 跨 replica）** | `http://127.0.0.1:30081` | /health/live 200 · /health/ready 200 (status/db/redis = UP) |
| port-forward（NodePort 替代；直連 deploy） | `kubectl port-forward -n yocat-office-backend-local deploy/yocat-office-backend-api 8080:8080` → http://127.0.0.1:8080 | — |
| Ingress（Traefik, in-cluster 已驗路由正確） | `Host: yocat-office-backend.local`（host :80 若被佔用則走 NodePort） | — |

## 關鍵 API 旅程（launch 驗收，真打外部 Service）
```
register        POST http://127.0.0.1:30081/auth/register   -> token + account + wallet(gems:0)
topup(假付費)   POST http://127.0.0.1:30081/wallet/topup     {"pack":"g_big"} -> gems 700
spend           POST http://127.0.0.1:30081/wallet/spend     {"currency":"gems","amount":10} -> gems 690
overdraw        POST http://127.0.0.1:30081/wallet/spend     {"amount":1000000} -> HTTP 402 {"error":"餘額不足"}（balance 690 未變）
```
> 假付費維持假：topup 無真實金流閘道，server 直接發包（fake payment）。

## ▶ API Base URL（複製貼到瀏覽器 / Postman / curl 即可打）

# 🔌 http://127.0.0.1:30081/

示例：`curl http://127.0.0.1:30081/health/ready`

## 停止本地環境
`bash scripts/launch-teardown.sh`
（刪除 yocat-office-backend-local namespace + DROP yocat BC schemas；不觸碰既有 membership public schema）

## 重新 launch
`bash scripts/launch-local.sh`

LAUNCH_STATUS: SUCCESS
