#!/bin/bash
# =============================================================================
# Swagger Documentation Generator (Hash-based)
# =============================================================================
# 소스 코드 변경 시에만 swagger 문서를 재생성합니다.
# 해시 파일로 변경 여부를 추적합니다.
#
# 사용법:
#   ./docker/scripts/generate-swagger.sh [service]
#   ./docker/scripts/generate-swagger.sh all
#   ./docker/scripts/generate-swagger.sh --force  # 강제 재생성
# =============================================================================

set -e

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 프로젝트 루트
cd "$(dirname "$0")/../.."
ROOT_DIR=$(pwd)

# swag 설치 확인
check_swag() {
    if ! command -v swag &> /dev/null; then
        echo -e "${YELLOW}⚠️  swag이 설치되어 있지 않습니다. 설치 중...${NC}"
        go install github.com/swaggo/swag/cmd/swag@latest
        echo -e "${GREEN}✅ swag 설치 완료${NC}"
    fi
}

# 해시 계산 (Go 파일들의 swagger 어노테이션 기반)
calculate_hash() {
    local service_dir=$1
    # Go 파일들 중 swagger 어노테이션(@)이 있는 파일들의 해시
    find "$service_dir" -name "*.go" -type f -exec grep -l "@" {} \; 2>/dev/null | \
        sort | xargs cat 2>/dev/null | md5sum | cut -d' ' -f1
}

# 서비스별 swagger 생성
generate_swagger() {
    local service_name=$1
    local service_dir=$2
    local force=$3

    if [ ! -d "$service_dir" ]; then
        echo -e "${YELLOW}⚠️  $service_name 디렉토리가 없습니다: $service_dir${NC}"
        return
    fi

    local hash_file="$service_dir/.swagger-hash"
    local current_hash=$(calculate_hash "$service_dir")
    local stored_hash=""

    if [ -f "$hash_file" ]; then
        stored_hash=$(cat "$hash_file")
    fi

    # 해시 비교 (force 옵션 시 무조건 생성)
    if [ "$force" != "true" ] && [ "$current_hash" == "$stored_hash" ]; then
        echo -e "${BLUE}⏭️  $service_name: 변경 없음 (스킵)${NC}"
        return
    fi

    echo -e "${YELLOW}🔄 $service_name: Swagger 생성 중...${NC}"

    cd "$service_dir"

    # swag init 실행
    if swag init -g cmd/api/main.go -o docs --parseDependency --parseInternal 2>/dev/null; then
        # 해시 저장
        echo "$current_hash" > "$hash_file"
        echo -e "${GREEN}✅ $service_name: Swagger 생성 완료${NC}"
    else
        echo -e "${YELLOW}⚠️  $service_name: Swagger 생성 실패 (어노테이션 확인 필요)${NC}"
    fi

    cd "$ROOT_DIR"
}

# 메인 로직
main() {
    local target=${1:-all}
    local force="false"

    if [ "$target" == "--force" ] || [ "$2" == "--force" ]; then
        force="true"
        if [ "$target" == "--force" ]; then
            target="all"
        fi
    fi

    check_swag

    echo -e "${BLUE}📝 Swagger 문서 생성 (해시 기반)${NC}"
    if [ "$force" == "true" ]; then
        echo -e "${YELLOW}   --force: 모든 서비스 강제 재생성${NC}"
    fi
    echo ""

    case $target in
        all)
            generate_swagger "user-service" "$ROOT_DIR/user-service" "$force"
            generate_swagger "board-service" "$ROOT_DIR/board-service" "$force"
            generate_swagger "chat-service" "$ROOT_DIR/chat-service" "$force"
            generate_swagger "noti-service" "$ROOT_DIR/noti-service" "$force"
            generate_swagger "storage-service" "$ROOT_DIR/services/storage-service" "$force"
            generate_swagger "video-service" "$ROOT_DIR/services/video-service" "$force"
            ;;
        user-service|user)
            generate_swagger "user-service" "$ROOT_DIR/user-service" "$force"
            ;;
        board-service|board)
            generate_swagger "board-service" "$ROOT_DIR/board-service" "$force"
            ;;
        chat-service|chat)
            generate_swagger "chat-service" "$ROOT_DIR/chat-service" "$force"
            ;;
        noti-service|noti)
            generate_swagger "noti-service" "$ROOT_DIR/noti-service" "$force"
            ;;
        storage-service|storage)
            generate_swagger "storage-service" "$ROOT_DIR/services/storage-service" "$force"
            ;;
        video-service|video)
            generate_swagger "video-service" "$ROOT_DIR/services/video-service" "$force"
            ;;
        *)
            echo "사용법: $0 [service|all] [--force]"
            echo ""
            echo "서비스:"
            echo "  all            - 모든 Go 서비스"
            echo "  user-service   - User Service"
            echo "  board-service  - Board Service"
            echo "  chat-service   - Chat Service"
            echo "  noti-service   - Notification Service"
            echo "  storage-service - Storage Service"
            echo "  video-service  - Video Service"
            echo ""
            echo "옵션:"
            echo "  --force      - 변경 여부와 관계없이 강제 재생성"
            exit 1
            ;;
    esac

    echo ""
    echo -e "${GREEN}✅ Swagger 생성 완료${NC}"
}

main "$@"
