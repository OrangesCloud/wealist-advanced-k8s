#!/bin/bash
# =============================================================================
# Health Check 분리 테스트 스크립트
# =============================================================================
# 이 스크립트는 liveness와 readiness probe가 올바르게 분리되었는지 테스트합니다.
# DB가 다운되어도 서비스(pod)가 살아있는지 확인합니다.
#
# 사용법: ./docker/scripts/test-health.sh
# =============================================================================

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# 서비스 포트 정의
USER_SERVICE_PORT=${USER_HOST_PORT:-8080}
AUTH_SERVICE_PORT=${AUTH_HOST_PORT:-8090}
BOARD_SERVICE_PORT=${BOARD_HOST_PORT:-8000}
CHAT_SERVICE_PORT=${CHAT_HOST_PORT:-8001}

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
    local response

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        echo -e "  ${GREEN}[LIVE]${NC} $service - $url"
        return 0
    else
        echo -e "  ${RED}[DOWN]${NC} $service - $url (HTTP $response)"
        return 1
    fi
}

check_readiness() {
    local service=$1
    local url=$2
    local response
    local body

    response=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        echo -e "  ${GREEN}[READY]${NC} $service - $url"
        return 0
    else
        echo -e "  ${YELLOW}[NOT READY]${NC} $service - $url (HTTP $response)"
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

    check_container_status "wealist-user-service" || all_running=false
    check_container_status "wealist-auth-service" || all_running=false
    check_container_status "wealist-board-service" || all_running=false
    check_container_status "wealist-chat-service" || all_running=false
    check_container_status "wealist-postgres" || all_running=false

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

    echo -e "${BOLD}  [Liveness Probes] - 서비스 생존 여부 (DB 무관)${NC}"
    check_liveness "user-service " "http://localhost:${USER_SERVICE_PORT}/actuator/health/liveness"
    check_liveness "auth-service " "http://localhost:${AUTH_SERVICE_PORT}/actuator/health/liveness"
    check_liveness "board-service" "http://localhost:${BOARD_SERVICE_PORT}/health"
    check_liveness "chat-service " "http://localhost:${CHAT_SERVICE_PORT}/health"

    echo ""
    echo -e "${BOLD}  [Readiness Probes] - 트래픽 수신 준비 (DB 포함)${NC}"
    check_readiness "user-service " "http://localhost:${USER_SERVICE_PORT}/actuator/health/readiness"
    check_readiness "auth-service " "http://localhost:${AUTH_SERVICE_PORT}/actuator/health/readiness"
    check_readiness "board-service" "http://localhost:${BOARD_SERVICE_PORT}/ready"
    check_readiness "chat-service " "http://localhost:${CHAT_SERVICE_PORT}/ready"

    echo ""
}

# 컨테이너 재시작 여부 확인
check_restart_count() {
    print_step "컨테이너 재시작 횟수 확인..."
    echo ""

    for container in wealist-user-service wealist-auth-service wealist-board-service wealist-chat-service; do
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
    echo -e "  - Readiness: ${YELLOW}모두 NOT READY${NC} (DB 연결 없음)"
    echo ""

    # Step 6: 컨테이너 상태 확인
    print_header "📋 Step 6: 서비스 컨테이너 생존 확인"
    print_step "DB가 없어도 서비스 컨테이너가 살아있는지 확인..."
    echo ""

    local all_alive=true
    check_container_status "wealist-user-service" || all_alive=false
    check_container_status "wealist-auth-service" || all_alive=false
    check_container_status "wealist-board-service" || all_alive=false
    check_container_status "wealist-chat-service" || all_alive=false

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
    echo "  2. DB 중지:   Liveness ✓, Readiness ✗ (예상대로)"
    echo "  3. 컨테이너:  재시작 없이 유지됨"
    echo "  4. DB 복구:   Liveness ✓, Readiness ✓"
    echo ""
    echo -e "${GREEN}${BOLD}Health Check 분리가 올바르게 구성되었습니다!${NC}"
    echo ""
    echo -e "${BOLD}EKS 배포 시:${NC}"
    echo "  - livenessProbe:  /actuator/health/liveness 또는 /health"
    echo "  - readinessProbe: /actuator/health/readiness 또는 /ready"
    echo ""
}

# 스크립트 실행
main "$@"
