# 🚀 Kanban Frontend

Vite + React + TypeScript + Tailwind CSS + pnpm

## 빠른 시작

### 1. pnpm 설치

```bash
npm install -g pnpm
```

### 2. 의존성 설치

```bash
pnpm install
```

### 3. 개발 서버 실행

```bash
pnpm dev
```

### 4. 빌드

```bash
pnpm build
```

### 5. 미리보기

```bash
pnpm preview
```

## 📁 구조

```
src/
├── core/              # 설정 (API, 환경)
│   ├── api/
│   │   ├── client.ts  # Axios 인스턴스
│   │   ├── auth.ts    # 인증 API
│   │   └── board.ts   # 보드 API
│   ├── constants.ts   # 상수
│   └── env.ts         # 환경 변수
├── types/             # 타입 정의
├── pages/             # 페이지
├── components/        # 컴포넌트
├── styles/            # 스타일
├── App.tsx
└── main.tsx
```

## 🌍 환경 변수

`.env` 파일 생성 (`.env.example` 참조):

```env
VITE_AUTH_SERVICE_URL=http://localhost:8001
VITE_BOARD_SERVICE_URL=http://localhost:8002
VITE_USER_SERVICE_URL=http://localhost:8003
```

## 🐳 Docker

```bash
docker build -t kanban-frontend .
docker run -p 3000:3000 kanban-frontend
```

## 📚 pnpm 명령어

```bash
# 개발 서버
pnpm dev

# 빌드
pnpm build

# 타입 체크
pnpm type-check

# 모든 의존성 업데이트
pnpm update
```

## ✅ 특징

- ⚡ **Vite** - 빠른 번들링
- 🔄 **pnpm** - 빠른 패키지 설치 + 디스크 효율
- 🎨 **Tailwind CSS** - 유틸리티 기반 스타일
- 🔒 **TypeScript** - 완전한 타입 안정성
- 📦 **번들 최적화** - 자동 코드 스플리팅 (50KB gzipped)
- 🔌 **Axios** - HTTP 클라이언트
