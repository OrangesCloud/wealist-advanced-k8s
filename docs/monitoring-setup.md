# weAlist 모니터링 스택 설정 가이드

## 개요

이 문서는 weAlist 프로젝트에 추가된 Prometheus, Loki, Grafana 기반 모니터링 스택에 대해 설명합니다.

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        Grafana (3001)                           │
│                    시각화 대시보드                                │
│  - 기획자 대시보드 (DAU/MAU, 비즈니스)                           │
│  - 개발자 대시보드 (API, 로그)                                   │
│  - 인프라 대시보드 (서비스 상태)                                  │
│  - DB 대시보드 (PostgreSQL, Redis)                              │
│  - 로그 분석 대시보드 (디버깅)                                   │
└────────────────────────┬────────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┬───────────────┐
         │               │               │               │
         ▼               ▼               ▼               ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ Prometheus  │  │    Loki     │  │   Redis     │  │  PostgreSQL │
│   (9090)    │  │   (3100)    │  │  Exporter   │  │   Exporter  │
│  메트릭 저장  │  │  로그 저장   │  │   (9121)    │  │   (9187)    │
└──────┬──────┘  └──────┬──────┘  └─────────────┘  └─────────────┘
       │                │
       │                ▼
       │         ┌─────────────┐
       │         │  Promtail   │
       │         │ Docker 로그  │
       │         │   수집기     │
       │         └──────┬──────┘
       │                │
       ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Application Services                         │
│  user | auth | board | chat | noti | storage | video            │
│  /metrics 엔드포인트 노출 + 비즈니스 메트릭                        │
└─────────────────────────────────────────────────────────────────┘
```

## 접속 정보

| 서비스 | URL | 용도 |
|--------|-----|------|
| **Grafana** | http://localhost:3001 | 대시보드 (admin/admin) |
| **Prometheus** | http://localhost:9090 | 메트릭 쿼리 UI |
| **Loki** | http://localhost:3100 | 로그 API |
| **PostgreSQL Exporter** | http://localhost:9187 | PostgreSQL 메트릭 |
| **Redis Exporter** | http://localhost:9121 | Redis 메트릭 |

## 대시보드 목록

| 대시보드 | 대상 | 설명 |
|----------|------|------|
| **기획자 대시보드** | 기획자/PM | DAU/MAU, 신규 회원, 워크스페이스, 비즈니스 KPI |
| **인프라 대시보드** | DevOps | 서비스 상태, 에러율, Redis/DB 현황 |
| **개발자 대시보드** | 개발자 | API 성능, 로그, 디버깅 정보 |
| **DB 대시보드** | DBA/DevOps | PostgreSQL/Redis 상세 모니터링 |
| **로그 분석** | 개발자 | 에러 로그 검색, 문제 API 탐지 |
| **서비스 상세** | 개발자 | 서비스별 상세 메트릭 |

## 알림 규칙 (Alerting)

| 알림 | 조건 | 심각도 |
|------|------|--------|
| 서비스 다운 | `up == 0` (1분 이상) | Critical |
| 높은 에러율 | HTTP 5xx > 5% (2분 이상) | Warning |
| 느린 응답 | p95 > 2초 (5분 이상) | Warning |
| Redis 메모리 | > 200MB (5분 이상) | Warning |
| 트래픽 급증 | RPS > 100 (2분 이상) | Info |

## 추가된 파일

### Docker Compose 설정
- `docker/compose/docker-compose.yml` - 모니터링 서비스 추가

### 모니터링 설정 파일
```
docker/monitoring/
├── prometheus/
│   └── prometheus.yml          # 스크래핑 대상 설정
├── loki/
│   └── loki-config.yml         # 로그 저장 설정
├── promtail/
│   └── promtail-config.yml     # Docker 로그 수집 설정
└── grafana/
    └── provisioning/
        ├── datasources/
        │   └── datasources.yml # Prometheus, Loki 자동 등록
        └── dashboards/
            ├── dashboards.yml
            └── json/
                └── services-overview.json  # 기본 대시보드
```

### Go 서비스 메트릭 미들웨어
- `services/user-service/internal/middleware/metrics.go`
- `services/chat-service/internal/middleware/metrics.go`
- `services/storage-service/internal/middleware/metrics.go`
- `services/video-service/internal/middleware/metrics.go`

## 수집되는 메트릭

### 공통 HTTP 메트릭 (모든 Go 서비스)

| 메트릭 | 타입 | 설명 |
|--------|------|------|
| `http_requests_total` | Counter | 총 HTTP 요청 수 (method, path, status 라벨) |
| `http_request_duration_seconds` | Histogram | 요청 처리 시간 |
| `http_requests_in_flight` | Gauge | 현재 처리 중인 요청 수 |

### 서비스별 커스텀 메트릭

**user-service (비즈니스 메트릭):**
- `user_registrations_total` - 신규 회원 가입 수
- `user_logins_total` - 로그인 수 (DAU/MAU 추정용)
- `users_active_total` - 현재 동시 접속자
- `users_registered_total` - 전체 등록 사용자 수
- `workspaces_created_total` - 워크스페이스 생성 수
- `workspaces_total` - 전체 워크스페이스 수
- `workspace_members_total` - 워크스페이스별 멤버 수

**board-service (비즈니스 메트릭):**
- `board_service_projects_total` - 전체 프로젝트 수
- `board_service_boards_total` - 전체 보드 수
- `board_service_project_created_total` - 프로젝트 생성 수
- `board_service_board_created_total` - 보드 생성 수

**chat-service:**
- `websocket_connections_total` - 총 WebSocket 연결 수
- `websocket_active_connections` - 현재 활성 WebSocket 연결
- `chat_daily_active_users` - 일일 채팅 사용자
- `chat_messages_sent_total` - 채팅 메시지 전송 수
- `chat_rooms_active` - 활성 채팅방 수

**storage-service:**
- `storage_upload_bytes_total` - 총 업로드 바이트
- `storage_download_bytes_total` - 총 다운로드 바이트

**video-service:**
- `video_rooms_active` - 활성 화상회의 방 수
- `video_participants_total` - 총 참가자 수
- `video_participants_active` - 현재 진행 중인 참가자 수
- `video_daily_active_users` - 일일 비디오 사용자
- `video_calls_started_total` - 비디오 통화 시작 수
- `video_call_duration_seconds` - 통화 시간

### Spring Boot (auth-service)
- `/actuator/prometheus` 엔드포인트
- JVM, HTTP, 캐시 등 Spring Boot 표준 메트릭

### PostgreSQL 메트릭 (postgres-exporter)
- `pg_stat_database_*` - 데이터베이스 통계
- `pg_stat_user_tables_*` - 테이블 통계
- `pg_locks_*` - 락 정보
- `pg_stat_bgwriter_*` - Background Writer 통계
- `pg_replication_*` - 복제 상태 (해당시)

### Redis 메트릭 (redis-exporter)
- `redis_memory_used_bytes` - 메모리 사용량
- `redis_connected_clients` - 연결된 클라이언트 수
- `redis_commands_processed_total` - 처리된 명령 수
- `redis_keyspace_*` - 키스페이스 정보
- `redis_db_keys` - 키 개수

## 메트릭 엔드포인트

| 서비스 | 엔드포인트 |
|--------|-----------|
| user-service | http://localhost:8080/metrics |
| auth-service | http://localhost:8090/actuator/prometheus |
| board-service | http://localhost:8000/metrics |
| chat-service | http://localhost:8001/metrics |
| noti-service | http://localhost:8002/metrics |
| storage-service | http://localhost:8003/metrics |
| video-service | http://localhost:8004/metrics |

## 사용법

### 개발 환경 시작
```bash
./docker/scripts/dev.sh up
```

### Grafana 대시보드 접속
1. http://localhost:3001 접속
2. admin / admin 으로 로그인
3. Dashboards → weAlist → Services Overview 선택

### Prometheus 쿼리 예시
```promql
# 서비스별 초당 요청 수
sum(rate(http_requests_total[5m])) by (job)

# 95 퍼센타일 응답 시간
histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket[5m])) by (le, job))

# 에러율
sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))
```

### Loki 로그 쿼리 예시 (Grafana Explore)
```logql
# 특정 서비스 로그
{compose_service="user-service"}

# 에러 로그만
{compose_service=~".+"} |= "error"

# JSON 필드 추출
{compose_service="board-service"} | json | level="error"
```

## 데이터 보관 기간

- **Prometheus**: 7일 (docker-compose에서 설정)
- **Loki**: 7일 (loki-config.yml에서 설정)
- **Grafana**: 영구 저장 (grafana-data 볼륨)

## 의존성 추가

### Go 서비스
```go
// go.mod에 추가됨
github.com/prometheus/client_golang v1.18.0
```

### Spring Boot (auth-service)
```gradle
// build.gradle에 이미 포함됨
implementation 'io.micrometer:micrometer-registry-prometheus'
```

## 트러블슈팅

### Prometheus에서 타겟이 DOWN으로 표시될 때
1. http://localhost:9090/targets 접속
2. 에러 메시지 확인
3. 해당 서비스가 실행 중인지 확인

### Grafana에서 데이터가 보이지 않을 때
1. 데이터소스 연결 확인 (Configuration → Data Sources)
2. 시간 범위 확인 (우측 상단)
3. 쿼리 직접 테스트 (Explore 메뉴)

### 로그가 수집되지 않을 때
1. Promtail 컨테이너 로그 확인: `docker logs wealist-promtail`
2. Docker 소켓 마운트 확인
3. Loki 연결 상태 확인
