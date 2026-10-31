# docker/api/Dockerfile — yocat-office-backend API (Spring Modulith Modular Monolith, 7 BC)
# Source of truth: LOCAL_DEPLOY §4.4 + EDD §3.5 (Base Image eclipse-temurin:21-jre-alpine,
# multi-module Maven reactor build of apps/api). Multi-stage: Maven build → jre-alpine runtime.
#
# Build (Rancher Desktop containerd):
#   nerdctl --namespace k8s.io build -t yocat-office-backend/api:local -f docker/api/Dockerfile .
# The build context MUST be the repo root (.) so the full multi-module reactor is available.

# ── Stage 1: build (full JDK + Maven) ────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Warm the dependency cache first (reactor poms only) for faster incremental rebuilds.
COPY pom.xml .
COPY apps/api/pom.xml apps/api/pom.xml
RUN mvn -q -pl apps/api -am dependency:go-offline
# Then the sources; package the executable boot jar (tests run in CI, not in the image build).
COPY . .
RUN mvn -q -pl apps/api -am clean package -DskipTests

# ── Stage 2: runtime (JRE-only, non-root) ────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# busybox wget (alpine) backs the HEALTHCHECK; no extra package needed.
RUN addgroup -S app && adduser -S app -G app
# Spring Boot repackage produces a single runnable fat jar under apps/api/target/.
COPY --from=build /src/apps/api/target/yocat-office-backend-api.jar /app/app.jar
USER app
EXPOSE 8080
# Container-level liveness mirror of the K8s livenessProbe (/health/live, EDD §3.6.2).
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/health/live || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
