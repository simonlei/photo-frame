package models

import "time"

type User struct {
	ID        uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	Openid    string    `gorm:"type:varchar(64);uniqueIndex;not null" json:"openid"`
	Nickname  string    `gorm:"type:varchar(100)" json:"nickname"`
	Token     string    `gorm:"type:varchar(64);uniqueIndex" json:"-"`
	CreatedAt time.Time `json:"created_at"`

	Devices []Device `gorm:"many2many:device_users;" json:"-"`
}
