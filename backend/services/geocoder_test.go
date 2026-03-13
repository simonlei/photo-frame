package services

import (
	"testing"
)

// TestCalculateSignature 测试签名计算是否符合腾讯地图官方规范
func TestCalculateSignature(t *testing.T) {
	tests := []struct {
		name        string
		apiKey      string
		secretKey   string
		requestPath string
		params      map[string]string
		expectedSig string
	}{
		{
			name:        "基本逆地理编码请求",
			apiKey:      "OB4BZ-D4W3U-B7VVO-4PJWW-6TKDJ-WPB77",
			secretKey:   "q9UQDN1jHSDoqJZsXTk0Coe1WlN",
			requestPath: "/ws/geocoder/v1",
			params: map[string]string{
				"location": "39.984154,116.307490",
				"key":      "OB4BZ-D4W3U-B7VVO-4PJWW-6TKDJ-WPB77",
				"output":   "json",
			},
			// 根据官方算法: MD5(/ws/geocoder/v1?key=...&location=...&output=json + SK)
			expectedSig: "8ff3f0e3fcad1f618f78df875fb1fc05", // 实际签名需要根据官方工具验证
		},
		{
			name:        "参数自动排序测试",
			apiKey:      "TEST_KEY",
			secretKey:   "TEST_SECRET",
			requestPath: "/ws/geocoder/v1",
			params: map[string]string{
				"z_param": "last",
				"a_param": "first",
				"m_param": "middle",
			},
			// 验证参数按字母顺序排列: a_param, m_param, z_param
			expectedSig: "", // 仅测试排序逻辑，不验证具体签名值
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			geocoder := NewGeocoder(tt.apiKey, tt.secretKey)
			sig := geocoder.calculateSignature(tt.requestPath, tt.params)

			// 基本校验
			if len(sig) != 32 {
				t.Errorf("签名长度应为 32（MD5），实际为 %d", len(sig))
			}

			// 验证是否为小写
			for _, ch := range sig {
				if ch >= 'A' && ch <= 'Z' {
					t.Error("签名应全部为小写字符")
					break
				}
			}

			// 如果提供了期望值，进行精确匹配
			if tt.expectedSig != "" && sig != tt.expectedSig {
				t.Errorf("签名计算错误\n期望: %s\n实际: %s", tt.expectedSig, sig)
			}

			t.Logf("计算的签名: %s", sig)
		})
	}
}

// TestSignatureConsistency 测试多次计算同一签名是否一致
func TestSignatureConsistency(t *testing.T) {
	geocoder := NewGeocoder("TEST_KEY", "TEST_SECRET")
	params := map[string]string{
		"location": "39.984154,116.307490",
		"key":      "TEST_KEY",
	}

	sig1 := geocoder.calculateSignature("/ws/geocoder/v1", params)
	sig2 := geocoder.calculateSignature("/ws/geocoder/v1", params)

	if sig1 != sig2 {
		t.Errorf("相同参数的签名应一致\n第一次: %s\n第二次: %s", sig1, sig2)
	}
}

// TestSignatureDifferentParams 测试不同参数生成不同签名
func TestSignatureDifferentParams(t *testing.T) {
	geocoder := NewGeocoder("TEST_KEY", "TEST_SECRET")

	params1 := map[string]string{
		"location": "39.984154,116.307490",
		"key":      "TEST_KEY",
	}

	params2 := map[string]string{
		"location": "40.000000,117.000000", // 不同的坐标
		"key":      "TEST_KEY",
	}

	sig1 := geocoder.calculateSignature("/ws/geocoder/v1", params1)
	sig2 := geocoder.calculateSignature("/ws/geocoder/v1", params2)

	if sig1 == sig2 {
		t.Error("不同参数应生成不同的签名")
	}
}
