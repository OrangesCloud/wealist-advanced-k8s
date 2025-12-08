# Builder stage
FROM golang:1.24-bookworm AS builder

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates tzdata && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy go mod files
COPY go.mod go.sum* ./

# Copy source code first for go mod tidy
COPY . .

# Download dependencies and generate go.sum if missing
RUN go mod tidy && go mod download

# Swagger generation disabled for faster builds
# RUN if [ ! -f docs/swagger.json ]; then \
#         go install github.com/swaggo/swag/cmd/swag@latest && \
#         swag init -g cmd/api/main.go -o docs --parseDependency --parseInternal; \
#     else \
#         echo "Swagger docs already exist, skipping generation"; \
#     fi

# Build the application
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-w -s" -o user-api ./cmd/api

# Runtime stage
FROM alpine:latest

# Install runtime dependencies
RUN apk --no-cache add ca-certificates tzdata wget

# Create non-root user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

# Set working directory
WORKDIR /home/appuser

# Copy binary from builder
COPY --from=builder --chown=appuser:appuser /app/user-api .

# Copy configuration files
COPY --from=builder --chown=appuser:appuser /app/configs ./configs

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
CMD ["./user-api"]
