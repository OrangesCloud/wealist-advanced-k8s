# frontend/Dockerfile (프로덕션용, 자동 빌드 보장)

FROM node:20-alpine AS builder
WORKDIR /app
RUN npm install -g pnpm
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# 빌드 실패 시 컨테이너 중단
RUN pnpm run build || (echo "Build failed. Check Vite config or env vars." && exit 1)

FROM nginx:stable-alpine
EXPOSE 80
COPY --from=builder /app/dist /usr/share/nginx/html
CMD ["nginx", "-g", "daemon off;"]