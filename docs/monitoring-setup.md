# Monitoring Setup Guide

Wealist 프로젝트의 모니터링 시스템 구성 및 설정 가이드입니다.

## 모니터링 스택 개요

| 컴포넌트 | 역할 | 로컬 포트 | 이미지 |
|---------|------|----------|--------|
| Prometheus | 메트릭 수집/저장 | 9090 | prom/prometheus:v2.48.0 |
| Loki | 로그 수집/저장 | 3100 | grafana/loki:2.9.2 |
| Promtail | Docker 로그 수집 | - | grafana/promtail:2.9.2 |
| Grafana | 시각화 대시보드 | 3001 | grafana/grafana:10.2.2 |
| Redis Exporter | Redis 메트릭 수집 | 9121 | oliver006/redis_exporter:v1.55.0 |
| Postgres Exporter | PostgreSQL 메트릭 수집 | 9187 | prometheuscommunity/postgres-exporter:v0.15.0 |

## 모니터링 시작/중지

```bash
# 모니터링 스택 시작
./docker/scripts/monitoring.sh up [dev|prod]

# 모니터링 스택 중지
./docker/scripts/monitoring.sh down [dev|prod]

# 재시작
./docker/scripts/monitoring.sh restart [dev|prod]

# 상태 확인
./docker/scripts/monitoring.sh status [dev|prod]

# 로그 확인
./docker/scripts/monitoring.sh logs [dev|prod] [service]
```

## 접속 정보

- **Prometheus**: http://localhost:9090
- **Loki**: http://localhost:3100
- **Grafana**: http://localhost:3001
  - 기본 계정: `admin` / `admin`

### Grafana 데이터소스 설정

Grafana provisioning을 통해 자동 설정되어 있습니다. 수동 설정 시:

**Prometheus 데이터소스:**
1. Configuration > Data Sources > Add data source
2. Prometheus 선택
3. URL: `http://prometheus:9090`
4. Save & Test

**Loki 데이터소스:**
1. Configuration > Data Sources > Add data source
2. Loki 선택
3. URL: `http://loki:3100`
4. Save & Test

## 서비스별 Metrics Endpoint

### Go 서비스 (Prometheus client_golang)

| 서비스 | Metrics Endpoint | Health Endpoint |
|--------|------------------|-----------------|
| board-service | `/metrics`, `/api/boards/metrics` | `/health`, `/ready` |
| user-service | `/metrics` | `/health`, `/ready` |
| chat-service | `/metrics` | `/health`, `/ready` |
| noti-service | `/metrics` | `/health`, `/ready` |
| storage-service | `/metrics` | `/health`, `/ready` |
| video-service | `/metrics` | `/health`, `/ready` |

### Spring Boot 서비스 (Micrometer)

| 서비스 | Metrics Endpoint | Health Endpoint |
|--------|------------------|-----------------|
| auth-service | `/actuator/prometheus` | `/actuator/health/liveness`, `/actuator/health/readiness` |

**auth-service Actuator 노출 엔드포인트**:
- `/actuator/health` - 전체 헬스 상태
- `/actuator/info` - 애플리케이션 정보
- `/actuator/metrics` - 메트릭 목록
- `/actuator/prometheus` - Prometheus 포맷 메트릭

## board-service 메트릭 상세

board-service는 포괄적인 Prometheus 메트릭을 제공합니다.

### HTTP 메트릭
```
board_service_http_requests_total{method, endpoint, status}
board_service_http_request_duration_seconds{method, endpoint}
```

### 데이터베이스 메트릭
```
# Connection Pool
board_service_db_connections_open
board_service_db_connections_in_use
board_service_db_connections_idle
board_service_db_connections_max
board_service_db_connection_wait_total
board_service_db_connection_wait_duration_seconds_total

# Query Performance
board_service_db_query_duration_seconds{operation, table}
board_service_db_query_errors_total{operation, table}
```

### 외부 API 메트릭
```
board_service_external_api_request_duration_seconds{endpoint, status}
board_service_external_api_requests_total{endpoint, method, status}
board_service_external_api_errors_total{endpoint, error_type}
```

### 비즈니스 메트릭
```
board_service_projects_total
board_service_boards_total
board_service_project_created_total
board_service_board_created_total
```

## Prometheus 설정

### 스크래핑 대상 설정 예시

```yaml
scrape_configs:
  - job_name: 'board-service'
    static_configs:
      - targets: ['board-service:8000']
    metrics_path: '/metrics'

  - job_name: 'auth-service'
    static_configs:
      - targets: ['auth-service:8090']
    metrics_path: '/actuator/prometheus'

  - job_name: 'noti-service'
    static_configs:
      - targets: ['noti-service:8002']
    metrics_path: '/metrics'

  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']

  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']

  - job_name: 'node'
    static_configs:
      - targets: ['node-exporter:9100']
```

## Exporter 구성

### Redis Exporter
- 이미지: `oliver006/redis_exporter`
- 포트: 9121
- Redis 연결: `redis://redis:6379`

### Postgres Exporter
- 이미지: `prometheuscommunity/postgres-exporter`
- 포트: 9187
- PostgreSQL 연결: `postgres://user:password@postgres:5432/db`

### Node Exporter
- 이미지: `prom/node-exporter`
- 포트: 9100
- 호스트 시스템 메트릭 수집

## Loki 로그 수집

Loki + Promtail을 통해 모든 Docker 컨테이너 로그를 수집합니다.

### 아키텍처

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  Services   │───▶│   Docker    │───▶│  Promtail   │───▶│    Loki     │
│  (logs)     │    │  json-file  │    │  (collector)│    │  (storage)  │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                                                                │
                                                                ▼
                                                         ┌─────────────┐
                                                         │   Grafana   │
                                                         │  (query)    │
                                                         └─────────────┘
```

### Promtail 설정

Promtail은 Docker 소켓을 통해 컨테이너 로그를 수집합니다:

```yaml
# docker/monitoring/promtail/promtail-config.yml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: containers
    static_configs:
      - targets:
          - localhost
        labels:
          job: containerlogs
          __path__: /var/lib/docker/containers/*/*log
    pipeline_stages:
      - json:
          expressions:
            output: log
            stream: stream
            attrs:
      - labels:
          stream:
      - output:
          source: output
```

### Grafana에서 로그 조회

1. Explore 메뉴 선택
2. 데이터소스: Loki 선택
3. Label browser에서 필터링:
   - `container_name`: 특정 컨테이너
   - `job`: containerlogs

#### 유용한 LogQL 쿼리

```logql
# 특정 서비스 로그
{container_name="wealist-board-service"}

# 에러 로그만 필터
{container_name=~"wealist-.*"} |= "error"

# JSON 로그 파싱
{container_name="wealist-board-service"} | json | level="error"

# 최근 5분간 에러 카운트
count_over_time({container_name=~"wealist-.*"} |= "error" [5m])
```

### 서비스 로깅 설정

모든 서비스는 json-file 드라이버를 사용하며 Promtail이 수집합니다:

```yaml
# docker-compose.yml
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

## 알림 설정 (Alertmanager)

추후 Alertmanager 구성 시 다음 알림 규칙 권장:

### 서비스 다운 알림
```yaml
groups:
  - name: service-alerts
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.job }} is down"
```

### 고지연 알림
```yaml
      - alert: HighLatency
        expr: histogram_quantile(0.95, rate(board_service_http_request_duration_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected in board-service"
```

## 트러블슈팅

### Prometheus가 타겟에 연결하지 못할 때
```bash
# 네트워크 확인
docker network ls
docker network inspect wealist-backend-net

# 서비스 상태 확인
curl http://localhost:8000/metrics
curl http://localhost:8090/actuator/prometheus

# Prometheus 타겟 상태 확인
curl http://localhost:9090/api/v1/targets
```

### Loki 로그가 수집되지 않을 때
```bash
# Promtail 상태 확인
docker logs wealist-promtail

# Loki 상태 확인
curl http://localhost:3100/ready

# Loki 레이블 확인
curl http://localhost:3100/loki/api/v1/labels
```

### Grafana 데이터소스 연결 실패
1. Prometheus/Loki 컨테이너 실행 확인
2. 네트워크 동일한지 확인 (wealist-monitoring-net)
3. URL을 컨테이너 이름으로 설정:
   - Prometheus: `http://prometheus:9090`
   - Loki: `http://loki:3100`

### 모니터링 스택 전체 재시작
```bash
docker compose -f docker/compose/docker-compose.yml restart prometheus loki promtail grafana redis-exporter postgres-exporter
```

## Grafana 대시보드 구성

Grafana provisioning을 통해 자동으로 대시보드가 등록됩니다. 대시보드 JSON 파일은 `docker/monitoring/grafana/provisioning/dashboards/json/` 경로에 위치합니다.

### 대시보드 구조

```
docker/monitoring/grafana/provisioning/
├── datasources/
│   └── datasources.yml          # Prometheus, Loki 자동 등록
├── dashboards/
│   ├── dashboards.yml           # 대시보드 프로비저닝 설정
│   └── json/
│       ├── services-overview.json
│       ├── database-dashboard.json
│       ├── infra-dashboard.json
│       ├── developer-dashboard.json
│       ├── business-dashboard.json
│       ├── service-detail.json
│       └── log-analysis-dashboard.json
└── alerting/                    # 알림 규칙 (추후)
```

### 역할별 대시보드

| 대상 | 대시보드 | 설명 |
|------|---------|------|
| 기획자/PM | 기획자 대시보드 | DAU/MAU, 비즈니스 메트릭 |
| 인프라 담당자 | 인프라 대시보드 | 서비스 상태, SLI, 트래픽 |
| 인프라 담당자 | 데이터베이스 모니터링 | PostgreSQL, Redis 상세 |
| 개발자 | 개발자 대시보드 | API 성능, 에러 분석 |
| 개발자 | 로그 분석 & 디버깅 | 로그 검색, 에러 탐지 |
| 공통 | 서비스 개요 | 전체 서비스 상태 한눈에 |
| 공통 | 서비스 상세 | 개별 서비스 심층 분석 |

---

### 1. weAlist Services Overview (services-overview.json)

**UID**: `wealist-overview`
**Tags**: `wealist`, `overview`

전체 서비스의 상태를 한눈에 파악할 수 있는 개요 대시보드입니다.

**주요 패널:**
- **Service Status**: 모든 서비스의 UP/DOWN 상태
- **Request Rate**: 서비스별 초당 요청 수 (RPS)
- **Response Time (p95)**: 서비스별 95퍼센타일 응답시간
- **All Service Logs**: Loki를 통한 전체 서비스 로그 스트림

---

### 2. 데이터베이스 모니터링 (database-dashboard.json)

**UID**: `database-dashboard`
**Tags**: `wealist`, `database`, `postgres`, `redis`

PostgreSQL과 Redis의 상세 메트릭을 모니터링하는 대시보드입니다.

**PostgreSQL 섹션:**
| 패널 | 메트릭 | 설명 |
|------|--------|------|
| PostgreSQL 상태 | `pg_up` | UP/DOWN 상태 |
| 활성 연결 수 | `pg_stat_activity_count` | 현재 연결 수 |
| 전체 DB 크기 | `pg_database_size_bytes` | 데이터베이스 용량 |
| 트랜잭션/초 | `pg_stat_database_xact_commit` | Commit 처리량 |
| 롤백/초 | `pg_stat_database_xact_rollback` | 롤백 발생량 |
| Cache Hit Ratio | `blks_hit / (blks_hit + blks_read)` | 캐시 효율 |
| Row 작업량 | `tup_inserted/updated/deleted` | INSERT/UPDATE/DELETE 추이 |

**Redis 섹션:**
| 패널 | 메트릭 | 설명 |
|------|--------|------|
| Redis 상태 | `redis_up` | UP/DOWN 상태 |
| 연결된 클라이언트 | `redis_connected_clients` | 현재 연결 수 |
| 메모리 사용량 | `redis_memory_used_bytes` | 메모리 사용량 |
| 총 Key 수 | `redis_db_keys` | 저장된 키 개수 |
| 명령어/초 | `redis_commands_processed_total` | 처리량 |
| Hit Rate | `hits / (hits + misses)` | 캐시 히트율 |

---

### 3. 인프라 대시보드 (infra-dashboard.json)

**UID**: `infra-dashboard`
**Tags**: `wealist`, `infra`, `인프라`

인프라 담당자를 위한 운영 대시보드입니다. SLI(Service Level Indicators) 중심으로 구성되어 있습니다.

**주요 섹션:**
- **전체 서비스 상태**: 모든 서비스 UP/DOWN 현황 (색상으로 구분)
- **핵심 지표 (SLI)**:
  - 전체 RPS
  - 전체 에러율 (5% 초과 시 빨간색)
  - 전체 응답시간 p95 (1초 초과 시 빨간색)
  - 처리 중 요청 수
- **서비스별 에러율**: 5xx 에러 추이 (5% 임계선 표시)
- **서비스별 응답시간**: p95 응답시간 추이
- **트래픽**: 서비스별 RPS, HTTP 상태코드 분포 (2xx/4xx/5xx)
- **인프라 (Redis)**: 연결 수, 메모리, 명령어/초
- **에러 로그**: Loki 기반 실시간 에러 로그

---

### 4. 개발자 대시보드 (developer-dashboard.json)

**UID**: `developer-dashboard`
**Tags**: `wealist`, `developer`, `개발자`

개발자를 위한 API 성능 분석 및 디버깅 대시보드입니다.

**Variables:**
- `$service`: 서비스 선택 (다중 선택 가능)
- `$search`: 로그 검색어 입력

**주요 섹션:**
- **API 성능 개요**: 서비스별 RPS, p95 응답시간, 에러율을 테이블로 표시
- **느린 엔드포인트 분석**: Top 20 느린 API (p95, p99, 요청수)
- **에러 분석**:
  - 5xx 에러 발생 추이 (서비스/경로별)
  - 4xx 에러 발생 추이 (서비스/상태코드별)
- **로그 탐색기**:
  - 에러 로그 (error|fail|exception|panic)
  - 키워드 검색
- **응답시간 분포**: p50, p90, p95, p99 백분위수 추이

---

### 5. 기획자 대시보드 - DAU/MAU & 비즈니스 (business-dashboard.json)

**UID**: `business-dashboard`
**Tags**: `wealist`, `business`, `기획자`, `DAU`, `MAU`

기획자/PM을 위한 비즈니스 메트릭 대시보드입니다. 기술적 지표보다 사용자 활동과 비즈니스 성과에 초점을 맞췄습니다.

**DAU/MAU 및 사용자 현황:**
| 패널 | 설명 |
|------|------|
| 현재 동시 접속자 | 실시간 활성 세션 수 |
| DAU (추정) | 일일 로그인 기준 추정치 |
| MAU (추정) | 월간 로그인 기준 추정치 |
| 전체 회원 수 | 총 가입자 수 |
| 오늘 신규 가입 | 당일 회원가입 수 |
| 전체 워크스페이스 | 워크스페이스 수 |

**Board 서비스 (프로젝트 & 보드):**
- 전체 프로젝트 수
- 전체 보드 수
- 오늘 생성된 프로젝트/보드
- 생성 추이 그래프

**Chat & Video 서비스:**
- 채팅 사용자, 활성 채팅방, 메시지 수
- 비디오 사용자, 진행 중 회의
- 활동 추이 그래프

**워크스페이스 현황:**
- 워크스페이스 생성 추이
- 전체 워크스페이스 수 추이

---

### 6. 서비스 상세 (service-detail.json)

**UID**: `service-detail`
**Tags**: `wealist`, `service`

개별 서비스를 심층 분석하기 위한 대시보드입니다. 드롭다운에서 서비스를 선택하여 사용합니다.

**Variables:**
- `$service`: 분석할 서비스 선택

**주요 섹션:**
- **서비스 상태**: 상태, RPS, 에러율, p95 응답시간, 처리 중 요청
- **트래픽 추이**: HTTP 상태코드별 요청, 응답 시간 분포 (p50/p95/p99)
- **엔드포인트별 성능**:
  - 요청량 Top 10
  - 느린 엔드포인트 p95 Top 10
- **로그**: 해당 서비스의 최근 로그 스트림

---

### 7. 로그 분석 & 디버깅 (log-analysis-dashboard.json)

**UID**: `log-analysis-dashboard`
**Tags**: `wealist`, `logs`, `debugging`, `로그`

Loki 기반 로그 분석 및 디버깅 전용 대시보드입니다.

**Variables:**
- `$service`: 서비스 선택 (다중 선택 가능)
- `$search`: 검색어 입력 (요청 ID, 사용자 ID 등)

**에러 현황:**
| 패널 | 설명 |
|------|------|
| 총 에러 수 | 선택 기간 내 에러 로그 수 |
| 심각한 에러 | Panic/Fatal 로그 수 |
| 경고 수 | Warning 로그 수 |
| 연결 문제 | Timeout/Connection refused 등 |

**서비스별 에러 분석:**
- 서비스별 에러 추이 (Stacked bar)
- 서비스별 HTTP 에러 (4xx/5xx)

**문제 API 탐지:**
- 5xx 에러 로그 (문제 API 찾기)
- 느린 요청/타임아웃 로그

**디버깅:**
- 키워드 검색 (요청 ID, 사용자 ID 등)
- 구조화된 에러/경고 로그 (JSON 파싱)
- 전체 로그 실시간 스트림

---

### 대시보드 사용 팁

1. **기획자**: 매일 기획자 대시보드에서 DAU/MAU 확인, 신규 가입 추이 모니터링
2. **인프라**: 인프라 대시보드를 메인 모니터에 띄워두고 에러율/응답시간 감시
3. **개발자**: 배포 후 개발자 대시보드에서 에러 추이 확인, 느린 API 최적화
4. **장애 대응**: 로그 분석 대시보드에서 에러 키워드 검색, 요청 ID 추적

### 대시보드 커스터마이징

대시보드 수정 후 영구 저장하려면:

1. Grafana UI에서 대시보드 수정
2. Dashboard settings > JSON Model 복사
3. `docker/monitoring/grafana/provisioning/dashboards/json/` 해당 파일 업데이트
4. 모니터링 스택 재시작 또는 30초 대기 (자동 업데이트)
