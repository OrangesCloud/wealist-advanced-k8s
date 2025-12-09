# Wealist Port Configuration

Wealist 프로젝트에서 사용하는 모든 포트 정보입니다.

## 서비스 포트 요약

| 서비스 | 내부 포트 | 외부 포트 (로컬) | 설명 |
|--------|----------|-----------------|------|
| frontend | 5173 | 3000 | React + Vite 개발 서버 |
| nginx | 80 | 80 | API Gateway |
| user-service | 8080 | 8080 | 사용자 관리 서비스 |
| auth-service | 8090 | 8090 | 인증/토큰 서비스 |
| board-service | 8000 | 8000 | 보드/프로젝트 서비스 |
| chat-service | 8001 | 8001 | 채팅 서비스 |
| noti-service | 8002 | 8002 | 알림 서비스 |
| storage-service | 8003 | 8003 | 파일 스토리지 서비스 |
| video-service | 8004 | 8004 | 영상통화 서비스 |

## 인프라 포트

| 서비스 | 내부 포트 | 외부 포트 (로컬) | 설명 |
|--------|----------|-----------------|------|
| PostgreSQL | 5432 | 5432 | 데이터베이스 |
| Redis | 6379 | 6379 | 캐시/세션 |
| MinIO API | 9000 | 9000 | S3 호환 스토리지 |
| MinIO Console | 9001 | 9001 | MinIO 관리 UI |

## WebRTC/미디어 포트

| 서비스 | 포트 | 프로토콜 | 설명 |
|--------|-----|---------|------|
| LiveKit HTTP/WS | 7880 | TCP | API 및 WebSocket |
| LiveKit RTC | 7881 | TCP | WebRTC TCP |
| LiveKit Media | 50000-50020 | UDP | WebRTC 미디어 (개발용 제한) |
| TURN/STUN | 3478 | TCP/UDP | NAT 트래버설 |
| TURN TLS | 5349 | TCP/UDP | 보안 TURN |

## 모니터링 포트

| 서비스 | 포트 | 설명 |
|--------|-----|------|
| Prometheus | 9090 | 메트릭 수집 서버 |
| Grafana | 3001 | 모니터링 대시보드 |
| Node Exporter | 9100 | 호스트 메트릭 |
| Redis Exporter | 9121 | Redis 메트릭 |
| Postgres Exporter | 9187 | PostgreSQL 메트릭 |

## API 엔드포인트 (NGINX 라우팅)

NGINX가 80 포트에서 다음과 같이 라우팅합니다:

| 경로 | 대상 서비스 | 포트 |
|------|-----------|------|
| `/api/users` | user-service | 8080 |
| `/api/auth` | auth-service | 8090 |
| `/api/boards` | board-service | 8000 |
| `/api/chats` | chat-service | 8001 |
| `/api/notifications` | noti-service | 8002 |
| `/api/storage` | storage-service | 8003 |
| `/api/video` | video-service | 8004 |
| `/ws/chat` | chat-service | 8001 (WebSocket) |
| `/ws/notifications` | noti-service | 8002 (WebSocket) |

## 환경변수를 통한 포트 커스터마이징

`.env` 파일에서 외부 포트를 변경할 수 있습니다:

```bash
# Backend Services
USER_HOST_PORT=8080
AUTH_HOST_PORT=8090
BOARD_HOST_PORT=8000
CHAT_HOST_PORT=8001
NOTI_HOST_PORT=8002
STORAGE_HOST_PORT=8003
VIDEO_HOST_PORT=8004

# Frontend
FRONTEND_HOST_PORT=3000

# Infrastructure
POSTGRES_HOST_PORT=5432
REDIS_HOST_PORT=6379
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001
```

## Kubernetes 서비스 포트

K8s 환경에서는 ClusterIP 서비스로 내부 통신합니다:

| 서비스 | ClusterIP 포트 | 설명 |
|--------|---------------|------|
| user-service | 8080 | |
| auth-service | 8081 | K8s에서는 8081 사용 |
| board-service | 8000 | |
| chat-service | 8001 | |
| noti-service | 8002 | |
| storage-service | 8003 | |
| video-service | 8003 | K8s configmap 기준 |
| postgres | 5432 | |
| redis | 6379 | |
| livekit | 7880, 7881 | |
| coturn | 3478, 5349 | |

## Health/Metrics 엔드포인트

| 서비스 | Health | Ready | Metrics |
|--------|--------|-------|---------|
| user-service | `/health` | `/ready` | - |
| auth-service | `/actuator/health/liveness` | `/actuator/health/readiness` | `/actuator/prometheus` |
| board-service | `/health` | `/ready` | `/metrics` |
| chat-service | `/health` | `/ready` | - |
| noti-service | `/health` | `/ready` | `/metrics` |
| storage-service | `/health` | `/ready` | - |
| video-service | `/health` | `/ready` | - |

## 포트 충돌 확인

로컬에서 포트 사용 현황 확인:

```bash
# macOS/Linux
lsof -i :8080
netstat -an | grep 8080

# Docker 컨테이너 포트 확인
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

## Swagger/API 문서 포트

개발 환경에서 Swagger UI 접근:

| 서비스 | Swagger URL |
|--------|------------|
| User API | http://localhost:8080/swagger/index.html |
| Auth API | http://localhost:8090/swagger-ui/index.html |
| Board API | http://localhost:8000/swagger/index.html |
| Chat API | http://localhost:8001/swagger/index.html |
| Noti API | http://localhost:8002/swagger/index.html |
| Storage API | http://localhost:8003/swagger/index.html |
| Video API | http://localhost:8004/swagger/index.html |
