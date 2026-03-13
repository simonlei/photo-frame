package handlers

import (
	"fmt"
	"io"
	"log"
	"mime"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/simonlei/photo-frame/backend/models"
	"github.com/simonlei/photo-frame/backend/services"
	"github.com/simonlei/photo-frame/backend/storage"
	"github.com/simonlei/photo-frame/backend/workers"
	"gorm.io/gorm"
)

// isUserBoundToDevice 校验用户是否已绑定指定设备
func isUserBoundToDevice(db *gorm.DB, userID uint, deviceID string) bool {
	var count int64
	db.Table("device_users").Where("device_id = ? AND user_id = ?", deviceID, userID).Count(&count)
	return count > 0
}

// UploadPhoto 上传照片到 COS，元数据写入 MySQL
func UploadPhoto(db *gorm.DB, cos *storage.COSStorage, geocodeWorker *workers.GeocodeWorker) gin.HandlerFunc {
	return func(c *gin.Context) {
		user := c.MustGet("user").(*models.User)
		deviceID := c.PostForm("device_id")
		if deviceID == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少 device_id"})
			return
		}

		// 校验用户已绑定该设备
		if !isUserBoundToDevice(db, user.ID, deviceID) {
			c.JSON(http.StatusForbidden, gin.H{"error": "未绑定该相框"})
			return
		}

		file, err := c.FormFile("file")
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少上传文件"})
			return
		}

		// 校验文件扩展名
		ext := strings.ToLower(filepath.Ext(file.Filename))
		allowedExts := map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".heic": true, ".webp": true}
		if !allowedExts[ext] {
			c.JSON(http.StatusBadRequest, gin.H{"error": "只支持图片格式（jpg/png/heic/webp）"})
			return
		}

		// 打开文件，先做 Magic Bytes 校验
		src, err := file.Open()
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "文件读取失败"})
			return
		}
		defer src.Close()

		// 读取文件头用于 MIME 检测（防止扩展名伪造）
		header := make([]byte, 512)
		n, _ := src.Read(header)
		detectedType := http.DetectContentType(header[:n])
		if !strings.HasPrefix(detectedType, "image/") {
			c.JSON(http.StatusBadRequest, gin.H{"error": "文件内容不是有效的图片"})
			return
		}
		// 重置读取位置，从头上传
		if _, err := src.Seek(0, io.SeekStart); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "文件读取失败"})
			return
		}

		// ✨ 新增：提取 EXIF（在 COS 上传前）
		exifData, err := services.ExtractEXIF(src)
		if err != nil {
			log.Printf("EXIF 提取失败 (user=%d, file=%s): %v", user.ID, file.Filename, err)
			// 不返回错误，继续上传流程
		}

		// ⚠️ 关键：重置文件指针到起始位置
		if _, err := src.Seek(0, io.SeekStart); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "文件读取错误"})
			return
		}

		contentType := mime.TypeByExtension(ext)
		if contentType == "" {
			contentType = detectedType
		}

		// 构造 COS key
		now := time.Now()
		cosKey := fmt.Sprintf("photos/%s/%d/%02d/%s%s",
			deviceID, now.Year(), now.Month(), uuid.New().String(), ext)

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
			// ✨ EXIF 字段
			TakenAt:     exifData.TakenAt,
			Latitude:    exifData.Latitude,
			Longitude:   exifData.Longitude,
			CameraMake:  exifData.CameraMake,
			CameraModel: exifData.CameraModel,
		}
		if err := db.Create(&photo).Error; err != nil {
			// COS 已上传成功但 DB 写入失败，尝试清理 COS（best-effort）
			_ = cos.Delete(c.Request.Context(), cosKey)
			c.JSON(http.StatusInternalServerError, gin.H{"error": "数据库写入失败"})
			return
		}

		// ✨ 新增：如果有 GPS 坐标，加入地理编码队列
		if photo.Latitude != nil && photo.Longitude != nil && geocodeWorker != nil {
			if err := geocodeWorker.Enqueue(photo.ID, *photo.Latitude, *photo.Longitude); err != nil {
				log.Printf("地理编码入队失败 (photo_id=%d): %v", photo.ID, err)
			}
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
		user := c.MustGet("user").(*models.User)
		deviceID := c.Query("device_id")
		if deviceID == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "缺少 device_id"})
			return
		}

		// 校验当前用户已绑定该设备（防止 IDOR 越权访问他人相框）
		if !isUserBoundToDevice(db, user.ID, deviceID) {
			c.JSON(http.StatusForbidden, gin.H{"error": "无权访问该相框"})
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
			ID              uint       `json:"id"`
			URL             string     `json:"url"`
			TakenAt         *time.Time `json:"taken_at,omitempty"`
			UploadedAt      time.Time  `json:"uploaded_at"`
			Latitude        *float64   `json:"latitude,omitempty"`
			Longitude       *float64   `json:"longitude,omitempty"`
			LocationAddress *string    `json:"location_address,omitempty"`
			CameraMake      *string    `json:"camera_make,omitempty"`
			CameraModel     *string    `json:"camera_model,omitempty"`
			UploaderName    string     `json:"uploader_name"`
		}

		var photos []models.Photo
		if err := query.Preload("User").Find(&photos).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "查询失败"})
			return
		}

		items := make([]photoItem, 0, len(photos))
		for _, p := range photos {
			items = append(items, photoItem{
				ID:              p.ID,
				URL:             p.CosURL,
				TakenAt:         p.TakenAt,
				UploadedAt:      p.UploadedAt,
				Latitude:        p.Latitude,
				Longitude:       p.Longitude,
				LocationAddress: p.LocationAddress,
				CameraMake:      p.CameraMake,
				CameraModel:     p.CameraModel,
				UploaderName:    p.User.Nickname,
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
		if err := db.Where("id = ?", photoID).First(&photo).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "照片不存在"})
			return
		}

		// 校验当前用户已绑定该照片所属相框（允许相框内所有绑定用户删除）
		if !isUserBoundToDevice(db, user.ID, photo.DeviceID) {
			c.JSON(http.StatusForbidden, gin.H{"error": "无权删除该照片"})
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
