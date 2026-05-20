# Kubernetes

This folder contains the initial Kubernetes scaffold.

## MVP strategy

Start with Docker Compose. Move to Kubernetes after the first vertical slice works locally.

## Apply base manifests

```bash
kubectl apply -k infra/k8s/base
```

Before applying, create a real `open-ecosystem-secrets` Secret. Do not commit real secrets.

## Future improvements

- Kustomize overlays for dev/prod
- Helm chart
- NetworkPolicies
- External Secrets
- cert-manager
- Gateway API instead of Ingress
- HPA for API/workers
- RabbitMQ/Redis/PostgreSQL operators or external managed services
