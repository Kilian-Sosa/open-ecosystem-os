PNPM ?= corepack pnpm
COMPOSE_BASE = docker compose -f infra/docker/docker-compose.yml
COMPOSE_OBS = docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.observability.yml

ifeq ($(OS),Windows_NT)
ENSURE_ENV = powershell -NoProfile -ExecutionPolicy Bypass -Command "if (-not (Test-Path -LiteralPath '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env'; Write-Host 'Created .env from .env.example' }"
else
ENSURE_ENV = if [ ! -f .env ]; then cp .env.example .env && echo "Created .env from .env.example"; fi
endif

.PHONY: install format format-check lint typecheck test test-unit test-integration test-e2e build docker-up docker-down docker-logs smoke security-scan k8s-validate ci-local up down logs ps obs-up obs-down seed reset ensure-env

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

docker-down:
	$(COMPOSE_OBS) down --remove-orphans

docker-logs:
	$(COMPOSE_BASE) logs -f

smoke:
	$(COMPOSE_BASE) config
	kubectl kustomize infra/k8s/base >/dev/null

security-scan:
	@if command -v trivy >/dev/null 2>&1; then trivy fs --severity HIGH,CRITICAL --exit-code 1 .; else echo "trivy not installed; security scan deferred."; fi

k8s-validate:
	@if command -v kubeconform >/dev/null 2>&1; then kubectl kustomize infra/k8s/base | kubeconform -strict -summary; else kubectl kustomize infra/k8s/base >/dev/null && echo "kubeconform not installed; rendered Kubernetes manifests only."; fi

ci-local: format-check lint typecheck test-unit build smoke k8s-validate

up: docker-up

down: docker-down

logs: docker-logs

ps:
	$(COMPOSE_BASE) ps

obs-up: ensure-env
	$(COMPOSE_OBS) --env-file .env up -d --build

obs-down:
	$(COMPOSE_OBS) down --remove-orphans

seed:
	./scripts/seed-demo-data.sh

reset:
	./scripts/reset-demo-data.sh
