package handlers

import (
	"log"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/simonlei/photo-frame/backend/models"
	"github.com/simonlei/photo-frame/backend/storage"
	"gorm.io/gorm"
)

// AdminStats 返回系统总览统计
func AdminStats(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var userCount, deviceCount, photoCount int64
		db.Model(&models.User{}).Count(&userCount)
		db.Model(&models.Device{}).Count(&deviceCount)
		db.Model(&models.Photo{}).Count(&photoCount)

		type deviceStat struct {
			DeviceID   string `json:"device_id"`
			DeviceName string `json:"device_name"`
			PhotoCount int64  `json:"photo_count"`
		}
		var topDevices []deviceStat
		db.Model(&models.Photo{}).
			Select("photos.device_id, devices.name as device_name, count(*) as photo_count").
			Joins("JOIN devices ON devices.id = photos.device_id").
			Group("photos.device_id, devices.name").
			Order("photo_count DESC").
			Limit(10).
			Scan(&topDevices)

		c.JSON(http.StatusOK, gin.H{
			"user_count":   userCount,
			"device_count": deviceCount,
			"photo_count":  photoCount,
			"top_devices":  topDevices,
		})
	}
}

// AdminListDevices 列出所有设备及统计
func AdminListDevices(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		type deviceItem struct {
			ID         string `json:"id"`
			Name       string `json:"name"`
			UserCount  int64  `json:"user_count"`
			PhotoCount int64  `json:"photo_count"`
			CreatedAt  string `json:"created_at"`
		}

		var devices []models.Device
		if err := db.Find(&devices).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "查询失败"})
			return
		}

		items := make([]deviceItem, 0, len(devices))
		for _, d := range devices {
			var userCount, photoCount int64
			db.Table("device_users").Where("device_id = ?", d.ID).Count(&userCount)
			db.Model(&models.Photo{}).Where("device_id = ?", d.ID).Count(&photoCount)
			items = append(items, deviceItem{
				ID:         d.ID,
				Name:       d.Name,
				UserCount:  userCount,
				PhotoCount: photoCount,
				CreatedAt:  d.CreatedAt.Format("2006-01-02 15:04:05"),
			})
		}
		c.JSON(http.StatusOK, gin.H{"devices": items})
	}
}

// AdminDeleteDevice 删除设备及其所有照片（含 COS）
func AdminDeleteDevice(db *gorm.DB, cos *storage.COSStorage) gin.HandlerFunc {
	return func(c *gin.Context) {
		deviceID := c.Param("id")

		var device models.Device
		if err := db.First(&device, "id = ?", deviceID).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
			return
		}

		// 先查出该设备所有照片的 COS key
		var photos []models.Photo
		db.Where("device_id = ?", deviceID).Find(&photos)

		// 删除 device_users 关联
		db.Exec("DELETE FROM device_users WHERE device_id = ?", deviceID)
		// 删除照片记录
		db.Where("device_id = ?", deviceID).Delete(&models.Photo{})
		// 删除设备
		if err := db.Delete(&device).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "删除设备失败"})
			return
		}

		// 清理 COS（best-effort，失败只记录日志）
		for _, p := range photos {
			if err := cos.Delete(c.Request.Context(), p.CosKey); err != nil {
				log.Printf("COS 清理失败 key=%s: %v", p.CosKey, err)
			}
		}

		c.JSON(http.StatusOK, gin.H{"message": "设备已删除"})
	}
}

// AdminListDevicePhotos 列出某设备下所有照片（分页）
func AdminListDevicePhotos(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		deviceID := c.Param("id")

		page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
		pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "50"))
		if page < 1 {
			page = 1
		}
		if pageSize < 1 || pageSize > 200 {
			pageSize = 50
		}
		offset := (page - 1) * pageSize

		var total int64
		db.Model(&models.Photo{}).Where("device_id = ?", deviceID).Count(&total)

		var photos []models.Photo
		if err := db.Where("device_id = ?", deviceID).
			Preload("User").
			Order("uploaded_at DESC").
			Offset(offset).Limit(pageSize).
			Find(&photos).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "查询失败"})
			return
		}

		type photoItem struct {
			ID           uint   `json:"id"`
			URL          string `json:"url"`
			UploaderName string `json:"uploader_name"`
			UploadedAt   string `json:"uploaded_at"`
		}
		items := make([]photoItem, 0, len(photos))
		for _, p := range photos {
			items = append(items, photoItem{
				ID:           p.ID,
				URL:          p.CosURL,
				UploaderName: p.User.Nickname,
				UploadedAt:   p.UploadedAt.Format("2006-01-02 15:04:05"),
			})
		}
		c.JSON(http.StatusOK, gin.H{"photos": items, "total": total, "page": page, "page_size": pageSize})
	}
}

// AdminListUsers 列出所有用户及统计
func AdminListUsers(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		type userItem struct {
			ID          uint   `json:"id"`
			Nickname    string `json:"nickname"`
			DeviceCount int64  `json:"device_count"`
			PhotoCount  int64  `json:"photo_count"`
			CreatedAt   string `json:"created_at"`
		}

		var users []models.User
		if err := db.Find(&users).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "查询失败"})
			return
		}

		items := make([]userItem, 0, len(users))
		for _, u := range users {
			var deviceCount, photoCount int64
			db.Table("device_users").Where("user_id = ?", u.ID).Count(&deviceCount)
			db.Model(&models.Photo{}).Where("user_id = ?", u.ID).Count(&photoCount)
			items = append(items, userItem{
				ID:          u.ID,
				Nickname:    u.Nickname,
				DeviceCount: deviceCount,
				PhotoCount:  photoCount,
				CreatedAt:   u.CreatedAt.Format("2006-01-02 15:04:05"),
			})
		}
		c.JSON(http.StatusOK, gin.H{"users": items})
	}
}

// AdminListPhotos 跨设备列出所有照片（分页）
func AdminListPhotos(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
		pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "50"))
		if page < 1 {
			page = 1
		}
		if pageSize < 1 || pageSize > 200 {
			pageSize = 50
		}
		offset := (page - 1) * pageSize

		var total int64
		db.Model(&models.Photo{}).Count(&total)

		var photos []models.Photo
		if err := db.Preload("User").Preload("Device").
			Order("uploaded_at DESC").
			Offset(offset).Limit(pageSize).
			Find(&photos).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "查询失败"})
			return
		}

		type photoItem struct {
			ID           uint   `json:"id"`
			URL          string `json:"url"`
			DeviceID     string `json:"device_id"`
			DeviceName   string `json:"device_name"`
			UploaderName string `json:"uploader_name"`
			UploadedAt   string `json:"uploaded_at"`
		}
		items := make([]photoItem, 0, len(photos))
		for _, p := range photos {
			items = append(items, photoItem{
				ID:           p.ID,
				URL:          p.CosURL,
				DeviceID:     p.DeviceID,
				DeviceName:   p.Device.Name,
				UploaderName: p.User.Nickname,
				UploadedAt:   p.UploadedAt.Format("2006-01-02 15:04:05"),
			})
		}
		c.JSON(http.StatusOK, gin.H{"photos": items, "total": total, "page": page, "page_size": pageSize})
	}
}

// AdminDeletePhoto 管理员强制删除照片（含 COS）
func AdminDeletePhoto(db *gorm.DB, cos *storage.COSStorage) gin.HandlerFunc {
	return func(c *gin.Context) {
		photoIDStr := c.Param("id")
		photoID, err := strconv.ParseUint(photoIDStr, 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "无效的照片 ID"})
			return
		}

		var photo models.Photo
		if err := db.First(&photo, photoID).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "照片不存在"})
			return
		}

		if err := db.Delete(&photo).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "删除失败"})
			return
		}
		_ = cos.Delete(c.Request.Context(), photo.CosKey)

		c.JSON(http.StatusOK, gin.H{"message": "删除成功"})
	}
}
