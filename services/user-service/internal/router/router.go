package router

import (
	"github.com/gin-gonic/gin"
	// swaggerFiles "github.com/swaggo/files"
	// ginSwagger "github.com/swaggo/gin-swagger"
	"go.uber.org/zap"

	"user-service/internal/client"
	"user-service/internal/database"
	"user-service/internal/handler"
	"user-service/internal/middleware"
	"user-service/internal/repository"
	"user-service/internal/service"
)

// Config holds router configuration
type Config struct {
	Logger     *zap.Logger
	JWTSecret  string
	BasePath   string
	S3Client   *client.S3Client
	AuthClient *client.AuthClient
}

// Setup sets up the router with all routes
func Setup(cfg Config) *gin.Engine {
	r := gin.New()

	// Middleware
	r.Use(gin.Recovery())
	r.Use(middleware.Logger(cfg.Logger))
	r.Use(middleware.CORS("*"))
	r.Use(middleware.Metrics()) // Prometheus metrics middleware

	// Prometheus metrics endpoint
	r.GET("/metrics", middleware.MetricsHandler())

	// Health check routes
	r.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "healthy", "service": "user-service"})
	})
	r.GET("/ready", func(c *gin.Context) {
		if !database.IsConnected() {
			c.JSON(503, gin.H{"status": "not ready", "reason": "database not connected", "service": "user-service"})
			return
		}
		c.JSON(200, gin.H{"status": "ready", "service": "user-service"})
	})

	// Swagger documentation (disabled for faster builds)
	// r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	// Initialize repositories (DB는 전역에서 가져옴)
	db := database.GetDB()
	userRepo := repository.NewUserRepository(db)
	workspaceRepo := repository.NewWorkspaceRepository(db)
	memberRepo := repository.NewWorkspaceMemberRepository(db)
	profileRepo := repository.NewUserProfileRepository(db)
	joinReqRepo := repository.NewJoinRequestRepository(db)
	attachmentRepo := repository.NewAttachmentRepository(db)

	// Initialize services
	userService := service.NewUserService(userRepo, cfg.Logger)
	workspaceService := service.NewWorkspaceService(
		workspaceRepo,
		memberRepo,
		joinReqRepo,
		profileRepo,
		userRepo,
		cfg.Logger,
	)
	profileService := service.NewProfileService(profileRepo, memberRepo, userRepo, cfg.Logger)
	attachmentService := service.NewAttachmentService(attachmentRepo, cfg.S3Client, cfg.Logger)

	// Initialize handlers
	userHandler := handler.NewUserHandler(userService)
	workspaceHandler := handler.NewWorkspaceHandler(workspaceService)
	profileHandler := handler.NewProfileHandler(profileService, attachmentService)

	// API routes group
	api := r.Group(cfg.BasePath)

	// Auth middleware - use auth-service validator if available, otherwise use local JWT
	var authMiddleware gin.HandlerFunc
	if cfg.AuthClient != nil {
		authMiddleware = middleware.AuthWithValidator(cfg.AuthClient)
	} else {
		authMiddleware = middleware.Auth(cfg.JWTSecret)
	}

	// ============================================================
	// Internal routes (no auth required for service-to-service)
	// ============================================================
	internal := api.Group("/internal")
	{
		internal.GET("/users/:userId/exists", userHandler.UserExists)
		internal.POST("/oauth/login", userHandler.OAuthLogin)
	}

	// ============================================================
	// User routes
	// ============================================================
	users := api.Group("/users")
	{
		users.POST("", userHandler.CreateUser) // Public for OAuth callback
		users.GET("/me", authMiddleware, userHandler.GetMe)
		users.DELETE("/me", authMiddleware, userHandler.DeleteMe)
		users.GET("/:userId", authMiddleware, userHandler.GetUser)
		users.PUT("/:userId", authMiddleware, userHandler.UpdateUser)
		users.PUT("/:userId/restore", authMiddleware, userHandler.RestoreUser)
	}

	// ============================================================
	// Workspace routes
	// ============================================================
	workspaces := api.Group("/workspaces")
	workspaces.Use(authMiddleware)
	{
		workspaces.POST("/create", workspaceHandler.CreateWorkspace)
		workspaces.GET("/all", workspaceHandler.GetAllWorkspaces)
		workspaces.GET("/public/:workspaceName", workspaceHandler.SearchPublicWorkspaces)
		workspaces.GET("/:workspaceId", workspaceHandler.GetWorkspace)
		workspaces.PUT("/ids/:workspaceId", workspaceHandler.UpdateWorkspace)
		workspaces.DELETE("/:workspaceId", workspaceHandler.DeleteWorkspace)
		workspaces.POST("/default", workspaceHandler.SetDefaultWorkspace)

		// Workspace settings
		workspaces.GET("/:workspaceId/settings", workspaceHandler.GetWorkspaceSettings)
		workspaces.PUT("/:workspaceId/settings", workspaceHandler.UpdateWorkspaceSettings)

		// Workspace members
		workspaces.GET("/:workspaceId/members", workspaceHandler.GetMembers)
		workspaces.POST("/:workspaceId/members/invite", workspaceHandler.InviteMember)
		workspaces.PUT("/:workspaceId/members/:memberId/role", workspaceHandler.UpdateMemberRole)
		workspaces.DELETE("/:workspaceId/members/:memberId", workspaceHandler.RemoveMember)
		workspaces.GET("/:workspaceId/validate-member/:userId", workspaceHandler.ValidateMember)

		// Join requests
		workspaces.POST("/join-requests", workspaceHandler.CreateJoinRequest)
		workspaces.GET("/:workspaceId/joinRequests", workspaceHandler.GetJoinRequests)
		workspaces.GET("/:workspaceId/pendingMembers", workspaceHandler.GetJoinRequests) // Alias for frontend compatibility
		workspaces.PUT("/:workspaceId/joinRequests/:requestId", workspaceHandler.ProcessJoinRequest)
	}

	// ============================================================
	// Profile routes
	// ============================================================
	profiles := api.Group("/profiles")
	profiles.Use(authMiddleware)
	{
		profiles.POST("", profileHandler.CreateProfile)
		profiles.GET("/me", profileHandler.GetMyProfile)
		profiles.GET("/all/me", profileHandler.GetAllMyProfiles)
		profiles.PUT("/me", profileHandler.UpdateProfile)
		profiles.GET("/workspace/:workspaceId/user/:userId", profileHandler.GetUserProfile)
		profiles.DELETE("/workspace/:workspaceId", profileHandler.DeleteProfile)

		// Profile image upload
		profiles.POST("/me/image/presigned-url", profileHandler.GeneratePresignedURL)
		profiles.POST("/me/image/attachment", profileHandler.SaveAttachment)
		profiles.PUT("/me/image", profileHandler.ConfirmProfileImage)
	}

	return r
}
