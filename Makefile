.PHONY: help dev-up dev-down dev-logs build-all build-% deploy-local deploy-eks clean

# Kind cluster name (default: wealist)
KIND_CLUSTER ?= wealist-project


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
	@echo "  Kind (Local Kubernetes):"
	@echo "    make kind-create        - Create kind cluster with Ingress support"
	@echo "    make kind-delete        - Delete kind cluster"
	@echo "    make kind-load-all      - Load all images to kind cluster"
	@echo "    make kind-load-<svc>    - Load specific service image to kind"
	@echo "    make ingress-install    - Install nginx-ingress controller"
	@echo "    make ingress-status     - Check ingress controller status"
	@echo ""
	@echo "  Kubernetes (Local/Kind):"
	@echo "    make k8s-apply-local    - Load images & apply k8s manifests (local)"
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
# Kind (Local Kubernetes Cluster)
# =============================================================================

# KIND_CONFIG: kind-config (default), kind-config-single, kind-config-ha
KIND_CONFIG ?= kind-config

kind-create:
	kind create cluster --config infrastructure/kind/$(KIND_CONFIG).yaml
	@echo "Kind cluster '$(KIND_CLUSTER)' created successfully"
	@echo ""
	@echo "Next steps:"
	@echo "  1. make ingress-install  # Install nginx-ingress controller"
	@echo "  2. make k8s-apply-local  # Deploy services"
	@echo ""
	@echo "SSH 터널링: ssh -L 8080:localhost:80 ..."
	@echo "브라우저: http://localhost:8080"

# 노드 구성 옵션:
#   make kind-create                          # 기본 (1 master + 2 workers)
#   make kind-create KIND_CONFIG=kind-config-single  # 싱글 노드
#   make kind-create KIND_CONFIG=kind-config-ha      # HA (3 masters + 3 workers)

kind-delete:
	kind delete cluster --name $(KIND_CLUSTER)

# Install nginx-ingress controller for Kind
ingress-install:
	kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
	@echo "Patching ingress-nginx to run on control-plane node..."
	kubectl patch deployment ingress-nginx-controller -n ingress-nginx --patch-file infrastructure/kind/ingress-nginx-patch.yaml
	@echo "Waiting for ingress-nginx controller to be ready..."
	kubectl wait --namespace ingress-nginx \
		--for=condition=ready pod \
		--selector=app.kubernetes.io/component=controller \
		--timeout=120s
	@echo "nginx-ingress controller installed successfully on control-plane node!"

ingress-status:
	@echo "=== Ingress Controller ==="
	kubectl get pods -n ingress-nginx
	@echo ""
	@echo "=== Ingress Resources ==="
	kubectl get ingress -n wealist-app 2>/dev/null || echo "No ingress in wealist-app"

kind-load-all: $(addprefix kind-load-,$(SERVICES))
	@echo "All images loaded to kind cluster '$(KIND_CLUSTER)'"

kind-load-user-service:
	kind load docker-image user-service:local --name $(KIND_CLUSTER)

kind-load-auth-service:
	kind load docker-image auth-service:local --name $(KIND_CLUSTER)

kind-load-board-service:
	kind load docker-image board-service:local --name $(KIND_CLUSTER)

kind-load-chat-service:
	kind load docker-image chat-service:local --name $(KIND_CLUSTER)

kind-load-noti-service:
	kind load docker-image noti-service:local --name $(KIND_CLUSTER)

kind-load-storage-service:
	kind load docker-image storage-service:local --name $(KIND_CLUSTER)

kind-load-video-service:
	kind load docker-image video-service:local --name $(KIND_CLUSTER)

kind-load-frontend:
	kind load docker-image frontend:local --name $(KIND_CLUSTER)

# =============================================================================
# Kubernetes - Local (Kustomize + Kind)
# =============================================================================

k8s-apply-local: kind-load-all
	kubectl apply -f services/namespace.yaml

	kubectl apply -k infrastructure/overlays/local
	kubectl apply -k services/user-service/k8s/overlays/local
	kubectl apply -k services/auth-service/k8s/overlays/local
	kubectl apply -k services/board-service/k8s/overlays/local
	kubectl apply -k services/chat-service/k8s/overlays/local
	kubectl apply -k services/noti-service/k8s/overlays/local
	kubectl apply -k services/storage-service/k8s/overlays/local
	kubectl apply -k services/video-service/k8s/overlays/local
	kubectl apply -k services/frontend/k8s/overlays/local
	# 통합 Ingress 적용
	kubectl apply -f infrastructure/base/ingress/wealist-ingress.yaml

k8s-delete-local:
	kubectl delete -f infrastructure/base/ingress/wealist-ingress.yaml --ignore-not-found
	kubectl delete -k services/frontend/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/video-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/storage-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/noti-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/chat-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/board-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/auth-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/user-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k infrastructure/overlays/local --ignore-not-found
	kubectl delete -f services/namespace.yaml --ignore-not-found

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
	@echo "=== Kubernetes Pods (wealist-app) ==="
	kubectl get pods -n wealist-app 2>/dev/null || echo "Namespace wealist-app not found"
	@echo ""
	@echo "=== Kubernetes Pods (wealist-infra) ==="
	kubectl get pods -n wealist-infra 2>/dev/null || echo "Namespace wealist-infra not found"
