# Wealist Infrastructure Guide

Wealist 프로젝트의 인프라 구성 및 서비스별 의존성을 설명합니다.

## 인프라 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client (Browser)                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           NGINX (API Gateway)                                │
│                              Port: 80                                        │
└─────────────────────────────────────────────────────────────────────────────┘
          │              │              │              │              │
          ▼              ▼              ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ user-service │ │ auth-service │ │board-service │ │ chat-service │ │ noti-service │
│   (Go)       │ │ (Spring)     │ │   (Go)       │ │   (Go)       │ │   (Go)       │
│   :8080      │ │   :8090      │ │   :8000      │ │   :8001      │ │   :8002      │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
          │              │              │              │              │
          ▼              ▼              ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│storage-service│ │video-service │ │   frontend   │
│   (Go)       │ │   (Go)       │ │   (React)    │
│   :8003      │ │   :8004      │ │   :3000      │
└──────────────┘ └──────────────┘ └──────────────┘
          │              │
          ▼              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Infrastructure Layer                                 │
├──────────────┬──────────────┬──────────────┬──────────────┬─────────────────┤
│  PostgreSQL  │    Redis     │    MinIO     │   LiveKit    │     Coturn      │
│    :5432     │    :6379     │  :9000/:9001 │ :7880/:7881  │  :3478/:5349    │
└──────────────┴──────────────┴──────────────┴──────────────┴─────────────────┘
```

## 인프라 컴포넌트 상세

### 1. PostgreSQL

데이터 영속성을 위한 관계형 데이터베이스입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `postgres:17-alpine` |
| 포트 | 5432 |
| 볼륨 | `wealist-postgres-data` |

#### 데이터베이스 목록

| 데이터베이스 | 용도 | 사용 서비스 |
|-------------|------|------------|
| `user_db` | 사용자, 워크스페이스 정보 | user-service |
| `board_db` | 프로젝트, 보드, 태스크 | board-service |
| `chat_db` | 채팅방, 메시지 | chat-service |
| `noti_db` | 알림 정보 | noti-service |
| `storage_db` | 파일 메타데이터, 폴더 구조 | storage-service |
| `video_db` | 영상통화 기록, 참여자 정보 | video-service |

#### 서비스별 연결 정보

```
user-service    → postgresql://user_user:password@postgres:5432/user_db
board-service   → postgresql://board_user:password@postgres:5432/board_db
chat-service    → postgresql://chat_user:password@postgres:5432/chat_db
noti-service    → postgresql://noti_user:password@postgres:5432/noti_db
storage-service → postgresql://storage_user:password@postgres:5432/storage_db
video-service   → postgresql://video_user:password@postgres:5432/video_db
```

---

### 2. Redis

세션 관리, 캐싱, 실시간 데이터 처리를 위한 인메모리 데이터스토어입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `redis:7.2-alpine` |
| 포트 | 6379 |
| 볼륨 | `wealist-redis-data` |
| 최대 메모리 | 256MB |
| 제거 정책 | allkeys-lru |

#### 사용 서비스 및 용도

| 서비스 | 용도 | DB 번호 |
|--------|------|---------|
| auth-service | JWT 리프레시 토큰 저장, 세션 관리 | 0 |
| board-service | 캐시, Rate Limiting | 1 |
| chat-service | Pub/Sub 메시지 브로드캐스트, 온라인 상태 | 2 |
| noti-service | 실시간 알림 Pub/Sub | 3 |
| video-service | 방 상태 관리, 참여자 정보 캐시 | 4 |

#### 주요 기능별 사용

```
┌─────────────────────────────────────────────────────────────┐
│                         Redis                                │
├─────────────────────────────────────────────────────────────┤
│  [인증]                                                      │
│  ├─ refresh_token:{userId} → 리프레시 토큰 저장              │
│  └─ blacklist:{token} → 로그아웃된 토큰                      │
│                                                              │
│  [채팅]                                                      │
│  ├─ chat:room:{roomId}:users → 채팅방 참여자                 │
│  ├─ chat:user:{userId}:status → 온라인 상태                  │
│  └─ PubSub: chat:room:{roomId} → 메시지 브로드캐스트         │
│                                                              │
│  [알림]                                                      │
│  └─ PubSub: noti:user:{userId} → 실시간 알림                 │
│                                                              │
│  [영상통화]                                                   │
│  ├─ video:room:{roomId}:participants → 참여자 목록           │
│  └─ video:room:{roomId}:state → 방 상태                      │
└─────────────────────────────────────────────────────────────┘
```

---

### 3. MinIO (S3 호환 스토리지)

파일 업로드/다운로드를 위한 오브젝트 스토리지입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `minio/minio:latest` |
| API 포트 | 9000 |
| Console 포트 | 9001 |
| 볼륨 | `wealist-minio-data` |
| 기본 버킷 | `wealist-dev-files` |

#### 사용 서비스 및 용도

| 서비스 | 용도 |
|--------|------|
| user-service | 프로필 이미지 업로드 |
| board-service | 태스크 첨부파일, 커버 이미지 |
| storage-service | 파일 드라이브 (폴더, 파일 관리) |

#### 버킷 구조

```
wealist-dev-files/
├── profiles/              # 프로필 이미지
│   └── {userId}/
│       └── avatar.jpg
├── boards/                # 보드 첨부파일
│   └── {boardId}/
│       └── attachments/
├── storage/               # 파일 드라이브
│   └── {workspaceId}/
│       └── {folderId}/
│           └── {fileId}
└── video/                 # 영상통화 녹화 (추후)
    └── {roomId}/
```

#### Presigned URL 플로우

```
Client ──(1) 업로드 요청──▶ Backend ──(2) Presigned URL 생성──▶ MinIO
   │                                                              │
   └─────────(3) Presigned URL로 직접 업로드──────────────────────┘
```

---

### 4. LiveKit (WebRTC SFU)

영상/음성 통화를 위한 미디어 서버입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `livekit/livekit-server:v1.5` |
| HTTP/WebSocket | 7880 |
| RTC (TCP) | 7881 |
| RTC (UDP) | 50000-50020 |

#### 사용 서비스

| 서비스 | 용도 |
|--------|------|
| video-service | 방 생성, 토큰 발급, 참여자 관리 |
| frontend | WebRTC 클라이언트 연결 |

#### 연동 흐름

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Frontend   │    │video-service │    │   LiveKit    │
└──────────────┘    └──────────────┘    └──────────────┘
       │                   │                   │
       │ (1) 방 참여 요청   │                   │
       │──────────────────▶│                   │
       │                   │ (2) 방 생성/조회   │
       │                   │──────────────────▶│
       │                   │◀──────────────────│
       │                   │ (3) 참여 토큰 발급 │
       │◀──────────────────│                   │
       │                   │                   │
       │ (4) WebSocket 연결 (ws://livekit:7880)│
       │───────────────────────────────────────▶│
       │                   │                   │
       │ (5) WebRTC 미디어 스트림               │
       │◀──────────────────────────────────────▶│
```

---

### 5. Coturn (TURN/STUN 서버)

NAT/방화벽 환경에서 WebRTC 연결을 위한 릴레이 서버입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `coturn/coturn:4.6` |
| STUN/TURN | 3478 (TCP/UDP) |
| TURN TLS | 5349 (TCP/UDP) |

#### 사용 목적

```
┌─────────────────┐                              ┌─────────────────┐
│   Client A      │                              │   Client B      │
│  (NAT 환경)     │                              │  (NAT 환경)     │
└─────────────────┘                              └─────────────────┘
         │                                                │
         │ (1) STUN: 공인 IP 확인                         │
         │────────────────────▶┌─────────┐◀───────────────│
         │                     │ Coturn  │                │
         │ (2) TURN: 미디어 릴레이 (P2P 불가 시)          │
         │◀────────────────────│         │────────────────▶│
                               └─────────┘
```

#### LiveKit과의 연동

LiveKit 설정에서 Coturn을 TURN 서버로 지정:

```yaml
# livekit.yaml
turn:
  enabled: true
  udp_port: 3478
  tls_port: 5349
```

---

### 6. NGINX (API Gateway)

모든 API 요청을 라우팅하는 리버스 프록시입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `nginx:alpine` |
| 포트 | 80 |

#### 라우팅 규칙

| 경로 | 대상 서비스 | 프로토콜 |
|------|------------|---------|
| `/api/users` | user-service:8080 | HTTP |
| `/api/auth` | auth-service:8090 | HTTP |
| `/api/boards` | board-service:8000 | HTTP |
| `/api/chats` | chat-service:8001 | HTTP |
| `/api/notifications` | noti-service:8002 | HTTP |
| `/api/storage` | storage-service:8003 | HTTP |
| `/api/video` | video-service:8004 | HTTP |
| `/ws/chat` | chat-service:8001 | WebSocket |
| `/ws/notifications` | noti-service:8002 | WebSocket |

---

## 모니터링 인프라

### Prometheus

메트릭 수집 및 저장을 위한 시계열 데이터베이스입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `prom/prometheus:v2.48.0` |
| 포트 | 9090 |
| 볼륨 | `wealist-prometheus-data` |
| 보관 기간 | 7일 |

#### 스크래핑 대상

| Job | Target | Metrics Path |
|-----|--------|-------------|
| board-service | board-service:8000 | /metrics |
| auth-service | auth-service:8090 | /actuator/prometheus |
| noti-service | noti-service:8002 | /metrics |
| redis | redis-exporter:9121 | /metrics |
| postgres | postgres-exporter:9187 | /metrics |

---

### Loki

로그 수집 및 검색을 위한 로그 집계 시스템입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `grafana/loki:2.9.2` |
| 포트 | 3100 |
| 볼륨 | `wealist-loki-data` |

---

### Promtail

Docker 컨테이너 로그를 Loki로 전송하는 에이전트입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `grafana/promtail:2.9.2` |
| 마운트 | `/var/run/docker.sock`, `/var/lib/docker/containers` |

---

### Grafana

메트릭 및 로그 시각화 대시보드입니다.

| 항목 | 값 |
|------|-----|
| 이미지 | `grafana/grafana:10.2.2` |
| 포트 | 3001 |
| 볼륨 | `wealist-grafana-data` |
| 기본 계정 | admin / admin |

#### 데이터소스

| 이름 | 타입 | URL |
|------|------|-----|
| Prometheus | prometheus | http://prometheus:9090 |
| Loki | loki | http://loki:3100 |

---

### Exporters

| Exporter | 이미지 | 포트 | 대상 |
|----------|--------|------|------|
| Redis Exporter | oliver006/redis_exporter:v1.55.0 | 9121 | Redis |
| Postgres Exporter | prometheuscommunity/postgres-exporter:v0.15.0 | 9187 | PostgreSQL |

---

## 서비스-인프라 의존성 매트릭스

| 서비스 | PostgreSQL | Redis | MinIO | LiveKit | Coturn | NGINX |
|--------|:----------:|:-----:|:-----:|:-------:|:------:|:-----:|
| user-service | ✅ | - | ✅ | - | - | ✅ |
| auth-service | - | ✅ | - | - | - | ✅ |
| board-service | ✅ | ✅ | ✅ | - | - | ✅ |
| chat-service | ✅ | ✅ | - | - | - | ✅ |
| noti-service | ✅ | ✅ | - | - | - | ✅ |
| storage-service | ✅ | - | ✅ | - | - | ✅ |
| video-service | ✅ | ✅ | - | ✅ | ✅ | ✅ |
| frontend | - | - | - | ✅ | ✅ | ✅ |

---

## Docker Networks

| 네트워크 | 용도 | 연결된 서비스 |
|---------|------|--------------|
| `wealist-frontend-net` | 프론트엔드 통신 | nginx, frontend, all services |
| `wealist-backend-net` | 백엔드 서비스 간 통신 | all services, minio, livekit, coturn |
| `wealist-database-net` | 데이터베이스 통신 | postgres, redis, all services |
| `wealist-monitoring-net` | 모니터링 통신 | prometheus, loki, promtail, grafana, exporters |

---

## Docker Volumes

| 볼륨 | 용도 | 연결된 서비스 |
|------|------|--------------|
| `wealist-postgres-data` | PostgreSQL 데이터 | postgres |
| `wealist-redis-data` | Redis 데이터 | redis |
| `wealist-minio-data` | 오브젝트 스토리지 | minio |
| `wealist-prometheus-data` | 메트릭 데이터 | prometheus |
| `wealist-loki-data` | 로그 데이터 | loki |
| `wealist-grafana-data` | 대시보드 설정 | grafana |

---

## 환경별 인프라 차이

### Local (Docker Compose)

- 모든 인프라가 Docker 컨테이너로 실행
- 단일 노드 환경
- MinIO를 S3 대체로 사용
- Coturn/LiveKit 개발용 설정

### Production (EKS)

| Local | Production |
|-------|------------|
| PostgreSQL Container | Amazon RDS |
| Redis Container | Amazon ElastiCache |
| MinIO | Amazon S3 |
| LiveKit Container | LiveKit Cloud 또는 EC2 |
| Coturn Container | AWS TURN 서비스 또는 EC2 |

---

## 인프라 시작/중지

```bash
# 전체 인프라 시작
./docker/scripts/dev.sh up

# 모니터링 스택만 시작
./docker/scripts/monitoring.sh up

# 특정 서비스만 시작
docker compose -f docker/compose/docker-compose.yml up postgres redis minio

# 전체 중지
./docker/scripts/dev.sh down
```

---

## 트러블슈팅

### PostgreSQL 연결 실패

```bash
# 컨테이너 상태 확인
docker logs wealist-postgres

# 데이터베이스 목록 확인
docker exec -it wealist-postgres psql -U postgres -c "\l"

# 특정 DB 연결 테스트
docker exec -it wealist-postgres psql -U board_user -d board_db -c "SELECT 1"
```

### Redis 연결 실패

```bash
# Redis 상태 확인
docker exec -it wealist-redis redis-cli -a ${REDIS_PASSWORD} ping

# 키 목록 확인
docker exec -it wealist-redis redis-cli -a ${REDIS_PASSWORD} keys "*"
```

### MinIO 버킷 확인

```bash
# MinIO Console 접속
open http://localhost:9001

# mc 클라이언트로 확인
docker exec -it wealist-minio-init mc ls minio/wealist-dev-files
```

### LiveKit 연결 확인

```bash
# LiveKit 상태
curl http://localhost:7880

# 로그 확인
docker logs wealist-livekit
```
