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
| noti-service | `/metrics` | `/health`, `/ready` |
| chat-service | - | `/health`, `/ready` |
| user-service | - | `/health`, `/ready` |
| storage-service | - | `/health`, `/ready` |
| video-service | - | `/health`, `/ready` |

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
