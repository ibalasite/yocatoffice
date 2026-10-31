#!/usr/bin/env bash
# scripts/prereq-check.sh
# gencode[recon-prereq-check] — verify local toolchain against archConfig.
#
# archConfig (from .gencode-state.json):
#   lang_stack      = java
#   framework       = spring-boot-3.3
#   test_framework  = junit5
#   orm             = spring-data-jdbc
#   infra_runtime   = k8s-local   -> Rancher Desktop / k3s HARD prereq
#   ci_platform     = github-actions
#   db_type         = postgres
#   cache_type      = redis
#
# Two classes of prerequisites:
#   A. HARD prereq  — user must install (GUI / IDE). Missing => BLOCKED.
#   B. AUTO prereq  — AI auto-installs via OS package manager. Install fail => BLOCKED.
set +e

# ── arch inputs (overridable via env for re-use) ──
INFRA="${INFRA:-k8s-local}"
CLIENT="${CLIENT:-none}"
LANG_STACK="${LANG_STACK:-java}"
JAVA_MIN="${JAVA_MIN:-21}"
NEEDS_PLAYWRIGHT="${NEEDS_PLAYWRIGHT:-0}"   # client_engine=none => no web build
NEEDS_K6="${NEEDS_K6:-0}"
NEEDS_GITLEAKS="${NEEDS_GITLEAKS:-0}"

HARD_MISSING=()
AUTO_INSTALLED=()
AUTO_FAILED=()
REPORT_LINES=()

log() { echo "$@"; REPORT_LINES+=("$@"); }

# === Step 1: HARD prereq (missing => BLOCKED) ===
log "## HARD prerequisites"

# Rancher Desktop / k3s (if k8s-local)  — detect via a working kube node
if [[ "$INFRA" == "k8s-local" ]]; then
  if kubectl get nodes >/dev/null 2>&1; then
    NODE_LINE=$(kubectl get nodes --no-headers 2>/dev/null | head -1)
    log "  OK  Rancher Desktop / k3s — kubectl node: ${NODE_LINE}"
  else
    HARD_MISSING+=("Rancher Desktop — kubectl get nodes returned no node -> https://rancherdesktop.io")
    log "  MISSING  Rancher Desktop (kubectl get nodes) -> https://rancherdesktop.io"
  fi
  # docker daemon (Testcontainers depends on it)
  if docker version >/dev/null 2>&1; then
    log "  OK  docker daemon — $(docker version --format '{{.Server.Version}}' 2>/dev/null)"
  else
    HARD_MISSING+=("docker daemon not reachable (needed for Testcontainers) -> start Rancher Desktop")
    log "  MISSING  docker daemon (Testcontainers) -> start Rancher Desktop"
  fi
fi


fi

# Unity (if client_engine=unity)
if [[ "$CLIENT" == "unity" ]]; then
  command -v Unity >/dev/null 2>&1 || HARD_MISSING+=("Unity Editor -> https://unity.com/download")
fi

if [[ "$CLIENT" == "none" ]]; then
fi

if [[ ${#HARD_MISSING[@]} -gt 0 ]]; then
  echo ""
  echo "HARD prerequisites missing (install manually, then /gencode-resume):"
  for e in "${HARD_MISSING[@]}"; do echo "  - $e"; done
  exit 1
fi

# === Step 2: AUTO prereq (missing => auto-install) ===
log ""
log "## AUTO prerequisites"

detect_pm() {
  case "$(uname -s)" in
    Darwin) command -v brew >/dev/null && echo "brew" ;;
    Linux)  command -v apt >/dev/null && echo "apt" || { command -v dnf >/dev/null && echo "dnf"; } ;;
    MINGW*|CYGWIN*|MSYS*) command -v winget >/dev/null && echo "winget" || { command -v scoop >/dev/null && echo "scoop"; } ;;
  esac
}
PM=$(detect_pm)
log "  package-manager: ${PM:-<none>}  (OS: $(uname -s))"

auto_install() {
  local name="$1" check_cmd="$2" pkg_winget="$3" pkg_brew="$4" pkg_apt="$5"
  if eval "$check_cmd" >/dev/null 2>&1; then
    local v; v=$(eval "$check_cmd" 2>&1 | head -1)
    log "  OK  $name (already installed) — $v"
    return 0
  fi
  log "  INSTALL  $name missing, auto-installing via ${PM}..."
  case "$PM" in
    winget) winget install --silent --accept-source-agreements --accept-package-agreements -e --id "$pkg_winget" ;;
    brew)   brew install "$pkg_brew" ;;
    apt)    sudo apt-get update && sudo apt-get install -y "$pkg_apt" ;;
  esac
  # re-hash PATH for the current shell where possible
  hash -r 2>/dev/null
  if eval "$check_cmd" >/dev/null 2>&1; then
    local v; v=$(eval "$check_cmd" 2>&1 | head -1)
    AUTO_INSTALLED+=("$name")
    log "  INSTALLED  $name — $v"
  else
    AUTO_FAILED+=("$name (installed pkg but check cmd still fails; may need shell/PATH reload)")
    log "  FAIL  $name — check command still fails after install"
  fi
}

case "$LANG_STACK" in
  java)
    auto_install "java>=${JAVA_MIN}" \
      "java -version 2>&1 | grep -E '\"(2[1-9]|[3-9][0-9])'" \
      "EclipseAdoptium.Temurin.${JAVA_MIN}.JDK" "openjdk@${JAVA_MIN}" "openjdk-${JAVA_MIN}-jdk"
    auto_install "maven" "mvn -version" "Apache.Maven" "maven" "maven"
    ;;
  typescript)
    auto_install "node>=20" "node --version | grep -E 'v(2[0-9]|[3-9][0-9])'" "OpenJS.NodeJS" "node@20" "nodejs" ;;
  python)
    auto_install "python3" "python3 --version" "Python.Python.3.12" "python@3.12" "python3" ;;
  go)
    auto_install "go" "go version" "GoLang.Go" "go" "golang" ;;
esac

# Playwright (only if a web/H5 client is produced) — client_engine=none => skipped
if [[ "$NEEDS_PLAYWRIGHT" == "1" ]]; then
  npx playwright --version >/dev/null 2>&1 || { npm install -D @playwright/test && npx playwright install --with-deps; }
else
  log "  N/A  Playwright — client_engine=none (no web/H5 build)"
fi

# CI / perf / secret-scan tooling
auto_install "gh" "gh --version" "GitHub.cli" "gh" "gh"
[[ "$NEEDS_K6" == "1" ]]       && auto_install "k6" "k6 version" "k6.k6" "k6" "k6"
[[ "$NEEDS_GITLEAKS" == "1" ]] && auto_install "gitleaks" "gitleaks version" "gitleaks.gitleaks" "gitleaks" "gitleaks"

# === Final verdict ===
echo ""
if [[ ${#AUTO_FAILED[@]} -gt 0 ]]; then
  echo "AUTO install failed:"
  for e in "${AUTO_FAILED[@]}"; do echo "  - $e"; done
  exit 1
fi

echo "All prerequisites OK"
[[ ${#AUTO_INSTALLED[@]} -gt 0 ]] && echo "   Auto-installed: ${AUTO_INSTALLED[*]}"
exit 0
