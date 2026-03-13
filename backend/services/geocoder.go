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
