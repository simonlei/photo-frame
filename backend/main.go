package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
	"github.com/simonlei/photo-frame/backend/database"
	"github.com/simonlei/photo-frame/backend/handlers"
	"github.com/simonlei/photo-frame/backend/middleware"
	"github.com/simonlei/photo-frame/backend/services"
	"github.com/simonlei/photo-frame/backend/storage"
	"github.com/simonlei/photo-frame/backend/workers"
)

func main() {
	// 加载 .env 文件（开发环境）
	_ = godotenv.Load()

	// 生产环境关闭 gin debug 日志
	if os.Getenv("APP_ENV") == "production" {
		gin.SetMode(gin.ReleaseMode)
	}

	// 初始化数据库
	db, err := database.Init()
	if err != nil {
		log.Fatalf("数据库初始化失败: %v", err)
	}

	// 初始化 COS 存储
	cos := storage.NewCOS(
		os.Getenv("COS_SECRET_ID"),
		os.Getenv("COS_SECRET_KEY"),
		os.Getenv("COS_BUCKET"),
		os.Getenv("COS_REGION"),
	)

	// ✨ 新增：初始化腾讯地图服务（使用签名校验方式）
	tencentMapKey := os.Getenv("TENCENT_MAP_API_KEY")
	tencentMapSecret := os.Getenv("TENCENT_MAP_SECRET_KEY")
	if tencentMapKey == "" || tencentMapSecret == "" {
		log.Fatal("❌ TENCENT_MAP_API_KEY 和 TENCENT_MAP_SECRET_KEY 必须同时设置")
	}
	geocoder := services.NewGeocoder(tencentMapKey, tencentMapSecret)

	// ✨ 新增：启动地理编码 Worker
	geocodeWorker := workers.NewGeocodeWorker(db, geocoder)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	geocodeWorker.Start(ctx, 3) // 并发数 3

	// 初始化 Gin
	r := gin.New()
	r.Use(gin.Recovery())
	if os.Getenv("APP_ENV") != "production" {
		r.Use(gin.Logger())
	}

	// 最大上传 8MB（超出部分写临时文件）
	r.MaxMultipartMemory = 8 << 20

	// 公开接口
	r.POST("/api/wx-login", handlers.WxLogin(db))
	r.POST("/api/device/register", handlers.DeviceRegister(db))
	r.GET("/api/device/bind-status", handlers.DeviceBindStatus(db))
	r.GET("/api/version/latest", handlers.VersionLatest())

	// 需要用户 token 的接口
	userAPI := r.Group("/api")
	userAPI.Use(middleware.UserAuth(db))
	{
		userAPI.POST("/bind", handlers.Bind(db))
		userAPI.GET("/my/frames", handlers.MyFrames(db))
		userAPI.POST("/upload", handlers.UploadPhoto(db, cos, geocodeWorker))
		userAPI.GET("/photos", handlers.ListPhotos(db))
		userAPI.DELETE("/photos/:id", handlers.DeletePhoto(db, cos))
	}

	// 管理员接口（ADMIN_TOKEN 鉴权，与用户 token 完全分开）
	adminToken := os.Getenv("ADMIN_TOKEN")
	if adminToken == "" {
		log.Fatal("ADMIN_TOKEN 环境变量未设置")
	}
	adminAPI := r.Group("/api/admin")
	adminAPI.Use(middleware.AdminAuth(adminToken))
	{
		adminAPI.GET("/stats", handlers.AdminStats(db))
		adminAPI.GET("/devices", handlers.AdminListDevices(db))
		adminAPI.DELETE("/devices/:id", handlers.AdminDeleteDevice(db, cos))
		adminAPI.GET("/devices/:id/photos", handlers.AdminListDevicePhotos(db))
		adminAPI.GET("/users", handlers.AdminListUsers(db))
		adminAPI.GET("/photos", handlers.AdminListPhotos(db))
		adminAPI.DELETE("/photos/:id", handlers.AdminDeletePhoto(db, cos))
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Printf("服务启动，监听端口 :%s", port)

	// ✨ 新增：优雅关闭
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		if err := r.Run(":" + port); err != nil {
			log.Fatalf("服务启动失败: %v", err)
		}
	}()

	<-quit
	log.Println("正在关闭服务...")
	cancel() // 停止 Worker
	geocodeWorker.Stop()
	log.Println("服务已安全关闭")
}
