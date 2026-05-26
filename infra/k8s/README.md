# Kubernetes

This folder contains the initial Kubernetes scaffold.

## MVP strategy

Start with Docker Compose. Move to Kubernetes after the first vertical slice works locally.

## Apply base manifests

```bash
kubectl apply -k infra/k8s/base
```

Before applying, create a real `open-ecosystem-secrets` Secret. Do not commit real secrets.

## Validate manifests

```bash
make k8s-validate
```

The validation target renders `base`, `overlays/dev`, and `overlays/prod`. It uses local `kubeconform` when installed, or Dockerized `kubeconform` when Docker is available.

## Apply the dev overlay

```bash
kubectl apply -k infra/k8s/overlays/dev
```

The dev overlay includes fake local-only Secret values for runnable local clusters. Do not reuse those values outside local development.

## Future improvements

- Helm chart
- NetworkPolicies
- External Secrets
- cert-manager
- Gateway API instead of Ingress
- HPA for API/workers
- RabbitMQ/Redis/PostgreSQL operators or external managed services
