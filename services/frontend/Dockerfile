# frontend/Dockerfile (프로덕션용, 자동 빌드 보장)

FROM node:20-alpine AS builder
WORKDIR /app

# 빌드 타임 환경변수 (VITE_* 변수는 빌드 시점에 주입됨)
# VITE_DEPLOYMENT_ENV: 'docker-compose' | 'k8s' | 'cloudfront'
# - docker-compose: 각 서비스별 포트로 직접 접근
# - k8s (기본값): Ingress가 라우팅 처리 (상대 경로 사용)
# - cloudfront: 별도 API 도메인 사용
ARG VITE_DEPLOYMENT_ENV=k8s
ARG VITE_API_BASE_URL=
ARG VITE_API_DOMAIN=
ENV VITE_DEPLOYMENT_ENV=${VITE_DEPLOYMENT_ENV}
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_API_DOMAIN=${VITE_API_DOMAIN}

RUN npm install -g pnpm
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# 빌드 실패 시 컨테이너 중단
RUN pnpm run build || (echo "Build failed. Check Vite config or env vars." && exit 1)

FROM nginx:stable-alpine
EXPOSE 80
COPY --from=builder /app/dist /usr/share/nginx/html
# SPA 라우팅을 위한 nginx 설정
COPY nginx.conf /etc/nginx/conf.d/default.conf
CMD ["nginx", "-g", "daemon off;"]