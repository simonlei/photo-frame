package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/simonlei/photo-frame/backend/models"
	"gorm.io/gorm"
)

// DeviceRegister 相框设备首次注册，返回 device_id 和 qr_token
func DeviceRegister(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		device := models.Device{
			ID:      uuid.New().String(),
			Name:    "我的相框",
			QrToken: uuid.New().String(),
		}
		if err := db.Create(&device).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "设备注册失败"})
			return
		}
		c.JSON(http.StatusOK, gin.H{
			"device_id": device.ID,
			"qr_token":  device.QrToken,
		})
	}
}

// Bind 用户扫码绑定相框
func Bind(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var req struct {
			QrToken string `json:"qr_token" binding:"required"`
		}
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少 qr_token"})
			return
		}

		user := c.MustGet("user").(*models.User)

		var device models.Device
		if err := db.Where("qr_token = ?", req.QrToken).First(&device).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "相框不存在或二维码已过期"})
			return
		}

		// 建立绑定关系（忽略已绑定的错误）
		if err := db.Model(&device).Association("Users").Append(user); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "绑定失败"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"device_id":   device.ID,
			"device_name": device.Name,
		})
	}
}

// DeviceBindStatus 相框 App 轮询：查询是否已有用户绑定，绑定后返回 user_token 供设备调用受保护接口
func DeviceBindStatus(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		deviceID := c.Query("device_id")
		if deviceID == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少 device_id"})
			return
		}
		var device models.Device
		if err := db.Where("id = ?", deviceID).First(&device).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"bound": false})
			return
		}
		var users []models.User
		if err := db.Model(&device).Association("Users").Find(&users); err != nil || len(users) == 0 {
			c.JSON(http.StatusOK, gin.H{"bound": false})
			return
		}
		c.JSON(http.StatusOK, gin.H{
			"bound":      true,
			"user_token": users[0].Token,
		})
	}
}

// MyFrames 返回当前用户绑定的相框列表。
// 响应中包含 qr_token 字段，可用于生成邀请绑定链接（photoframe://bind?qr_token=...）。
// 该接口受用户 token 认证保护，仅返回当前用户已绑定的设备，因此 qr_token 仅对
// 已绑定该设备的用户可见，安全性由 UserAuth 中间件保障。
func MyFrames(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		user := c.MustGet("user").(*models.User)

		var devices []models.Device
		if err := db.Model(user).Association("Devices").Find(&devices); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "查询失败"})
			return
		}
		c.JSON(http.StatusOK, gin.H{"frames": devices})
	}
}
