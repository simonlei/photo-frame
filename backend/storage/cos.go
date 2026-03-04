package storage

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"

	"github.com/tencentyun/cos-go-sdk-v5"
)

type COSStorage struct {
	client *cos.Client
	bucket string
	region string
}

func NewCOS(secretID, secretKey, bucket, region string) *COSStorage {
	bucketURL, _ := url.Parse(fmt.Sprintf("https://%s.cos.%s.myqcloud.com", bucket, region))
	baseURL := &cos.BaseURL{BucketURL: bucketURL}
	client := cos.NewClient(baseURL, &http.Client{
		Transport: &cos.AuthorizationTransport{
			SecretID:  secretID,
			SecretKey: secretKey,
		},
	})
	return &COSStorage{client: client, bucket: bucket, region: region}
}

// Upload 上传文件流到 COS，返回访问 URL
func (s *COSStorage) Upload(ctx context.Context, key string, reader io.Reader, contentType string) (string, error) {
	opt := &cos.ObjectPutOptions{
		ObjectPutHeaderOptions: &cos.ObjectPutHeaderOptions{
			ContentType: contentType,
		},
	}
	_, err := s.client.Object.Put(ctx, key, reader, opt)
	if err != nil {
		return "", fmt.Errorf("COS 上传失败: %w", err)
	}
	cosURL := fmt.Sprintf("https://%s.cos.%s.myqcloud.com/%s", s.bucket, s.region, key)
	return cosURL, nil
}

// Delete 删除 COS 对象
func (s *COSStorage) Delete(ctx context.Context, key string) error {
	_, err := s.client.Object.Delete(ctx, key)
	if err != nil {
		return fmt.Errorf("COS 删除失败: %w", err)
	}
	return nil
}
