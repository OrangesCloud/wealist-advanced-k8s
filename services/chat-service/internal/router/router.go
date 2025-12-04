// internal/router/router.go
package router

import (
	"chat-service/internal/client"
	"chat-service/internal/handler"
	"chat-service/internal/middleware"

	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
	"go.uber.org/zap"
	"gorm.io/gorm"
)

func SetupRouter(
	logger *zap.Logger,
	userClient client.UserClient,
	chatHandler *handler.ChatHandler,
	messageHandler *handler.MessageHandler,
	wsHandler *handler.WSHandler,
	corsOrigins string,
	db *gorm.DB,
) *gin.Engine {
	router := gin.New()

	// Global Middleware
	router.Use(middleware.Logger(logger))
	router.Use(middleware.Recovery(logger))
	router.Use(middleware.CORS(corsOrigins))

	// Health Check endpoints (Kubernetes probe 호환)
	// /health - liveness probe: 서비스 자체가 살아있는지만 체크 (DB 연결 무관)
	router.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "healthy", "service": "chat-service"})
	})
	// /api/chats/health - liveness probe (Docker health check용)
	router.GET("/api/chats/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "healthy", "service": "chat-service"})
	})
	// /ready - readiness probe: DB 연결 상태까지 체크
	router.GET("/ready", readinessHandler(db))
	// /api/chats/ready - readiness probe (base path 버전)
	router.GET("/api/chats/ready", readinessHandler(db))

	// Swagger UI
	router.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	// Auth Middleware
	authMiddleware := middleware.NewAuthMiddleware(userClient, logger)

	// 🔥 Presence Handler (router 내부에서 생성)
	presenceHandler := handler.NewPresenceHandler(wsHandler)

	// 🔥 WebSocket - auth middleware 밖에 위치 (자체 토큰 검증 사용)
	router.GET("/api/chats/ws/:chatId", wsHandler.HandleWebSocket)

	// 🔥 Global Presence WebSocket - 앱 접속 시 온라인 상태 등록
	router.GET("/api/chats/ws/presence", wsHandler.HandlePresenceWebSocket)

	// API Routes (인증 필요)
	api := router.Group("/api/chats")
	api.Use(authMiddleware.RequireAuth())
	{
		// Chat Routes
		api.POST("", chatHandler.CreateChat)
		api.GET("/my", chatHandler.GetMyChats)
		api.GET("/workspace/:workspaceId", chatHandler.GetWorkspaceChats)
		api.GET("/:chatId", chatHandler.GetChat)
		api.DELETE("/:chatId", chatHandler.DeleteChat)
		api.POST("/:chatId/participants", chatHandler.AddParticipants)
		api.DELETE("/:chatId/participants/:userId", chatHandler.RemoveParticipant)

		// Message Routes
		api.GET("/messages/:chatId", messageHandler.GetMessages)
		api.POST("/messages/:chatId", messageHandler.SendMessage)
		api.DELETE("/messages/:messageId", messageHandler.DeleteMessage)
		api.POST("/messages/read", messageHandler.MarkMessagesAsRead)
		api.GET("/messages/:chatId/unread", messageHandler.GetUnreadCount)
		api.PUT("/messages/:chatId/last-read", messageHandler.UpdateLastRead)

		// 🔥 Presence Routes
		api.GET("/presence/online", presenceHandler.GetOnlineUsers)
		api.GET("/presence/status/:userId", presenceHandler.CheckUserStatus)
	}

	return router
}

// readinessHandler returns a handler for readiness probe
// DB 연결 상태까지 체크하여 트래픽 수신 가능 여부 판단
func readinessHandler(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		// DB가 nil이면 아직 연결 안 됨
		if db == nil {
			c.JSON(503, gin.H{
				"status":   "not_ready",
				"database": "not_initialized",
				"error":    "database connection not established yet",
			})
			return
		}

		// Check database connection
		sqlDB, err := db.DB()
		if err != nil {
			c.JSON(503, gin.H{
				"status":   "not_ready",
				"database": "error",
				"error":    err.Error(),
			})
			return
		}

		if err := sqlDB.Ping(); err != nil {
			c.JSON(503, gin.H{
				"status":   "not_ready",
				"database": "disconnected",
				"error":    err.Error(),
			})
			return
		}

		c.JSON(200, gin.H{
			"status":   "ready",
			"database": "connected",
		})
	}
}