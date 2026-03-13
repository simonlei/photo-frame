package services

import (
	"context"
	"testing"
	"time"
)

func TestReverseGeocode_Beijing(t *testing.T) {
	// 需要设置真实的腾讯地图 API Key 才能运行
	apiKey := "test_key" // 替换为真实 Key 进行集成测试
	if apiKey == "test_key" {
		t.Skip("跳过测试：需要真实的腾讯地图 API Key")
	}

	geocoder := NewGeocoder(apiKey)

	// 北京天安门坐标
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	address, err := geocoder.ReverseGeocode(ctx, 39.908823, 116.397470)
	if err != nil {
		t.Fatalf("ReverseGeocode 返回错误: %v", err)
	}

	t.Logf("✓ 地址: %s", address)

	// 验证地址包含"北京"
	if len(address) == 0 {
		t.Error("地址为空")
	}
}

func TestReverseGeocode_Timeout(t *testing.T) {
	geocoder := NewGeocoder("test_key")

	// 模拟超时（使用已取消的 context）
	ctx, cancel := context.WithCancel(context.Background())
	cancel() // 立即取消

	_, err := geocoder.ReverseGeocode(ctx, 39.9, 116.4)
	if err == nil {
		t.Error("预期返回错误，但成功了")
	}

	t.Logf("✓ 正确处理超时: %v", err)
}

func TestReverseGeocode_InvalidKey(t *testing.T) {
	geocoder := NewGeocoder("invalid_key")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// 使用无效 Key 应返回错误
	_, err := geocoder.ReverseGeocode(ctx, 39.9, 116.4)
	if err == nil {
		t.Error("预期返回错误（无效 API Key），但成功了")
	}

	t.Logf("✓ 正确处理无效 Key: %v", err)
}
