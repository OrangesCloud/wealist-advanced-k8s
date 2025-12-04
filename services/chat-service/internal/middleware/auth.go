// internal/middleware/auth.go
package middleware

import (
	"chat-service/internal/client"
	"context"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

type AuthMiddleware struct {
	userClient client.UserClient
	logger     *zap.Logger
}

func NewAuthMiddleware(userClient client.UserClient, logger *zap.Logger) *AuthMiddleware {
	return &AuthMiddleware{
		userClient: userClient,
		logger:     logger,
	}
}

// RequireAuth는 JWT 토큰 검증 미들웨어입니다
func (m *AuthMiddleware) RequireAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		// 1. Authorization 헤더에서 토큰 추출
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			m.logger.Warn("Missing Authorization header",
				zap.String("path", c.Request.URL.Path))
			c.JSON(http.StatusUnauthorized, gin.H{
				"error": "Authorization header required",
			})
			c.Abort()
			return
		}

		// 2. Bearer 토큰 파싱
		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) != 2 || parts[0] != "Bearer" {
			m.logger.Warn("Invalid Authorization header format")
			c.JSON(http.StatusUnauthorized, gin.H{
				"error": "Invalid Authorization header format. Expected: Bearer <token>",
			})
			c.Abort()
			return
		}

		token := parts[1]

		// 3. User Service에서 토큰 검증
		ctx, cancel := context.WithTimeout(c.Request.Context(), 5*time.Second)
		defer cancel()

		validationResp, err := m.userClient.ValidateToken(ctx, token)
		if err != nil {
			m.logger.Error("Token validation failed",
				zap.Error(err),
				zap.String("path", c.Request.URL.Path))
			c.JSON(http.StatusUnauthorized, gin.H{
				"error": "Token validation failed",
			})
			c.Abort()
			return
		}

		if !validationResp.Valid {
			m.logger.Warn("Invalid token",
				zap.String("message", validationResp.Message),
				zap.String("path", c.Request.URL.Path))
			c.JSON(http.StatusUnauthorized, gin.H{
				"error": "Invalid token",
				"message": validationResp.Message,
			})
			c.Abort()
			return
		}

		// 4. Context에 userID 저장
		c.Set("userID", validationResp.UserID)
		c.Set("token", token)

		m.logger.Debug("Token validated successfully",
			zap.String("userId", validationResp.UserID),
			zap.String("path", c.Request.URL.Path))

		c.Next()
	}
}

// GetUserID는 Context에서 userID를 가져옵니다
func GetUserID(c *gin.Context) (string, bool) {
	userID, exists := c.Get("userID")
	if !exists {
		return "", false
	}
	userIDStr, ok := userID.(string)
	return userIDStr, ok
}

// GetToken은 Context에서 token을 가져옵니다
func GetToken(c *gin.Context) (string, bool) {
	token, exists := c.Get("token")
	if !exists {
		return "", false
	}
	tokenStr, ok := token.(string)
	return tokenStr, ok
}