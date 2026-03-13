package workers

import (
	"context"
	"fmt"
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

// Start 启动 Worker（并发数 concurrency）
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
