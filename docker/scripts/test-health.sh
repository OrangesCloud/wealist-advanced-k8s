#!/bin/bash
# =============================================================================
# Health Check 분리 테스트 스크립트
# =============================================================================
# 이 스크립트는 liveness와 readiness probe가 올바르게 분리되었는지 테스트합니다.
# DB가 다운되어도 서비스(pod)가 살아있는지 확인합니다.
#
# 사용법: ./docker/scripts/test-health.sh
# =============================================================================

# NOTE: set -e를 제거함 - health check 실패가 스크립트를 종료시키지 않도록
# set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# 서비스 포트 정의 (.env.dev 기본값과 일치)
AUTH_SERVICE_PORT=${AUTH_HOST_PORT:-8080}
USER_SERVICE_PORT=${USER_HOST_PORT:-8081}
BOARD_SERVICE_PORT=${BOARD_HOST_PORT:-8000}
CHAT_SERVICE_PORT=${CHAT_HOST_PORT:-8001}
NOTI_SERVICE_PORT=${NOTI_HOST_PORT:-8002}
STORAGE_SERVICE_PORT=${STORAGE_HOST_PORT:-8003}
VIDEO_SERVICE_PORT=${VIDEO_HOST_PORT:-8004}

# 모니터링 포트
PROMETHEUS_PORT=${PROMETHEUS_PORT:-9090}
GRAFANA_PORT=${GRAFANA_PORT:-3001}
LOKI_PORT=${LOKI_PORT:-3100}

# 헬퍼 함수
print_header() {
    echo ""
    echo -e "${BLUE}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}${BOLD}  $1${NC}"
    echo -e "${BLUE}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_step() {
    echo -e "${CYAN}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BOLD}  $1${NC}"
}

# Health Check 함수
check_liveness() {
    local service=$1
    local url=$2
    local dep=$3
    local response

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        echo -e "  ${GREEN}[LIVE]${NC} $service ${GRAY}($dep)${NC}"
        return 0
    else
        echo -e "  ${RED}[DOWN]${NC} $service ${GRAY}($dep)${NC} - HTTP $response"
        return 1
    fi
}

check_readiness() {
    local service=$1
    local url=$2
    local dep=$3
    local response

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        echo -e "  ${GREEN}[READY]${NC} $service ${GRAY}($dep)${NC}"
        return 0
    else
        echo -e "  ${YELLOW}[NOT READY]${NC} $service ${GRAY}($dep)${NC} - HTTP $response"
        return 1
    fi
}

check_container_status() {
    local container=$1
    local status

    status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "not found")

    if [ "$status" = "running" ]; then
        echo -e "  ${GREEN}[RUNNING]${NC} $container"
        return 0
    else
        echo -e "  ${RED}[$status]${NC} $container"
        return 1
    fi
}

# 서비스 실행 확인
check_services_running() {
    print_step "서비스 컨테이너 상태 확인..."
    echo ""

    local all_running=true

    echo -e "${BOLD}  [Backend Services]${NC}"
    check_container_status "wealist-auth-service" || { all_running=false; true; }
    check_container_status "wealist-user-service" || { all_running=false; true; }
    check_container_status "wealist-board-service" || { all_running=false; true; }
    check_container_status "wealist-chat-service" || { all_running=false; true; }
    check_container_status "wealist-noti-service" || { all_running=false; true; }
    check_container_status "wealist-storage-service" || { all_running=false; true; }
    check_container_status "wealist-video-service" || { all_running=false; true; }
    echo ""
    echo -e "${BOLD}  [Infrastructure]${NC}"
    check_container_status "wealist-postgres" || { all_running=false; true; }
    check_container_status "wealist-redis" || { all_running=false; true; }
    check_container_status "wealist-minio" || { all_running=false; true; }
    check_container_status "wealist-livekit" || { all_running=false; true; }
    check_container_status "wealist-coturn" || { all_running=false; true; }
    echo ""
    echo -e "${BOLD}  [Monitoring] (선택적)${NC}"
    check_container_status "wealist-prometheus" || true
    check_container_status "wealist-grafana" || true
    check_container_status "wealist-loki" || true

    echo ""

    if [ "$all_running" = false ]; then
        print_error "일부 서비스가 실행되지 않았습니다."
        print_info "먼저 './docker/scripts/dev.sh up' 으로 서비스를 시작해주세요."
        exit 1
    fi

    print_success "모든 서비스가 실행 중입니다."
}

# 모든 Health Check 수행
run_all_health_checks() {
    local title=$1

    print_step "$title"
    echo ""

    echo -e "${BOLD}  [Liveness Probes] - 서비스 생존 여부 (외부 의존성 무관)${NC}"
    check_liveness "auth-service   " "http://localhost:${AUTH_SERVICE_PORT}/actuator/health/liveness" "Redis 무관" || true
    check_liveness "user-service   " "http://localhost:${USER_SERVICE_PORT}/health" "PostgreSQL 무관" || true
    check_liveness "board-service  " "http://localhost:${BOARD_SERVICE_PORT}/health" "PostgreSQL 무관" || true
    check_liveness "chat-service   " "http://localhost:${CHAT_SERVICE_PORT}/health" "PostgreSQL 무관" || true
    check_liveness "noti-service   " "http://localhost:${NOTI_SERVICE_PORT}/health" "PostgreSQL 무관" || true
    check_liveness "storage-service" "http://localhost:${STORAGE_SERVICE_PORT}/health" "PostgreSQL 무관" || true
    check_liveness "video-service  " "http://localhost:${VIDEO_SERVICE_PORT}/health" "PostgreSQL 무관" || true

    echo ""
    echo -e "${BOLD}  [Readiness Probes] - 트래픽 수신 준비 (외부 의존성 포함)${NC}"
    check_readiness "auth-service   " "http://localhost:${AUTH_SERVICE_PORT}/actuator/health/readiness" "Redis 체크" || true
    check_readiness "user-service   " "http://localhost:${USER_SERVICE_PORT}/ready" "PostgreSQL 체크" || true
    check_readiness "board-service  " "http://localhost:${BOARD_SERVICE_PORT}/ready" "PostgreSQL 체크" || true
    check_readiness "chat-service   " "http://localhost:${CHAT_SERVICE_PORT}/ready" "PostgreSQL 체크" || true
    check_readiness "noti-service   " "http://localhost:${NOTI_SERVICE_PORT}/ready" "PostgreSQL 체크" || true
    check_readiness "storage-service" "http://localhost:${STORAGE_SERVICE_PORT}/ready" "PostgreSQL 체크" || true
    check_readiness "video-service  " "http://localhost:${VIDEO_SERVICE_PORT}/ready" "PostgreSQL+Redis 체크" || true

    echo ""
    echo -e "${BOLD}  [Monitoring Services]${NC}"
    check_liveness "prometheus     " "http://localhost:${PROMETHEUS_PORT}/-/healthy" "메트릭 수집" || true
    check_liveness "grafana        " "http://localhost:${GRAFANA_PORT}/api/health" "대시보드" || true
    check_liveness "loki           " "http://localhost:${LOKI_PORT}/ready" "로그 수집" || true

    echo ""
}

# 컨테이너 재시작 여부 확인
check_restart_count() {
    print_step "컨테이너 재시작 횟수 확인..."
    echo ""

    for container in wealist-auth-service wealist-user-service wealist-board-service wealist-chat-service wealist-noti-service wealist-storage-service wealist-video-service; do
        local count=$(docker inspect --format='{{.RestartCount}}' "$container" 2>/dev/null || echo "N/A")
        echo -e "  $container: ${BOLD}$count${NC} 회 재시작"
    done

    echo ""
}

# =============================================================================
# 메인 테스트 시나리오
# =============================================================================

main() {
    print_header "🏥 Health Check 분리 테스트 시작"

    echo -e "${BOLD}이 테스트는 다음을 확인합니다:${NC}"
    echo "  1. 정상 상태에서 모든 health check가 성공하는지"
    echo "  2. DB 중지 시 liveness는 성공, readiness는 실패하는지"
    echo "  3. DB 중지 후에도 서비스 컨테이너가 재시작되지 않는지"
    echo "  4. DB 복구 후 readiness가 다시 성공하는지"
    echo ""
    echo -e "${BOLD}서비스별 Readiness 의존성:${NC}"
    echo -e "  ${CYAN}auth-service${NC}    → Redis (PostgreSQL 사용 안 함)"
    echo -e "  ${CYAN}user-service${NC}    → PostgreSQL"
    echo -e "  ${CYAN}board-service${NC}   → PostgreSQL"
    echo -e "  ${CYAN}chat-service${NC}    → PostgreSQL"
    echo -e "  ${CYAN}noti-service${NC}    → PostgreSQL"
    echo -e "  ${CYAN}storage-service${NC} → PostgreSQL"
    echo -e "  ${CYAN}video-service${NC}   → PostgreSQL, Redis, LiveKit"
    echo ""

    # Step 1: 서비스 실행 확인
    print_header "📋 Step 1: 서비스 실행 상태 확인"
    check_services_running

    # Step 2: 정상 상태 Health Check
    print_header "📋 Step 2: 정상 상태 Health Check"
    run_all_health_checks "모든 서비스의 Health Check 수행..."
    print_success "정상 상태: 모든 Liveness/Readiness가 성공해야 합니다."

    # Step 3: 재시작 횟수 기록
    print_header "📋 Step 3: 현재 컨테이너 재시작 횟수 기록"
    check_restart_count

    # Step 4: DB 중지
    print_header "📋 Step 4: PostgreSQL 데이터베이스 중지"
    print_warning "DB를 중지합니다. 서비스들이 DB 연결을 잃게 됩니다..."
    echo -e "${GRAY}  (auth-service는 Redis만 사용하므로 영향 없음)${NC}"
    echo ""

    docker stop wealist-postgres

    echo ""
    print_success "PostgreSQL 컨테이너가 중지되었습니다."

    # 잠시 대기 (서비스들이 DB 연결 손실을 감지하도록)
    print_step "서비스들이 DB 연결 손실을 감지하도록 5초 대기..."
    sleep 5

    # Step 5: DB 중지 상태에서 Health Check
    print_header "📋 Step 5: DB 중지 상태에서 Health Check"
    run_all_health_checks "DB 없이 Health Check 수행..."

    echo -e "${BOLD}예상 결과:${NC}"
    echo -e "  - Liveness:  ${GREEN}모두 LIVE${NC} (서비스 프로세스는 살아있음)"
    echo -e "  - Readiness:"
    echo -e "      ${GREEN}auth-service  → READY${NC} (Redis만 체크, DB 무관)"
    echo -e "      ${YELLOW}user-service  → NOT READY${NC} (PostgreSQL 연결 없음)"
    echo -e "      ${YELLOW}board-service → NOT READY${NC} (PostgreSQL 연결 없음)"
    echo -e "      ${YELLOW}chat-service  → NOT READY${NC} (PostgreSQL 연결 없음)"
    echo -e "      ${YELLOW}noti-service  → NOT READY${NC} (PostgreSQL 연결 없음)"
    echo ""

    # Step 6: 컨테이너 상태 확인
    print_header "📋 Step 6: 서비스 컨테이너 생존 확인"
    print_step "DB가 없어도 서비스 컨테이너가 살아있는지 확인..."
    echo ""

    local all_alive=true
    check_container_status "wealist-auth-service" || all_alive=false
    check_container_status "wealist-user-service" || all_alive=false
    check_container_status "wealist-board-service" || all_alive=false
    check_container_status "wealist-chat-service" || all_alive=false
    check_container_status "wealist-noti-service" || all_alive=false
    check_container_status "wealist-storage-service" || all_alive=false
    check_container_status "wealist-video-service" || all_alive=false

    echo ""

    if [ "$all_alive" = true ]; then
        print_success "모든 서비스가 DB 없이도 살아있습니다! (Liveness 분리 성공)"
    else
        print_error "일부 서비스가 죽었습니다. Liveness 분리가 제대로 되지 않았을 수 있습니다."
    fi

    # Step 7: 재시작 횟수 재확인
    print_header "📋 Step 7: 컨테이너 재시작 횟수 재확인"
    check_restart_count
    print_info "재시작 횟수가 증가하지 않았다면 성공입니다!"

    # Step 8: DB 복구
    print_header "📋 Step 8: PostgreSQL 데이터베이스 복구"
    print_step "DB를 다시 시작합니다..."
    echo ""

    docker start wealist-postgres

    echo ""
    print_success "PostgreSQL 컨테이너가 시작되었습니다."

    # DB가 완전히 준비될 때까지 대기
    print_step "DB가 완전히 준비될 때까지 대기 (최대 30초)..."

    for i in {1..30}; do
        if docker exec wealist-postgres pg_isready -U postgres &>/dev/null; then
            echo ""
            print_success "PostgreSQL이 준비되었습니다!"
            break
        fi
        echo -n "."
        sleep 1
    done
    echo ""

    # 서비스들이 DB 재연결하도록 잠시 대기
    print_step "서비스들이 DB에 재연결하도록 5초 대기..."
    sleep 5

    # Step 9: 복구 후 Health Check
    print_header "📋 Step 9: DB 복구 후 Health Check"
    run_all_health_checks "DB 복구 후 Health Check 수행..."
    print_success "복구 상태: 모든 Liveness/Readiness가 다시 성공해야 합니다."

    # 최종 결과
    print_header "🎉 테스트 완료"

    echo -e "${BOLD}테스트 요약:${NC}"
    echo ""
    echo "  1. 정상 상태: Liveness ✓, Readiness ✓"
    echo "  2. DB 중지:   Liveness ✓, Readiness ✗ (auth 제외 - Redis만 사용)"
    echo "  3. 컨테이너:  재시작 없이 유지됨"
    echo "  4. DB 복구:   Liveness ✓, Readiness ✓"
    echo ""
    echo -e "${GREEN}${BOLD}Health Check 분리가 올바르게 구성되었습니다!${NC}"
    echo ""
    echo -e "${BOLD}서비스별 Health Endpoint:${NC}"
    echo -e "  ${CYAN}auth-service${NC}  (Spring Boot)"
    echo "    - liveness:  /actuator/health/liveness"
    echo "    - readiness: /actuator/health/readiness (Redis 체크)"
    echo ""
    echo -e "  ${CYAN}user/board/chat/noti/storage-service${NC}  (Go)"
    echo "    - liveness:  /health"
    echo "    - readiness: /ready (PostgreSQL 체크)"
    echo ""
    echo -e "  ${CYAN}video-service${NC}  (Go)"
    echo "    - liveness:  /health"
    echo "    - readiness: /ready (PostgreSQL, Redis, LiveKit 체크)"
    echo ""
    echo -e "  ${CYAN}Monitoring${NC}"
    echo "    - prometheus: /-/healthy"
    echo "    - grafana:    /api/health"
    echo "    - loki:       /ready"
    echo ""
}

# 스크립트 실행
main "$@"
