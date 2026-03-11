package handlers

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

const githubReleaseURL = "https://api.github.com/repos/simonlei/photo-frame/releases/latest"

var (
	cachedRelease *githubRelease
	cacheExpireAt time.Time
	cacheTTL      = 5 * time.Minute
)

type githubRelease struct {
	TagName string        `json:"tag_name"`
	Body    string        `json:"body"`
	Assets  []githubAsset `json:"assets"`
}

type githubAsset struct {
	Name               string `json:"name"`
	BrowserDownloadURL string `json:"browser_download_url"`
}

// VersionLatest 获取最新 APK 版本信息（代理 GitHub Releases API）
func VersionLatest() gin.HandlerFunc {
	return func(c *gin.Context) {
		rel, err := fetchLatestRelease()
		if err != nil || rel == nil {
			c.JSON(http.StatusOK, gin.H{"version": nil})
			return
		}
		version := rel.TagName
		if len(version) > 0 && version[0] == 'v' {
			version = version[1:]
		}
		apkURL := ""
		for _, a := range rel.Assets {
			if a.Name == "app-release.apk" {
				apkURL = a.BrowserDownloadURL
				break
			}
		}
		c.JSON(http.StatusOK, gin.H{
			"version":    version,
			"apk_url":    apkURL,
			"apk_sha256": nil,
			"changelog":  rel.Body,
		})
	}
}

func fetchLatestRelease() (*githubRelease, error) {
	if cachedRelease != nil && time.Now().Before(cacheExpireAt) {
		return cachedRelease, nil
	}
	req, err := http.NewRequest("GET", githubReleaseURL, nil)
	if err != nil {
		return cachedRelease, err
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", "photo-frame-backend")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return cachedRelease, err // 失败时返回过期缓存兜底
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return cachedRelease, nil
	}
	var rel githubRelease
	if err := json.NewDecoder(resp.Body).Decode(&rel); err != nil {
		return nil, err
	}
	cachedRelease = &rel
	cacheExpireAt = time.Now().Add(cacheTTL)
	return cachedRelease, nil
}
