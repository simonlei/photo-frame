package middleware

import (
	"crypto/subtle"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
)

// AdminAuth 校验固定的 ADMIN_TOKEN（通过参数传入，启动时读取一次），与用户 token 完全分开
func AdminAuth(adminToken string) gin.HandlerFunc {
	return func(c *gin.Context) {
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

		if subtle.ConstantTimeCompare([]byte(parts[1]), []byte(adminToken)) != 1 {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "管理员 Token 无效"})
			return
		}

		c.Next()
	}
}
