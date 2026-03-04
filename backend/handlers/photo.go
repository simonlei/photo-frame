package handlers

import (
	"fmt"
	"mime"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/simonlei/photo-frame/backend/models"
	"github.com/simonlei/photo-frame/backend/storage"
	"gorm.io/gorm"
)

// UploadPhoto 上传照片到 COS，元数据写入 MySQL
func UploadPhoto(db *gorm.DB, cos *storage.COSStorage) gin.HandlerFunc {
	return func(c *gin.Context) {
		user := c.MustGet("user").(*models.User)
		deviceID := c.PostForm("device_id")
		if deviceID == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少 device_id"})
			return
		}

		// 校验用户已绑定该设备
		var device models.Device
		if err := db.Where("id = ?", deviceID).First(&device).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "相框不存在"})
			return
		}
		count := db.Model(&device).Association("Users").Count()
		// 简单校验：查找绑定关系
		var bindCheck int64
		db.Table("device_users").Where("device_id = ? AND user_id = ?", deviceID, user.ID).Count(&bindCheck)
		if bindCheck == 0 {
			_ = count
			c.JSON(http.StatusForbidden, gin.H{"error": "未绑定该相框"})
			return
		}

		file, err := c.FormFile("file")
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少上传文件"})
			return
		}

		// 校验文件类型
		ext := strings.ToLower(filepath.Ext(file.Filename))
		allowedExts := map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".heic": true, ".webp": true}
		if !allowedExts[ext] {
			c.JSON(http.StatusBadRequest, gin.H{"error": "只支持图片格式（jpg/png/heic/webp）"})
			return
		}

		// 构造 COS key
		now := time.Now()
		cosKey := fmt.Sprintf("photos/%s/%d/%02d/%s%s",
			deviceID, now.Year(), now.Month(), uuid.New().String(), ext)

		src, err := file.Open()
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "文件读取失败"})
			return
		}
		defer src.Close()

		contentType := mime.TypeByExtension(ext)
		if contentType == "" {
			contentType = "image/jpeg"
		}

		cosURL, err := cos.Upload(c.Request.Context(), cosKey, src, contentType)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "上传 COS 失败，请重试"})
			return
		}

		photo := models.Photo{
			DeviceID:   deviceID,
			UserID:     user.ID,
			CosKey:     cosKey,
			CosURL:     cosURL,
			UploadedAt: now,
		}
		if err := db.Create(&photo).Error; err != nil {
			// COS 已上传成功但 DB 写入失败，尝试清理 COS（best-effort）
			_ = cos.Delete(c.Request.Context(), cosKey)
			c.JSON(http.StatusInternalServerError, gin.H{"error": "数据库写入失败"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"id":  photo.ID,
			"url": cosURL,
		})
	}
}

// ListPhotos 获取相框照片列表（相框 App 轮询用）
func ListPhotos(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		deviceID := c.Query("device_id")
		if deviceID == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少 device_id"})
			return
		}

		query := db.Where("device_id = ?", deviceID).Order("uploaded_at ASC")

		// 支持增量拉取
		if since := c.Query("since"); since != "" {
			if t, err := time.Parse(time.RFC3339, since); err == nil {
				query = query.Where("uploaded_at > ?", t)
			}
		}

		// 限制单次最多返回 200 张
		if limitStr := c.Query("limit"); limitStr != "" {
			if limit, err := strconv.Atoi(limitStr); err == nil && limit > 0 && limit <= 200 {
				query = query.Limit(limit)
			}
		} else {
			query = query.Limit(200)
		}

		type photoItem struct {
			ID           uint       `json:"id"`
			URL          string     `json:"url"`
			TakenAt      *time.Time `json:"taken_at"`
			UploaderName string     `json:"uploader_name"`
			UploadedAt   time.Time  `json:"uploaded_at"`
		}

		var photos []models.Photo
		if err := query.Preload("User").Find(&photos).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "查询失败"})
			return
		}

		items := make([]photoItem, 0, len(photos))
		for _, p := range photos {
			items = append(items, photoItem{
				ID:           p.ID,
				URL:          p.CosURL,
				TakenAt:      p.TakenAt,
				UploaderName: p.User.Nickname,
				UploadedAt:   p.UploadedAt,
			})
		}
		c.JSON(http.StatusOK, gin.H{"photos": items})
	}
}

// DeletePhoto 删除照片（同时清理 COS 和 MySQL）
func DeletePhoto(db *gorm.DB, cos *storage.COSStorage) gin.HandlerFunc {
	return func(c *gin.Context) {
		user := c.MustGet("user").(*models.User)
		photoIDStr := c.Param("id")
		photoID, err := strconv.ParseUint(photoIDStr, 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "无效的照片 ID"})
			return
		}

		var photo models.Photo
		if err := db.Where("id = ? AND user_id = ?", photoID, user.ID).First(&photo).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "照片不存在或无权限删除"})
			return
		}

		// 先删 DB，再删 COS（DB 失败可回滚，COS 删失败影响不大）
		if err := db.Delete(&photo).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "删除失败"})
			return
		}
		_ = cos.Delete(c.Request.Context(), photo.CosKey)

		c.JSON(http.StatusOK, gin.H{"message": "删除成功"})
	}
}
