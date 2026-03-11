package middleware

import (
	"net/http"
	"os"
	"strings"

	"github.com/gin-gonic/gin"
)

// AdminAuth 校验固定的 ADMIN_TOKEN（通过环境变量配置），与用户 token 完全分开
func AdminAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		adminToken := os.Getenv("ADMIN_TOKEN")
		if adminToken == "" {
			c.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{"error": "管理员 Token 未配置"})
			return
		}

		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "缺少 Authorization header"})
			return
		}
		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) != 2 || parts[0] != "Bearer" || parts[1] == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Authorization 格式错误"})
			return
		}

		if parts[1] != adminToken {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "管理员 Token 无效"})
			return
		}

		c.Next()
	}
}
