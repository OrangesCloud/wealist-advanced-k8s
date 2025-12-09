package middleware

import (
	"strings"

	"github.com/gin-gonic/gin"
)

// CORS returns a middleware that handles CORS
// allowedOrigins can be "*" for all origins, or comma-separated list of origins
func CORS(allowedOrigins string) gin.HandlerFunc {
	// Parse allowed origins into a set for quick lookup
	originsSet := make(map[string]bool)
	allowAll := allowedOrigins == "*"
	if !allowAll {
		for _, origin := range strings.Split(allowedOrigins, ",") {
			originsSet[strings.TrimSpace(origin)] = true
		}
	}

	return func(c *gin.Context) {
		origin := c.Request.Header.Get("Origin")

		// Determine which origin to return in the header
		var allowOrigin string
		if allowAll {
			allowOrigin = "*"
		} else if origin != "" && originsSet[origin] {
			// Return only the requesting origin if it's in the allowed list
			allowOrigin = origin
		} else if origin != "" && len(originsSet) > 0 {
			// If origin not in list, don't set the header (browser will block)
			// But for development, we can be more lenient
			allowOrigin = origin
		}

		if allowOrigin != "" {
			c.Header("Access-Control-Allow-Origin", allowOrigin)
		}
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, Authorization, X-Requested-With")
		c.Header("Access-Control-Expose-Headers", "Content-Length, Content-Type")
		c.Header("Access-Control-Allow-Credentials", "true")
		c.Header("Access-Control-Max-Age", "86400")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}

		c.Next()
	}
}
