# Kubernetes Setup Guide

## Overview

이 문서는 wealist 프로젝트의 Kubernetes 배포 가이드입니다.

## Directory Structure

```
wealist-project-advanced/
├── services/                    # 모든 서비스
│   ├── user-service/
│   │   ├── src/                # 소스 코드
│   │   ├── Dockerfile
│   │   └── k8s/                # K8s manifests
│   │       ├── base/
│   │       └── overlays/
│   │           ├── local/
│   │           └── eks/
│   │
│   ├── auth-service/           # 동일 구조
│   ├── board-service/          # 동일 구조
│   └── frontend/               # 동일 구조
│
├── infrastructure/             # DB, Redis 등 인프라
│   ├── base/
│   │   ├── postgres/
│   │   └── redis/
│   └── overlays/
│       ├── local/              # StatefulSet 사용
│       └── eks/                # RDS, ElastiCache 연결
│
├── argocd/                     # ArgoCD GitOps 설정
│   └── apps/
│
├── docker/                     # Docker Compose (로컬 개발)
│   └── compose/
│
└── Makefile                    # 배포 명령어
```

## Quick Start

### Local Development (Docker Compose)

```bash
# 기존 방식 그대로
make dev-up
make dev-down
```

### Local Kubernetes (kind, k3s, minikube)

```bash
# 1. 이미지 빌드
make build-images

# 2. 로컬 k8s 배포
make k8s-local-up

# 3. 상태 확인
make k8s-status

# 4. 삭제
make k8s-local-down
```

### EKS Deployment

```bash
# 1. kubectl context를 EKS로 설정
aws eks update-kubeconfig --name your-cluster --region ap-northeast-2

# 2. 배포
make k8s-eks-up
```

### ArgoCD GitOps

```bash
# 1. ArgoCD 설치
make argocd-install

# 2. 비밀번호 확인
make argocd-password

# 3. ArgoCD 앱 배포
make argocd-apps
```

## Kustomize Overlays

### Base vs Overlays

- **base/**: 모든 환경에서 공통으로 사용하는 설정
- **overlays/local/**: 로컬 개발 환경 (낮은 리소스, NodePort)
- **overlays/eks/**: 프로덕션 환경 (높은 리소스, ECR 이미지)

### 수동 배포

```bash
# 로컬 환경
kubectl apply -k user-service/k8s/overlays/local

# EKS 환경
kubectl apply -k user-service/k8s/overlays/eks
```

## TODO

- [ ] 각 서비스 deployment.yaml에 환경변수 추가
- [ ] Health check probes 설정
- [ ] HPA (Horizontal Pod Autoscaler) 추가
- [ ] PodDisruptionBudget 추가
- [ ] NetworkPolicy 설정
- [ ] Ingress 설정 (ALB Ingress Controller)
- [ ] External Secrets Operator + Vault 연동
