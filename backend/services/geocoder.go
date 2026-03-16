package services

import (
	"context"
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"
)

// Geocoder 腾讯地图逆地理编码服务（使用签名校验方式）
type Geocoder struct {
	apiKey     string
	secretKey  string // 用于签名计算的密钥
	httpClient *http.Client
}

// NewGeocoder 创建地理编码服务
// apiKey: 腾讯地图 API Key
// secretKey: 签名密钥（SK），从控制台勾选"SN校验"后获取
func NewGeocoder(apiKey, secretKey string) *Geocoder {
	return &Geocoder{
		apiKey:    apiKey,
		secretKey: secretKey,
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// calculateSignature 计算腾讯地图 API 请求签名
// 签名算法: MD5(请求路径 + "?" + 排序后的参数 + SK)
// 参数必须按字母升序排列，且使用未编码的原始值
func (g *Geocoder) calculateSignature(requestPath string, params map[string]string) string {
	// 1. 提取参数键并排序
	keys := make([]string, 0, len(params))
	for k := range params {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	// 2. 按排序顺序拼接参数 (key=value&key2=value2)
	var sortedParams []string
	for _, k := range keys {
		sortedParams = append(sortedParams, fmt.Sprintf("%s=%s", k, params[k]))
	}
	paramsStr := strings.Join(sortedParams, "&")

	// 3. 拼接完整字符串: 路径 + "?" + 参数 + SK
	plainText := requestPath + "?" + paramsStr + g.secretKey

	// 4. 计算 MD5 并转为小写
	hash := md5.Sum([]byte(plainText))
	return hex.EncodeToString(hash[:])
}

// ReverseGeocode 将坐标转换为地址
// 返回详细地址格式：省·市·区·街道
func (g *Geocoder) ReverseGeocode(ctx context.Context, lat, lon float64) (string, error) {
	// 腾讯地图 API 文档：https://lbs.qq.com/service/webService/webServiceGuide/webServiceGcoder
	apiURL := "https://apis.map.qq.com/ws/geocoder/v1/"
	requestPath := "/ws/geocoder/v1/"

	// 1. 构建参数（使用原始未编码的值用于签名计算）
	params := map[string]string{
		"location": fmt.Sprintf("%f,%f", lat, lon),
		"key":      g.apiKey,
		"get_poi":  "0", // 不获取 POI 信息（减少响应体积）
	}

	// 2. 计算签名
	sig := g.calculateSignature(requestPath, params)

	// 3. 构建最终 URL（参数值需要 URL 编码，但 sig 保持原样）
	urlParams := url.Values{}
	for k, v := range params {
		urlParams.Set(k, v)
	}
	urlParams.Set("sig", sig)

	finalURL := apiURL + "?" + urlParams.Encode()

	// 4. 发起请求
	req, err := http.NewRequestWithContext(ctx, "GET", finalURL, nil)
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
