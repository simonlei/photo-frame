-- 回滚脚本：移除照片 EXIF 元数据字段
-- 执行时间：按需
-- 说明：回滚 20260313_add_photo_exif_fields.sql 的变更

DROP INDEX idx_photos_coords ON photos;
DROP INDEX idx_photos_taken_at ON photos;
ALTER TABLE photos DROP COLUMN camera_model;
ALTER TABLE photos DROP COLUMN camera_make;
ALTER TABLE photos DROP COLUMN location_address;
ALTER TABLE photos DROP COLUMN longitude;
ALTER TABLE photos DROP COLUMN latitude;
