# Build stage
FROM golang:1.24-alpine AS builder

# Install dependencies
RUN apk add --no-cache git

WORKDIR /app

# Copy go mod files
COPY go.mod ./

# Download dependencies and generate go.sum
RUN go mod download || true
RUN go mod tidy

# Copy source code
COPY . .

# Ensure dependencies are resolved
RUN go mod tidy

# Build binary
RUN CGO_ENABLED=0 GOOS=linux go build -o /video-service ./cmd/api/main.go

# Runtime stage
FROM alpine:3.19

# Install runtime dependencies
RUN apk add --no-cache ca-certificates tzdata wget

WORKDIR /app

# Copy binary from builder
COPY --from=builder /video-service .

# Copy config files
COPY configs/ ./configs/

# Expose port
EXPOSE 8004

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 -O /dev/null http://localhost:8004/health || exit 1

# Run
CMD ["./video-service"]
