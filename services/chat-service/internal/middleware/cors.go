// internal/middleware/cors.go
package middleware

import (
	"strings"

	"github.com/gin-gonic/gin"
)

// CORS는 CORS 헤더를 설정하는 미들웨어입니다
func CORS(allowedOrigins string) gin.HandlerFunc {
	origins := strings.Split(allowedOrigins, ",")
	
	return func(c *gin.Context) {
		origin := c.Request.Header.Get("Origin")
		
		// 허용된 Origin인지 확인
		allowed := false
		for _, allowedOrigin := range origins {
			allowedOrigin = strings.TrimSpace(allowedOrigin)
			if allowedOrigin == "*" || allowedOrigin == origin {
				allowed = true
				break
			}
		}

		if allowed {
			c.Writer.Header().Set("Access-Control-Allow-Origin", origin)
		} else if len(origins) > 0 && origins[0] == "*" {
			c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		}

		c.Writer.Header().Set("Access-Control-Allow-Credentials", "true")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin, X-Requested-With")
		c.Writer.Header().Set("Access-Control-Max-Age", "86400")

		// Preflight 요청 처리
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}

		c.Next()
	}
}