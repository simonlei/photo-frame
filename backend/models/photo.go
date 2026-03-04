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

type Version struct {
	ID        uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	Version   string    `gorm:"type:varchar(20);not null" json:"version"`
	ApkURL    string    `gorm:"type:varchar(1024);not null" json:"apk_url"`
	ApkSha256 string    `gorm:"type:varchar(64)" json:"apk_sha256"`
	Changelog string    `gorm:"type:text" json:"changelog"`
	CreatedAt time.Time `json:"created_at"`
}
