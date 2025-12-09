.PHONY: help dev-up dev-down dev-logs build-all build-% deploy-local deploy-eks clean

# Default target
help:
	@echo "Wealist Project - Available commands:"
	@echo ""
	@echo "  Development:"
	@echo "    make dev-up          - Start all services with Docker Compose"
	@echo "    make dev-down        - Stop all services"
	@echo "    make dev-logs        - View logs from all services"
	@echo "    make dev-restart     - Restart all services"
	@echo "    make dev-build       - Build all services"
	@echo ""
	@echo "  Build:"
	@echo "    make build-all       - Build all service images"
	@echo "    make build-<service> - Build specific service (e.g., build-user-service)"
	@echo ""
	@echo "  Kubernetes (Local):"
	@echo "    make k8s-apply-local    - Apply all k8s manifests (local)"
	@echo "    make k8s-delete-local   - Delete all k8s resources (local)"
	@echo "    make kustomize-<svc>    - Preview kustomize output for service"
	@echo ""
	@echo "  Kubernetes (EKS):"
	@echo "    make k8s-apply-eks      - Apply all k8s manifests (EKS)"
	@echo "    make k8s-delete-eks     - Delete all k8s resources (EKS)"
	@echo ""
	@echo "  Utility:"
	@echo "    make clean           - Clean build artifacts and volumes"
	@echo "    make test-health     - Test health endpoints"
	@echo "    make status          - Show status of containers and pods"
	@echo "    make monitoring      - Start monitoring stack"

# =============================================================================
# Development (Docker Compose)
# =============================================================================

dev-up:
	./docker/scripts/dev.sh up

dev-down:
	./docker/scripts/dev.sh down

dev-logs:
	./docker/scripts/dev.sh logs

dev-restart:
	./docker/scripts/dev.sh restart

dev-build:
	./docker/scripts/dev.sh build

# =============================================================================
# Build Docker Images
# =============================================================================

SERVICES := user-service auth-service board-service chat-service noti-service storage-service video-service frontend

build-all: $(addprefix build-,$(SERVICES))

build-user-service:
	docker build -t user-service:local -f services/user-service/docker/Dockerfile services/user-service

build-auth-service:
	docker build -t auth-service:local -f services/auth-service/Dockerfile services/auth-service

build-board-service:
	docker build -t board-service:local -f services/board-service/docker/Dockerfile services/board-service

build-chat-service:
	docker build -t chat-service:local -f services/chat-service/docker/Dockerfile services/chat-service

build-noti-service:
	docker build -t noti-service:local -f services/noti-service/docker/Dockerfile services/noti-service

build-storage-service:
	docker build -t storage-service:local -f services/storage-service/docker/Dockerfile services/storage-service

build-video-service:
	docker build -t video-service:local -f services/video-service/docker/Dockerfile services/video-service

build-frontend:
	docker build -t frontend:local -f services/frontend/Dockerfile services/frontend

# =============================================================================
# Kubernetes - Local (Kustomize)
# =============================================================================

k8s-apply-local:
	kubectl apply -k infrastructure/overlays/local
	kubectl apply -k services/user-service/k8s/overlays/local
	kubectl apply -k services/auth-service/k8s/overlays/local
	kubectl apply -k services/board-service/k8s/overlays/local
	kubectl apply -k services/chat-service/k8s/overlays/local
	kubectl apply -k services/noti-service/k8s/overlays/local
	kubectl apply -k services/storage-service/k8s/overlays/local
	kubectl apply -k services/video-service/k8s/overlays/local
	kubectl apply -k services/frontend/k8s/overlays/local

k8s-delete-local:
	kubectl delete -k services/frontend/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/video-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/storage-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/noti-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/chat-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/board-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/auth-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/user-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k infrastructure/overlays/local --ignore-not-found

# Preview kustomize output
kustomize-infra:
	kubectl kustomize infrastructure/overlays/local

kustomize-user-service:
	kubectl kustomize services/user-service/k8s/overlays/local

kustomize-auth-service:
	kubectl kustomize services/auth-service/k8s/overlays/local

kustomize-board-service:
	kubectl kustomize services/board-service/k8s/overlays/local

kustomize-chat-service:
	kubectl kustomize services/chat-service/k8s/overlays/local

kustomize-noti-service:
	kubectl kustomize services/noti-service/k8s/overlays/local

kustomize-storage-service:
	kubectl kustomize services/storage-service/k8s/overlays/local

kustomize-video-service:
	kubectl kustomize services/video-service/k8s/overlays/local

kustomize-frontend:
	kubectl kustomize services/frontend/k8s/overlays/local

# =============================================================================
# Kubernetes - EKS
# =============================================================================

k8s-apply-eks:
	kubectl apply -k infrastructure/overlays/eks
	kubectl apply -k services/user-service/k8s/overlays/eks
	kubectl apply -k services/auth-service/k8s/overlays/eks
	kubectl apply -k services/board-service/k8s/overlays/eks
	kubectl apply -k services/chat-service/k8s/overlays/eks
	kubectl apply -k services/noti-service/k8s/overlays/eks
	kubectl apply -k services/storage-service/k8s/overlays/eks
	kubectl apply -k services/video-service/k8s/overlays/eks
	kubectl apply -k services/frontend/k8s/overlays/eks

k8s-delete-eks:
	kubectl delete -k services/frontend/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/video-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/storage-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/noti-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/chat-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/board-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/auth-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/user-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k infrastructure/overlays/eks --ignore-not-found

# =============================================================================
# Utility
# =============================================================================

clean:
	./docker/scripts/clean.sh

test-health:
	./docker/scripts/test-health.sh

monitoring:
	./docker/scripts/monitoring.sh

# Status check
status:
	@echo "=== Docker Containers ==="
	docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
	@echo ""
	@echo "=== Kubernetes Pods (wealist-dev) ==="
	kubectl get pods -n wealist-dev 2>/dev/null || echo "Namespace not found"
