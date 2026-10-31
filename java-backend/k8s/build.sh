#!/usr/bin/env bash
# k8s/build.sh — build the API image into the Rancher Desktop k3s containerd (k8s.io namespace).
# Rancher Desktop uses containerd, so the image MUST land in the k8s.io namespace or k3s cannot see it.
# Equivalent make target: `make image-build-api`. (LOCAL_DEPLOY §4.4)
set -euo pipefail
cd "$(dirname "$0")/.."            # repo outputDir root (build context = reactor root)

IMAGE="${IMAGE_API:-yocat-office-backend/api:local}"
DOCKERFILE="docker/api/Dockerfile"

echo "[build] nerdctl --namespace k8s.io build -t ${IMAGE} -f ${DOCKERFILE} ."
nerdctl --namespace k8s.io build -t "${IMAGE}" -f "${DOCKERFILE}" .
echo "[build] done; verify:"
nerdctl --namespace k8s.io images | grep yocat-office-backend || true
