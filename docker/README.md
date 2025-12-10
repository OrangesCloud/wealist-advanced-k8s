# weAlist Docker Environment Guide

> **Note**: 상세한 Docker 환경 가이드는 [.kiro/docs/DOCKER_ENVIRONMENT_GUIDE.md](../.kiro/docs/DOCKER_ENVIRONMENT_GUIDE.md)로 이동되었습니다.

weAlist 프로젝트의 Docker 환경 설정 가이드입니다.

## 디렉토리 구조

```
docker/
├── compose/                   # Docker Compose 파일
│   ├── docker-compose.yml     # 기본 서비스 (모든 환경 공통)
│   ├── docker-compose.dev.yml # 개발 환경 오버라이드
│   └── docker-compose.prod.yml # 프로덕션 환경 오버라이드
├── env/                       # 환경변수 파일
│   ├── .env.dev.example       # 개발 환경 템플릿
│   └── .env.prod.example      # 프로덕션 환경 템플릿
├── init/                      # 초기화 스크립트
│   └── postgres/              # PostgreSQL 초기화
├── scripts/                   # 유틸리티 스크립트
│   ├── dev.sh                 # 개발 환경 스크립트
│   └── prod.sh                # 프로덕션 환경 스크립트
└── README.md                  # 현재 문서
```

## 빠른 시작 (개발 환경)

### 1. 환경변수 파일 생성

```bash
# 템플릿 파일 복사
cp docker/env/.env.dev.example docker/env/.env.dev

# .env.dev 파일에서 필요한 값 수정 (특히 OAuth 관련)
```

### 2. 개발 환경 시작

```bash
# 방법 1: 스크립트 사용 (권장)
./docker/scripts/dev.sh up

# 방법 2: 직접 docker compose 명령
docker compose --env-file docker/env/.env.dev \
  -f docker/compose/docker-compose.yml \
  -f docker/compose/docker-compose.dev.yml \
  up
```

### 3. 서비스 접속

개발 환경에서 다음 포트로 서비스에 접근할 수 있습니다:

- **Frontend**: http://localhost:3000
- **User Service API**: http://localhost:8080
- **User Service Swagger**: http://localhost:8080/swagger-ui/index.html
- **Board Service API**: http://localhost:8000
- **Board Service Swagger**: http://localhost:8000/swagger/index.html
- **PostgreSQL**: localhost:5432
- **Redis**: localhost:6379
- **MinIO Console**: http://localhost:9001 (로그인: minioadmin / minioadmin123)
- **MinIO API**: http://localhost:9000

## 환경변수 파일 설명

### Docker Compose와 환경변수 처리

Docker Compose는 환경변수를 **두 가지 다른 방식**으로 처리합니다:

1. **Compose 파일 자체 변수 치환** (`${VARIABLE}`):
   - 최상위 `--env-file` 옵션으로 로드된 파일 사용
   - `docker-compose.yml` 파일 내에서 `${USER_DB_NAME}` 같은 변수

2. **컨테이너 환경변수**:
   - `env_file` 지시어 또는 `environment` 섹션으로 전달
   - 컨테이너 내부 프로세스가 사용

### 예: 환경변수 전달 방식

올바른 **두 가지 다른 방식**으로 환경변수를 전달해야 합니다:

#### 방법 1: 스크립트 사용 (권장)

```bash
# 개발 환경
./docker/scripts/dev.sh up

# 프로덕션 환경
./docker/scripts/prod.sh up
```

스크립트는 자동으로 `--env-file` 옵션을 포함시킵니다.

#### 방법 2: 직접 docker compose 명령

```bash
# --env-file 옵션을 반드시 먼저 지정해야 합니다!
docker compose --env-file docker/env/.env.dev \
  -f docker/compose/docker-compose.yml \
  -f docker/compose/docker-compose.dev.yml \
  up
```

**주의**: `--env-file` 옵션 없이 실행하면 환경변수 누락 경고가 발생합니다:

```
WARN[0000] The "POSTGRES_SUPERUSER" variable is not set. Defaulting to a blank string.
WARN[0000] The "USER_DB_NAME" variable is not set. Defaulting to a blank string.
```

## 스크립트 명령어

### 개발 환경 (dev.sh)

```bash
# 서비스 시작 (백그라운드) - 기본
./docker/scripts/dev.sh up

# 서비스 시작 (포그라운드)
./docker/scripts/dev.sh up-fg

# 서비스 중지
./docker/scripts/dev.sh down

# 서비스 재시작
./docker/scripts/dev.sh restart

# 로그 확인 (전체)
./docker/scripts/dev.sh logs

# 로그 확인 (특정 서비스)
./docker/scripts/dev.sh logs user-service

# 실행 중인 서비스 확인
./docker/scripts/dev.sh ps

# 이미지 다시 빌드
./docker/scripts/dev.sh build

# 빌드 후 시작
./docker/scripts/dev.sh rebuild

# 컨테이너 접속
./docker/scripts/dev.sh exec user-service

# 모든 서비스 및 볼륨 정리
./docker/scripts/dev.sh clean
```

### 프로덕션 환경 (prod.sh)

```bash
# 서비스 시작 (로그 레벨 제한)
./docker/scripts/prod.sh up

# 서비스 중지
./docker/scripts/prod.sh down

# 서비스 재시작
./docker/scripts/prod.sh restart [service]

# 로그 확인 (최근 100줄)
./docker/scripts/prod.sh logs [service] [lines]

# 서비스 상태 확인
./docker/scripts/prod.sh status

# 헬스체크 확인
./docker/scripts/prod.sh health

# 최신 이미지 다운로드
./docker/scripts/prod.sh pull

# 무중단 업데이트 (이미지 pull + 재시작)
./docker/scripts/prod.sh update
```

## 문제 해결

### 1. 환경변수 누락 경고

**증상**:
```
WARN[0000] The "POSTGRES_SUPERUSER" variable is not set. Defaulting to a blank string.
```

**원인**:
- `--env-file` 옵션 없이 docker compose 실행
- `.env.dev` 파일이 존재하지 않거나 환경변수 누락

**해결**:
```bash
# 1. 환경변수 파일 존재 확인
ls -la docker/env/.env.dev

# 2. 스크립트 사용 (권장, 자동으로 --env-file 포함)
./docker/scripts/dev.sh up

# 3. 또는 --env-file 옵션 명시
docker compose --env-file docker/env/.env.dev \
  -f docker/compose/docker-compose.yml \
  -f docker/compose/docker-compose.dev.yml \
  config
```

### 2. 환경변수 파일이 없음

**증상**:
```
Error: docker/env/.env.dev 파일이 존재하지 않습니다.
```

**해결**:
```bash
# 템플릿 파일 복사
cp docker/env/.env.dev.example docker/env/.env.dev

# 필요한 값 수정 (특히 OAuth 관련)
vi docker/env/.env.dev
```

### 3. 서비스 헬스체크 실패

**user-service unhealthy**:
- JWT 환경변수 누락 확인: `JWT_ACCESS_TOKEN_EXPIRATION_MS`, `JWT_REFRESH_TOKEN_EXPIRATION_MS`
- `.env.dev` 파일이 제대로 생성되었는지 확인
- 로그 확인: `./docker/scripts/dev.sh logs user-service`

### 4. 설정 확인

전체 컴포즈 설정 확인:

```bash
# 스크립트로 최종 설정 확인
docker compose --env-file docker/env/.env.dev \
  -f docker/compose/docker-compose.yml \
  -f docker/compose/docker-compose.dev.yml \
  config

# 특정 변수 확인
docker compose --env-file docker/env/.env.dev \
  -f docker/compose/docker-compose.yml \
  -f docker/compose/docker-compose.dev.yml \
  config | grep -A 5 "POSTGRES_HOST"
```

### 5. 컨테이너 내부 환경변수 확인

```bash
# 컨테이너 내부의 환경변수 확인
docker compose --env-file docker/env/.env.dev \
  -f docker/compose/docker-compose.yml \
  -f docker/compose/docker-compose.dev.yml \
  run --rm user-service sh -c 'printenv | grep POSTGRES'
```

## 네트워크 구조

### 서비스별 네트워크

```
┌─────────────────────────────────────┐
│        frontend-net                 │
│  ┌──────────┐      ┌──────────┐    │
│  │ Frontend │ ───▶ │ Backend  │    │
│  │          │      │ Services │    │
│  └──────────┘      └────┬─────┘    │
└─────────────────────────┼──────────┘
                          │
                          ▼
┌─────────────────────────┼──────────┐
│       backend-net       │          │
│                    ┌────┴─────┐    │
│                    │ Backend  │    │
│                    │ Services │    │
│                    └────┬─────┘    │
└─────────────────────────┼──────────┘
                          │
                          ▼
┌─────────────────────────┼──────────┐
│      database-net       │          │
│ (internal - 외부 접근 불가)        │
│                    ┌────┴─────┐    │
│                    │ Database │    │
│                    │  Redis   │    │
│                    └──────────┘    │
└─────────────────────────────────────┘
```

### 볼륨

- `wealist-postgres-data`: PostgreSQL 데이터 영구 저장
- `wealist-redis-data`: Redis 데이터 영구 저장
- `minio-data`: MinIO 객체 스토리지 데이터 (개발 환경 전용)

## 보안 고려사항

### 개발 환경

- 모든 포트를 호스트에 노출 (디버깅 편의성)
- `POSTGRES_HOST_AUTH_METHOD=trust` 사용 (로컬 개발 편의성)
- 간단한 패스워드 사용 가능
- 상세한 로그 출력

### 프로덕션 환경

- 최소한의 포트만 노출
- 강력한 패스워드 사용
- 데이터베이스 네트워크는 internal (외부 접근 불가)
- 로그 레벨 제한
- 리소스 제한 설정

## 참고 자료

- [Docker Compose 공식 문서](https://docs.docker.com/compose/)
- [프로젝트 메인 README](../README.md)
- [API 문서](./.claude/api-user-documentation.md)
