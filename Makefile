PNPM ?= corepack pnpm
COMPOSE_BASE = docker compose -f infra/docker/docker-compose.yml
COMPOSE_OBS = docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.observability.yml

ifeq ($(OS),Windows_NT)
NULL_DEVICE = NUL
ENSURE_ENV = powershell -NoProfile -ExecutionPolicy Bypass -Command "if (-not (Test-Path -LiteralPath '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env'; Write-Host 'Created .env from .env.example' }"
K8S_VALIDATE = powershell -NoProfile -ExecutionPolicy Bypass -Command "$$paths = @('infra/k8s/base','infra/k8s/overlays/dev','infra/k8s/overlays/prod'); $$kubeconform = Get-Command kubeconform -ErrorAction SilentlyContinue; foreach ($$path in $$paths) { if ($$kubeconform) { kubectl kustomize $$path | kubeconform -strict -summary } else { kubectl kustomize $$path > $$null } }; if (-not $$kubeconform) { Write-Host 'kubeconform not installed; rendered base, dev, and prod Kubernetes manifests only.' }"
SEED_DEMO = powershell -NoProfile -ExecutionPolicy Bypass -File scripts/seed-demo-data.ps1
RESET_DEMO = powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-demo-data.ps1
else
NULL_DEVICE = /dev/null
ENSURE_ENV = if [ ! -f .env ]; then cp .env.example .env && echo "Created .env from .env.example"; fi
K8S_VALIDATE = if command -v kubeconform >$(NULL_DEVICE) 2>&1; then for overlay in infra/k8s/base infra/k8s/overlays/dev infra/k8s/overlays/prod; do kubectl kustomize $$overlay | kubeconform -strict -summary; done; else kubectl kustomize infra/k8s/base >$(NULL_DEVICE) && kubectl kustomize infra/k8s/overlays/dev >$(NULL_DEVICE) && kubectl kustomize infra/k8s/overlays/prod >$(NULL_DEVICE) && echo "kubeconform not installed; rendered base, dev, and prod Kubernetes manifests only."; fi
SEED_DEMO = ./scripts/seed-demo-data.sh
RESET_DEMO = ./scripts/reset-demo-data.sh
endif

.PHONY: install format format-check lint typecheck test test-unit test-integration test-e2e build docker-up docker-watch docker-watch-web docker-watch-api docker-watch-worker docker-down docker-logs smoke security-scan k8s-validate ci-local up watch down logs ps obs-up obs-watch obs-down seed reset ensure-env

ensure-env:
	@$(ENSURE_ENV)

install: ensure-env
	cd apps/web && $(PNPM) install

format:
	cd apps/web && $(PNPM) format

format-check:
	cd apps/web && $(PNPM) format:check

lint:
	cd apps/web && $(PNPM) lint

typecheck:
	cd apps/web && $(PNPM) typecheck

test: test-unit

test-unit:
	cd apps/web && $(PNPM) test
	cd apps/api && ./mvnw test
	cd apps/worker && ./mvnw test

test-integration:
	@echo "Integration tests are deferred until Testcontainers-backed flows exist."

test-e2e:
	@echo "E2E tests are deferred until the first vertical slice is runnable in Compose."

build:
	cd apps/web && $(PNPM) build
	cd apps/api && ./mvnw -DskipTests package
	cd apps/worker && ./mvnw -DskipTests package

docker-up: ensure-env
	$(COMPOSE_BASE) --env-file .env up -d --build

docker-watch: ensure-env
	$(COMPOSE_BASE) --env-file .env up --build --watch

docker-watch-web: ensure-env
	$(COMPOSE_BASE) --env-file .env up --build --watch web

docker-watch-api: ensure-env
	$(COMPOSE_BASE) --env-file .env up --build --watch api

docker-watch-worker: ensure-env
	$(COMPOSE_BASE) --env-file .env up --build --watch worker

docker-down:
	$(COMPOSE_OBS) down --remove-orphans

docker-logs:
	$(COMPOSE_BASE) logs -f

smoke:
	$(COMPOSE_BASE) config
	kubectl kustomize infra/k8s/base >$(NULL_DEVICE)
	kubectl kustomize infra/k8s/overlays/dev >$(NULL_DEVICE)
	kubectl kustomize infra/k8s/overlays/prod >$(NULL_DEVICE)

security-scan:
	@if command -v trivy >/dev/null 2>&1; then trivy fs --severity HIGH,CRITICAL --exit-code 1 .; else echo "trivy not installed; security scan deferred."; fi

k8s-validate:
	@$(K8S_VALIDATE)

ci-local: format-check lint typecheck test-unit build smoke k8s-validate

up: docker-up

watch: docker-watch

down: docker-down

logs: docker-logs

ps:
	$(COMPOSE_BASE) ps

obs-up: ensure-env
	$(COMPOSE_OBS) --env-file .env up -d --build

obs-watch: ensure-env
	$(COMPOSE_OBS) --env-file .env up --build --watch

obs-down:
	$(COMPOSE_OBS) down --remove-orphans

seed:
	$(SEED_DEMO)

reset:
	$(RESET_DEMO)
