# Kubernetes Setup Guide

이 문서는 Wealist 프로젝트의 Kubernetes 배포 방법을 설명합니다.

## Prerequisites

- kubectl 설치
- Kubernetes 클러스터 (local: minikube/kind/Docker Desktop, prod: EKS)
- kustomize (kubectl에 내장)

## 디렉토리 구조

```
.
├── infrastructure/           # 인프라 (PostgreSQL, Redis)
│   ├── base/
│   │   ├── postgres/
│   │   └── redis/
│   └── overlays/
│       ├── local/           # 로컬 개발환경
│       └── eks/             # AWS EKS 환경
│
├── services/                 # 애플리케이션 서비스
│   ├── user-service/
│   │   └── k8s/
│   │       ├── base/
│   │       └── overlays/
│   │           ├── local/
│   │           └── eks/
│   ├── auth-service/
│   ├── board-service/
│   ├── chat-service/
│   ├── noti-service/
│   └── frontend/
│
└── argocd/                   # ArgoCD 앱 정의
    └── apps/
```

## Local 배포 (Minikube/Kind)

### 1. 클러스터 시작

```bash
# Minikube 사용 시
minikube start

# Kind 사용 시
kind create cluster --name wealist
```

### 2. 이미지 빌드

```bash
# 모든 서비스 이미지 빌드
make build-all

# 특정 서비스만 빌드
make build-user-service
```

### 3. 배포

```bash
# 전체 배포
make k8s-apply-local

# 또는 개별 배포
kubectl apply -k infrastructure/overlays/local
kubectl apply -k services/user-service/k8s/overlays/local
```

### 4. 확인

```bash
kubectl get pods -n wealist-dev
kubectl get services -n wealist-dev
```

### 5. 삭제

```bash
make k8s-delete-local
```

## EKS 배포

### 1. 환경 변수 설정

```bash
export AWS_ACCOUNT_ID=123456789012
export AWS_REGION=ap-northeast-2
export IMAGE_TAG=v1.0.0
export RDS_ENDPOINT=wealist-db.xxx.rds.amazonaws.com
export ELASTICACHE_ENDPOINT=wealist-redis.xxx.cache.amazonaws.com
```

### 2. ECR 로그인 및 이미지 푸시

```bash
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# 이미지 태그 및 푸시
docker tag user-service:local $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/wealist/user-service:$IMAGE_TAG
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/wealist/user-service:$IMAGE_TAG
```

### 3. 배포

```bash
make k8s-apply-eks
```

## ArgoCD 사용

### 1. ArgoCD 설치

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

### 2. ArgoCD 앱 배포

```bash
make argocd-apply
```

### 3. ArgoCD UI 접속

```bash
# 포트 포워딩
kubectl port-forward svc/argocd-server -n argocd 8080:443

# 초기 비밀번호 확인
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

브라우저에서 https://localhost:8080 접속 (username: admin)

## Kustomize 미리보기

배포 전 생성될 매니페스트 확인:

```bash
# 인프라
make kustomize-infra

# 개별 서비스
make kustomize-user-service
make kustomize-board-service
```

## 네임스페이스 전략

| 환경 | 네임스페이스 | 설명 |
|------|-------------|------|
| Local | wealist-dev | 로컬 개발 환경 |
| EKS | wealist-prod | 프로덕션 환경 |

## 트러블슈팅

### Pod이 시작되지 않는 경우

```bash
kubectl describe pod <pod-name> -n wealist-dev
kubectl logs <pod-name> -n wealist-dev
```

### ConfigMap/Secret 확인

```bash
kubectl get configmap -n wealist-dev
kubectl get secret -n wealist-dev
```

### 서비스 연결 테스트

```bash
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://user-service:8080/health/ready
```
