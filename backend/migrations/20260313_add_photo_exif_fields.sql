-- 生产环境迁移脚本：添加照片 EXIF 元数据字段
-- 执行时间：2026-03-13
-- 说明：为 photos 表添加地理位置、相机信息等 EXIF 字段

ALTER TABLE photos ADD COLUMN latitude DECIMAL(10, 8) COMMENT '纬度';
ALTER TABLE photos ADD COLUMN longitude DECIMAL(11, 8) COMMENT '经度';
ALTER TABLE photos ADD COLUMN location_address VARCHAR(255) COMMENT '逆编码详细地址';
ALTER TABLE photos ADD COLUMN camera_make VARCHAR(100) COMMENT '相机制造商';
ALTER TABLE photos ADD COLUMN camera_model VARCHAR(100) COMMENT '相机型号';

-- 索引（未来支持地理围栏查询和按拍摄时间排序）
CREATE INDEX idx_photos_coords ON photos(latitude, longitude);
CREATE INDEX idx_photos_taken_at ON photos(taken_at);
