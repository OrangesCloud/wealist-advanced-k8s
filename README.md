# weAlist Project

프로젝트 관리 및 협업 플랫폼

## Project Structure

```
wealist-project-advanced/
├── services/                    # 마이크로서비스
│   ├── user-service/           # 사용자 관리 (Spring Boot)
│   ├── auth-service/           # 인증/토큰 관리 (Spring Boot)
│   ├── board-service/          # 보드/프로젝트 관리 (Go)
│   ├── chat-service/           # 채팅 (Go)
│   └── frontend/               # 프론트엔드 (React + Vite)
│
├── infrastructure/             # K8s 인프라 (postgres, redis)
├── argocd/                     # ArgoCD GitOps
├── docker/                     # Docker Compose
├── prometheus/                 # 모니터링
└── k6-tests/                   # 부하 테스트
```

## Quick Start

### 로컬 개발 (Docker Compose)

```bash
# 환경 시작
make dev-up

# 로그 확인
make dev-logs

# 환경 중지
make dev-down

# 완전 삭제 (볼륨 포함)
make dev-clean
```

**접속 정보:**
| 서비스 | URL |
|--------|-----|
| Frontend | http://localhost:3000 |
| Auth API | http://localhost:8080 |
| User API | http://localhost:8090 |
| Board API | http://localhost:8000 |
| Chat API | http://localhost:8001 |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |
| MinIO Console | http://localhost:9001 |

### Kubernetes 배포

#### 로컬 K8s (kind, k3s, minikube)

```bash
# 이미지 빌드
make build-images

# 배포
make k8s-local-up

# 상태 확인
make k8s-status

# 삭제
make k8s-local-down
```

#### EKS 배포

```bash
# kubectl context 설정
aws eks update-kubeconfig --name your-cluster --region ap-northeast-2

# 배포
make k8s-eks-up

# 삭제
make k8s-eks-down
```

#### ArgoCD GitOps

```bash
# ArgoCD 설치
make argocd-install

# 초기 비밀번호 확인
make argocd-password

# 앱 배포
make argocd-apps
```

## 개별 서비스 빌드

```bash
make build-user      # user-service
make build-auth      # auth-service
make build-board     # board-service
make build-chat      # chat-service
make build-frontend  # frontend
make build-images    # 전체 빌드
```

## 문서

- [K8s Setup Guide](docs/K8S_SETUP.md)
- [Docker 설정](docker/README.md)
