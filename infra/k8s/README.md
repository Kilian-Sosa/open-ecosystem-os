# Kubernetes

This folder contains the initial Kubernetes manifests for Open Ecosystem OS.

## Layout

```txt
infra/k8s/base
infra/k8s/overlays/dev
infra/k8s/overlays/prod
```

The base contains the app namespace, web/API/worker workloads, shared ConfigMap,
Secret example, Services, and MVP Ingress. The base does not include a real
Secret and does not deploy data services by itself.

The dev overlay adds local-only fake Secrets and single-node PostgreSQL, Redis,
RabbitMQ, MinIO, and Meilisearch StatefulSets for kind/minikube.

The prod overlay is intentionally app-only. Create production Secrets and data
service endpoints outside these manifests before applying it.

## Validate manifests

```bash
kubectl kustomize infra/k8s/base
kubectl kustomize infra/k8s/overlays/dev
kubectl kustomize infra/k8s/overlays/prod
make k8s-validate
```

`make k8s-validate` runs stricter schema checks with local `kubeconform` when
installed. If it is not installed but Docker is available, it runs
`ghcr.io/yannh/kubeconform:latest`. If neither is available, it still verifies
that all Kustomize overlays render.

## Before applying

`kubectl apply` requires an active Kubernetes cluster context. If kubectl tries
to reach `http://localhost:8080/openapi/v2`, no cluster context is configured.

Check the active context:

```bash
kubectl config current-context
kubectl cluster-info
```

For minikube, create/select the context first:

```bash
minikube start
kubectl config use-context minikube
kubectl cluster-info
```

For kind, create the cluster first:

```bash
kind create cluster --name open-ecosystem-os --config infra/k8s/overlays/dev/kind-cluster.yaml
kubectl config use-context kind-open-ecosystem-os
kubectl cluster-info
```

## Build local images

Build the web image with an empty same-origin API base. The frontend API helpers
already call `/api/...`, and the Ingress routes `/api` to the backend.

```bash
docker build -t open-ecosystem-web:dev --build-arg NEXT_PUBLIC_API_BASE_URL= apps/web
docker build -t open-ecosystem-api:dev apps/api
docker build -t open-ecosystem-worker:dev apps/worker
```

## Deploy to minikube

```bash
minikube start
minikube addons enable ingress
kubectl -n ingress-nginx rollout status deployment/ingress-nginx-controller
minikube docker-env --shell powershell | Invoke-Expression
docker build -t open-ecosystem-web:dev --build-arg NEXT_PUBLIC_API_BASE_URL= apps/web
docker build -t open-ecosystem-api:dev apps/api
docker build -t open-ecosystem-worker:dev apps/worker
kubectl apply -k infra/k8s/overlays/dev
kubectl -n open-ecosystem-data rollout status statefulset/postgres
kubectl -n open-ecosystem-data rollout status statefulset/rabbitmq
kubectl -n open-ecosystem-os rollout status deployment/open-ecosystem-api
kubectl -n open-ecosystem-os rollout status deployment/open-ecosystem-worker
kubectl -n open-ecosystem-os rollout status deployment/open-ecosystem-web
```

Add the minikube IP to your hosts file:

```powershell
$ip = minikube ip
Add-Content -Path "$env:SystemRoot\System32\drivers\etc\hosts" -Value "$ip open-ecosystem.local"
```

On Windows with the Docker driver, minikube may route Ingress through a tunnel
instead. If the addon output says Ingress is available at `127.0.0.1`, keep this
running in another terminal:

```bash
minikube tunnel
```

Then use this hosts entry:

```txt
127.0.0.1 open-ecosystem.local
```

Then open:

```txt
http://open-ecosystem.local
```

If the app pods show `ImagePullBackOff`, the local images were not built inside
minikube's Docker daemon. Re-run the image build commands after
`minikube docker-env --shell powershell | Invoke-Expression`, then restart the
app deployments:

```bash
kubectl -n open-ecosystem-os rollout restart deployment/open-ecosystem-web deployment/open-ecosystem-api deployment/open-ecosystem-worker
```

If an older local web image was accidentally built with
`NEXT_PUBLIC_API_BASE_URL=/api`, browser requests may call `/api/api/...`.
The dev overlay includes a temporary compatibility Ingress that rewrites those
stale local requests back to `/api/...`. Rebuild the web image with an empty
`NEXT_PUBLIC_API_BASE_URL` when refreshing local images.

## Deploy to kind

```bash
kind create cluster --name open-ecosystem-os --config infra/k8s/overlays/dev/kind-cluster.yaml
docker build -t open-ecosystem-web:dev --build-arg NEXT_PUBLIC_API_BASE_URL=/api apps/web
docker build -t open-ecosystem-api:dev apps/api
docker build -t open-ecosystem-worker:dev apps/worker
kind load docker-image open-ecosystem-web:dev --name open-ecosystem-os
kind load docker-image open-ecosystem-api:dev --name open-ecosystem-os
kind load docker-image open-ecosystem-worker:dev --name open-ecosystem-os
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl -n ingress-nginx rollout status deployment/ingress-nginx-controller
kubectl apply -k infra/k8s/overlays/dev
```

Add this hosts entry:

```txt
127.0.0.1 open-ecosystem.local
```

## Secrets

Do not apply `infra/k8s/base/secret.example.yaml` directly.

The dev overlay commits fake local-only values so kind/minikube can run without
manual setup. Do not reuse those values in production.

For production, create a real `open-ecosystem-secrets` Secret in
`open-ecosystem-os` before applying the prod overlay:

```bash
kubectl -n open-ecosystem-os create secret generic open-ecosystem-secrets \
  --from-literal=SPRING_DATASOURCE_URL='jdbc:postgresql://CHANGE_ME:5432/openecosystem' \
  --from-literal=SPRING_DATASOURCE_USERNAME='CHANGE_ME' \
  --from-literal=SPRING_DATASOURCE_PASSWORD='CHANGE_ME' \
  --from-literal=RABBITMQ_USERNAME='CHANGE_ME' \
  --from-literal=RABBITMQ_PASSWORD='CHANGE_ME' \
  --from-literal=S3_ACCESS_KEY='CHANGE_ME' \
  --from-literal=S3_SECRET_KEY='CHANGE_ME' \
  --from-literal=DRIVE_ENCRYPTION_KEY_ID='CHANGE_ME' \
  --from-literal=DRIVE_ENCRYPTION_KEY_BASE64='CHANGE_ME_32_BYTE_AES_KEY_BASE64' \
  --from-literal=MEILISEARCH_MASTER_KEY='CHANGE_ME'
```

## Networking

Ingress is the MVP public entry point, matching ADR 0005. Gateway API manifests
should be added later only after the target local/self-hosted environment is
ready for Gateway API CRDs and controllers.
