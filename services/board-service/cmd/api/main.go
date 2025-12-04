// @title           Board Service API
// @version         1.0
// @description     프로젝트 보드 관리 API
// @termsOfService  http://swagger.io/terms/

// @contact.name   API Support
// @contact.url    http://www.wealist.co.kr/support
// @contact.email  support@wealist.co.kr

// @license.name  Apache 2.0
// @license.url   http://www.apache.org/licenses/LICENSE-2.0.html

// @host      localhost:8000
// @BasePath  /api/boards

// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Type "Bearer" followed by a space and JWT token.

package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"

	_ "project-board-api/docs" // Swagger docs import

	"project-board-api/internal/client"
	"project-board-api/internal/config"
	"project-board-api/internal/database"
	"project-board-api/internal/metrics"
	"project-board-api/internal/router"
)

func main() {
	// Load configuration
	cfg, err := config.Load("configs/config.yaml")
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to load configuration: %v\n", err)
		os.Exit(1)
	}

	// Initialize logger
	logger, err := initLogger(cfg.Logger.Level)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to initialize logger: %v\n", err)
		os.Exit(1)
	}
	defer logger.Sync()

	// Set Gin mode
	if cfg.Server.Mode == "release" {
		gin.SetMode(gin.ReleaseMode)
	}

	logger.Info("Starting Board Service",
		zap.String("port", cfg.Server.Port),
		zap.String("mode", cfg.Server.Mode),
		zap.String("base_path", cfg.Server.BasePath),
		zap.String("user_api_url", cfg.UserAPI.BaseURL),
		zap.String("auth_api_url", cfg.AuthAPI.BaseURL),
	)

	// Initialize database (실패해도 앱은 시작됨 - EKS pod 생존 보장)
	dbConfig := database.Config{
		DSN:             cfg.Database.GetDSN(),
		MaxOpenConns:    cfg.Database.MaxOpenConns,
		MaxIdleConns:    cfg.Database.MaxIdleConns,
		ConnMaxLifetime: cfg.Database.ConnMaxLifetime,
	}

	db, err := database.New(dbConfig)
	if err != nil {
		logger.Warn("⚠️  Failed to connect to database on startup, will retry in background",
			zap.Error(err))
		// 백그라운드에서 DB 연결 재시도 (5초 간격)
		database.NewAsync(dbConfig, 5*time.Second)
	} else {
		logger.Info("Database connected successfully")

		// Run auto migration (DB 연결된 경우만)
		if err := database.AutoMigrate(db); err != nil {
			logger.Warn("Failed to run database migrations", zap.Error(err))
		} else {
			logger.Info("Database migrations completed")
		}
	}

	// Initialize metrics
	m := metrics.New()
	logger.Info("Metrics initialized")

	// Initialize S3 client
	var s3Client *client.S3Client
	if cfg.S3.Bucket != "" && cfg.S3.Region != "" {
		s3Client, err = client.NewS3Client(&cfg.S3)
		if err != nil {
			logger.Warn("Failed to initialize S3 client, attachment features may be limited", zap.Error(err))
		} else {
			logger.Info("S3 client initialized",
				zap.String("bucket", cfg.S3.Bucket),
				zap.String("region", cfg.S3.Region),
			)
		}
	} else {
		logger.Warn("S3 configuration incomplete, attachment features disabled")
	}

	// Initialize User Client with auth-service URL for token validation (includes blacklist check)
	userClient := client.NewUserClient(
		cfg.UserAPI.BaseURL,
		cfg.AuthAPI.BaseURL, // auth-service URL for token validation
		cfg.UserAPI.Timeout,
		logger,
		m,
	)
	logger.Info("User client initialized with auth-service integration",
		zap.String("user_api_url", cfg.UserAPI.BaseURL),
		zap.String("auth_api_url", cfg.AuthAPI.BaseURL),
	)

	// Setup router with all dependencies
	r := router.Setup(router.Config{
		DB:                 db,
		Logger:             logger,
		JWTSecret:          cfg.JWT.Secret,
		UserClient:         userClient,
		BasePath:           cfg.Server.BasePath,
		UserServiceBaseURL: cfg.UserAPI.BaseURL,
		Metrics:            m,
		S3Client:           s3Client,
	})

	// Create HTTP server
	srv := &http.Server{
		Addr:         fmt.Sprintf(":%s", cfg.Server.Port),
		Handler:      r,
		ReadTimeout:  cfg.Server.ReadTimeout,
		WriteTimeout: cfg.Server.WriteTimeout,
	}

	// Start server in goroutine
	go func() {
		logger.Info("Board Service started successfully",
			zap.String("address", srv.Addr),
			zap.String("swagger", fmt.Sprintf("http://localhost:%s%s/swagger/index.html", cfg.Server.Port, cfg.Server.BasePath)),
		)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal("Failed to start server", zap.Error(err))
		}
	}()

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	logger.Info("Shutting down server...")

	// Graceful shutdown with timeout
	ctx, cancel := context.WithTimeout(context.Background(), cfg.Server.ShutdownTimeout)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		logger.Error("Server forced to shutdown", zap.Error(err))
	}

	logger.Info("Server exited gracefully")
}

// initLogger initializes the zap logger with the specified level
func initLogger(level string) (*zap.Logger, error) {
	var zapLevel zapcore.Level
	switch level {
	case "debug":
		zapLevel = zapcore.DebugLevel
	case "info":
		zapLevel = zapcore.InfoLevel
	case "warn":
		zapLevel = zapcore.WarnLevel
	case "error":
		zapLevel = zapcore.ErrorLevel
	default:
		zapLevel = zapcore.InfoLevel
	}

	config := zap.Config{
		Level:            zap.NewAtomicLevelAt(zapLevel),
		Development:      zapLevel == zapcore.DebugLevel,
		Encoding:         "json",
		EncoderConfig:    zap.NewProductionEncoderConfig(),
		OutputPaths:      []string{"stdout"},
		ErrorOutputPaths: []string{"stderr"},
	}

	return config.Build()
}
