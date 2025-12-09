package router

import (
	"video-service/internal/client"
	"video-service/internal/config"
	"video-service/internal/handler"
	"video-service/internal/middleware"
	"video-service/internal/repository"
	"video-service/internal/service"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
	"gorm.io/gorm"
)

func Setup(cfg *config.Config, db *gorm.DB, redisClient *redis.Client, logger *zap.Logger) *gin.Engine {
	if cfg.Server.Env == "production" {
		gin.SetMode(gin.ReleaseMode)
	}

	r := gin.New()
	r.Use(gin.Recovery())
	r.Use(middleware.LoggerMiddleware(logger))
	r.Use(middleware.MetricsMiddleware()) // Prometheus metrics
	// Note: CORS is handled by NGINX, not here (to avoid duplicate headers)

	// Initialize repositories
	roomRepo := repository.NewRoomRepository(db)

	// Initialize user client for workspace validation
	var userClient client.UserClient
	if cfg.Services.UserServiceURL != "" {
		userClient = client.NewUserClient(cfg.Services.UserServiceURL, logger)
		logger.Info("User client initialized", zap.String("url", cfg.Services.UserServiceURL))
	} else {
		logger.Warn("User service URL not configured, workspace validation will be skipped")
	}

	// Initialize services
	roomService := service.NewRoomService(roomRepo, userClient, cfg.LiveKit, redisClient, logger)

	// Initialize validator
	validator := middleware.NewAuthServiceValidator(cfg.Auth.ServiceURL, cfg.Auth.SecretKey, logger)

	// Initialize handlers
	roomHandler := handler.NewRoomHandler(roomService, logger)
	healthHandler := handler.NewHealthHandler(db, redisClient)

	// Health endpoints (no auth)
	r.GET("/health", healthHandler.Health)
	r.GET("/ready", healthHandler.Ready)
	r.GET("/metrics", middleware.MetricsHandler()) // Prometheus metrics

	// API routes with base path
	api := r.Group(cfg.Server.BasePath)
	{
		// Health under base path
		api.GET("/health", healthHandler.Health)
		api.GET("/ready", healthHandler.Ready)

		// Authenticated routes
		authenticated := api.Group("")
		authenticated.Use(middleware.AuthMiddleware(validator))
		{
			// Room routes
			authenticated.POST("/rooms", roomHandler.CreateRoom)
			authenticated.GET("/rooms/workspace/:workspaceId", roomHandler.GetWorkspaceRooms)
			authenticated.GET("/rooms/:roomId", roomHandler.GetRoom)
			authenticated.POST("/rooms/:roomId/join", roomHandler.JoinRoom)
			authenticated.POST("/rooms/:roomId/leave", roomHandler.LeaveRoom)
			authenticated.POST("/rooms/:roomId/end", roomHandler.EndRoom)
			authenticated.GET("/rooms/:roomId/participants", roomHandler.GetParticipants)
			authenticated.POST("/rooms/:roomId/transcript", roomHandler.SaveTranscript)

			// Call history routes
			authenticated.GET("/history/workspace/:workspaceId", roomHandler.GetWorkspaceCallHistory)
			authenticated.GET("/history/me", roomHandler.GetMyCallHistory)
			authenticated.GET("/history/:historyId", roomHandler.GetCallHistory)
			authenticated.GET("/history/:historyId/transcript", roomHandler.GetTranscript)
		}
	}

	return r
}
