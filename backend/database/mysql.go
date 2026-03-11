package database

import (
	"fmt"
	"os"
	"time"

	"github.com/simonlei/photo-frame/backend/models"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func Init() (*gorm.DB, error) {
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=True&loc=Local",
		os.Getenv("DB_USER"),
		os.Getenv("DB_PASS"),
		os.Getenv("DB_HOST"),
		os.Getenv("DB_PORT"),
		os.Getenv("DB_NAME"),
	)

	// 生产环境只记录 Warn 及以上，减少 I/O 开销
	logLevel := logger.Info
	if os.Getenv("APP_ENV") == "production" {
		logLevel = logger.Warn
	}

	db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logLevel),
	})
	if err != nil {
		return nil, fmt.Errorf("连接 MySQL 失败: %w", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		return nil, err
	}
	sqlDB.SetMaxOpenConns(20)
	sqlDB.SetMaxIdleConns(5)
	sqlDB.SetConnMaxLifetime(time.Hour) // 防止连接池中出现已被服务器关闭的僵尸连接

	// 开发环境才自动迁移，生产环境手动执行 SQL 迁移脚本
	if os.Getenv("APP_ENV") != "production" {
		if err := db.AutoMigrate(
			&models.Device{},
			&models.User{},
			&models.Photo{},
		); err != nil {
			return nil, fmt.Errorf("数据库迁移失败: %w", err)
		}
	}

	return db, nil
}
