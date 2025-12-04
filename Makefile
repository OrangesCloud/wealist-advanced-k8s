# =============================================================================
# weAlist Project - Makefile
# =============================================================================
# K8s deployment and local development commands
# =============================================================================

.PHONY: help dev-up dev-down k8s-local-up k8s-local-down k8s-eks-up build-images

# Default environment
ENV ?= local

# =============================================================================
# Help
# =============================================================================
help:
	@echo "weAlist Project Commands"
	@echo ""
	@echo "=== Docker Compose (Local Development) ==="
	@echo "  make dev-up        - Start local dev environment (docker-compose)"
	@echo "  make dev-down      - Stop local dev environment"
	@echo "  make dev-logs      - View logs"
	@echo ""
	@echo "=== Kubernetes ==="
	@echo "  make k8s-local-up  - Deploy to local k8s (kind/k3s/minikube)"
	@echo "  make k8s-local-down- Remove from local k8s"
	@echo "  make k8s-eks-up    - Deploy to EKS"
	@echo "  make k8s-status    - Check deployment status"
	@echo ""
	@echo "=== Build ==="
	@echo "  make build-images  - Build all Docker images"
	@echo "  make build-user    - Build user-service image"
	@echo "  make build-auth    - Build auth-service image"
	@echo "  make build-board   - Build board-service image"
	@echo "  make build-chat    - Build chat-service image"
	@echo "  make build-frontend- Build frontend image"

# =============================================================================
# Docker Compose (Local Development)
# =============================================================================
dev-up:
	./docker/scripts/dev.sh up

dev-down:
	./docker/scripts/dev.sh down

dev-logs:
	./docker/scripts/dev.sh logs

dev-clean:
	./docker/scripts/dev.sh clean

# =============================================================================
# Kubernetes - Local (kind, k3s, minikube)
# =============================================================================
k8s-local-up: k8s-local-infra k8s-local-services
	@echo "✅ Local K8s deployment complete!"

k8s-local-infra:
	@echo "📦 Deploying infrastructure to local k8s..."
	kubectl apply -k infrastructure/overlays/local

k8s-local-services:
	@echo "🚀 Deploying services to local k8s..."
	kubectl apply -k services/user-service/k8s/overlays/local
	kubectl apply -k services/auth-service/k8s/overlays/local
	kubectl apply -k services/board-service/k8s/overlays/local
	kubectl apply -k services/chat-service/k8s/overlays/local
	kubectl apply -k services/frontend/k8s/overlays/local

k8s-local-down:
	@echo "🗑️  Removing from local k8s..."
	kubectl delete -k services/frontend/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/chat-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/board-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/auth-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k services/user-service/k8s/overlays/local --ignore-not-found
	kubectl delete -k infrastructure/overlays/local --ignore-not-found

# =============================================================================
# Kubernetes - EKS
# =============================================================================
k8s-eks-up: k8s-eks-infra k8s-eks-services
	@echo "✅ EKS deployment complete!"

k8s-eks-infra:
	@echo "📦 Deploying infrastructure to EKS..."
	kubectl apply -k infrastructure/overlays/eks

k8s-eks-services:
	@echo "🚀 Deploying services to EKS..."
	kubectl apply -k services/user-service/k8s/overlays/eks
	kubectl apply -k services/auth-service/k8s/overlays/eks
	kubectl apply -k services/board-service/k8s/overlays/eks
	kubectl apply -k services/chat-service/k8s/overlays/eks
	kubectl apply -k services/frontend/k8s/overlays/eks

k8s-eks-down:
	@echo "🗑️  Removing from EKS..."
	kubectl delete -k services/frontend/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/chat-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/board-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/auth-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k services/user-service/k8s/overlays/eks --ignore-not-found
	kubectl delete -k infrastructure/overlays/eks --ignore-not-found

# =============================================================================
# Kubernetes - Status
# =============================================================================
k8s-status:
	@echo "📊 Deployment Status:"
	@echo ""
	@echo "=== Pods ==="
	kubectl get pods -n wealist-local 2>/dev/null || kubectl get pods -n wealist 2>/dev/null || echo "No pods found"
	@echo ""
	@echo "=== Services ==="
	kubectl get svc -n wealist-local 2>/dev/null || kubectl get svc -n wealist 2>/dev/null || echo "No services found"

# =============================================================================
# Build Docker Images
# =============================================================================
build-images: build-user build-auth build-board build-chat build-frontend
	@echo "✅ All images built!"

build-user:
	@echo "🔨 Building user-service..."
	docker build -t wealist/user-service:latest ./services/user-service

build-auth:
	@echo "🔨 Building auth-service..."
	docker build -t wealist/auth-service:latest ./services/auth-service

build-board:
	@echo "🔨 Building board-service..."
	docker build -t wealist/board-service:latest -f ./services/board-service/docker/Dockerfile ./services/board-service

build-chat:
	@echo "🔨 Building chat-service..."
	docker build -t wealist/chat-service:latest -f ./services/chat-service/docker/Dockerfile ./services/chat-service

build-frontend:
	@echo "🔨 Building frontend..."
	docker build -t wealist/frontend:latest ./services/frontend

# =============================================================================
# ArgoCD
# =============================================================================
argocd-install:
	@echo "📦 Installing ArgoCD..."
	kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
	@echo "⏳ Waiting for ArgoCD to be ready..."
	kubectl wait --for=condition=available --timeout=300s deployment/argocd-server -n argocd

argocd-password:
	@echo "🔑 ArgoCD initial admin password:"
	@kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
	@echo ""

argocd-apps:
	@echo "📦 Deploying ArgoCD applications..."
	kubectl apply -f argocd/apps/project.yaml
	kubectl apply -f argocd/apps/root-app.yaml
