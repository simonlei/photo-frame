package services

import (
	"io"
	"os"
	"testing"
)

func TestExtractEXIF_WithFullMetadata(t *testing.T) {
	// 注意：这个测试需要准备包含完整 EXIF 的测试图片
	// 路径：backend/testdata/iphone_photo.jpg
	file, err := os.Open("../testdata/iphone_photo.jpg")
	if err != nil {
		t.Skipf("测试图片不存在，跳过测试: %v", err)
		return
	}
	defer file.Close()

	exifData, err := ExtractEXIF(file)
	if err != nil {
		t.Fatalf("ExtractEXIF 返回错误: %v", err)
	}

	if exifData.TakenAt != nil {
		t.Logf("✓ 拍摄时间: %v", *exifData.TakenAt)
	}

	if exifData.Latitude != nil && exifData.Longitude != nil {
		t.Logf("✓ GPS 坐标: lat=%.6f, lon=%.6f", *exifData.Latitude, *exifData.Longitude)
	}

	if exifData.CameraModel != nil {
		t.Logf("✓ 相机型号: %s", *exifData.CameraModel)
	}

	// 验证文件指针已重置
	pos, _ := file.Seek(0, io.SeekCurrent)
	if pos != 0 {
		t.Errorf("文件指针未重置到起始位置，当前位置: %d", pos)
	}
}

func TestExtractEXIF_NoMetadata(t *testing.T) {
	// 测试无 EXIF 的图片（如截图）
	file, err := os.Open("../testdata/screenshot.png")
	if err != nil {
		t.Skipf("测试图片不存在，跳过测试: %v", err)
		return
	}
	defer file.Close()

	exifData, err := ExtractEXIF(file)
	if err != nil {
		t.Fatalf("ExtractEXIF 不应返回错误: %v", err)
	}

	// 无 EXIF 的图片应该返回空数据
	if exifData.TakenAt != nil {
		t.Errorf("预期 TakenAt 为 nil，实际: %v", exifData.TakenAt)
	}

	if exifData.Latitude != nil || exifData.Longitude != nil {
		t.Errorf("预期 GPS 坐标为 nil")
	}

	t.Log("✓ 无 EXIF 数据的图片处理正常")
}
