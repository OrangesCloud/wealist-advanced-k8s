#!/bin/bash
# =============================================================================
# Wealist 멀티레포 마이그레이션 스크립트
#
# 사용법:
#   ./scripts/migrate-to-multirepo.sh <service-name> <new-repo-path>
#
# 예시:
#   ./scripts/migrate-to-multirepo.sh board-service ~/repos/board-service
# =============================================================================

set -e

SERVICE_NAME=$1
NEW_REPO_PATH=$2
COMMON_MODULE="github.com/wealist/common"
NEW_COMMON_MODULE="${NEW_COMMON_MODULE:-github.com/YOUR_ORG/wealist-go-common}"

if [ -z "$SERVICE_NAME" ] || [ -z "$NEW_REPO_PATH" ]; then
    echo "Usage: $0 <service-name> <new-repo-path>"
    echo ""
    echo "Environment variables:"
    echo "  NEW_COMMON_MODULE - 새 공통 라이브러리 모듈 경로"
    echo "                      (기본값: github.com/YOUR_ORG/wealist-go-common)"
    echo ""
    echo "Example:"
    echo "  NEW_COMMON_MODULE=github.com/myorg/go-common $0 board-service ~/repos/board-service"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MONO_REPO_ROOT="$(dirname "$SCRIPT_DIR")"
SERVICE_PATH="$MONO_REPO_ROOT/services/$SERVICE_NAME"

if [ ! -d "$SERVICE_PATH" ]; then
    echo "Error: Service not found at $SERVICE_PATH"
    exit 1
fi

echo "=== Wealist 멀티레포 마이그레이션 ==="
echo "서비스: $SERVICE_NAME"
echo "소스: $SERVICE_PATH"
echo "대상: $NEW_REPO_PATH"
echo "공통 모듈: $NEW_COMMON_MODULE"
echo ""

# 1. 새 레포 디렉토리 생성
echo "[1/5] 새 레포 디렉토리 생성..."
mkdir -p "$NEW_REPO_PATH"

# 2. 서비스 파일 복사
echo "[2/5] 서비스 파일 복사..."
cp -r "$SERVICE_PATH"/* "$NEW_REPO_PATH/"

# 3. go.work 제거 (있으면)
echo "[3/5] go.work 제거..."
rm -f "$NEW_REPO_PATH/go.work"
rm -f "$NEW_REPO_PATH/go.work.sum"

# 4. import 경로 업데이트
echo "[4/5] import 경로 업데이트..."
if [ "$COMMON_MODULE" != "$NEW_COMMON_MODULE" ]; then
    find "$NEW_REPO_PATH" -name "*.go" -exec sed -i "s|$COMMON_MODULE|$NEW_COMMON_MODULE|g" {} \;
    echo "  - $COMMON_MODULE → $NEW_COMMON_MODULE"
fi

# 5. go.mod 정리
echo "[5/5] go.mod 정리..."
cd "$NEW_REPO_PATH"

# replace 지시문 제거
if [ -f go.mod ]; then
    sed -i '/^replace.*wealist\/common/d' go.mod
    echo "  - replace 지시문 제거됨"
fi

echo ""
echo "=== 마이그레이션 완료 ==="
echo ""
echo "다음 단계:"
echo "  1. cd $NEW_REPO_PATH"
echo "  2. git init && git add . && git commit -m 'Initial commit'"
echo "  3. go mod tidy  # 의존성 다운로드"
echo "  4. go build ./...  # 빌드 테스트"
echo ""
echo "공통 라이브러리 의존성 추가 (아직 안 했다면):"
echo "  go get $NEW_COMMON_MODULE@latest"
echo ""
