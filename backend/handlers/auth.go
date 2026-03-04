package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/simonlei/photo-frame/backend/models"
	"gorm.io/gorm"
)

type wxSessionResp struct {
	OpenID     string `json:"openid"`
	SessionKey string `json:"session_key"`
	ErrCode    int    `json:"errcode"`
	ErrMsg     string `json:"errmsg"`
}

// WxLogin 微信 code 换 token
func WxLogin(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var req struct {
			Code string `json:"code" binding:"required"`
		}
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少 code 参数"})
			return
		}

		// 调用微信接口换取 openid
		wxURL := fmt.Sprintf(
			"https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
			os.Getenv("WX_APPID"), os.Getenv("WX_SECRET"), req.Code,
		)
		resp, err := http.Get(wxURL)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "微信接口请求失败"})
			return
		}
		defer resp.Body.Close()

		var wxResp wxSessionResp
		if err := json.NewDecoder(resp.Body).Decode(&wxResp); err != nil || wxResp.OpenID == "" {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "微信登录失败"})
			return
		}

		// 查找或创建用户
		var user models.User
		result := db.Where("openid = ?", wxResp.OpenID).First(&user)
		if result.Error == gorm.ErrRecordNotFound {
			user = models.User{
				Openid: wxResp.OpenID,
				Token:  uuid.New().String(),
			}
			if err := db.Create(&user).Error; err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "创建用户失败"})
				return
			}
		} else if result.Error != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "数据库错误"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"token":  user.Token,
			"openid": user.Openid,
		})
	}
}
