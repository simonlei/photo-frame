---
title: 照片 EXIF 信息与地理位置展示功能
type: feat
status: active
date: 2026-03-13
origin: docs/brainstorms/2026-03-13-photo-exif-location-brainstorm.md
---

# 照片 EXIF 信息与地理位置展示功能

## 概述

为相框系统的所有平台（Android 相框端、小程序、管理后台）添加照片 EXIF 元数据展示功能,实现真实拍摄时间和地理位置信息的提取与显示。

### 核心功能

1. **EXIF 提取**：上传时同步从照片提取拍摄时间、GPS 坐标、相机型号
2. **地理编码**：后台异步将 GPS 坐标转换为详细地址（省·市·区·街道）
3. **全平台展示**：Android、小程序、管理后台统一显示元数据
4. **优雅降级**：无 GPS 照片静默隐藏位置字段

### 技术决策依据

所有核心决策源自头脑风暴文档（见 `origin` 字段），关键决策包括：
- **数据库设计**：扩展现有 Photo 表（性能最优，实现简单）
- **处理时机**：EXIF 提取同步（<100ms），地理编码异步（避免阻塞）
- **地图 API**：腾讯地图（统一账号体系，10万次/天免费额度）
- **无 GPS 处理**：静默隐藏位置字段（无负面提示）

---

## 问题陈述 / 动机

### 当前问题

1. **拍摄时间缺失**：
   - 现有 `Photo.TakenAt` 字段已预留但未使用
   - 所有照片按上传时间排序，无法反映真实拍摄顺序
   - 用户上传旧照片时，显示的上传时间无意义

2. **位置信息缺失**：
   - 无法知道照片拍摄地点
   - 无法按地理位置组织照片
   - 错过了回忆场景的重要上下文

3. **设备信息缺失**：
   - 无法展示相机型号
   - 无法进行设备统计分析

### 用户价值

- **回忆增强**：看到照片时联想拍摄时间和地点
- **时间准确性**：真实拍摄时间而非上传时间
- **地点记忆**：详细地址（如"北京市·海淀区·颐和园路"）唤起回忆
- **设备记录**：记住每张照片的拍摄设备

---

## 技术方案

### 架构设计

```
照片上传流程（backend/handlers/photo.go）
    ↓
1. 文件校验（Magic Bytes）
    ↓
2. EXIF 提取（同步，< 100ms）
    ├─ 拍摄时间 → Photo.TakenAt
    ├─ GPS 坐标 → Photo.Latitude/Longitude
    └─ 相机信息 → Photo.CameraMake/CameraModel
    ↓
3. ⚠️ 重置文件指针 Seek(0, io.SeekStart)
    ↓
4. 上传到 COS
    ↓
5. 写入数据库（含 EXIF 字段）
    ↓
6. 返回成功响应
    ↓
7. [异步] 发送到地理编码队列
    ↓
8. 腾讯地图 API 逆编码
    ↓
9. 更新 Photo.LocationAddress 字段
```

**关键约束**：
- EXIF 提取必须在文件上传到 COS 之前
- 文件流读取后必须 `Seek(0)` 重置指针（避免上传文件缺失头部）
- 地理编码 Worker 并发数控制在 2-4 个（避免 API 限流）

---

## 数据库 Schema 变更

### 迁移策略

**开发环境**（自动）：
```go
// backend/database/mysql.go 中的 AutoMigrate 会自动添加字段
// 无需手动操作
```

**生产环境**（手动迁移脚本）：
```sql
-- 生产环境迁移脚本：migrations/20260313_add_photo_exif_fields.sql
ALTER TABLE photos ADD COLUMN latitude DECIMAL(10, 8) COMMENT '纬度';
ALTER TABLE photos ADD COLUMN longitude DECIMAL(11, 8) COMMENT '经度';
ALTER TABLE photos ADD COLUMN location_address VARCHAR(255) COMMENT '逆编码详细地址';
ALTER TABLE photos ADD COLUMN camera_make VARCHAR(100) COMMENT '相机制造商';
ALTER TABLE photos ADD COLUMN camera_model VARCHAR(100) COMMENT '相机型号';

-- 索引（未来支持地理围栏查询）
CREATE INDEX idx_photos_coords ON photos(latitude, longitude);
CREATE INDEX idx_photos_taken_at ON photos(taken_at);
```

**回滚脚本**：
```sql
-- migrations/20260313_add_photo_exif_fields_down.sql
DROP INDEX idx_photos_coords ON photos;
DROP INDEX idx_photos_taken_at ON photos;
ALTER TABLE photos DROP COLUMN camera_model;
ALTER TABLE photos DROP COLUMN camera_make;
ALTER TABLE photos DROP COLUMN location_address;
ALTER TABLE photos DROP COLUMN longitude;
ALTER TABLE photos DROP COLUMN latitude;
```

### 模型更新

**backend/models/photo.go**：
```go
type Photo struct {
	ID         uint      `gorm:"primaryKey;autoIncrement" json:"id"`
	DeviceID   string    `gorm:"type:varchar(36);not null;index:idx_device_uploaded" json:"device_id"`
	UserID     uint      `gorm:"not null" json:"user_id"`
	CosKey     string    `gorm:"type:varchar(512);not null" json:"-"`
	CosURL     string    `gorm:"type:varchar(1024);not null" json:"url"`
	
	// 时间信息
	TakenAt    *time.Time `gorm:"index" json:"taken_at,omitempty"` // EXIF 拍摄时间
	UploadedAt time.Time  `gorm:"index:idx_device_uploaded" json:"uploaded_at"`
	
	// 地理位置（新增）
	Latitude        *float64 `gorm:"type:decimal(10,8)" json:"latitude,omitempty"`
	Longitude       *float64 `gorm:"type:decimal(11,8)" json:"longitude,omitempty"`
	LocationAddress *string  `gorm:"size:255" json:"location_address,omitempty"`
	
	// 相机信息（新增）
	CameraMake  *string `gorm:"size:100" json:"camera_make,omitempty"`
	CameraModel *string `gorm:"size:100" json:"camera_model,omitempty"`
	
	CreatedAt time.Time `json:"-"`
	UpdatedAt time.Time `json:"-"`
	
	// 关联关系
	User   User   `gorm:"foreignKey:UserID" json:"-"`
	Device Device `gorm:"foreignKey:DeviceID" json:"-"`
}
```

**关键约定（见项目研究报告）**：
- ✅ 使用指针类型 `*float64`, `*string` 允许 NULL 值
- ✅ 使用 `json:"omitempty"` 避免返回 null 字段
- ✅ 保持现有 `json:"-"` 隐藏敏感字段（CosKey）
- ✅ 索引策略：`taken_at` 单独索引，`(latitude, longitude)` 复合索引

---

## 后端实现细节

### 第一阶段：EXIF 提取服务

#### 新建文件：backend/services/exif_extractor.go

```go
package services

import (
	"bytes"
	"io"
	"time"
	"github.com/rwcarlsen/goexif/exif"
	"github.com/rwcarlsen/goexif/tiff"
)

// EXIFData 提取的 EXIF 元数据
type EXIFData struct {
	TakenAt     *time.Time
	Latitude    *float64
	Longitude   *float64
	CameraMake  *string
	CameraModel *string
}

// ExtractEXIF 从文件流提取 EXIF 数据
// ⚠️ 关键：读取后必须调用 src.Seek(0, io.SeekStart) 重置指针
func ExtractEXIF(src io.ReadSeeker) (*EXIFData, error) {
	data := &EXIFData{}
	
	// 只读取前 64KB（EXIF 通常在文件头部）
	limitReader := io.LimitReader(src, 64*1024)
	
	// 缓存读取的数据，避免影响原始流
	buf := new(bytes.Buffer)
	teeReader := io.TeeReader(limitReader, buf)
	
	x, err := exif.Decode(teeReader)
	if err != nil {
		// 提取失败不应阻塞上传流程
		return data, nil // 返回空数据而非错误
	}
	
	// 1. 提取拍摄时间
	if tm, err := x.DateTime(); err == nil {
		data.TakenAt = &tm
	}
	
	// 2. 提取 GPS 坐标
	lat, lon, err := x.LatLong()
	if err == nil {
		data.Latitude = &lat
		data.Longitude = &lon
	}
	
	// 3. 提取相机制造商
	if make, err := x.Get(exif.Make); err == nil {
		if str, err := make.StringVal(); err == nil {
			data.CameraMake = &str
		}
	}
	
	// 4. 提取相机型号
	if model, err := x.Get(exif.Model); err == nil {
		if str, err := model.StringVal(); err == nil {
			data.CameraModel = &str
		}
	}
	
	return data, nil
}
```

#### 修改文件：backend/handlers/photo.go - UploadPhoto 函数

**现有流程**（简化）：
```go
func UploadPhoto(db *gorm.DB, cos *storage.COSStorage) gin.HandlerFunc {
	return func(c *gin.Context) {
		// 1. 文件校验
		header := make([]byte, 512)
		src.Read(header)
		contentType := http.DetectContentType(header)
		src.Seek(0, io.SeekStart)
		
		// 2. 上传到 COS
		cosURL, err := cos.Upload(...)
		
		// 3. 写入数据库
		photo := models.Photo{
			CosURL: cosURL,
			TakenAt: nil,  // ← 当前未使用
		}
		db.Create(&photo)
	}
}
```

**新增 EXIF 提取步骤**（插入到步骤 1 和 2 之间）：
```go
func UploadPhoto(db *gorm.DB, cos *storage.COSStorage) gin.HandlerFunc {
	return func(c *gin.Context) {
		// ... 现有文件校验逻辑 ...
		
		// ✨ 新增：提取 EXIF（在 COS 上传前）
		exifData, err := services.ExtractEXIF(src)
		if err != nil {
			log.Printf("EXIF 提取失败 (user=%d): %v", user.ID, err)
			// 不返回错误，继续上传流程
		}
		
		// ⚠️ 关键：重置文件指针到起始位置
		if _, err := src.Seek(0, io.SeekStart); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "文件读取错误"})
			return
		}
		
		// ... 上传到 COS 的现有逻辑 ...
		
		// 3. 写入数据库（包含 EXIF 字段）
		photo := models.Photo{
			DeviceID:   deviceID,
			UserID:     user.ID,
			CosKey:     cosKey,
			CosURL:     cosURL,
			UploadedAt: time.Now(),
			
			// ✨ EXIF 字段
			TakenAt:     exifData.TakenAt,
			Latitude:    exifData.Latitude,
			Longitude:   exifData.Longitude,
			CameraMake:  exifData.CameraMake,
			CameraModel: exifData.CameraModel,
		}
		
		if err := db.Create(&photo).Error; err != nil {
			// ... 错误处理 ...
		}
		
		// ✨ 新增：如果有 GPS 坐标，加入地理编码队列
		if photo.Latitude != nil && photo.Longitude != nil {
			go func(photoID uint, lat, lon float64) {
				if err := enqueueGeocode(photoID, lat, lon); err != nil {
					log.Printf("地理编码入队失败 (photo_id=%d): %v", photoID, err)
				}
			}(photo.ID, *photo.Latitude, *photo.Longitude)
		}
		
		// ... 返回响应 ...
	}
}
```

---

### 第二阶段：地理编码服务

#### 新建文件：backend/services/geocoder.go

```go
package services

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

// Geocoder 腾讯地图逆地理编码服务
type Geocoder struct {
	apiKey     string
	httpClient *http.Client
}

// NewGeocoder 创建地理编码服务
func NewGeocoder(apiKey string) *Geocoder {
	return &Geocoder{
		apiKey: apiKey,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// ReverseGeocode 将坐标转换为地址
// 返回详细地址格式：省·市·区·街道
func (g *Geocoder) ReverseGeocode(ctx context.Context, lat, lon float64) (string, error) {
	// 腾讯地图 API 文档：https://lbs.qq.com/service/webService/webServiceGuide/webServiceGcoder
	apiURL := "https://apis.map.qq.com/ws/geocoder/v1/"
	params := url.Values{}
	params.Set("location", fmt.Sprintf("%f,%f", lat, lon))
	params.Set("key", g.apiKey)
	params.Set("get_poi", "0") // 不获取 POI 信息（减少响应体积）
	
	req, err := http.NewRequestWithContext(ctx, "GET", apiURL+"?"+params.Encode(), nil)
	if err != nil {
		return "", fmt.Errorf("构造请求失败: %w", err)
	}
	
	resp, err := g.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("API 请求失败: %w", err)
	}
	defer resp.Body.Close()
	
	// 限制响应体大小（防止恶意响应）
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1024*1024)) // 1MB limit
	if err != nil {
		return "", fmt.Errorf("读取响应失败: %w", err)
	}
	
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("API 返回错误状态码: %d, body: %s", resp.StatusCode, string(body))
	}
	
	// 解析响应
	var result struct {
		Status  int    `json:"status"`
		Message string `json:"message"`
		Result  struct {
			AddressComponent struct {
				Province string `json:"province"`
				City     string `json:"city"`
				District string `json:"district"`
				Street   string `json:"street"`
			} `json:"address_component"`
		} `json:"result"`
	}
	
	if err := json.Unmarshal(body, &result); err != nil {
		return "", fmt.Errorf("解析 JSON 失败: %w", err)
	}
	
	if result.Status != 0 {
		return "", fmt.Errorf("API 返回错误: status=%d, message=%s", result.Status, result.Message)
	}
	
	// 组合详细地址（省·市·区·街道）
	ac := result.Result.AddressComponent
	address := fmt.Sprintf("%s·%s·%s·%s", ac.Province, ac.City, ac.District, ac.Street)
	
	return address, nil
}
```

---

### 第三阶段：异步地理编码队列

#### 方案选择

**决策（基于项目研究）**：
- ❌ **不使用 Redis**：项目当前未使用 Redis（brainstorm 误判）
- ✅ **使用 Go Channel + Goroutine**：轻量级，适合小规模队列

**替代 Redis 的内存队列实现**：

#### 新建文件：backend/workers/geocode_worker.go

```go
package workers

import (
	"context"
	"log"
	"sync"
	"time"
	"gorm.io/gorm"
	"github.com/simonlei/photo-frame/backend/models"
	"github.com/simonlei/photo-frame/backend/services"
)

// GeocodeWorker 地理编码后台 Worker
type GeocodeWorker struct {
	db       *gorm.DB
	geocoder *services.Geocoder
	queue    chan geocodeTask
	wg       sync.WaitGroup
}

type geocodeTask struct {
	photoID uint
	lat     float64
	lon     float64
	retries int // 重试次数
}

// NewGeocodeWorker 创建 Worker
func NewGeocodeWorker(db *gorm.DB, geocoder *services.Geocoder) *GeocodeWorker {
	return &GeocodeWorker{
		db:       db,
		geocoder: geocoder,
		queue:    make(chan geocodeTask, 100), // 缓冲 100 个任务
	}
}

// Start 启动 Worker（并发数 3）
func (w *GeocodeWorker) Start(ctx context.Context, concurrency int) {
	for i := 0; i < concurrency; i++ {
		w.wg.Add(1)
		go w.worker(ctx, i)
	}
	log.Printf("GeocodeWorker 启动，并发数=%d", concurrency)
}

// worker 处理单个任务
func (w *GeocodeWorker) worker(ctx context.Context, id int) {
	defer w.wg.Done()
	
	for {
		select {
		case <-ctx.Done():
			log.Printf("Worker %d 退出", id)
			return
		case task := <-w.queue:
			w.processTask(ctx, task)
		}
	}
}

// processTask 执行地理编码
func (w *GeocodeWorker) processTask(ctx context.Context, task geocodeTask) {
	// 获取地址
	address, err := w.geocoder.ReverseGeocode(ctx, task.lat, task.lon)
	if err != nil {
		log.Printf("地理编码失败 (photo_id=%d, retries=%d): %v", task.photoID, task.retries, err)
		
		// 失败重试（最多 3 次，指数退避）
		if task.retries < 3 {
			task.retries++
			backoff := time.Duration(1<<uint(task.retries)) * time.Second // 2s, 4s, 8s
			time.Sleep(backoff)
			
			// 重新入队
			select {
			case w.queue <- task:
				log.Printf("地理编码重试 %d/3 (photo_id=%d)", task.retries, task.photoID)
			default:
				log.Printf("队列已满，放弃重试 (photo_id=%d)", task.photoID)
			}
		}
		return
	}
	
	// 更新数据库
	if err := w.db.Model(&models.Photo{}).
		Where("id = ?", task.photoID).
		Update("location_address", address).Error; err != nil {
		log.Printf("更新地址失败 (photo_id=%d): %v", task.photoID, err)
		return
	}
	
	log.Printf("地理编码成功 (photo_id=%d, address=%s)", task.photoID, address)
}

// Enqueue 添加任务到队列
func (w *GeocodeWorker) Enqueue(photoID uint, lat, lon float64) error {
	task := geocodeTask{
		photoID: photoID,
		lat:     lat,
		lon:     lon,
		retries: 0,
	}
	
	select {
	case w.queue <- task:
		return nil
	default:
		return fmt.Errorf("队列已满")
	}
}

// Stop 停止 Worker
func (w *GeocodeWorker) Stop() {
	close(w.queue)
	w.wg.Wait()
	log.Println("GeocodeWorker 已停止")
}
```

#### 修改文件：backend/main.go - 启动 Worker

```go
func main() {
	// ... 现有初始化代码 ...
	
	// ✨ 新增：初始化腾讯地图服务
	geocoder := services.NewGeocoder(os.Getenv("TENCENT_MAP_API_KEY"))
	
	// ✨ 新增：启动地理编码 Worker
	geocodeWorker := workers.NewGeocodeWorker(db, geocoder)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	geocodeWorker.Start(ctx, 3) // 并发数 3
	
	// ... 路由注册 ...
	
	// 优雅关闭
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	
	log.Println("正在关闭服务...")
	cancel() // 停止 Worker
	geocodeWorker.Stop()
}
```

#### 修改文件：backend/handlers/photo.go - 入队逻辑

```go
// 全局 Worker 实例（通过依赖注入）
var globalGeocodeWorker *workers.GeocodeWorker

// UploadPhoto 中调用
if photo.Latitude != nil && photo.Longitude != nil {
	if err := globalGeocodeWorker.Enqueue(photo.ID, *photo.Latitude, *photo.Longitude); err != nil {
		log.Printf("地理编码入队失败 (photo_id=%d): %v", photo.ID, err)
	}
}
```

---

### 第四阶段：环境变量配置

#### 修改文件：backend/.env.example

```bash
# 应用配置
PORT=8080
APP_ENV=development

# 数据库
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=photoframe
DB_PASS=your_password_here
DB_NAME=photoframe

# 微信小程序
WX_APPID=your_wx_appid_here
WX_SECRET=your_wx_secret_here

# 腾讯云 COS
COS_SECRET_ID=your_cos_secret_id_here
COS_SECRET_KEY=your_cos_secret_key_here
COS_BUCKET=your-bucket-name
COS_REGION=ap-guangzhou

# 管理后台
ADMIN_TOKEN=your_admin_token_here

# ✨ 新增：腾讯地图 API
TENCENT_MAP_API_KEY=your_tencent_map_key_here
```

#### 更新文件：backend/go.mod - 添加依赖

```bash
go get github.com/rwcarlsen/goexif/exif
go get github.com/rwcarlsen/goexif/tiff
```

---

## Android 前端实现

### 第一阶段：数据模型更新

#### 修改文件：android/app/src/main/java/com/photoframe/data/Photo.kt

```kotlin
data class Photo(
    val id: Long,
    val url: String,
    
    // 时间信息
    val takenAt: String?,           // EXIF 拍摄时间
    val uploadedAt: String,
    
    // 地理位置（新增）
    val latitude: Double?,
    val longitude: Double?,
    val locationAddress: String?,   // 详细地址
    
    // 相机信息（新增）
    val cameraMake: String?,
    val cameraModel: String?,
    
    // 上传者信息
    val uploaderName: String
)
```

---

### 第二阶段：UI 布局更新

#### 修改文件：android/app/src/main/res/layout/item_photo.xml

**现有布局**（简化）：
```xml
<FrameLayout>
    <ImageView
        android:id="@+id/ivPhoto"
        android:scaleType="centerCrop" />
    
    <LinearLayout
        android:id="@+id/llInfo"
        android:background="#80000000"
        android:padding="12dp"
        android:gravity="end|bottom">
        
        <TextView
            android:id="@+id/tvInfo"
            android:text="2026-03-10 · 张三"
            android:textColor="#FFFFFF" />
    </LinearLayout>
</FrameLayout>
```

**新增 EXIF 信息显示**：
```xml
<FrameLayout>
    <ImageView android:id="@+id/ivPhoto" ... />
    
    <LinearLayout
        android:id="@+id/llInfo"
        android:orientation="vertical"
        android:background="#80000000"
        android:padding="12dp"
        android:gravity="end|bottom">
        
        <!-- 拍摄时间 -->
        <TextView
            android:id="@+id/tvTakenTime"
            android:textSize="14sp"
            android:textColor="#FFFFFF"
            android:visibility="gone" />
        
        <!-- 地理位置 -->
        <TextView
            android:id="@+id/tvLocation"
            android:textSize="14sp"
            android:textColor="#FFFFFF"
            android:layout_marginTop="4dp"
            android:visibility="gone"
            android:ellipsize="end"
            android:maxLines="1" />
        
        <!-- 上传者 -->
        <TextView
            android:id="@+id/tvUploader"
            android:textSize="12sp"
            android:textColor="#CCCCCC"
            android:layout_marginTop="4dp" />
    </LinearLayout>
</FrameLayout>
```

---

### 第三阶段：适配器更新

#### 修改文件：android/app/src/main/java/com/photoframe/adapter/SlideShowAdapter.kt

```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val photo = photos[position]
    
    // 加载图片（现有逻辑）
    Glide.with(holder.itemView.context)
        .load(photo.url)
        .into(holder.ivPhoto)
    
    // ✨ 新增：EXIF 信息显示逻辑
    if (showInfo) {
        // 1. 拍摄时间（优先使用 takenAt，回退到 uploadedAt）
        val displayTime = photo.takenAt?.take(16)?.replace("T", " ") 
            ?: photo.uploadedAt.take(10)
        holder.tvTakenTime.apply {
            text = "📸 拍摄于 $displayTime"
            visibility = View.VISIBLE
        }
        
        // 2. 地理位置（有值才显示）
        if (!photo.locationAddress.isNullOrEmpty()) {
            holder.tvLocation.apply {
                text = "📍 ${photo.locationAddress}"
                visibility = View.VISIBLE
            }
        } else {
            holder.tvLocation.visibility = View.GONE
        }
        
        // 3. 上传者
        holder.tvUploader.text = "👤 ${photo.uploaderName}"
    } else {
        // 隐藏所有信息
        holder.tvTakenTime.visibility = View.GONE
        holder.tvLocation.visibility = View.GONE
        holder.tvUploader.visibility = View.GONE
    }
}
```

---

## 小程序前端实现

### 第一阶段：照片管理页更新

#### 修改文件：miniprogram/pages/manage/manage.wxml

**在照片缩略图下方添加地址显示**：
```html
<view class="photo-cell" wx:for="{{photos}}" wx:key="id">
  <image 
    src="{{item.url}}" 
    mode="aspectFill"
    lazy-load="{{true}}"
    bindtap="previewPhoto"
    data-index="{{index}}" />
  
  <!-- ✨ 新增：地理位置显示 -->
  <view class="photo-location" wx:if="{{item.locationAddress}}">
    📍 {{item.locationAddress}}
  </view>
  
  <!-- 原有：上传时间 -->
  <view class="photo-time">{{item.uploadedAt}}</view>
  
  <!-- 删除按钮 -->
  <view class="photo-delete" bindtap="deletePhoto" data-id="{{item.id}}">×</view>
</view>
```

#### 修改文件：miniprogram/pages/manage/manage.wxss

```css
.photo-location {
  position: absolute;
  bottom: 30rpx; /* 在时间上方 */
  left: 0;
  right: 0;
  padding: 4rpx 8rpx;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 22rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis; /* 地址过长时截断 */
}
```

---

### 第二阶段：照片详情页（新增）

#### 新建文件：miniprogram/pages/photo-detail/photo-detail.wxml

```html
<view class="container">
  <!-- 大图预览 -->
  <swiper 
    class="photo-swiper"
    current="{{currentIndex}}"
    bindchange="onSwiperChange">
    <swiper-item wx:for="{{photos}}" wx:key="id">
      <image 
        src="{{item.url}}" 
        mode="aspectFit"
        class="photo-image" />
    </swiper-item>
  </swiper>
  
  <!-- EXIF 信息卡片 -->
  <view class="info-card">
    <!-- 拍摄信息 -->
    <view class="info-section" wx:if="{{currentPhoto.takenAt || currentPhoto.cameraModel}}">
      <view class="section-title">📸 拍摄信息</view>
      <view class="info-row" wx:if="{{currentPhoto.takenAt}}">
        <text class="info-label">时间</text>
        <text class="info-value">{{currentPhoto.takenAt}}</text>
      </view>
      <view class="info-row" wx:if="{{currentPhoto.cameraModel}}">
        <text class="info-label">相机</text>
        <text class="info-value">{{currentPhoto.cameraModel}}</text>
      </view>
    </view>
    
    <!-- 位置信息 -->
    <view class="info-section" wx:if="{{currentPhoto.locationAddress}}">
      <view class="section-title">📍 位置信息</view>
      <view class="info-row">
        <text class="info-value full-width">{{currentPhoto.locationAddress}}</text>
      </view>
    </view>
    
    <!-- 上传信息 -->
    <view class="info-section">
      <view class="section-title">👤 上传信息</view>
      <view class="info-row">
        <text class="info-label">上传者</text>
        <text class="info-value">{{currentPhoto.uploaderName}}</text>
      </view>
      <view class="info-row">
        <text class="info-label">上传时间</text>
        <text class="info-value">{{currentPhoto.uploadedAt}}</text>
      </view>
    </view>
  </view>
  
  <!-- 操作按钮 -->
  <view class="action-buttons">
    <button class="btn-delete" bindtap="deletePhoto">删除照片</button>
    <button class="btn-share" open-type="share">分享</button>
  </view>
</view>
```

#### 新建文件：miniprogram/pages/photo-detail/photo-detail.js

```javascript
Page({
  data: {
    photos: [],
    currentIndex: 0,
    currentPhoto: {}
  },
  
  onLoad(options) {
    const photos = wx.getStorageSync('photos') || []
    const index = parseInt(options.index) || 0
    
    this.setData({
      photos: photos,
      currentIndex: index,
      currentPhoto: photos[index]
    })
  },
  
  onSwiperChange(e) {
    const index = e.detail.current
    this.setData({
      currentIndex: index,
      currentPhoto: this.data.photos[index]
    })
  },
  
  deletePhoto() {
    wx.showModal({
      title: '确认删除',
      content: '删除后无法恢复',
      success: (res) => {
        if (res.confirm) {
          // 调用删除 API
          // ...
        }
      }
    })
  }
})
```

#### 修改文件：miniprogram/pages/manage/manage.js - 添加跳转

```javascript
// 点击照片跳转详情页
previewPhoto(e) {
  const index = e.currentTarget.dataset.index
  wx.navigateTo({
    url: `/pages/photo-detail/photo-detail?index=${index}`
  })
}
```

---

## 管理后台前端实现

### 修改文件：admin-frontend/src/lib/api.ts - 类型定义

```typescript
export interface Photo {
  id: number
  device_id: string
  url: string
  taken_at?: string          // EXIF 拍摄时间（新增）
  uploaded_at: string
  latitude?: number          // 纬度（新增）
  longitude?: number         // 经度（新增）
  location_address?: string  // 详细地址（新增）
  camera_make?: string       // 相机制造商（新增）
  camera_model?: string      // 相机型号（新增）
  uploader_name: string
  device_name: string
}
```

---

### 修改文件：admin-frontend/src/components/PhotoGrid.tsx

```tsx
import { Image, Tooltip } from 'antd'

export function PhotoGrid({ photos }: { photos: Photo[] }) {
  return (
    <div className="photo-grid">
      {photos.map(photo => (
        <div key={photo.id} className="photo-item">
          <Tooltip 
            title={
              <div className="photo-tooltip">
                {photo.taken_at && (
                  <div>📸 {photo.taken_at}</div>
                )}
                {photo.location_address && (
                  <div>📍 {photo.location_address}</div>
                )}
                {photo.camera_model && (
                  <div>📷 {photo.camera_model}</div>
                )}
              </div>
            }
            placement="top"
          >
            <Image 
              src={photo.url} 
              alt="照片"
              preview={{
                mask: <div className="photo-mask">查看详情</div>
              }}
            />
          </Tooltip>
          
          {/* 照片下方显示地址（截断） */}
          {photo.location_address && (
            <div className="photo-address">
              📍 {photo.location_address.slice(0, 20)}
              {photo.location_address.length > 20 && '...'}
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
```

---

## 测试策略

### 单元测试

#### 新建文件：backend/services/exif_extractor_test.go

```go
package services

import (
	"os"
	"testing"
	"github.com/stretchr/testify/assert"
)

func TestExtractEXIF_WithFullMetadata(t *testing.T) {
	// 准备测试图片（iPhone 拍摄，包含完整 EXIF）
	file, err := os.Open("testdata/iphone_photo.jpg")
	assert.NoError(t, err)
	defer file.Close()
	
	exif, err := ExtractEXIF(file)
	assert.NoError(t, err)
	assert.NotNil(t, exif.TakenAt)
	assert.NotNil(t, exif.Latitude)
	assert.NotNil(t, exif.Longitude)
	assert.NotNil(t, exif.CameraModel)
	assert.Contains(t, *exif.CameraModel, "iPhone")
	
	// 验证文件指针已重置
	pos, _ := file.Seek(0, io.SeekCurrent)
	assert.Equal(t, int64(0), pos, "文件指针应在起始位置")
}

func TestExtractEXIF_NoMetadata(t *testing.T) {
	// 截图（无 EXIF）
	file, err := os.Open("testdata/screenshot.png")
	assert.NoError(t, err)
	defer file.Close()
	
	exif, err := ExtractEXIF(file)
	assert.NoError(t, err) // 不应返回错误
	assert.Nil(t, exif.TakenAt)
	assert.Nil(t, exif.Latitude)
}

func TestExtractEXIF_PartialGPS(t *testing.T) {
	// 仅有经度无纬度（边界情况）
	file, err := os.Open("testdata/partial_gps.jpg")
	assert.NoError(t, err)
	defer file.Close()
	
	exif, err := ExtractEXIF(file)
	assert.NoError(t, err)
	// GPS 坐标应全为 nil（同时存在才有效）
	assert.Nil(t, exif.Latitude)
	assert.Nil(t, exif.Longitude)
}
```

#### 新建文件：backend/services/geocoder_test.go

```go
package services

import (
	"context"
	"testing"
	"github.com/stretchr/testify/assert"
)

func TestReverseGeocode_Beijing(t *testing.T) {
	// Mock API Key（测试时使用真实 Key 或 Mock HTTP）
	geocoder := NewGeocoder("test_key")
	
	// 北京天安门坐标
	address, err := geocoder.ReverseGeocode(context.Background(), 39.908823, 116.397470)
	assert.NoError(t, err)
	assert.Contains(t, address, "北京")
	assert.Contains(t, address, "东城区")
}

func TestReverseGeocode_Timeout(t *testing.T) {
	geocoder := NewGeocoder("test_key")
	
	// 模拟超时（使用已取消的 context）
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	
	_, err := geocoder.ReverseGeocode(ctx, 39.9, 116.4)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "context canceled")
}
```

---

### 集成测试

#### 测试场景清单

| 场景 | 验证点 | 预期结果 |
|------|--------|----------|
| **上传带完整 EXIF 的照片** | 1. EXIF 提取成功<br>2. 数据库字段正确<br>3. 地理编码队列入队 | `taken_at`, `latitude`, `longitude`, `camera_model` 非空 |
| **上传无 GPS 照片** | 1. EXIF 提取成功<br>2. GPS 字段为 NULL<br>3. 不入地理编码队列 | `latitude`/`longitude` 为 NULL，照片正常入库 |
| **上传截图（无 EXIF）** | 1. EXIF 提取返回空数据<br>2. 所有 EXIF 字段为 NULL<br>3. 照片正常入库 | 回退使用 `uploaded_at` 作为显示时间 |
| **地理编码 Worker 处理** | 1. 从队列消费任务<br>2. 调用腾讯地图 API<br>3. 更新 `location_address` | 10 秒内地址更新成功 |
| **地理编码 API 失败** | 1. 失败重试 3 次<br>2. 指数退避（2s, 4s, 8s）<br>3. 最终放弃 | `location_address` 保持 NULL，不影响照片显示 |
| **Android 端同步** | 1. 上传照片后等待 60 秒<br>2. 相框端同步照片<br>3. 显示 EXIF 信息 | 照片信息叠加层显示拍摄时间和地址 |
| **小程序照片详情** | 1. 点击照片缩略图<br>2. 进入详情页<br>3. 查看完整 EXIF | 显示所有非空 EXIF 字段 |
| **管理后台 Tooltip** | 1. 鼠标悬停照片<br>2. Tooltip 显示 | 显示拍摄时间、地址、相机型号 |

#### 集成测试脚本

```bash
#!/bin/bash
# tests/integration/test_exif_upload.sh

# 1. 准备测试图片
TEST_IMAGE="tests/fixtures/iphone_photo_with_gps.jpg"

# 2. 上传照片
RESPONSE=$(curl -X POST http://localhost:8080/api/upload \
  -H "Authorization: Bearer $TEST_TOKEN" \
  -F "file=@$TEST_IMAGE" \
  -F "device_id=test_device")

PHOTO_ID=$(echo $RESPONSE | jq -r '.id')

# 3. 验证数据库记录
DB_RESULT=$(mysql -h 127.0.0.1 -u photoframe -p$DB_PASS photoframe \
  -e "SELECT taken_at, latitude, longitude, camera_model FROM photos WHERE id=$PHOTO_ID" \
  -sN)

echo "数据库记录: $DB_RESULT"

# 4. 等待地理编码（最多 30 秒）
for i in {1..30}; do
  LOCATION=$(mysql -h 127.0.0.1 -u photoframe -p$DB_PASS photoframe \
    -e "SELECT location_address FROM photos WHERE id=$PHOTO_ID" \
    -sN)
  
  if [ ! -z "$LOCATION" ]; then
    echo "✅ 地理编码成功: $LOCATION"
    break
  fi
  
  sleep 1
done

# 5. 验证 API 响应
API_RESPONSE=$(curl -s http://localhost:8080/api/photos?device_id=test_device \
  -H "Authorization: Bearer $TEST_TOKEN")

echo $API_RESPONSE | jq ".photos[] | select(.id==$PHOTO_ID)"
```

---

### 端到端测试

#### Android 测试（Espresso）

```kotlin
// android/app/src/androidTest/kotlin/com/photoframe/ExifDisplayTest.kt

@RunWith(AndroidJUnit4::class)
class ExifDisplayTest {
    
    @Test
    fun testPhotoInfoDisplay_WithEXIF() {
        // 1. 启动 Activity
        ActivityScenario.launch(MainActivity::class.java)
        
        // 2. 等待照片同步（模拟）
        onView(withId(R.id.viewPager)).perform(waitForSync())
        
        // 3. 验证拍摄时间显示
        onView(withId(R.id.tvTakenTime))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("📸 拍摄于"))))
        
        // 4. 验证地理位置显示
        onView(withId(R.id.tvLocation))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("📍"))))
    }
    
    @Test
    fun testPhotoInfoDisplay_NoGPS() {
        // 验证无 GPS 照片不显示位置
        onView(withId(R.id.tvLocation))
            .check(matches(not(isDisplayed())))
    }
}
```

---

## 性能基准测试

### EXIF 提取性能

```go
// backend/services/exif_extractor_benchmark_test.go

func BenchmarkExtractEXIF(b *testing.B) {
	file, _ := os.Open("testdata/sample_5mb.jpg")
	defer file.Close()
	
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		ExtractEXIF(file)
		file.Seek(0, io.SeekStart)
	}
}

// 预期结果：< 50ms per operation
```

### 上传流程性能

```bash
# 压测工具：Apache Bench
ab -n 100 -c 10 -H "Authorization: Bearer $TOKEN" \
   -F "file=@tests/fixtures/photo.jpg" \
   -F "device_id=test_device" \
   http://localhost:8080/api/upload

# 对比指标：
# - 添加 EXIF 提取前：平均 450ms/请求
# - 添加 EXIF 提取后：平均 500ms/请求（增加 < 100ms ✅）
```

---

## 部署清单

### 第一步：后端部署

1. **更新依赖**：
```bash
cd backend
go get github.com/rwcarlsen/goexif/exif
go mod tidy
```

2. **配置环境变量**：
```bash
# 生产环境 .env 文件
echo "TENCENT_MAP_API_KEY=YOUR_REAL_KEY" >> .env
```

3. **执行数据库迁移**（生产环境）：
```bash
mysql -h $DB_HOST -u $DB_USER -p$DB_PASS $DB_NAME < migrations/20260313_add_photo_exif_fields.sql
```

4. **重启后端服务**：
```bash
docker-compose restart backend
```

5. **验证 Worker 启动**：
```bash
docker-compose logs backend | grep "GeocodeWorker 启动"
# 应输出：GeocodeWorker 启动，并发数=3
```

---

### 第二步：前端部署

#### Android

1. **更新 API 数据模型**（已完成代码修改）
2. **重新构建 APK**：
```bash
cd android
./gradlew assembleRelease
```
3. **上传到管理后台**（触发用户自动更新）

#### 小程序

1. **上传代码到微信开发者工具**
2. **提交审核**（预计 1-2 天）
3. **发布线上版本**

#### 管理后台

1. **构建生产版本**：
```bash
cd admin-frontend
npm run build
```
2. **部署到 Nginx**（已在 docker-compose.yml 配置）

---

### 第三步：验证部署

```bash
# 1. 上传测试照片（带 EXIF）
curl -X POST https://your-domain.com/api/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@tests/fixtures/iphone_photo.jpg" \
  -F "device_id=test_device"

# 2. 检查数据库
mysql -h $DB_HOST -u $DB_USER -p -e \
  "SELECT id, taken_at, latitude, longitude, location_address FROM photoframe.photos ORDER BY id DESC LIMIT 1;"

# 3. 等待 10 秒后验证地理编码
# location_address 应有值

# 4. 在 Android 相框端验证显示
# 检查照片信息叠加层是否显示拍摄时间和地址
```

---

## 历史数据迁移（可选）

### 迁移脚本：backend/scripts/migrate_historical_photos.go

```go
package main

import (
	"context"
	"log"
	"time"
	"gorm.io/gorm"
	// ... imports
)

func main() {
	db := initDB()
	cos := initCOS()
	
	// 查询所有未提取 EXIF 的照片（taken_at 为 NULL）
	var photos []models.Photo
	db.Where("taken_at IS NULL").FindInBatches(&photos, 100, func(tx *gorm.DB, batch int) error {
		log.Printf("处理第 %d 批，共 %d 张照片", batch, len(photos))
		
		for _, photo := range photos {
			// 1. 从 COS 下载原图（使用临时文件）
			tmpFile, err := downloadFromCOS(cos, photo.CosKey)
			if err != nil {
				log.Printf("下载失败 (photo_id=%d): %v", photo.ID, err)
				continue
			}
			defer os.Remove(tmpFile.Name())
			
			// 2. 提取 EXIF
			exifData, err := services.ExtractEXIF(tmpFile)
			if err != nil {
				log.Printf("EXIF 提取失败 (photo_id=%d): %v", photo.ID, err)
				continue
			}
			
			// 3. 更新数据库
			updates := map[string]interface{}{
				"taken_at":     exifData.TakenAt,
				"latitude":     exifData.Latitude,
				"longitude":    exifData.Longitude,
				"camera_make":  exifData.CameraMake,
				"camera_model": exifData.CameraModel,
			}
			
			if err := db.Model(&photo).Updates(updates).Error; err != nil {
				log.Printf("更新失败 (photo_id=%d): %v", photo.ID, err)
				continue
			}
			
			// 4. 入队地理编码
			if exifData.Latitude != nil && exifData.Longitude != nil {
				enqueueGeocode(photo.ID, *exifData.Latitude, *exifData.Longitude)
			}
			
			log.Printf("✅ 成功处理 photo_id=%d", photo.ID)
		}
		
		return nil
	})
	
	log.Println("迁移完成")
}
```

**运行方式**：
```bash
go run backend/scripts/migrate_historical_photos.go
```

---

## 监控与告警

### 关键指标

| 指标 | 监控方式 | 告警阈值 |
|------|---------|---------|
| **EXIF 提取成功率** | 日志统计 | < 70% |
| **地理编码成功率** | 日志统计 | < 50% |
| **地理编码队列长度** | Worker 指标 | > 500 |
| **腾讯地图 API 调用量** | API 控制台 | > 80,000/天 |
| **照片上传耗时（P95）** | APM 监控 | > 1000ms |

### 日志埋点

```go
// 在关键路径添加日志
log.Printf("[EXIF] 提取成功 (photo_id=%d, has_gps=%v, camera=%s)", 
    photo.ID, photo.Latitude != nil, photo.CameraModel)

log.Printf("[Geocode] 入队成功 (photo_id=%d, lat=%.6f, lon=%.6f)", 
    photo.ID, *photo.Latitude, *photo.Longitude)

log.Printf("[Geocode] 编码成功 (photo_id=%d, address=%s, duration=%v)", 
    photo.ID, address, time.Since(start))
```

---

## 验收标准

### 功能验收

- [ ] **后端**：
  - [ ] 上传照片自动提取 EXIF（拍摄时间、GPS、相机型号）
  - [ ] 有 GPS 坐标的照片自动地理编码
  - [ ] 地理编码失败自动重试 3 次
  - [ ] API 返回新增的 5 个 EXIF 字段
  
- [ ] **Android**：
  - [ ] 照片信息叠加层显示拍摄时间
  - [ ] 照片信息叠加层显示地理位置
  - [ ] 无 GPS 照片不显示位置行
  - [ ] 设置中可切换信息显示开关
  
- [ ] **小程序**：
  - [ ] 照片管理页缩略图下方显示地址（截断至 10 字符）
  - [ ] 点击照片进入详情页
  - [ ] 详情页显示完整 EXIF 信息
  
- [ ] **管理后台**：
  - [ ] 鼠标悬停照片显示 Tooltip
  - [ ] Tooltip 包含拍摄时间、地址、相机型号

### 性能验收

- [ ] EXIF 提取耗时 < 100ms（P95）
- [ ] 照片上传总耗时增加 < 100ms
- [ ] 地理编码队列延迟 < 10 秒（P95）
- [ ] 腾讯地图 API 成功率 > 95%

### 边界情况验收

- [ ] 上传无 EXIF 照片（截图）→ 所有字段为 NULL，照片正常入库
- [ ] 上传无 GPS 照片 → `latitude`/`longitude` 为 NULL，不触发地理编码
- [ ] 腾讯地图 API 限流/失败 → 自动重试，失败 3 次后放弃
- [ ] 海外 GPS 坐标 → 返回国家级地址（如"美国·加利福尼亚州"）

---

## 风险与缓解措施

### 风险 1：EXIF 提取影响上传性能

**概率**：低  
**影响**：中  

**缓解措施**：
1. 压测验证（1000 张照片上传测试）
2. 监控上传耗时 P95 指标
3. 如延迟 > 200ms，改为异步提取（先入库，后台补充 EXIF）

---

### 风险 2：腾讯地图 API 成本超预期

**概率**：低  
**影响**：低  

**缓解措施**：
1. 设置 API 调用监控告警（每日 80,000 次告警）
2. 免费额度 10 万次/天足够小型应用（预计日均 500-1000 次）
3. 超额时考虑限流（仅对新上传照片编码）

---

### 风险 3：历史照片迁移失败率高

**概率**：中  
**影响**：低  

**缓解措施**：
1. 接受部分照片无 EXIF 的现实（用户可能上传截图/编辑过的照片）
2. 优先处理最近 6 个月的照片
3. 提供手动重试脚本

---

### 风险 4：文件指针未重置导致 COS 上传损坏

**概率**：高（如不注意）  
**影响**：高（照片损坏）  

**缓解措施**：
1. ⚠️ **关键**：在 EXIF 提取后强制调用 `src.Seek(0, io.SeekStart)`
2. 添加单元测试验证文件指针位置
3. 上传后验证 COS 文件大小（应与原始文件一致）

---

## 成功指标

### 功能覆盖率
- ≥ 70% 的新上传照片成功提取 EXIF
- ≥ 50% 的照片包含 GPS 信息
- 100% 的有 GPS 照片完成地理编码（失败重试后）

### 性能指标
- 照片上传耗时增加 < 100ms（P95）
- 地理编码队列延迟 < 10 秒（P95）
- EXIF 提取错误率 < 5%

### 用户体验
- Android 端信息显示清晰可读
- 小程序/后台无 UI 错位或数据缺失
- 无 GPS 照片静默处理（无负面提示）

### 稳定性
- EXIF 提取错误率 < 5%
- 地理编码 API 失败自动降级
- 系统无因 EXIF 功能导致的上传失败

---

## 未来扩展可能性

### 短期（3-6 个月）
1. **按地理位置筛选**："显示在北京拍摄的所有照片"
2. **相机信息统计**："哪款相机拍摄的照片最多？"
3. **时间轴视图**：按拍摄时间（而非上传时间）组织照片

### 长期（6-12 个月）
1. **地图视图模式**：在地图上展示所有带位置的照片
2. **智能相册**：自动按地点创建相册（"北京旅行"、"杭州出差"）
3. **位置隐私保护**：用户可选择不显示/删除位置信息

---

## 文档更新

### 需要更新的文档

- [ ] **API 文档**：更新 `/api/photos` 响应字段说明
- [ ] **部署文档**：添加腾讯地图 API Key 配置步骤
- [ ] **用户手册**：说明 EXIF 信息展示功能
- [ ] **开发者指南**：添加 EXIF 提取和地理编码架构图

---

## 源文档与参考资料

### 源文档
- **Brainstorm 文档**：[docs/brainstorms/2026-03-13-photo-exif-location-brainstorm.md](../brainstorms/2026-03-13-photo-exif-location-brainstorm.md)
  
  **关键决策引用**：
  - 数据库设计选择：扩展现有 Photo 表（查询性能最优）
  - 处理时机：EXIF 提取同步，地理编码异步（避免阻塞）
  - 地图 API 选择：腾讯地图（统一账号体系，10万次/天免费额度）
  - 无 GPS 处理：静默隐藏位置字段（无负面提示）

### 内部参考
- **项目架构**：d:/work/photo-frame/backend/main.go
- **现有模型**：d:/work/photo-frame/backend/models/photo.go（已预留 `TakenAt` 字段）
- **上传逻辑**：d:/work/photo-frame/backend/handlers/photo.go（需修改）
- **Android 适配器**：d:/work/photo-frame/android/app/src/main/java/com/photoframe/adapter/SlideShowAdapter.kt

### 外部参考
- **goexif 库文档**：https://github.com/rwcarlsen/goexif
- **腾讯地图 API**：https://lbs.qq.com/service/webService/webServiceGuide/webServiceGcoder
- **EXIF 标准**：https://en.wikipedia.org/wiki/Exif

### 相关文档
- **功能差距分析**：docs/2026-03-10-photo-frame-feature-gap-analysis.md
- **系统实现计划**：docs/plans/2026-03-04-feat-photo-frame-system-plan.md
- **代码审查建议**：docs/solutions/multi-category/photo-frame-comprehensive-code-review.md

---

## 实施时间线

| 阶段 | 任务 | 预计工期 | 负责人 |
|------|------|---------|--------|
| **第一阶段** | 后端基础设施 | 3-4 天 | 后端开发 |
| | - 数据库迁移 | 0.5 天 | |
| | - 引入 goexif 库 | 0.5 天 | |
| | - 实现 EXIF 提取 | 1 天 | |
| | - 配置腾讯地图 API | 0.5 天 | |
| | - 实现地理编码 Worker | 1.5 天 | |
| | - 单元测试 | 1 天 | |
| **第二阶段** | API 更新 | 1 天 | 后端开发 |
| | - 更新 Photo 模型 | 0.5 天 | |
| | - 修改 API 响应 | 0.5 天 | |
| **第三阶段** | 前端展示 | 4-5 天 | 前端团队 |
| | - Android 实现 | 2 天 | Android 开发 |
| | - 小程序实现 | 1.5 天 | 小程序开发 |
| | - 管理后台实现 | 1.5 天 | Web 前端开发 |
| **第四阶段** | 测试与优化 | 2-3 天 | 测试工程师 |
| | - 集成测试 | 1 天 | |
| | - 性能测试 | 1 天 | |
| | - UI/UX 调优 | 1 天 | |
| **总计** | | **10-13 天** | |

---

## 备注

1. **关键依赖**：腾讯地图 API Key 必须在后端开发前申请完成
2. **测试数据**：需准备 20-30 张带完整 EXIF 的测试照片（iPhone、Android、相机拍摄）
3. **生产部署**：必须在低峰期（凌晨）执行数据库迁移，预计停机时间 < 5 分钟
4. **版本兼容**：旧版 Android 客户端可正常工作（新字段自动忽略），但无法显示 EXIF 信息

---

**计划创建时间**：2026-03-13  
**最后更新时间**：2026-03-13  
**计划状态**：待审核
