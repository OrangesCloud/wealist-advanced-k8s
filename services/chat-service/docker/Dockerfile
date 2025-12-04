# Build stage
FROM golang:1.25-alpine AS builder

WORKDIR /app

# Install git for dependencies
RUN apk add --no-cache git

# Copy go mod files
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o chat-service ./cmd/server

# Final stage
FROM alpine:latest

RUN apk --no-cache add ca-certificates wget

WORKDIR /root/

# Copy the binary from builder
COPY --from=builder /app/chat-service .

# Expose port
EXPOSE 8001

# Health check - liveness probe
# /health: DB 연결과 무관하게 서비스 자체 상태만 체크 (EKS에서 pod 생존 보장)
# readiness는 /ready 엔드포인트로 별도 확인 가능
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8001/health || exit 1

# Run the application
CMD ["./chat-service"]