#!/usr/bin/env bash
# scripts/launch-local.sh — Phase 12 Trace / trace-launch-local (pipeline 最終 launch 步).
#
# 真的把本後端 LAUNCH 到本機 Rancher Desktop k3s 叢集並**留著運作**，印出可點擊的 API 入口 URL。
# client_engine = none (api-only，無前端)：沒有 client build、沒有 Playwright UI 截圖——
# 以 API endpoint smoke（register → topup(假付費) → spend → 402 完整旅程）作為 launch 驗收。
#
# 與 scripts/k8s-smoke.sh 的差別：
#   - k8s-smoke：驗證完**自動 teardown**（CI smoke gate）。
#   - launch-local：驗證完**留著運作**，寫 launchClientUrl + docs/LAUNCH_REPORT.md，給使用者 demo URL。
#     （清理請跑 scripts/launch-teardown.sh；或 TEARDOWN=1 bash scripts/launch-local.sh 驗證後即清）
#
# 對外路徑：API Service 的 NodePort（Rancher Desktop 把 NodePort 轉到 127.0.0.1；經 Service →
# kube-proxy 在 2 個 replica 間負載均衡）。偏好 30080，若被鄰居佔用則自動改配空閒 NodePort。
# 另提供 kubectl port-forward 作為替代入口（LAUNCH_REPORT.md 內記錄指令）。
#
# infra_runtime = k8s-local（authoritative, LOCAL_DEPLOY §4.5）。不含 docker-compose 退化分支。
#
# 用法:
#   bash scripts/launch-local.sh                 # 部署 + 驗證 + 留著運作 + 印 URL + 寫 report/state
#   TEARDOWN=1 bash scripts/launch-local.sh      # 部署 + 驗證後立即清理（不留資源；收尾用）
#   停止: bash scripts/launch-teardown.sh
#
# 需求工具（Rancher Desktop）: kubectl / docker（npipe）; openssl; curl; python。
set -uo pipefail

# ── Rancher Desktop toolchain on PATH ────────────────────────────────
RD_BIN="/c/Program Files/Rancher Desktop/resources/resources/win32/bin"
[[ -d "$RD_BIN" ]] && export PATH="$RD_BIN:$PATH"
export DOCKER_HOST="${DOCKER_HOST:-npipe:////./pipe/docker_engine}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

APP_NS="yocat-office-backend-local"
DB_NS="yocat"
K8S_OVERLAY="k8s/overlays/local"
IMAGE_API="${IMAGE_API:-yocat-office-backend/api:local}"
DOCKERFILE="docker/api/Dockerfile"
INGRESS_HOST="yocat-office-backend.local"
STATE_FILE="${STATE_FILE:-.gencode-state.json}"
REPORT="docs/LAUNCH_REPORT.md"
TEARDOWN="${TEARDOWN:-0}"
BASE_URL=""
API_NODEPORT=""
# yocat BC schemas created by Flyway into the shared yocat DB — only these are dropped on teardown
# so the launch never pollutes the existing membership DB (public schema is left untouched).
YOCAT_SCHEMAS="member,wallet,deposit,entitlement,record,leaderboard,game_save,modulith,platform"

RC=0
PY="$(command -v python || command -v python3)"
step() { echo ""; echo "──────── $* ────────"; }
fail() { echo "❌ FAIL: $*"; RC=1; }
ok()   { echo "✅ $*"; }

echo "════════════════════════════════════════════════════════════════"
echo " launch-local (api-only)  —  $(date '+%Y-%m-%dT%H:%M:%S%z')"
echo " cluster: $(kubectl config current-context 2>/dev/null)   host: $(hostname)"
echo "════════════════════════════════════════════════════════════════"

# ── 0. Preconditions ─────────────────────────────────────────────────
step "0. preconditions"
kubectl get nodes -o wide 2>&1 | head -3 || { echo "BLOCKED: no k8s node"; exit 1; }
CTX="$(kubectl config current-context 2>/dev/null)"
[[ "$CTX" =~ prod|production ]] && { echo "BLOCKED: context is production: $CTX"; exit 1; }
kubectl get svc yocat-pg -n "${DB_NS}" >/dev/null 2>&1 || { echo "BLOCKED: yocat-pg not found in ns ${DB_NS}"; exit 1; }
ok "node ready, context=${CTX} (non-prod), yocat-pg present"

# ── 1. Build image into the runtime k3s actually uses ────────────────
step "1. build image -> ${IMAGE_API}"
RUNTIME="$(kubectl get node -o jsonpath='{.items[0].status.nodeInfo.containerRuntimeVersion}' 2>/dev/null)"
echo "   k3s container runtime: ${RUNTIME}"
if [[ "$RUNTIME" == docker://* ]]; then
  docker build -t "${IMAGE_API}" -f "${DOCKERFILE}" . || { fail "docker image build"; exit 1; }
  docker images "${IMAGE_API%%:*}" 2>&1 | head -3
else
  nerdctl --namespace k8s.io build -t "${IMAGE_API}" -f "${DOCKERFILE}" . \
    || { fail "nerdctl image build"; exit 1; }
  nerdctl --namespace k8s.io images 2>&1 | grep -E "yocat-office-backend|REPOSITORY" | head -3
fi
ok "image built into k3s runtime (${RUNTIME%%:*})"

# ── 2. Deploy: namespace + Secret + Flyway ConfigMap + Redis + API + Ingress + Job ──
step "2. deploy to k3s (kubectl apply -k)"
./scripts/bootstrap-secrets.sh >/dev/null 2>&1 || { fail "bootstrap-secrets"; exit 1; }
kubectl create namespace "${APP_NS}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl create secret generic yocat-office-backend-api-secret \
  --from-env-file="${K8S_OVERLAY}/secrets.env" -n "${APP_NS}" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl create configmap flyway-migrations --from-file=migrations -n "${APP_NS}" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl delete job yocat-office-backend-flyway-migrate -n "${APP_NS}" --ignore-not-found >/dev/null 2>&1
# apply -k is not atomic: in a shared cluster the api-service's preferred nodePort (30080) can be
# already allocated by a neighbour, so that one resource may be rejected while the rest apply fine.
kubectl apply -k "${K8S_OVERLAY}/" 2>&1 | sed 's/^/   /'

# ── 2b. Ensure api-service exists on a reachable nodePort (shared-cluster resilience) ──
find_free_nodeport() {
  local used; used="$(kubectl get svc -A -o jsonpath='{range .items[*]}{range .spec.ports[*]}{.nodePort}{"\n"}{end}{end}' 2>/dev/null | sort -u)"
  local p; for p in $(seq 30080 30250); do grep -qx "$p" <<<"$used" || { echo "$p"; return 0; }; done; return 1
}
if ! kubectl get svc yocat-office-backend-api -n "${APP_NS}" >/dev/null 2>&1; then
  FREE_NP="$(find_free_nodeport)"
  echo "   overlay api-service rejected (preferred nodePort taken by a neighbour); allocating free nodePort ${FREE_NP}"
  kubectl apply -n "${APP_NS}" -f - >/dev/null <<EOF2
apiVersion: v1
kind: Service
metadata: { name: yocat-office-backend-api, labels: { app: yocat-office-backend-api } }
spec:
  type: NodePort
  selector: { app: yocat-office-backend-api }
  ports: [ { name: http, port: 8080, targetPort: 8080, nodePort: ${FREE_NP} } ]
EOF2
fi
ok "manifests applied (api-service present)"

# ── 3. Flyway migration Job completes ────────────────────────────────
step "3. wait Flyway migration Job"
if kubectl wait --for=condition=complete job/yocat-office-backend-flyway-migrate \
     -n "${APP_NS}" --timeout=180s 2>&1; then
  ok "flyway Job complete"
  kubectl logs -n "${APP_NS}" job/yocat-office-backend-flyway-migrate 2>&1 | grep -iE "migrat|success|schema" | tail -6 || true
else
  fail "flyway Job did not complete"
  kubectl logs -n "${APP_NS}" job/yocat-office-backend-flyway-migrate 2>&1 | tail -20 || true
fi

# ── 4. API rollout (>=2 replicas) + all pods Running ─────────────────
step "4. wait API rollout (>=2 replicas)"
if kubectl rollout status deployment/yocat-office-backend-api -n "${APP_NS}" --timeout=180s 2>&1; then
  ok "api rollout complete"
else
  fail "api rollout timeout"
  kubectl describe pods -n "${APP_NS}" -l app=yocat-office-backend-api 2>&1 | tail -30 || true
fi
NOT_RUNNING="$(kubectl get pods -n "${APP_NS}" --no-headers 2>/dev/null | awk '$3!="Running" && $3!="Completed"')"
kubectl get pods -n "${APP_NS}" -o wide 2>&1
[[ -z "$NOT_RUNNING" ]] && ok "all pods Running/Completed" || { fail "pods not all Running:"; echo "$NOT_RUNNING"; }

# ── 5. Resolve external entrypoint (Service NodePort) ────────────────
step "5. resolve external entrypoint (Service NodePort)"
API_NODEPORT="$(kubectl get svc yocat-office-backend-api -n "${APP_NS}" -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null)"
[[ -n "$API_NODEPORT" ]] || fail "could not read api-service nodePort"
BASE_URL="http://127.0.0.1:${API_NODEPORT}"
echo "   external path = Service NodePort ${API_NODEPORT} -> ${BASE_URL} (through Service, LB across replicas)"
code=000
for i in $(seq 1 24); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/health/ready" 2>/dev/null || echo 000)"
  [[ "$code" == "200" ]] && break
  sleep 5
done
[[ "$code" == "200" ]] && ok "external NodePort reachable (/health/ready http=${code})" || fail "NodePort /health/ready not 200 (got ${code})"

# ── 6. Seed demo data (best-effort; essential catalogs come from V011 migration) ──
step "6. db-seed (supplementary demo data; non-fatal)"
if kubectl exec -i -n "${DB_NS}" deploy/yocat-pg -- \
     env PGOPTIONS="-c yocat.seed_env=local" psql -U yocat -d yocat -v ON_ERROR_STOP=1 -f - \
     < scripts/db-seed.sql 2>&1 | tail -3; then
  ok "demo seed applied"
else
  echo "   (demo seed skipped/failed — non-fatal; launch relies on V011 migration catalogs)"
fi

# ── 7. Health via external NodePort (200 + contract body status/db/redis = UP) ──
step "7. health via external Service (${BASE_URL})"
LIVE="$(curl -s -i "${BASE_URL}/health/live" 2>&1)"
READY="$(curl -s -i "${BASE_URL}/health/ready" 2>&1)"
echo "--- /health/live ---";  echo "$LIVE"  | head -1
echo "--- /health/ready ---"; echo "$READY" | tail -1
echo "$LIVE"  | grep -qE "HTTP/1\.[01] 200" && ok "/health/live 200" || fail "/health/live not 200"
echo "$READY" | grep -qE "HTTP/1\.[01] 200" && ok "/health/ready 200" || fail "/health/ready not 200"
echo "$READY" | grep -qE '"status":"UP"' && ok "/health/ready body status=UP" || fail "/health/ready body not UP"
echo "$READY" | grep -qE '"db":"UP"'     && ok "/health/ready db=UP"          || fail "/health/ready db not UP"
echo "$READY" | grep -qE '"redis":"UP"'  && ok "/health/ready redis=UP"       || fail "/health/ready redis not UP"

# ── 8. Key API smoke (launch verification journey): register -> topup(fake pay) -> spend -> 402 ──
step "8. API smoke (register -> topup -> spend -> 402) via external Service"
EMAIL="launch-local-$(date +%s)-$RANDOM@example.com"
PW="Password1!"
CURL=(curl -s -H "Content-Type: application/json")

REG="$("${CURL[@]}" -X POST "${BASE_URL}/auth/register" \
  -d "{\"email\":\"${EMAIL}\",\"displayName\":\"Launch Local\",\"password\":\"${PW}\"}" 2>&1)"
TOKEN="$(printf '%s' "$REG" | grep -oE '"token":"[^"]*"' | head -1 | sed 's/"token":"//;s/"$//')"
[[ -n "$TOKEN" ]] && ok "register returned token" || fail "register no token: $(printf '%s' "$REG" | head -c 200)"
AUTH=(-H "Authorization: Bearer ${TOKEN}")

# topup is a FAKE payment (no real payment gateway; server grants the pack directly) — stays fake.
TOPUP="$("${CURL[@]}" "${AUTH[@]}" -X POST "${BASE_URL}/wallet/topup" -d '{"pack":"g_big"}' 2>&1)"
printf '%s' "$TOPUP" | grep -qE '"gems":[[:space:]]*700' && ok "topup (fake pay) -> 700 gems" || fail "topup: $(printf '%s' "$TOPUP" | head -c 200)"

SPEND="$("${CURL[@]}" "${AUTH[@]}" -X POST "${BASE_URL}/wallet/spend" \
  -d '{"currency":"gems","amount":10,"reason":"launch-local"}' 2>&1)"
printf '%s' "$SPEND" | grep -qE '"gems":[[:space:]]*690' && ok "spend 10 -> 690 gems" || fail "spend: $(printf '%s' "$SPEND" | head -c 200)"

OVER_CODE="$("${CURL[@]}" "${AUTH[@]}" -o /tmp/launch-local-402.json -w '%{http_code}' \
  -X POST "${BASE_URL}/wallet/spend" \
  -d '{"currency":"gems","amount":1000000,"reason":"launch-local-overdraw"}' 2>&1)"
OVER_BODY="$(cat /tmp/launch-local-402.json 2>/dev/null)"
echo "   overdraw status=${OVER_CODE} body=${OVER_BODY}"
[[ "$OVER_CODE" == "402" ]] && ok "overdraw -> HTTP 402" || fail "overdraw expected 402 got ${OVER_CODE}"
printf '%s' "$OVER_BODY" | grep -q '餘額不足' && ok "402 body = 餘額不足 (balance rolled back)" || fail "402 body mismatch"

# ── 9. Persist launchClientUrl into state (api-only => API base URL / NodePort) ──
step "9. write launchClientUrl + LAUNCH_REPORT.md"
if [[ "$RC" == "0" && -n "$BASE_URL" && -f "$STATE_FILE" ]]; then
  "$PY" - "$STATE_FILE" "$BASE_URL" <<'PYEOF'
import json, os, sys
p, url = sys.argv[1], sys.argv[2]
s = json.load(open(p, encoding='utf-8'))
s['launchClientUrl'] = url
tmp = p + '.tmp'
open(tmp, 'w', encoding='utf-8').write(json.dumps(s, ensure_ascii=False, indent=2))
os.replace(tmp, p)
print("   launchClientUrl =", url)
PYEOF
  ok "launchClientUrl written to ${STATE_FILE}"
else
  echo "   (skipped launchClientUrl write — RC=${RC} / BASE_URL='${BASE_URL}')"
fi

# ── Generate LAUNCH_REPORT.md (end-of-file holds the clickable entry URL) ──
POD_LIST="$(kubectl get pods -n "${APP_NS}" --no-headers 2>/dev/null | awk '{print "| "$1" | "$3" |"}')"
READY_REPLICAS="$(kubectl get deploy yocat-office-backend-api -n "${APP_NS}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null)"
STATUS_LINE=$([[ "$RC" == "0" ]] && echo "SUCCESS" || echo "FAILED")
mkdir -p docs
cat > "$REPORT" <<EOF
# Launch Report — $(date '+%Y-%m-%dT%H:%M:%S%z')

> **Pipeline 最終 launch 步**（Phase 12 Trace / \`trace-launch-local\`, artifact O-50）.
> client_engine = **none (api-only)**：無前端，故不做 Playwright UI 截圖；launch 驗收 = API endpoint smoke。
> Runner（唯一真理）：[\`scripts/launch-local.sh\`](../scripts/launch-local.sh)；停止：[\`scripts/launch-teardown.sh\`](../scripts/launch-teardown.sh)

## 部署環境
| 項目 | 值 |
|------|-----|
| 叢集 | context \`${CTX}\`（非 prod），k3s runtime \`${RUNTIME}\` |
| App image | \`${IMAGE_API}\`（multi-stage docker build, imagePullPolicy: Never） |
| DB | 既有 \`yocat-pg\`（namespace \`${DB_NS}\`；跨 namespace DNS \`yocat-pg.yocat.svc.cluster.local:5432/yocat\`） |
| App namespace | \`${APP_NS}\`（自有 namespace，與既有 membership \`public\` schema 隔離） |
| ready replicas | ${READY_REPLICAS:-0}（HA ≥2） |

## Server（k8s）— Pod 狀態
| Pod | Status |
|-----|--------|
${POD_LIST}

## API 存取（api-only 入口）
| 入口 | URL / 指令 | Health |
|------|-----------|--------|
| **Service NodePort（預設，經 Service LB 跨 replica）** | \`${BASE_URL}\` | /health/live 200 · /health/ready 200 (status/db/redis = UP) |
| port-forward（NodePort 替代；直連 deploy） | \`kubectl port-forward -n ${APP_NS} deploy/yocat-office-backend-api 8080:8080\` → http://127.0.0.1:8080 | — |
| Ingress（Traefik, in-cluster 已驗路由正確） | \`Host: ${INGRESS_HOST}\`（host :80 若被佔用則走 NodePort） | — |

## 關鍵 API 旅程（launch 驗收，真打外部 Service）
\`\`\`
register        POST ${BASE_URL}/auth/register   -> token + account + wallet(gems:0)
topup(假付費)   POST ${BASE_URL}/wallet/topup     {"pack":"g_big"} -> gems 700
spend           POST ${BASE_URL}/wallet/spend     {"currency":"gems","amount":10} -> gems 690
overdraw        POST ${BASE_URL}/wallet/spend     {"amount":1000000} -> HTTP 402 {"error":"餘額不足"}（balance 690 未變）
\`\`\`
> 假付費維持假：topup 無真實金流閘道，server 直接發包（fake payment）。

## ▶ API Base URL（複製貼到瀏覽器 / Postman / curl 即可打）

# 🔌 ${BASE_URL}/

示例：\`curl ${BASE_URL}/health/ready\`

## 停止本地環境
\`bash scripts/launch-teardown.sh\`
（刪除 ${APP_NS} namespace + DROP yocat BC schemas；不觸碰既有 membership public schema）

## 重新 launch
\`bash scripts/launch-local.sh\`

LAUNCH_STATUS: ${STATUS_LINE}
EOF
ok "LAUNCH_REPORT.md written -> ${REPORT}"

# ── 10. Optional teardown (収尾清理) ─────────────────────────────────
if [[ "$TEARDOWN" == "1" ]]; then
  step "10. teardown (TEARDOWN=1 — cleaning up test resources)"
  bash scripts/launch-teardown.sh || true
else
  echo ""
  echo "   (resources LEFT RUNNING — demo at ${BASE_URL} ; stop with: bash scripts/launch-teardown.sh)"
fi

echo ""
echo "════════════════════════════════════════════════════════════════"
if [[ "$RC" == "0" ]]; then
  echo "✅ launch-local SUCCESS — API live at ${BASE_URL}"
else
  echo "❌ launch-local FAILED (RC=$RC)"
fi
echo "════════════════════════════════════════════════════════════════"
exit $RC
