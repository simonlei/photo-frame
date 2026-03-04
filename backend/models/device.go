package models

import "time"

type Device struct {
	ID        string    `gorm:"primaryKey;type:varchar(36)" json:"id"`
	Name      string    `gorm:"type:varchar(100)" json:"name"`
	QrToken   string    `gorm:"type:varchar(64);uniqueIndex" json:"qr_token"`
	CreatedAt time.Time `json:"created_at"`

	Users []User `gorm:"many2many:device_users;" json:"-"`
}
