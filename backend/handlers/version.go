package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/simonlei/photo-frame/backend/models"
	"gorm.io/gorm"
)

// VersionLatest 获取最新 APK 版本信息
func VersionLatest(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var v models.Version
		if err := db.Order("created_at DESC").First(&v).Error; err != nil {
			c.JSON(http.StatusOK, gin.H{"version": nil})
			return
		}
		c.JSON(http.StatusOK, gin.H{
			"version":   v.Version,
			"apk_url":   v.ApkURL,
			"changelog": v.Changelog,
		})
	}
}
