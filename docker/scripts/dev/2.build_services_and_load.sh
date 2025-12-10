#!/bin/bash
# 서비스 이미지 빌드 후 로컬 레지스트리에 푸시하는 스크립트
# Docker Hub rate limit 및 kind load 문제 완전 우회
# macOS bash 3.x 호환

set -e

REG_PORT="5001"
LOCAL_REG="localhost:${REG_PORT}"
TAG="${IMAGE_TAG:-latest}"  # 환경변수로 오버라이드 가능, 기본값 latest

# 색상 출력
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== 서비스 이미지 빌드 & 로컬 레지스트리 푸시 ==="
echo "로컬 레지스트리: ${LOCAL_REG}"
echo ""

# 레지스트리 실행 확인
if ! curl -s "http://${LOCAL_REG}/v2/" > /dev/null 2>&1; then
    echo -e "${RED}ERROR: 로컬 레지스트리가 실행 중이 아닙니다!${NC}"
    echo "먼저 ./0.setup-cluster.sh 를 실행하세요."
    exit 1
fi

# 프로젝트 루트로 이동 (스크립트는 docker/scripts/dev/ 에 위치)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$PROJECT_ROOT"
echo "Working directory: $PROJECT_ROOT"
echo ""

# 서비스 정보 (name|path|dockerfile)
ALL_SERVICES="auth-service|services/auth-service|Dockerfile
board-service|services/board-service|docker/Dockerfile
chat-service|services/chat-service|docker/Dockerfile
frontend|services/frontend|Dockerfile
noti-service|services/noti-service|docker/Dockerfile
storage-service|services/storage-service|docker/Dockerfile
user-service|services/user-service|docker/Dockerfile
video-service|services/video-service|docker/Dockerfile"

# 빌드할 서비스 선택
if [ $# -eq 0 ]; then
    BUILD_SERVICES="$ALL_SERVICES"
else
    BUILD_SERVICES=""
    for arg in "$@"; do
        line=$(echo "$ALL_SERVICES" | grep "^${arg}|" || true)
        if [ -n "$line" ]; then
            BUILD_SERVICES="${BUILD_SERVICES}${line}"$'\n'
        else
            echo -e "${RED}[ERROR] Unknown service: $arg${NC}"
        fi
    done
fi

echo "빌드 대상:"
echo "$BUILD_SERVICES" | while IFS='|' read -r name path dockerfile; do
    [ -n "$name" ] && echo "  - $name"
done
echo ""

# 빌드 및 푸시
echo "$BUILD_SERVICES" | while IFS='|' read -r name path dockerfile; do
    [ -z "$name" ] && continue

    IMAGE_NAME="${LOCAL_REG}/${name}:${TAG}"

    echo -e "${YELLOW}[BUILD] $name${NC}"
    echo "  Path: $path"
    echo "  Dockerfile: $dockerfile"
    echo "  Image: $IMAGE_NAME"

    # 빌드
    if docker build -t "$IMAGE_NAME" -f "$path/$dockerfile" "$path"; then
        echo -e "${GREEN}[SUCCESS] Built $IMAGE_NAME${NC}"
    else
        echo -e "${RED}[FAILED] Failed to build $name${NC}"
        continue
    fi

    # 로컬 레지스트리에 푸시
    echo "  Pushing to local registry..."
    if docker push "$IMAGE_NAME"; then
        echo -e "${GREEN}[SUCCESS] Pushed $IMAGE_NAME${NC}"
    else
        echo -e "${RED}[FAILED] Failed to push $name${NC}"
    fi

    echo ""
done

echo "=== 완료! ==="
echo ""
echo "로컬 레지스트리 이미지 확인:"
echo "  curl -s http://${LOCAL_REG}/v2/_catalog"
echo ""
echo "배포 명령어:"
echo "  make k8s-apply-registry"
echo ""
echo "또는 수동:"
echo "  kubectl apply -k k8s/overlays/develop-registry/all-services"
