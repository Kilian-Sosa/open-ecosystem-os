#!/usr/bin/env bash
set -euo pipefail

targets=(
  "infra/k8s/base"
  "infra/k8s/overlays/dev"
  "infra/k8s/overlays/prod"
)

kubeconform_image="${KUBECONFORM_IMAGE:-ghcr.io/yannh/kubeconform:latest}"

if command -v kubeconform >/dev/null 2>&1; then
  for target in "${targets[@]}"; do
    echo "Validating ${target} with local kubeconform"
    kubectl kustomize "${target}" | kubeconform -strict -summary
  done
elif command -v docker >/dev/null 2>&1; then
  for target in "${targets[@]}"; do
    echo "Validating ${target} with Dockerized kubeconform"
    kubectl kustomize "${target}" | docker run --rm -i "${kubeconform_image}" -strict -summary
  done
else
  for target in "${targets[@]}"; do
    echo "kubeconform and docker not installed; rendering ${target} only."
    kubectl kustomize "${target}" >/dev/null
  done
fi
