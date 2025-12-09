# Kubernetes Setup Guide

Wealist 프로젝트의 Kubernetes 배포 방법을 설명합니다.

## Prerequisites

- kubectl 설치
- Kubernetes 클러스터 (local: minikube/kind/Docker Desktop, prod: EKS)
- kustomize (kubectl에 내장)

## 디렉토리 구조

```
.
├── infrastructure/                    # 인프라 컴포넌트
│   ├── base/
│   │   ├── namespace.yaml
│   │   ├── postgres/                 # PostgreSQL
│   │   ├── redis/                    # Redis
│   │   ├── coturn/                   # TURN/STUN 서버
│   │   └── livekit/                  # LiveKit SFU
│   └── overlays/
│       ├── local/                    # 로컬 개발환경
│       └── eks/                      # AWS EKS 환경
│
├── services/                          # 애플리케이션 서비스
│   ├── user-service/
│   │   └── k8s/
│   │       ├── base/
│   │       │   ├── kustomization.yaml
│   │       │   ├── deployment.yaml
│   │       │   ├── service.yaml
│   │       │   ├── configmap.yaml
│   │       │   └── secret.yaml
│   │       └── overlays/
│   │           ├── local/
│   │           └── eks/
│   ├── auth-service/
│   ├── board-service/
│   ├── chat-service/
│   ├── noti-service/
│   ├── storage-service/              # 파일 스토리지 서비스
│   ├── video-service/                # 영상통화 서비스
│   └── frontend/
│
└── argocd/                            # ArgoCD 앱 정의
    └── apps/
```

## 서비스 목록

| 서비스 | 설명 | 포트 | 언어 |
|--------|------|------|------|
| user-service | 사용자 관리 | 8080 | Go |
| auth-service | 인증/토큰 | 8081 | Spring Boot |
| board-service | 보드/프로젝트 | 8000 | Go |
| chat-service | 채팅 | 8001 | Go |
| noti-service | 알림 | 8002 | Go |
| storage-service | 파일 스토리지 | 8003 | Go |
| video-service | 영상통화 | 8003 | Go |
| frontend | 웹 UI | 80 | React |

## 인프라 컴포넌트

| 컴포넌트 | 설명 | 포트 |
|---------|------|------|
| postgres | PostgreSQL 데이터베이스 | 5432 |
| redis | Redis 캐시/세션 | 6379 |
| livekit | WebRTC SFU 서버 | 7880, 7881 |
| coturn | TURN/STUN 서버 | 3478, 5349 |

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
make build-storage-service
make build-video-service
```

### 3. 배포

```bash
# 전체 배포
make k8s-apply-local

# 또는 개별 배포
# 인프라 먼저
kubectl apply -k infrastructure/overlays/local

# 서비스 배포
kubectl apply -k services/user-service/k8s/overlays/local
kubectl apply -k services/auth-service/k8s/overlays/local
kubectl apply -k services/board-service/k8s/overlays/local
kubectl apply -k services/chat-service/k8s/overlays/local
kubectl apply -k services/noti-service/k8s/overlays/local
kubectl apply -k services/storage-service/k8s/overlays/local
kubectl apply -k services/video-service/k8s/overlays/local
kubectl apply -k services/frontend/k8s/overlays/local
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
