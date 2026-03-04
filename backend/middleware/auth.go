package middleware

import (
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/simonlei/photo-frame/backend/models"
	"gorm.io/gorm"
)

const tokenMaxAge = 30 * 24 * time.Hour

// UserAuth 校验用户 Bearer token，将 user 注入 gin context
func UserAuth(db *gorm.DB) gin.HandlerFunc {
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
		token := parts[1]

		var user models.User
		if err := db.Where("token = ?", token).First(&user).Error; err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "token 无效"})
			return
		}

		// 校验 token 是否已过期（30天）
		if !user.TokenIssuedAt.IsZero() && time.Since(user.TokenIssuedAt) > tokenMaxAge {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "token 已过期，请重新登录"})
			return
		}

		c.Set("user", &user)
		c.Next()
	}
}
