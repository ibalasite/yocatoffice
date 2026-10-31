#!/usr/bin/env bash
# scripts/k8s-smoke.sh — Harden phase K8s deployment smoke test (真部署真打 health + API).
#
# 真的把本後端部署到本機 Rancher Desktop k3s 叢集，等 rollout + Flyway migration Job 完成，
# 對 Ingress（Traefik :80, host=yocat-office-backend.local）打 /health/live、/health/ready，
# 跑關鍵 API smoke（register → topup → spend → 402），並做基本 HA 驗證（≥2 replicas + pod-kill rolling）。
#
# 對外路徑走真實的 Service 入口（非 kubectl port-forward 直連 pod）：health + API smoke 透過
# API Service 的 NodePort（Rancher Desktop 會把 NodePort 轉到 127.0.0.1；經 Service → kube-proxy
# 在 2 個 replica 間負載均衡）。同時以 in-cluster 方式經 Traefik 驗證 Ingress 路由（Host header →
# Service），證明 Ingress manifest 正確；host 層的 Ingress 入口若被佔用（:80 非 Traefik）則以 best-effort
# 記錄（環境限制，非 manifest 缺陷）。NodePort 是 api-service.yaml 註記的「direct local access」真實入口。
#
# infra_runtime = k8s-local（authoritative, LOCAL_DEPLOY §4.5）。本 script 不含 docker-compose 退化分支，
# 因為文件明定 K8s-first（Iron Law §3：預設不蓋文件）。
#
# 用法:
#   bash scripts/k8s-smoke.sh              # 全流程 + 收尾清理（刪 namespace + drop yocat schemas）
#   KEEP_RESOURCES=1 bash scripts/k8s-smoke.sh   # 不清理（除錯用；叢集會殘留 yocat namespace + schemas）
#
# 需求工具（Rancher Desktop）: kubectl / nerdctl / helm / docker（npipe）; openssl; curl。
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
# External host→cluster entrypoint for the deployed Service. Default is the API Service NodePort on
# 127.0.0.1 (Rancher Desktop forwards NodePorts to localhost). The exact nodePort is DISCOVERED from
# the live Service after deploy (not hardcoded) because the overlay's preferred 30080 may already be
# taken by a neighbouring app in a shared cluster. BASE_URL is set in step 5c once the port is known.
BASE_URL=""
KEEP_RESOURCES="${KEEP_RESOURCES:-0}"
# yocat BC schemas created by Flyway into the shared yocat DB — dropped on teardown so the
# smoke never pollutes the existing membership DB (public schema is left untouched).
YOCAT_SCHEMAS="member,wallet,deposit,entitlement,record,leaderboard,game_save,modulith,platform"

RC=0
step() { echo ""; echo "──────── $* ────────"; }
fail() { echo "❌ FAIL: $*"; RC=1; }
ok()   { echo "✅ $*"; }

# ── Teardown (trap EXIT so pods never leak on failure) ───────────────
teardown() {
  local ec=$?
  if [[ "$KEEP_RESOURCES" == "1" ]]; then
    echo ""; echo "──────── teardown skipped (KEEP_RESOURCES=1) ────────"
    echo "   manual cleanup: kubectl delete namespace ${APP_NS} ; drop schemas {${YOCAT_SCHEMAS}} in ${DB_NS}/yocat"
    return
  fi
  step "teardown (cleaning up — keep existing yocat-pg DB rows)"
  kubectl delete namespace "${APP_NS}" --ignore-not-found --timeout=120s 2>&1 | tail -2 || true
  # Drop only the yocat BC schemas we added; the membership 'public' schema is never touched.
  kubectl exec -n "${DB_NS}" deploy/yocat-pg -- psql -U yocat -d yocat -q -c \
    "DROP SCHEMA IF EXISTS ${YOCAT_SCHEMAS} CASCADE;" 2>&1 | tail -2 || true
  kubectl get ns "${APP_NS}" >/dev/null 2>&1 && echo "   (namespace still terminating)" || ok "namespace removed"
  echo "   teardown done (orig exit=${ec})"
}
trap teardown EXIT

echo "════════════════════════════════════════════════════════════════"
echo " k8s-smoke  —  $(date '+%Y-%m-%dT%H:%M:%S%z')"
echo " cluster: $(kubectl config current-context 2>/dev/null)   host: $(hostname)"
echo "════════════════════════════════════════════════════════════════"

# ── 0. Preconditions (strengthened rule v0.7) ────────────────────────
step "0. preconditions"
kubectl get nodes -o wide 2>&1 | head -3 || { echo "BLOCKED: no k8s node"; exit 1; }
CTX="$(kubectl config current-context 2>/dev/null)"
[[ "$CTX" =~ prod|production ]] && { echo "BLOCKED: context is production: $CTX"; exit 1; }
kubectl get svc yocat-pg -n "${DB_NS}" >/dev/null 2>&1 || { echo "BLOCKED: yocat-pg not found in ns ${DB_NS}"; exit 1; }
ok "node ready, context=${CTX} (non-prod), yocat-pg present"

# ── 1. Build image into the runtime k3s actually uses ────────────────
# Rancher Desktop runs in one of two modes; the k3s node reports which. In dockerd/moby mode
# (containerRuntimeVersion = docker://...) k3s shares the host dockerd, so `docker build` lands
# the image where k3s can see it (imagePullPolicy: Never). In containerd mode, images must be
# built into the k8s.io containerd namespace via nerdctl. Detect and build accordingly.
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

# ── 2. Manifests dry-run (validate kustomize render) ─────────────────
step "2. kubectl kustomize + apply --dry-run=client"
kubectl kustomize "${K8S_OVERLAY}/" >/tmp/k8s-smoke-render.yaml 2>&1 || true
# secrets.env is required by the secretGenerator; create it before the real dry-run/apply.
./scripts/bootstrap-secrets.sh >/dev/null 2>&1 || { fail "bootstrap-secrets"; exit 1; }
kubectl apply -k "${K8S_OVERLAY}/" --dry-run=client >/dev/null 2>&1 \
  && ok "overlay manifests valid (dry-run)" || fail "dry-run invalid"

# ── 3. Apply: namespace + Secret + Flyway ConfigMap + Redis + API + Ingress + Job ──
step "3. deploy to k3s (kubectl apply -k)"
kubectl create namespace "${APP_NS}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
# Flyway Job mounts the 'flyway-migrations' ConfigMap (created imperatively from migrations/).
kubectl create configmap flyway-migrations --from-file=migrations -n "${APP_NS}" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl delete job yocat-office-backend-flyway-migrate -n "${APP_NS}" --ignore-not-found >/dev/null 2>&1
# apply -k is not atomic: in a shared cluster the api-service's preferred nodePort (30080) can be
# already allocated by a neighbour, so that one resource may be rejected while Deployment/Redis/
# Ingress/Job apply fine. We tolerate that here and reconcile the Service in step 3b.
kubectl apply -k "${K8S_OVERLAY}/" 2>&1 | sed 's/^/   /'

# ── 3b. Ensure api-service exists on a reachable nodePort (shared-cluster resilience) ──
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

# ── 4. Flyway migration Job completes ────────────────────────────────
step "4. wait Flyway migration Job"
if kubectl wait --for=condition=complete job/yocat-office-backend-flyway-migrate \
     -n "${APP_NS}" --timeout=180s 2>&1; then
  ok "flyway Job complete"
  kubectl logs -n "${APP_NS}" job/yocat-office-backend-flyway-migrate 2>&1 | grep -iE "migrat|success|schema" | tail -6 || true
else
  fail "flyway Job did not complete"
  kubectl logs -n "${APP_NS}" job/yocat-office-backend-flyway-migrate 2>&1 | tail -20 || true
fi

# ── 5. API rollout (>=2 replicas) ────────────────────────────────────
step "5. wait API rollout (>=2 replicas)"
if kubectl rollout status deployment/yocat-office-backend-api -n "${APP_NS}" --timeout=180s 2>&1; then
  ok "api rollout complete"
else
  fail "api rollout timeout"
  kubectl describe pods -n "${APP_NS}" -l app=yocat-office-backend-api 2>&1 | tail -30 || true
fi

# ── 5b. All pods Running (strengthened rule v0.7) ────────────────────
step "5b. all pods Running"
NOT_RUNNING="$(kubectl get pods -n "${APP_NS}" --no-headers 2>/dev/null | awk '$3!="Running" && $3!="Completed"')"
kubectl get pods -n "${APP_NS}" -o wide 2>&1
if [[ -n "$NOT_RUNNING" ]]; then
  fail "pods not all Running:"; echo "$NOT_RUNNING"
else
  ok "all pods Running/Completed"
fi

# ── 5c. Discover the Service nodePort -> external BASE_URL (Rancher forwards NodePort to 127.0.0.1) ──
step "5c. resolve external entrypoint (Service NodePort)"
API_NODEPORT="$(kubectl get svc yocat-office-backend-api -n "${APP_NS}" -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null)"
[[ -n "$API_NODEPORT" ]] || { fail "could not read api-service nodePort"; }
BASE_URL="http://127.0.0.1:${API_NODEPORT}"
echo "   external path = Service NodePort ${API_NODEPORT} -> ${BASE_URL} (through Service, LB across replicas)"
for i in $(seq 1 24); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/health/ready" 2>/dev/null || echo 000)"
  [[ "$code" == "200" ]] && break
  sleep 5
done
ok "external NodePort reachable (/health/ready http=${code})"

# ── 6. Seed demo data (best-effort; essential catalogs come from the V011 migration) ─
step "6. db-seed (supplementary demo data; non-fatal)"
# db-seed.sql reads the 'yocat.seed_env' GUC via current_setting() -> set it with PGOPTIONS
# (NOT psql -v, which rejects a dotted variable name). The reference catalogs the smoke needs
# (deposit packs / entitlement catalog) are already loaded by migration V011, so demo seed is optional.
if kubectl exec -i -n "${DB_NS}" deploy/yocat-pg -- \
     env PGOPTIONS="-c yocat.seed_env=local" psql -U yocat -d yocat -v ON_ERROR_STOP=1 -f - \
     < scripts/db-seed.sql 2>&1 | tail -3; then
  ok "demo seed applied"
else
  echo "   (demo seed skipped/failed — non-fatal; smoke relies on V011 migration catalogs)"
fi

# ── 7. Health via external NodePort (HTTP 200 + contract body status=UP, db=UP, redis=UP) ─
step "7. health via external Service (${BASE_URL})"
LIVE="$(curl -s -i "${BASE_URL}/health/live" 2>&1)"
READY="$(curl -s -i "${BASE_URL}/health/ready" 2>&1)"
echo "--- /health/live ---";  echo "$LIVE"  | head -1; echo "$LIVE"  | tail -1
echo "--- /health/ready ---"; echo "$READY" | head -1; echo "$READY" | tail -1
echo "$LIVE"  | grep -qE "HTTP/1\.[01] 200" && ok "/health/live 200" || fail "/health/live not 200"
echo "$READY" | grep -qE "HTTP/1\.[01] 200" && ok "/health/ready 200" || fail "/health/ready not 200"
# Contract (HealthController): /health/ready body = {"status":"UP","db":"UP","redis":"UP"}.
echo "$READY" | grep -qE '"status":"UP"' && ok "/health/ready body status=UP" || fail "/health/ready body not UP"
echo "$READY" | grep -qE '"db":"UP"'     && ok "/health/ready db=UP"          || fail "/health/ready db not UP"
echo "$READY" | grep -qE '"redis":"UP"'  && ok "/health/ready redis=UP"       || fail "/health/ready redis not UP"

# ── 7b. Ingress verification: Traefik routes Host:${INGRESS_HOST} -> Service (in-cluster proof) ──
step "7b. Ingress routing via Traefik (Host: ${INGRESS_HOST})"
# Run curl from a throwaway in-cluster pod against the Traefik Service with the ingress Host header.
# Parse the status code via a sentinel (INGHTTP=NNN=END) so stray digits from the pod name / attach
# banner cannot be mistaken for the HTTP code.
ING_RAW="$(kubectl run ingcheck-$$ --rm -i --restart=Never --image=curlimages/curl:8.10.1 \
  -n "${APP_NS}" --command -- curl -s -m 10 -o /dev/null -w 'INGHTTP=%{http_code}=END' \
  -H "Host: ${INGRESS_HOST}" http://traefik.kube-system.svc.cluster.local/health/live 2>/dev/null)"
ING_CODE="$(printf '%s' "$ING_RAW" | sed -n 's/.*INGHTTP=\([0-9][0-9][0-9]\)=END.*/\1/p' | tail -1)"
echo "   in-cluster Traefik ingress -> /health/live http=${ING_CODE:-none}"
[[ "$ING_CODE" == "200" ]] && ok "Ingress manifest routes correctly (Traefik -> Service -> pod, 200)" \
  || fail "Ingress did not route in-cluster (got ${ING_CODE:-none})"
# Host-level ingress entrypoint is best-effort: Rancher Desktop forwards Traefik to 127.0.0.1:80 ONLY
# when :80 is free. If a foreign server holds :80, the host cannot reach the ingress — an environment
# constraint, not a manifest defect (proven correct in-cluster above). We only count host-ingress as
# real when the responder is actually Traefik (Server header), so an unrelated :80 server that happens
# to answer 200 is NOT mistaken for the ingress.
HOST_HDRS="$(curl -s -m 5 --resolve "${INGRESS_HOST}:80:127.0.0.1" -D - -o /dev/null "http://${INGRESS_HOST}/health/live" 2>/dev/null || true)"
HOST_CODE="$(printf '%s' "$HOST_HDRS" | grep -oE 'HTTP/[0-9.]+ [0-9]{3}' | grep -oE '[0-9]{3}$' | head -1)"
if [[ "$HOST_CODE" == "200" ]] && printf '%s' "$HOST_HDRS" | grep -qiE '^server:.*traefik'; then
  ok "host-level ingress (127.0.0.1:80 = Traefik) reachable"
else
  SRV="$(printf '%s' "$HOST_HDRS" | grep -i '^server:' | head -1 | tr -d '\r')"
  echo "   NOTE: 127.0.0.1:80 is not Traefik (${SRV:-no server header}; host→:80 http=${HOST_CODE:-000}); an unrelated server holds :80."
  echo "   NOTE: ingress is proven correct in-cluster (200 above); host external smoke uses the Service NodePort. Not counted as a failure."
fi

# ── 8. Key API smoke via external Service: register -> topup -> spend -> 402 ──
step "8. API smoke (register -> topup -> spend -> 402) via external Service"
EMAIL="k8s-smoke-$(date +%s)-$RANDOM@example.com"
PW="Password1!"
CURL=(curl -s -H "Content-Type: application/json")

REG="$("${CURL[@]}" -X POST "${BASE_URL}/auth/register" \
  -d "{\"email\":\"${EMAIL}\",\"displayName\":\"K8s Smoke\",\"password\":\"${PW}\"}" 2>&1)"
TOKEN="$(printf '%s' "$REG" | grep -oE '"token":"[^"]*"' | head -1 | sed 's/"token":"//;s/"$//')"
[[ -n "$TOKEN" ]] && ok "register returned token" || fail "register no token: $(printf '%s' "$REG" | head -c 200)"
AUTH=(-H "Authorization: Bearer ${TOKEN}")

TOPUP="$("${CURL[@]}" "${AUTH[@]}" -X POST "${BASE_URL}/wallet/topup" -d '{"pack":"g_big"}' 2>&1)"
printf '%s' "$TOPUP" | grep -qE '"gems":[[:space:]]*700' && ok "topup -> 700 gems" || fail "topup: $(printf '%s' "$TOPUP" | head -c 200)"

SPEND="$("${CURL[@]}" "${AUTH[@]}" -X POST "${BASE_URL}/wallet/spend" \
  -d '{"currency":"gems","amount":10,"reason":"k8s-smoke"}' 2>&1)"
printf '%s' "$SPEND" | grep -qE '"gems":[[:space:]]*690' && ok "spend 10 -> 690 gems" || fail "spend: $(printf '%s' "$SPEND" | head -c 200)"

# overdraw -> 402 餘額不足 (server-authoritative, balance rolled back)
OVER_CODE="$("${CURL[@]}" "${AUTH[@]}" -o /tmp/k8s-smoke-402.json -w '%{http_code}' \
  -X POST "${BASE_URL}/wallet/spend" \
  -d '{"currency":"gems","amount":1000000,"reason":"k8s-smoke-overdraw"}' 2>&1)"
OVER_BODY="$(cat /tmp/k8s-smoke-402.json 2>/dev/null)"
echo "   overdraw status=${OVER_CODE} body=${OVER_BODY}"
[[ "$OVER_CODE" == "402" ]] && ok "overdraw -> HTTP 402" || fail "overdraw expected 402 got ${OVER_CODE}"
printf '%s' "$OVER_BODY" | grep -q '餘額不足' && ok "402 body = 餘額不足" || fail "402 body mismatch"

# ── 9. Basic HA: >=2 ready replicas + pod-kill rolling availability ──
step "9. HA verify (>=2 replicas + pod-kill rolling)"
READY_REPLICAS="$(kubectl get deploy yocat-office-backend-api -n "${APP_NS}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null)"
echo "   readyReplicas=${READY_REPLICAS:-0}"
[[ "${READY_REPLICAS:-0}" -ge 2 ]] && ok ">=2 ready replicas (HA)" || fail "expected >=2 ready replicas"
VICTIM="$(kubectl get pods -n "${APP_NS}" -l app=yocat-office-backend-api -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)"
if [[ -n "$VICTIM" ]]; then
  echo "   killing pod ${VICTIM} (self-heal + availability check)"
  kubectl delete pod -n "${APP_NS}" "$VICTIM" --wait=false >/dev/null 2>&1
  AVAIL_OK=1
  for i in $(seq 1 12); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/health/live" 2>/dev/null || echo 000)"
    [[ "$code" == "200" ]] || AVAIL_OK=0
    sleep 2
  done
  [[ "$AVAIL_OK" == "1" ]] && ok "service stayed available during pod kill (surviving replica served /health/live 200 via Service LB)" \
    || fail "service became unavailable during pod kill"
  kubectl rollout status deployment/yocat-office-backend-api -n "${APP_NS}" --timeout=120s 2>&1 | tail -1 \
    && ok "deployment self-healed back to full replicas" || fail "self-heal timeout"
fi

echo ""
echo "════════════════════════════════════════════════════════════════"
if [[ "$RC" == "0" ]]; then
  echo "✅ k8s smoke test passed"
else
  echo "❌ k8s smoke test FAILED (RC=$RC)"
fi
echo "════════════════════════════════════════════════════════════════"
exit $RC
