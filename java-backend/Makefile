# yocat-office-backend — Shared Make Targets (Local-First == CI)
# Source of truth: docs/CICD.md §4 Shared Make Targets + docs/LOCAL_DEPLOY.md §6.
# GitHub Actions / Gitea Actions / act AND local dev invoke the SAME targets so
# "local OK, CI broken" cannot happen. Every ci-*/cd-* target delegates to a base
# target (no CI-only logic). Run `make help` for the full catalog.
#
# Toolchain: JDK 21 (Temurin) + Maven 3.9.9. All targets delegate to the multi-module
# reactor rooted at ./pom.xml (module: apps/api). Integration tests use Testcontainers
# against Rancher Desktop (DOCKER_HOST npipe + docker-java API_VERSION, see below).

MVN ?= mvn
API := apps/api

# ── Integration-test env (Testcontainers ↔ Rancher Desktop) ──────────
# docker-java (Testcontainers 1.19.x) negotiates no API version; Rancher Desktop
# dockerd (min API 1.41) rejects the 1.32 default. Pin a modern version + npipe host.
API_VERSION ?= 1.43
DOCKER_HOST ?= npipe:////./pipe/docker_engine
export DOCKER_HOST
IT_FLAGS := -Dapi.version=$(API_VERSION)

# ── K8s-local deploy settings (LOCAL_DEPLOY §2/§4/§6) ────────────────
APP_NS       := yocat-office-backend-local
DB_NS        := yocat
K8S_OVERLAY  := k8s/overlays/local
IMAGE_API    := yocat-office-backend/api:local
DOCKERFILE   := docker/api/Dockerfile
MIGRATIONS   := migrations

# ── CI/CD image + deploy settings (docs/CICD.md §4.2) ────────────────
IMAGE_TAG    ?= $(shell git rev-parse --short=8 HEAD 2>/dev/null || echo local)
REGISTRY     ?= registry.sayyogames.internal/yocat-office-backend
PROJECT_SLUG ?= yocat-office-backend
ENV          ?= dev
NS_dev       := yocat-dev
NS_staging   := yocat-staging
NS_prod      := yocat-prod
NS_local     := yocat-office-backend-local

PID_FILE := .run/api.pid

.PHONY: help \
        dev build start up down clean type-check lint package verify \
        test test-unit test-integration test-contract test-bdd test-e2e \
        test-perf test-soak test-stress test-modulith test-schema-isolation \
        ci-lint ci-archunit ci-test-unit ci-test-integration ci-test-modulith \
        ci-contract-test ci-test-schema-isolation ci-spotbugs ci-semgrep \
        ci-dependency-scan ci-perf ci-soak ci-stress ci-dry-run \
        ci-build ci-build-image ci-image-sign ci-deploy cd-bump-image \
        cd-verify-ha ci-smoke ci-rollback \
        image-build-api db-up k8s-init k8s-apply k8s-restart-api k8s-delete k8s-clean \
        db-migrate db-seed db-status db-reset \
        health-check pods logs-api shell-api shell-db k9s \
        pf-api pf-db pf-redis pf-all pf-stop

# ────────────────────────────────────────────────────────────────────
help:                      ## list every target (grouped)
	@echo "yocat-office-backend - make targets (CI == local)"
	@echo ""
	@echo "Developer workflow:"
	@echo "  dev                    run API with live reload (spring-boot:run)"
	@echo "  build                  compile + package jar (skip tests)"
	@echo "  start                  run the packaged jar"
	@echo "  up / down              start/stop the API locally in background (graceful SIGTERM)"
	@echo "  type-check             compile only (no tests)"
	@echo "  lint                   static analysis (SpotBugs High/Critical gate)"
	@echo "  clean                  mvn clean"
	@echo "  package / verify       alias: build / ci-test-integration"
	@echo ""
	@echo "Tests (same targets CI runs):"
	@echo "  test / test-unit       unit tests (Surefire *Test.java)"
	@echo "  test-integration       all integration tests (Failsafe *IT, Testcontainers)"
	@echo "  test-contract          Pact consumer contracts (*PactTest)"
	@echo "  test-bdd               Cucumber BDD acceptance suite (RunBddContractIT)"
	@echo "  test-e2e               end-to-end acceptance suite (BDD over real HTTP)"
	@echo "  test-perf              k6 load test (tests/perf/load-test.js)"
	@echo "  test-soak              k6 soak test 2h (tests/perf/soak-test.js)"
	@echo "  test-stress            k6 stress test to 150% peak (tests/perf/stress-test.js)"
	@echo "  test-modulith          Spring Modulith boundary verify (@modulith scenarios)"
	@echo "  test-schema-isolation  per-BC PostgreSQL schema isolation (*SchemaIT)"
	@echo ""
	@echo "Database (k8s-local; Flyway Job + seed):"
	@echo "  db-migrate db-seed db-status db-reset"
	@echo ""
	@echo "Image & K8s deploy (Rancher Desktop k3s):"
	@echo "  image-build-api db-up k8s-init k8s-apply k8s-restart-api k8s-delete k8s-clean"
	@echo ""
	@echo "Observe & debug:"
	@echo "  health-check pods logs-api shell-api shell-db k9s"
	@echo ""
	@echo "Port-forward (NodePort alternatives):"
	@echo "  pf-api pf-db pf-redis pf-all pf-stop"
	@echo ""
	@echo "CI gates (wrap the base targets above, docs/CICD.md sec 2/sec 4):"
	@echo "  ci-lint ci-archunit ci-test-unit ci-test-integration ci-test-modulith"
	@echo "  ci-contract-test ci-test-schema-isolation ci-spotbugs ci-semgrep"
	@echo "  ci-dependency-scan ci-perf ci-soak ci-stress ci-dry-run"
	@echo ""
	@echo "CD / release:"
	@echo "  ci-build ci-build-image ci-image-sign ci-deploy cd-bump-image"
	@echo "  cd-verify-ha ci-smoke ci-rollback"

# ── Developer scripts ────────────────────────────────────────────────
dev:                       ## run API with live reload
	$(MVN) -pl $(API) spring-boot:run

build:                     ## compile + package jar
	$(MVN) -q -pl $(API) -am package -DskipTests

start:                     ## run packaged jar
	java -jar $(API)/target/yocat-office-backend-api.jar

type-check:                ## compile-only (no tests)
	$(MVN) -q -pl $(API) -am compile

lint:                      ## static style/analysis (SpotBugs High/Critical = 0)
	$(MVN) -q -pl $(API) spotbugs:check

clean:                     ## mvn clean
	$(MVN) -q clean

package: build             ## alias for build
verify: ci-test-integration ## alias for the integration gate

# ── Local run lifecycle (server-bootstrap: start/stop the bootstrapped API) ──
# DEFAULT-APPLIED: k8s-local up/down (kubectl apply -k) is owned by k8s-apply/k8s-delete;
# here up/down run the bootstrapped jar locally (background, PID-managed) so the server's
# startup is verifiable now. down sends SIGTERM → exercises EDD §3.6.5 graceful shutdown.
up: build                  ## start the API locally in the background (PID → .run/api.pid)
	@mkdir -p .run
	@if [ -f $(PID_FILE) ] && kill -0 $$(cat $(PID_FILE)) 2>/dev/null; then \
		echo "api already running (pid $$(cat $(PID_FILE)))"; \
	else \
		SERVER_PORT=$${SERVER_PORT:-8080} nohup java -jar $(API)/target/yocat-office-backend-api.jar > .run/api.log 2>&1 & echo $$! > $(PID_FILE); \
		echo "api started (pid $$(cat $(PID_FILE))) → http://localhost:$${SERVER_PORT:-8080}/health/live"; \
	fi

down:                      ## stop the locally-running API (graceful SIGTERM)
	@if [ -f $(PID_FILE) ] && kill -0 $$(cat $(PID_FILE)) 2>/dev/null; then \
		kill -TERM $$(cat $(PID_FILE)) && rm -f $(PID_FILE) && echo "api stopped (graceful SIGTERM)"; \
	else \
		echo "api not running"; rm -f $(PID_FILE); \
	fi

# ── Tests (every CI gate delegates to these; no CI-only test logic) ──
test: test-unit            ## unit tests (alias of test-unit)

test-unit:                 ## unit tests only (Surefire, *Test.java)
	$(MVN) -q -pl $(API) -am test

test-integration:          ## all integration tests (Failsafe, *IT.java, Testcontainers)
	$(MVN) -q -pl $(API) $(IT_FLAGS) verify

test-contract:             ## Pact consumer-driven contracts (*PactTest, EDD §4.6.1)
	$(MVN) -q -pl $(API) test -Dtest='*PactTest'

test-bdd:                  ## Cucumber BDD acceptance suite (RunBddContractIT, Testcontainers)
	$(MVN) -q -pl $(API) $(IT_FLAGS) -Dit.test=RunBddContractIT failsafe:integration-test

test-e2e:                  ## end-to-end acceptance (BDD driven over real HTTP; api-only E2E)
	$(MVN) -q -pl $(API) $(IT_FLAGS) -Dit.test=RunBddContractIT failsafe:integration-test

test-perf:                 ## k6 load test (P99 ≤ 1000ms @ 500 PCU; test-plan §3.4/§10)
	@if command -v k6 >/dev/null 2>&1 && [ -f tests/perf/load-test.js ]; then \
		k6 run tests/perf/load-test.js; \
	else \
		echo "k6 or tests/perf/load-test.js not available — skipping (guarded, see tests/perf/README.md)"; \
	fi

test-soak:                 ## k6 soak test — 2h @ ~375 PCU; mem-leak / HikariCP / Outbox backlog (test-plan §3.4 Scenario 4)
	@if command -v k6 >/dev/null 2>&1 && [ -f tests/perf/soak-test.js ]; then \
		k6 run tests/perf/soak-test.js; \
	else \
		echo "k6 or tests/perf/soak-test.js not available — skipping (guarded, see tests/perf/README.md)"; \
	fi

test-stress:               ## k6 stress test — ramp to 150% peak (750 PCU); degradation + recovery (test-plan §3.4 Scenario 3)
	@if command -v k6 >/dev/null 2>&1 && [ -f tests/perf/stress-test.js ]; then \
		k6 run tests/perf/stress-test.js; \
	else \
		echo "k6 or tests/perf/stress-test.js not available — skipping (guarded, see tests/perf/README.md)"; \
	fi

test-modulith:             ## Spring Modulith boundary verify — HC-1~HC-5 (@modulith scenarios)
	$(MVN) -q -pl $(API) $(IT_FLAGS) -Dit.test=RunBddContractIT \
	  -Dcucumber.filter.tags='@modulith' failsafe:integration-test

test-schema-isolation:     ## per-BC PostgreSQL schema isolation (*SchemaIT, Testcontainers)
	$(MVN) -q -pl $(API) $(IT_FLAGS) -Dit.test='*SchemaIT' failsafe:integration-test

# ── CI targets — wrappers over the base targets (docs/CICD.md §4.1) ──
ci-lint: lint              ## CI: style/lint gate

ci-archunit:               ## CI: Clean-Arch dependency rule — Domain ⊬ spring/java.sql (EDD §3.1b, @architecture)
	$(MVN) -q -pl $(API) $(IT_FLAGS) -Dit.test=RunBddContractIT \
	  -Dcucumber.filter.tags='@architecture' failsafe:integration-test

ci-test-unit:              ## CI: unit tests + unit JaCoCo report + core-txn 100% gate (test phase)
	$(MVN) -q -pl $(API) -am test                 # report-unit + coverage-check-core-unit bound to test phase
	@echo "✅ Unit tests + unit JaCoCo report + core-txn 100% gate passed (bundle Line≥80/Branch≥70 enforced at verify: make ci-test-integration)"

ci-test-integration: test-integration   ## CI: REST-assured + Testcontainers integration + merged JaCoCo gate (Line≥80/Branch≥70, verify phase)

ci-test-modulith: test-modulith         ## CI: ApplicationModules.verify() (HC-1~HC-5, EDD §4.3)

ci-contract-test: test-contract         ## CI: Pact consumer contracts (EDD §4.6.1)

ci-test-schema-isolation: test-schema-isolation  ## CI: per-BC schema isolation

ci-spotbugs:               ## CI: SpotBugs SAST (High/Critical = 0)
	$(MVN) -q -pl $(API) spotbugs:check

ci-semgrep:                ## CI: Semgrep SAST (external CLI; High/Critical = 0)
	semgrep --config auto --error apps/

ci-dependency-scan:        ## CI: OWASP dependency-check (CVE scan)
	$(MVN) -q -pl $(API) org.owasp:dependency-check-maven:check

ci-perf: test-perf         ## CI: k6 load test (test-plan §5)

ci-soak: test-soak         ## CI: k6 soak test — nightly weekly profile (test-plan §3.4 Scenario 4)

ci-stress: test-stress     ## CI: k6 stress test — nightly weekly release-gate profile (test-plan §3.4 Scenario 3)

ci-dry-run:                ## CI: run .github/workflows locally via act
	act -W .github/workflows -j build

# ── K8s-local deployment (Rancher Desktop k3s; LOCAL_DEPLOY §4/§6/§16) ──────────
# Infra runtime = k8s-local. Image via nerdctl into the k3s containerd namespace k8s.io.
image-build-api:           ## build API image into k3s containerd (k8s.io namespace)
	nerdctl --namespace k8s.io build -t $(IMAGE_API) -f $(DOCKERFILE) .

db-up:                     ## ensure the existing PostgreSQL (namespace yocat, postgres.yaml)
	kubectl apply -f ../docs/req/postgres.yaml

k8s-init:                  ## create app namespace + Secret (from secrets.env) + Redis
	@test -f $(K8S_OVERLAY)/secrets.env || { echo "run ./scripts/bootstrap-secrets.sh first"; exit 1; }
	kubectl create namespace $(APP_NS) --dry-run=client -o yaml | kubectl apply -f -
	kubectl create secret generic yocat-office-backend-api-secret \
	  --from-env-file=$(K8S_OVERLAY)/secrets.env -n $(APP_NS) \
	  --dry-run=client -o yaml | kubectl apply -f -

k8s-apply:                 ## deploy Redis + API (≥2 replicas) + Service + Ingress + Flyway Job
	kubectl apply -k $(K8S_OVERLAY)/

k8s-restart-api:           ## rolling restart API after a new image build
	kubectl rollout restart deploy/yocat-office-backend-api -n $(APP_NS)
	kubectl rollout status deploy/yocat-office-backend-api -n $(APP_NS) --timeout=120s

k8s-delete:                ## remove app resources (keeps existing PostgreSQL / PVC)
	kubectl delete -k $(K8S_OVERLAY)/

k8s-clean:                 ## delete the whole app namespace (does not touch yocat DB)
	kubectl delete namespace $(APP_NS)

# ── Database lifecycle (Flyway migration Job + seed; EDD §13.3) ───────
db-migrate:                ## run Flyway migration as a pre-deploy Job (migrations/ → ConfigMap)
	kubectl create configmap flyway-migrations --from-file=$(MIGRATIONS) -n $(APP_NS) \
	  --dry-run=client -o yaml | kubectl apply -f -
	kubectl delete job yocat-office-backend-flyway-migrate -n $(APP_NS) --ignore-not-found
	kubectl apply -k $(K8S_OVERLAY)/
	kubectl wait --for=condition=complete job/yocat-office-backend-flyway-migrate -n $(APP_NS) --timeout=180s

db-seed:                   ## load dev/CI seed data (idempotent; scripts/db-seed.sql)
	kubectl exec -i -n $(DB_NS) deploy/yocat-pg -- psql -U yocat -d yocat -v yocat.seed_env=local < scripts/db-seed.sql

db-status:                 ## show applied migrations
	kubectl exec -n $(DB_NS) deploy/yocat-pg -- psql -U yocat -d yocat \
	  -c "SELECT version, description, success FROM platform.schema_migrations ORDER BY installed_rank;"

db-reset:                  ## drop + recreate all BC schemas, then re-migrate + seed (DESTROYS local data)
	kubectl exec -n $(DB_NS) deploy/yocat-pg -- psql -U yocat -d yocat -c \
	  "DROP SCHEMA IF EXISTS member,wallet,deposit,entitlement,record,leaderboard,game_save,modulith,platform CASCADE;"
	$(MAKE) db-migrate db-seed

# ── Observe & debug ──────────────────────────────────────────────────
health-check:              ## curl /health/ready via Ingress + list app pods
	curl -s http://yocat-office-backend.local/health/ready || true
	kubectl get pods -n $(APP_NS)

pods:       ## list app pods
	kubectl get pods -n $(APP_NS)
logs-api:   ## tail all API replicas
	kubectl logs -f -l app=yocat-office-backend-api -n $(APP_NS) --max-log-requests=2
shell-api:  ## shell into an API pod
	kubectl exec -it deploy/yocat-office-backend-api -n $(APP_NS) -- sh
shell-db:   ## psql into the existing yocat-pg
	kubectl exec -it -n $(DB_NS) deploy/yocat-pg -- psql -U yocat -d yocat
k9s:        ## k9s on the app namespace
	k9s -n $(APP_NS)

# ── port-forward shortcuts (NodePort alternatives; LOCAL_DEPLOY §5/§12) ─
pf-api:   ; kubectl port-forward -n $(APP_NS) deploy/yocat-office-backend-api 8080:8080
pf-db:    ; kubectl port-forward -n $(DB_NS) deploy/yocat-pg 5432:5432
pf-redis: ; kubectl port-forward -n $(APP_NS) deploy/redis 6379:6379
pf-all:   ; $(MAKE) -j3 pf-api pf-db pf-redis
pf-stop:  ; @pkill -f "kubectl port-forward" 2>/dev/null || echo "no port-forward running"

# ── CI/CD release targets (CI == local; docs/CICD.md §4.2) ────────────
ci-build:       ## CI: clean package the reactor
	$(MVN) -q -pl $(API) -am clean package -DskipTests
ci-build-image: image-build-api   ## CI: build API image (nerdctl k8s.io)
ci-image-sign:  ## CI: cosign keyless sign the pushed image
	cosign sign --yes $(REGISTRY)/api:$(IMAGE_TAG)
ci-deploy:      k8s-apply          ## CI: deploy to k8s-local
cd-bump-image:  ## CD: kustomize edit set image for the target ENV overlay
	cd k8s/overlays/$(ENV) && kustomize edit set image \
	  $(REGISTRY)/api=$(REGISTRY)/api:$(IMAGE_TAG)
cd-verify-ha:   ## CD: rollout status + assert ≥2 running replicas (EDD §3.7)
	kubectl rollout status deploy/$(PROJECT_SLUG)-api -n $(NS_$(ENV)) --timeout=300s
	@n=$$(kubectl get pods -l app=$(PROJECT_SLUG)-api -n $(NS_$(ENV)) | grep -c Running); \
	 echo "running replicas = $$n"; test $$n -ge 2
ci-smoke:       ## CI: smoke test the deployed API (ENV-aware)
	bash ../docs/smoke-test.sh http://yocat-office-backend.local
ci-rollback:    ## CI: roll back the API deployment
	kubectl rollout undo deploy/yocat-office-backend-api -n $(APP_NS)
