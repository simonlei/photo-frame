package models

import "time"

type Photo struct {
	ID         uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	DeviceID   string    `gorm:"type:varchar(36);not null;index:idx_device_uploaded" json:"device_id"`
	UserID     uint      `gorm:"not null" json:"user_id"`
	CosKey     string    `gorm:"type:varchar(512);not null" json:"-"`
	CosURL     string    `gorm:"type:varchar(1024);not null" json:"url"`
	TakenAt    *time.Time `json:"taken_at"`
	UploadedAt time.Time  `gorm:"index:idx_device_uploaded" json:"uploaded_at"`

	User   User   `gorm:"foreignKey:UserID" json:"-"`
	Device Device `gorm:"foreignKey:DeviceID" json:"-"`
}
