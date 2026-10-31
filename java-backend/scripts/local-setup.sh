#!/usr/bin/env bash
# scripts/local-setup.sh — yoCat Office backend one-click local environment setup (O-21).
#
# Realises PRD US-PLAT-001 / AC-5 (local-first): from a fresh clone, stand up the full
# trust-layer backend on Rancher Desktop k8s (default) or Docker Compose, migrate + seed
# the DB, and prove it healthy — with no external cloud.
#
# Source of truth: docs/LOCAL_SETUP.md (this script's companion) + ../docs/LOCAL_DEPLOY.md.
# This script only orchestrates the already-generated assets — it invents no deploy logic:
#   - prerequisites : scripts/prereq-check.sh (recon-prereq-check, O-?) + inline JDK/Maven/Docker/kubectl gate
#   - secrets       : scripts/bootstrap-secrets.sh (local fake values via openssl rand; never printed)
#   - build/deploy  : Makefile targets (image-build-api / db-up / k8s-init / k8s-apply / db-migrate / db-seed)
#   - compose path  : docker-compose.yml
#   - env reference : .env.local.example
#
# Pipeline (k8s, default):
#   prereqs -> bootstrap-secrets -> db-up -> k8s-init -> image-build-api -> k8s-apply
#           -> wait-ready -> db-migrate -> db-seed -> health-check + HA replica assert -> [smoke] -> URLs
#
# Usage:
#   ./scripts/local-setup.sh              # full k8s-local setup (Rancher Desktop)
#   ./scripts/local-setup.sh --compose    # Docker Compose fallback (single-port, no k8s)
#   ./scripts/local-setup.sh --check      # prerequisites dry-run only (no mutation)
#   ./scripts/local-setup.sh --no-smoke   # skip the register->topup->spend smoke flow
#   ./scripts/local-setup.sh --skip-build # reuse the existing image (k8s) / built jar (compose)
#   ./scripts/local-setup.sh -h           # help
#
# Security: all local passwords are random fake values written to gitignored files
# (k8s/overlays/local/secrets.env, .env) — none are echoed to stdout. Never use in prod.

set -euo pipefail

# ── resolve repo root (java-backend/, where Makefile + k8s/ + scripts/ live) ──
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

# ── config (aligned with Makefile + LOCAL_DEPLOY §2/§9) ──
APP_NS="yocat-office-backend-local"
DB_NS="yocat"
INGRESS_HOST="yocat-office-backend.local"
MIN_JDK=21

# ── flags ──
MODE="k8s"          # k8s | compose
CHECK_ONLY=0
RUN_SMOKE=1
SKIP_BUILD=0

usage() { sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --compose)    MODE="compose" ;;
    --check)      CHECK_ONLY=1 ;;
    --no-smoke)   RUN_SMOKE=0 ;;
    --skip-build) SKIP_BUILD=1 ;;
    -h|--help)    usage; exit 0 ;;
    *) echo "unknown option: $1 (use -h)" >&2; exit 2 ;;
  esac
  shift
done

# ── pretty logging (no color when not a TTY) ──
if [[ -t 1 ]]; then C_B=$'\033[1m'; C_G=$'\033[32m'; C_Y=$'\033[33m'; C_R=$'\033[31m'; C_0=$'\033[0m'; else C_B=; C_G=; C_Y=; C_R=; C_0=; fi
step() { echo ""; echo "${C_B}== $* ==${C_0}"; }
ok()   { echo "  ${C_G}OK${C_0}   $*"; }
warn() { echo "  ${C_Y}WARN${C_0} $*"; }
fail() { echo "  ${C_R}FAIL${C_0} $*"; }
have() { command -v "$1" >/dev/null 2>&1; }

# ─────────────────────────────────────────────────────────────────────────────
# Stage 0: Prerequisites (JDK 21 / Maven / Docker / kubectl) — the dry-run gate.
# ─────────────────────────────────────────────────────────────────────────────
prereqs() {
  step "[0] Prerequisites (archConfig: java / spring-boot-3.3 / postgres / redis / k8s-local)"
  local hard_missing=0

  # JDK 21 (Eclipse Temurin) — HARD. Falls back to $JAVA_HOME/bin when java is off-PATH
  # (common on Windows + Git Bash: the JDK is installed but not exported to PATH).
  local JAVA_BIN=""
  if have java; then JAVA_BIN="java"
  elif [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then JAVA_BIN="${JAVA_HOME}/bin/java"
  elif [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java.exe" ]]; then JAVA_BIN="${JAVA_HOME}/bin/java.exe"; fi
  if [[ -n "$JAVA_BIN" ]]; then
    local jv
    jv="$("$JAVA_BIN" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"' | head -1)"
    jv="${jv:-0}"
    if [[ "$jv" -ge "$MIN_JDK" ]]; then ok "JDK $jv (>= ${MIN_JDK})$( [[ "$JAVA_BIN" != java ]] && echo ' via JAVA_HOME' )"; else fail "JDK $jv < ${MIN_JDK} — install Temurin 21: https://adoptium.net"; hard_missing=1; fi
  else
    fail "java not found (not on PATH, JAVA_HOME unset) — install Eclipse Temurin JDK 21: https://adoptium.net"; hard_missing=1
  fi

  # Maven (or the wrapper) — HARD
  if have mvn; then ok "Maven $(mvn -v 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
  elif [[ -x ./mvnw ]]; then ok "Maven wrapper ./mvnw present"
  else fail "mvn / ./mvnw not found — install Maven 3.9+ or use ./mvnw: https://maven.apache.org"; hard_missing=1; fi

  # Docker engine (Rancher Desktop) — HARD for compose + Testcontainers; recommended for k8s
  if have docker; then
    if docker version >/dev/null 2>&1; then ok "docker engine reachable ($(docker version --format '{{.Server.Version}}' 2>/dev/null || echo '?'))"
    else warn "docker CLI present but daemon unreachable — start Rancher Desktop (needed for build/compose/Testcontainers)"; [[ "$MODE" == "compose" ]] && hard_missing=1; fi
  else
    warn "docker not found — Rancher Desktop provides it"; [[ "$MODE" == "compose" ]] && hard_missing=1
  fi

  # kubectl + a live node — HARD for k8s mode only
  if [[ "$MODE" == "k8s" ]]; then
    if have kubectl; then
      if kubectl get nodes >/dev/null 2>&1; then ok "kubectl + k3s node reachable"
      else fail "kubectl present but no reachable node — enable Kubernetes in Rancher Desktop (ctx rancher-desktop)"; hard_missing=1; fi
    else
      fail "kubectl not found — comes with Rancher Desktop: https://rancherdesktop.io"; hard_missing=1
    fi
    have nerdctl && ok "nerdctl present (image build into k8s.io namespace)" || warn "nerdctl not found — Rancher Desktop provides it (containerd build)"
  fi

  # soft tooling
  have jq   && ok "jq present"   || warn "jq not found (health/smoke pretty-print) — choco install jq"
  have psql && ok "psql present" || warn "psql not found (direct DB access) — choco install postgresql16"

  # full recon-level report (non-fatal, informational)
  if [[ -x scripts/prereq-check.sh ]]; then
    echo "  ---- scripts/prereq-check.sh (full toolchain report) ----"
    INFRA=k8s-local CLIENT=none LANG_STACK=java JAVA_MIN=${MIN_JDK} bash scripts/prereq-check.sh 2>/dev/null | sed 's/^/  /' || true
  fi

  if [[ "$hard_missing" -ne 0 ]]; then
    echo ""; fail "one or more HARD prerequisites missing — install them, then re-run."
    return 1
  fi
  echo ""; ok "all HARD prerequisites satisfied"
}

# ─────────────────────────────────────────────────────────────────────────────
# Stage: k8s-local deploy (Rancher Desktop k3s) — the primary path.
# ─────────────────────────────────────────────────────────────────────────────
deploy_k8s() {
  step "[1] kubectl context"
  kubectl config use-context rancher-desktop >/dev/null 2>&1 || warn "could not switch to rancher-desktop context (continuing with current)"
  kubectl cluster-info >/dev/null 2>&1 && ok "cluster reachable" || { fail "cluster unreachable"; return 1; }

  step "[2] Secrets (local fake values; gitignored, never printed)"
  bash scripts/bootstrap-secrets.sh

  step "[3] Existing PostgreSQL (namespace ${DB_NS}, postgres.yaml) + app namespace/Secret/Redis"
  make db-up
  make k8s-init

  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    step "[4] Build API image (nerdctl -> k8s.io)"
    make image-build-api
  else
    warn "[4] --skip-build: reusing existing image yocat-office-backend/api:local"
  fi

  step "[5] Deploy API (>=2 replicas) + Redis + Service + Ingress"
  make k8s-apply
  kubectl wait --for=condition=Ready pods --all -n "${APP_NS}" --timeout=300s

  step "[6] Database: Flyway migrate + seed"
  make db-migrate
  make db-seed

  step "[7] Health + HA replica assertion"
  make health-check || true
  local replicas
  replicas="$(kubectl get pods -l app=yocat-office-backend-api -n "${APP_NS}" --no-headers 2>/dev/null | grep -c Running || true)"
  if [[ "${replicas:-0}" -ge 2 ]]; then ok "API Running replicas = ${replicas} (>= 2, minimal HA)"
  else fail "API Running replicas = ${replicas:-0} (< 2) — see docs/LOCAL_SETUP.md Troubleshooting"; fi

  BASE_URL="http://${INGRESS_HOST}"
}

# ─────────────────────────────────────────────────────────────────────────────
# Stage: Docker Compose fallback (single-port, no k8s).
# ─────────────────────────────────────────────────────────────────────────────
deploy_compose() {
  step "[1] .env (Compose host override; DB_PASSWORD only)"
  if [[ ! -f .env ]]; then
    [[ -f .env.example ]] && cp .env.example .env || true
    # generate a local fake DB password if the copied template still has a placeholder
    if ! grep -qE '^DB_PASSWORD=.+$' .env 2>/dev/null || grep -qE '^DB_PASSWORD=(change-me|REPLACE|your-|$)' .env 2>/dev/null; then
      { grep -vE '^DB_PASSWORD=' .env 2>/dev/null || true; echo "DB_PASSWORD=$(openssl rand -hex 24)"; } > .env.tmp && mv .env.tmp .env
    fi
    ok "wrote .env (gitignored)"
  else
    ok ".env already present"
  fi

  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    step "[2] docker compose build"
    docker compose build
  else
    warn "[2] --skip-build: reusing existing api image"
  fi

  step "[3] docker compose up -d (api x2 = minimal HA)"
  docker compose up -d
  echo "  waiting for db healthy ..."
  for _ in $(seq 1 30); do
    if docker compose ps db 2>/dev/null | grep -qiE 'healthy'; then break; fi
    sleep 2
  done
  docker compose ps

  step "[4] Database: Flyway migrate (in api container) + seed"
  docker compose exec -T api java -jar /app/app.jar --spring.flyway.migrate-only=true || warn "migrate-only returned non-zero (Spring Boot also migrates on boot)"
  if [[ -f scripts/db-seed.sql ]]; then
    docker compose exec -T db psql -U yocat -d yocat -v yocat.seed_env=local < scripts/db-seed.sql || warn "seed returned non-zero"
  fi

  step "[5] Health"
  curl -s http://localhost:8080/health/ready | { have jq && jq . || cat; } || warn "health/ready not yet 200 (api may still be booting)"
  BASE_URL="http://localhost:8080"
}

# ─────────────────────────────────────────────────────────────────────────────
# Smoke: register -> topup -> spend -> 402 (LOCAL_DEPLOY §18; real API.md paths).
# ─────────────────────────────────────────────────────────────────────────────
smoke() {
  step "[8] Smoke (register -> topup -> spend -> 402 overdraw)"
  local base="${BASE_URL:-http://${INGRESS_HOST}}"
  local email="smoke+$(date +%s)@${INGRESS_HOST}" token code

  curl -sf "${base}/health/ready" >/dev/null || { warn "health/ready not ready — skipping smoke"; return 0; }

  token="$(curl -sf -X POST "${base}/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"${email}\",\"displayName\":\"Smoke\",\"password\":\"Password1!\"}" | { have jq && jq -r '.token' || cat; })"
  [[ -n "${token}" && "${token}" != "null" ]] || { fail "register did not return a token"; return 1; }
  ok "register -> token acquired"

  curl -sf -X POST "${base}/wallet/topup" -H "Authorization: Bearer ${token}" \
    -H 'Content-Type: application/json' -d '{"pack":"g_small"}' >/dev/null && ok "topup g_small" || { fail "topup"; return 1; }

  curl -sf -X POST "${base}/wallet/spend" -H "Authorization: Bearer ${token}" \
    -H 'Content-Type: application/json' -d '{"currency":"gems","amount":10,"reason":"smoke"}' >/dev/null && ok "spend 10 gems" || { fail "spend"; return 1; }

  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${base}/wallet/spend" -H "Authorization: Bearer ${token}" \
    -H 'Content-Type: application/json' -d '{"currency":"gems","amount":999999999,"reason":"smoke-overdraw"}')"
  [[ "${code}" == "402" ]] && ok "overdraw -> 402 (insufficient balance, rolled back)" || { fail "expected 402, got ${code}"; return 1; }

  ok "smoke passed"
}

# ─────────────────────────────────────────────────────────────────────────────
# Access summary.
# ─────────────────────────────────────────────────────────────────────────────
summary() {
  step "Environment ready"
  if [[ "$MODE" == "k8s" ]]; then
    echo "  API (Ingress)   http://${INGRESS_HOST}/            (needs /etc/hosts: 127.0.0.1 ${INGRESS_HOST})"
    echo "  Readiness       http://${INGRESS_HOST}/health/ready"
    echo "  Liveness        http://${INGRESS_HOST}/health/live"
    echo "  Metrics         http://${INGRESS_HOST}/actuator/prometheus"
    echo "  API (NodePort)  http://localhost:30080/            (or: make pf-api)"
    echo "  PostgreSQL      localhost:30432  (db=yocat user=yocat; or: make pf-db)"
    echo "  Redis           localhost:30379  (or: make pf-redis)"
    echo ""
    echo "  Logs: make logs-api   Shell: make shell-api   Reset: make db-reset   Teardown: make k8s-clean"
  else
    echo "  API             http://localhost:8080/"
    echo "  Readiness       http://localhost:8080/health/ready"
    echo "  PostgreSQL      localhost:30432  (db=yocat user=yocat)"
    echo "  Redis           localhost:30379"
    echo ""
    echo "  Logs: docker compose logs -f api   Teardown: docker compose down   Reset: docker compose down -v"
  fi
}

# ── main ──
echo "${C_B}yoCat Office backend — local setup${C_0}  (mode=${MODE}$([[ $CHECK_ONLY -eq 1 ]] && echo ' / check-only'))"
prereqs || exit 1
if [[ "$CHECK_ONLY" -eq 1 ]]; then echo ""; ok "prerequisites dry-run complete (no changes made)"; exit 0; fi

if [[ "$MODE" == "k8s" ]]; then deploy_k8s; else deploy_compose; fi
[[ "$RUN_SMOKE" -eq 1 ]] && { smoke || warn "smoke incomplete — see output above"; }
summary
echo ""
ok "local-setup done. Full reference: docs/LOCAL_SETUP.md / ../docs/LOCAL_DEPLOY.md"
