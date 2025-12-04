// @title           Chat Service API
// @version         1.0
// @description     실시간 채팅 서비스 API
// @termsOfService  http://swagger.io/terms/

// @contact.name   API Support
// @contact.url    http://www.wealist.co.kr/support
// @contact.email  support@wealist.co.kr

// @license.name  Apache 2.0
// @license.url   http://www.apache.org/licenses/LICENSE-2.0.html

// @host      localhost:8001
// @BasePath  /api/chats

// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Type "Bearer" followed by a space and JWT token.

package main

import (
	"chat-service/internal/client"
	"chat-service/internal/database"
	"chat-service/internal/handler"
	"chat-service/internal/repository"
	"chat-service/internal/router"
	"chat-service/internal/service"
	"fmt"
	"log"
	"os"
	"time"

	_ "chat-service/docs" // 🔥 Swagger docs import

	"go.uber.org/zap"
)

func main() {
	// Logger 초기화
	logger, err := zap.NewProduction()
	if err != nil {
		log.Fatalf("Failed to initialize logger: %v", err)
	}
	defer logger.Sync()

	// 환경 변수 로드
	serverPort := getEnv("SERVER_PORT", "8001")
	userServiceURL := getEnv("USER_SERVICE_URL", "http://localhost:8080/api/users")
	authServiceURL := getEnv("AUTH_SERVICE_URL", "http://localhost:8090")
	corsOrigins := getEnv("CORS_ORIGINS", "*")

	logger.Info("🔧 Starting Chat Service",
		zap.String("port", serverPort),
		zap.String("userServiceURL", userServiceURL),
		zap.String("authServiceURL", authServiceURL),
		zap.String("corsOrigins", corsOrigins))

	// PostgreSQL 연결 시도 (실패해도 앱은 시작됨 - EKS pod 생존 보장)
	db, err := database.InitPostgres()
	if err != nil {
		logger.Warn("⚠️  Failed to connect to PostgreSQL on startup, will retry in background",
			zap.Error(err))
		// 백그라운드에서 DB 연결 재시도 (5초 간격)
		database.InitPostgresAsync(5 * time.Second)
	} else {
		logger.Info("✅ PostgreSQL connected")
	}

	// Redis 연결
	database.InitRedis()
	logger.Info("✅ Redis connected")

	// User Service Client 초기화 (authServiceURL 추가로 토큰 검증은 auth-service에서 처리)
	userClient := client.NewUserClient(userServiceURL, authServiceURL, 10*time.Second)

	// Repository 초기화
	chatRepo := repository.NewChatRepository(db)
	messageRepo := repository.NewMessageRepository(db)

	// Service 초기화
	chatService := service.NewChatService(chatRepo, messageRepo) // 🔥 messageRepo 추가 (unreadCount 계산용)
	messageService := service.NewMessageService(messageRepo, chatRepo)

	// Handler 초기화
	chatHandler := handler.NewChatHandler(chatService)
	messageHandler := handler.NewMessageHandler(messageService, chatService)
	wsHandler := handler.NewWSHandler(logger, userClient, messageService, chatService)

	// Router 설정
	r := router.SetupRouter(
		logger,
		userClient,
		chatHandler,
		messageHandler,
		wsHandler,
		corsOrigins,
		db,
	)

	// 서버 시작
	addr := fmt.Sprintf(":%s", serverPort)
	logger.Info("🚀 Chat Service started successfully",
		zap.String("address", addr),
		zap.String("swagger", fmt.Sprintf("http://localhost:%s/api/chats/swagger/index.html", serverPort)))

	if err := r.Run(addr); err != nil {
		logger.Fatal("Failed to start server", zap.Error(err))
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}