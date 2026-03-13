package services

import (
	"bytes"
	"io"
	"log"
	"time"

	"github.com/rwcarlsen/goexif/exif"
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

	log.Println("📸 开始解析 EXIF 数据...")
	x, err := exif.Decode(teeReader)
	if err != nil {
		// 提取失败不应阻塞上传流程
		log.Printf("⚠️  EXIF 解析失败（图片可能不包含 EXIF）: %v", err)
		return data, nil // 返回空数据而非错误
	}
	log.Println("✅ EXIF 数据解析成功")

	// 1. 提取拍摄时间
	if tm, err := x.DateTime(); err == nil {
		data.TakenAt = &tm
		log.Printf("  📅 拍摄时间: %s", tm.Format("2006-01-02 15:04:05"))
	} else {
		log.Printf("  ⚠️  未找到拍摄时间字段: %v", err)
	}

	// 2. 提取 GPS 坐标
	lat, lon, err := x.LatLong()
	if err == nil {
		data.Latitude = &lat
		data.Longitude = &lon
		log.Printf("  📍 GPS 坐标: (%.6f, %.6f)", lat, lon)
	} else {
		log.Printf("  ⚠️  未找到 GPS 坐标: %v", err)
	}

	// 3. 提取相机制造商
	if make, err := x.Get(exif.Make); err == nil {
		if str, err := make.StringVal(); err == nil {
			data.CameraMake = &str
			log.Printf("  📷 相机制造商: %s", str)
		}
	} else {
		log.Printf("  ⚠️  未找到相机制造商字段")
	}

	// 4. 提取相机型号
	if model, err := x.Get(exif.Model); err == nil {
		if str, err := model.StringVal(); err == nil {
			data.CameraModel = &str
			log.Printf("  📷 相机型号: %s", str)
		}
	} else {
		log.Printf("  ⚠️  未找到相机型号字段")
	}

	log.Println("📸 EXIF 提取完成")
	return data, nil
}
