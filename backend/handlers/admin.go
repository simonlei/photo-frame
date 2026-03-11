package handlers

import (
	"context"
	"log"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/simonlei/photo-frame/backend/models"
	"github.com/simonlei/photo-frame/backend/storage"
	"gorm.io/gorm"
)

// parsePage extracts page/page_size query params and returns page, pageSize, and offset.
func parsePage(c *gin.Context) (page, pageSize, offset int) {
	page, _ = strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ = strconv.Atoi(c.DefaultQuery("page_size", "50"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 200 {
		pageSize = 50
	}
	offset = (page - 1) * pageSize
	return
}

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

// AdminListDevices 列出所有设备及统计（分页，单次聚合查询）
func AdminListDevices(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		type deviceRow struct {
			ID         string    `json:"id"`
			Name       string    `json:"name"`
			UserCount  int64     `json:"user_count"`
			PhotoCount int64     `json:"photo_count"`
			CreatedAt  time.Time `json:"-"`
			CreatedAtS string    `json:"created_at"`
		}

		page, pageSize, offset := parsePage(c)

		var total int64
		db.Model(&models.Device{}).Count(&total)

		var rows []deviceRow
		db.Table("devices").
			Select("devices.id, devices.name, devices.created_at, " +
				"COUNT(DISTINCT du.user_id) as user_count, " +
				"COUNT(DISTINCT p.id) as photo_count").
			Joins("LEFT JOIN device_users du ON du.device_id = devices.id").
			Joins("LEFT JOIN photos p ON p.device_id = devices.id").
			Group("devices.id, devices.name, devices.created_at").
			Order("devices.created_at DESC").
			Offset(offset).Limit(pageSize).
			Scan(&rows)

		for i := range rows {
			rows[i].CreatedAtS = rows[i].CreatedAt.Format("2006-01-02 15:04:05")
		}

		c.JSON(http.StatusOK, gin.H{"devices": rows, "total": total, "page": page, "page_size": pageSize})
	}
}

// AdminDeleteDevice 删除设备及其所有照片（含 COS），使用事务保证原子性
func AdminDeleteDevice(db *gorm.DB, cos *storage.COSStorage) gin.HandlerFunc {
	return func(c *gin.Context) {
		deviceID := c.Param("id")

		var device models.Device
		if err := db.First(&device, "id = ?", deviceID).Error; err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "设备不存在"})
			return
		}

		var cosKeys []string
		err := db.Transaction(func(tx *gorm.DB) error {
			if err := tx.Model(&models.Photo{}).Where("device_id = ?", deviceID).Pluck("cos_key", &cosKeys).Error; err != nil {
				return err
			}
			if err := tx.Exec("DELETE FROM device_users WHERE device_id = ?", deviceID).Error; err != nil {
				return err
			}
			if err := tx.Where("device_id = ?", deviceID).Delete(&models.Photo{}).Error; err != nil {
				return err
			}
			return tx.Delete(&device).Error
		})
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "删除设备失败"})
			return
		}

		c.JSON(http.StatusOK, gin.H{"message": "设备已删除"})

		// COS 清理移至后台 goroutine，不阻塞 HTTP 响应
		go func(keys []string) {
			for _, key := range keys {
				if err := cos.Delete(context.Background(), key); err != nil {
					log.Printf("COS 清理失败 key=%s: %v", key, err)
				}
			}
		}(cosKeys)
	}
}

// AdminListDevicePhotos 列出某设备下所有照片（分页）
func AdminListDevicePhotos(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		deviceID := c.Param("id")

		page, pageSize, offset := parsePage(c)

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

// AdminListUsers 列出所有用户及统计（分页，单次聚合查询）
func AdminListUsers(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		type userRow struct {
			ID          uint      `json:"id"`
			Nickname    string    `json:"nickname"`
			DeviceCount int64     `json:"device_count"`
			PhotoCount  int64     `json:"photo_count"`
			CreatedAt   time.Time `json:"-"`
			CreatedAtS  string    `json:"created_at"`
		}

		page, pageSize, offset := parsePage(c)

		var total int64
		db.Model(&models.User{}).Count(&total)

		var rows []userRow
		db.Table("users").
			Select("users.id, users.nickname, users.created_at, " +
				"COUNT(DISTINCT du.device_id) as device_count, " +
				"COUNT(DISTINCT p.id) as photo_count").
			Joins("LEFT JOIN device_users du ON du.user_id = users.id").
			Joins("LEFT JOIN photos p ON p.user_id = users.id").
			Group("users.id, users.nickname, users.created_at").
			Order("users.created_at DESC").
			Offset(offset).Limit(pageSize).
			Scan(&rows)

		for i := range rows {
			rows[i].CreatedAtS = rows[i].CreatedAt.Format("2006-01-02 15:04:05")
		}

		c.JSON(http.StatusOK, gin.H{"users": rows, "total": total, "page": page, "page_size": pageSize})
	}
}

// AdminListPhotos 跨设备列出所有照片（分页）
func AdminListPhotos(db *gorm.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		page, pageSize, offset := parsePage(c)

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
