package models

import "time"

type Photo struct {
	ID         uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	DeviceID   string    `gorm:"type:varchar(36);not null;index:idx_device_uploaded" json:"device_id"`
	UserID     uint      `gorm:"not null" json:"user_id"`
	CosKey     string    `gorm:"type:varchar(512);not null" json:"-"`
	CosURL     string    `gorm:"type:varchar(1024);not null" json:"url"`
	
	// 时间信息
	TakenAt    *time.Time `gorm:"index" json:"taken_at,omitempty"`
	UploadedAt time.Time  `gorm:"index:idx_device_uploaded" json:"uploaded_at"`
	
	// 地理位置信息（新增）
	Latitude        *float64 `gorm:"type:decimal(10,8)" json:"latitude,omitempty"`
	Longitude       *float64 `gorm:"type:decimal(11,8)" json:"longitude,omitempty"`
	LocationAddress *string  `gorm:"size:255" json:"location_address,omitempty"`
	
	// 相机信息（新增）
	CameraMake  *string `gorm:"size:100" json:"camera_make,omitempty"`
	CameraModel *string `gorm:"size:100" json:"camera_model,omitempty"`
	
	CreatedAt time.Time `json:"-"`
	UpdatedAt time.Time `json:"-"`

	User   User   `gorm:"foreignKey:UserID" json:"-"`
	Device Device `gorm:"foreignKey:DeviceID" json:"-"`
}
