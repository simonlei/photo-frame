# 照片 EXIF 信息与地理位置展示功能

**日期**: 2026-03-13  
**状态**: 设计完成  
**相关文档**: [功能差距分析](../2026-03-10-photo-frame-feature-gap-analysis.md)

---

## 我们要构建什么

为相框系统的所有平台（Android 相框端、小程序、管理后台）添加照片 EXIF 元数据展示功能，包括：

1. **拍摄时间显示**：从照片 EXIF 提取真实拍摄时间，替代当前使用的上传时间
2. **地理位置展示**：提取 GPS 坐标并通过腾讯地图 API 逆编码为详细地址（省·市·区·街道格式）
3. **相机信息**（可选）：相机型号、镜头型号等设备信息
4. **跨平台一致性**：确保 Android、小程序、Web 后台显示相同的元数据

---

## 为什么选择这个方案

### 核心决策

**1. 数据库设计：扩展现有 Photo 表**

选择在 `Photo` 模型中直接添加字段，而非创建独立的 metadata 表，原因：
- 查询性能最优（无需 JOIN）
- 代码实现简单直接
- 相框场景的元数据字段有限（5-8个），不会导致表过度膨胀
- 现有 `TakenAt` 字段已预留，保持设计一致性

**2. 处理时机：混合方式**

- **EXIF 提取 - 同步**：上传时立即从图片文件读取 EXIF（拍摄时间、GPS 坐标、相机型号）
  - 原因：EXIF 读取速度快（< 100ms），不影响上传体验
  - 好处：照片入库时元数据立即可用
  
- **地理逆编码 - 异步**：后台任务通过腾讯地图 API 将坐标转换为地址
  - 原因：避免第三方 API 延迟阻塞上传流程
  - 好处：即使 API 失败，照片上传仍然成功
  - 实现：异步队列处理，失败自动重试

**3. 地图 API：腾讯地图**

- 与现有腾讯云 COS 服务统一账号体系
- 逆地理编码 API 每天 10 万次免费额度
- 国内服务稳定，延迟低
- SDK 支持 Go、Android、微信小程序全平台

**4. 无 GPS 照片处理：静默隐藏**

- 不显示位置字段，避免 UI 出现 "未知位置" 等负面提示
- 保持界面简洁，不打断用户浏览体验
- 数据库字段允许 NULL 值

---

## 关键技术决策

### 后端实现（Go）

**EXIF 提取库选择**：`github.com/rwcarlsen/goexif/exif`
- 成熟稳定（GitHub 3.9k stars）
- 零依赖，纯 Go 实现
- 支持完整的 EXIF、GPS、TIFF 标签

**处理流程**：
```
照片上传
  ↓
文件校验（现有逻辑）
  ↓
EXIF 提取（同步，< 100ms）
  ├─ 拍摄时间 → TakenAt 字段
  ├─ GPS 坐标 → Latitude/Longitude
  └─ 相机信息 → CameraModel/CameraMake
  ↓
上传到 COS（现有逻辑）
  ↓
写入数据库（含 EXIF 字段）
  ↓
返回成功响应
  ↓
[异步] 发送到地理编码队列
  ↓
腾讯地图 API 逆编码
  ↓
更新 LocationAddress 字段
```

**新增数据库字段**：
```sql
ALTER TABLE photos ADD COLUMN latitude DECIMAL(10, 8);        -- 纬度
ALTER TABLE photos ADD COLUMN longitude DECIMAL(11, 8);       -- 经度
ALTER TABLE photos ADD COLUMN location_address VARCHAR(255);  -- 详细地址
ALTER TABLE photos ADD COLUMN camera_make VARCHAR(100);       -- 相机制造商
ALTER TABLE photos ADD COLUMN camera_model VARCHAR(100);      -- 相机型号
```

**腾讯地图逆编码 API**：
```
GET https://apis.map.qq.com/ws/geocoder/v1/
参数：
  - location: 39.984154,116.307490
  - key: YOUR_API_KEY
  - get_poi: 0

返回示例：
{
  "result": {
    "address": "北京市海淀区颐和园路5号",
    "formatted_addresses": {
      "recommend": "颐和园(颐和园路5号)"
    },
    "address_component": {
      "province": "北京市",
      "city": "北京市",
      "district": "海淀区",
      "street": "颐和园路"
    }
  }
}
```

### Android 相框端

**显示方式**：扩展现有照片信息叠加层

当前显示：`2026-03-10 · 张三`  
增强后：
```
📸 拍摄于 2025-12-25 15:30
📍 北京市·海淀区·颐和园路
👤 张三上传
```

**实现位置**：
- 修改 `SlideShowAdapter.kt` 的 `onBindViewHolder` 方法
- 更新 `item_photo.xml` 布局，支持多行信息显示
- 添加开关控制是否显示位置信息（Settings）

**数据模型更新**：
```kotlin
data class Photo(
    val id: Long,
    val url: String,
    val takenAt: String?,           // 拍摄时间
    val latitude: Double?,          // 纬度
    val longitude: Double?,         // 经度
    val locationAddress: String?,   // 地址文本
    val cameraModel: String?,       // 相机型号
    val uploaderName: String,
    val uploadedAt: String
)
```

### 小程序

**显示方式**：
- **照片管理页**：缩略图下方显示地址（截断至 10 字符）
- **照片详情页**（新增）：点击照片进入详情，显示完整 EXIF 信息

**示例布局**：
```
[照片缩略图]
📍 北京·海淀区
2025-12-25
```

**API 调用**：复用现有 `/api/photos` 接口，后端返回字段自动包含新增的 EXIF 数据

### 管理后台 Web

**显示方式**：
- **照片列表页**：鼠标悬停显示 Tooltip（地址 + 拍摄时间）
- **照片详情弹窗**：展示完整 EXIF 信息表格

**示例 Tooltip**：
```
拍摄时间: 2025-12-25 15:30:45
拍摄地点: 北京市海淀区颐和园路5号
相机型号: iPhone 14 Pro
```

---

## 数据模型设计

### Go 模型 (backend/models/photo.go)

```go
type Photo struct {
    ID              uint       `gorm:"primaryKey"`
    DeviceID        string     `gorm:"index;not null"`
    UserID          uint       `gorm:"not null"`
    CosKey          string     `gorm:"not null"`
    CosURL          string     `gorm:"not null"`
    
    // 时间信息
    TakenAt         *time.Time `gorm:"index"`              // EXIF 拍摄时间
    UploadedAt      time.Time  `gorm:"not null"`
    
    // 地理位置
    Latitude        *float64   `gorm:"type:decimal(10,8)"` // 纬度
    Longitude       *float64   `gorm:"type:decimal(11,8)"` // 经度
    LocationAddress *string    `gorm:"size:255"`           // 逆编码地址
    
    // 相机信息
    CameraMake      *string    `gorm:"size:100"`           // 制造商
    CameraModel     *string    `gorm:"size:100"`           // 型号
    
    CreatedAt       time.Time
    UpdatedAt       time.Time
}
```

### API 响应示例

```json
{
  "id": 12345,
  "device_id": "frame_001",
  "url": "https://cos.example.com/photo.jpg",
  "taken_at": "2025-12-25T15:30:45+08:00",
  "uploaded_at": "2026-03-13T10:20:30+08:00",
  "latitude": 39.984154,
  "longitude": 116.307490,
  "location_address": "北京市·海淀区·颐和园路·5号",
  "camera_make": "Apple",
  "camera_model": "iPhone 14 Pro",
  "uploader_name": "张三"
}
```

---

## 异步地理编码队列设计

### 方案：Redis + Worker 模式

**为什么不用消息队列（Kafka/RabbitMQ）**：
- 项目规模小，无需重量级 MQ
- Redis 已在项目中使用（会话存储）
- Redis List 结构足够实现简单队列

**实现细节**：

1. **上传时入队**：
```go
// 照片上传成功后
if photo.Latitude != nil && photo.Longitude != nil {
    rdb.RPush(ctx, "geocode_queue", photo.ID)
}
```

2. **Worker 消费**：
```go
// 单独 goroutine 运行
for {
    photoID := rdb.BLPop(ctx, 5*time.Second, "geocode_queue")
    if photoID != nil {
        geocodePhoto(photoID)
    }
}
```

3. **失败重试**：
- 失败 3 次后放弃
- 每次重试指数退避（1s, 5s, 30s）
- 错误记录到日志

### 腾讯地图 API 配置

**申请流程**：
1. 登录腾讯位置服务控制台
2. 创建应用，获取 API Key
3. 开通 WebService API（逆地理编码）
4. 配置域名白名单或 IP 白名单

**配置环境变量**：
```bash
TENCENT_MAP_API_KEY=YOUR_KEY_HERE
```

---

## UI 设计建议

### Android 照片信息叠加层

**位置**：右下角半透明黑底（现有样式）

**布局结构**：
```xml
<LinearLayout
    android:orientation="vertical"
    android:background="#80000000"
    android:padding="12dp">
    
    <!-- 拍摄时间 -->
    <TextView
        android:id="@+id/tvTakenTime"
        android:text="📸 2025-12-25 15:30"
        android:textSize="14sp"
        android:textColor="#FFFFFF" />
    
    <!-- 地理位置 -->
    <TextView
        android:id="@+id/tvLocation"
        android:text="📍 北京市·海淀区·颐和园路"
        android:textSize="14sp"
        android:textColor="#FFFFFF"
        android:layout_marginTop="4dp" />
    
    <!-- 上传者 -->
    <TextView
        android:id="@+id/tvUploader"
        android:text="👤 张三"
        android:textSize="12sp"
        android:textColor="#CCCCCC"
        android:layout_marginTop="4dp" />
</LinearLayout>
```

**显示逻辑**：
- 如果 `locationAddress` 为空，则隐藏地理位置行
- 如果 `takenAt` 为空，回退显示 `uploadedAt`
- 信息过长时使用 `ellipsize="end"` 截断

### 小程序照片详情页（新增）

**触发方式**：点击照片缩略图

**布局内容**：
```
┌──────────────────────────┐
│   [大图预览]             │
│   可滑动切换              │
├──────────────────────────┤
│ 📸 拍摄信息               │
│   时间: 2025-12-25 15:30 │
│   相机: iPhone 14 Pro    │
├──────────────────────────┤
│ 📍 位置信息               │
│   北京市海淀区颐和园路5号  │
├──────────────────────────┤
│ 👤 上传信息               │
│   上传者: 张三            │
│   上传时间: 2026-03-13    │
├──────────────────────────┤
│ [删除照片] [分享]         │
└──────────────────────────┘
```

### 管理后台 PhotoGrid 增强

**Tooltip 内容**：
```tsx
<Tooltip title={
  <>
    <div>📸 {photo.taken_at || '拍摄时间未知'}</div>
    <div>📍 {photo.location_address || '位置未知'}</div>
    <div>📷 {photo.camera_model || ''}</div>
  </>
}>
  <Image src={photo.url} />
</Tooltip>
```

---

## 边界情况处理

### 1. EXIF 提取失败
**原因**：截图、绘图软件生成的图片、EXIF 被清除
**处理**：
- `TakenAt` 保持 NULL
- 使用 `UploadedAt` 作为回退显示时间
- 照片正常入库

### 2. GPS 坐标不完整
**原因**：部分设备仅记录经度或纬度
**处理**：
- 同时检查 `latitude` 和 `longitude` 非空
- 任一为空则不调用地图 API
- 数据库字段允许 NULL

### 3. 腾讯地图 API 限流/失败
**处理**：
- 返回 HTTP 429/500 时进入重试队列
- 失败 3 次后放弃，`LocationAddress` 保持 NULL
- 前端显示坐标原始值：`📍 39.984154, 116.307490`

### 4. 海外照片 GPS 坐标
**问题**：腾讯地图对国外地址支持有限
**处理**：
- API 仍会返回国家级地址（如 "美国·加利福尼亚州"）
- 考虑未来集成 Google Geocoding API 作为备选

### 5. 历史照片迁移
**场景**：现有数据库中已有 5000+ 张照片
**方案**：
- 编写迁移脚本，从 COS 下载原图重新提取 EXIF
- 分批处理（每批 100 张），避免内存溢出
- 提取失败的照片跳过，记录日志

---

## 性能考虑

### 1. EXIF 提取性能
- 单张照片 EXIF 读取 < 50ms
- 仅读取文件头部（前 64KB），不加载完整图片
- 使用 `exif.Decode(io.LimitReader(file, 64*1024))`

### 2. 地理编码性能
- 异步处理，不阻塞上传流程
- Worker 并发数：2-4 个（避免 API 限流）
- 批量上传场景下，队列积压自动消化

### 3. 数据库查询
- `Latitude`/`Longitude` 添加复合索引（未来支持地理围栏查询）
- `TakenAt` 单独索引（支持按拍摄时间排序）

### 4. API 响应大小
- 新增 5 个字段，每条记录增加约 200 字节
- 分页加载 50 张照片，增加 ~10KB 响应体积（可接受）

---

## 测试策略

### 单元测试

1. **EXIF 提取测试**：
   - 准备测试图片集（iPhone、Android、相机、无 EXIF）
   - 验证各类设备的 EXIF 解析正确性
   - 测试边界情况（损坏的 EXIF、非标准格式）

2. **地理编码测试**：
   - Mock 腾讯地图 API 响应
   - 测试失败重试逻辑
   - 测试海外坐标处理

### 集成测试

1. **上传流程测试**：
   - 上传带完整 EXIF 的照片 → 验证数据库字段
   - 上传无 GPS 照片 → 验证 NULL 处理
   - 上传截图 → 验证回退逻辑

2. **异步队列测试**：
   - 上传 10 张照片 → 验证队列消费
   - 模拟 API 失败 → 验证重试机制

### 端到端测试

1. **Android 端**：
   - 上传照片 → 等待同步 → 验证显示效果
   - 切换显示信息开关 → 验证 UI 更新

2. **小程序**：
   - 上传照片 → 刷新管理页 → 验证地址显示
   - 点击详情 → 验证完整信息

---

## 实施里程碑

### 第一阶段：后端基础设施（3-4 天）
1. 数据库迁移：添加新字段
2. 引入 `goexif` 库
3. 实现同步 EXIF 提取逻辑
4. 配置腾讯地图 API
5. 实现异步地理编码 Worker
6. 单元测试

### 第二阶段：API 更新（1 天）
1. 更新 Photo 模型 JSON 序列化
2. 修改现有 API 响应（向下兼容）
3. 更新 API 文档

### 第三阶段：前端展示（4-5 天）
1. **Android**（2 天）：
   - 更新数据模型
   - 修改 UI 布局
   - 添加设置开关
   
2. **小程序**（1.5 天）：
   - 管理页地址显示
   - 新增详情页
   
3. **管理后台**（1.5 天）：
   - Tooltip 增强
   - 详情弹窗完善

### 第四阶段：测试与优化（2-3 天）
1. 集成测试
2. 性能测试（压测地理编码队列）
3. UI/UX 调优
4. 历史照片迁移脚本

**总计**：10-13 个工作日

---

## 未来扩展可能性

### 短期（3-6 个月）
1. **按地理位置筛选**：
   - "显示在北京拍摄的所有照片"
   - 需要：地理围栏查询（MySQL 空间索引）

2. **相机信息统计**：
   - "哪款相机拍摄的照片最多？"
   - 设备信息聚合视图

3. **时间轴视图**：
   - 按拍摄时间（而非上传时间）组织照片
   - 支持年/月/日维度切换

### 长期（6-12 个月）
1. **地图视图模式**：
   - 在地图上展示所有带位置的照片
   - 点击标记查看照片
   - 需要：前端地图 SDK 集成

2. **智能相册**：
   - 自动按地点创建相册（"北京旅行"、"杭州出差"）
   - 基于地址聚类算法

3. **位置隐私保护**：
   - 用户可选择不显示/删除位置信息
   - GPS 坐标模糊化（精确到区级）

---

## 需要的资源

### 技术资源
1. **腾讯地图 API Key**：
   - WebService API 权限
   - 逆地理编码接口
   - 预计日调用量：500-1000 次（远低于免费额度）

2. **开发环境**：
   - Redis（已有）
   - 测试用带 EXIF 的照片样本（20-30 张）

### 人力资源
1. **后端开发**：1 人 × 5 天
2. **Android 开发**：1 人 × 2 天
3. **小程序开发**：1 人 × 1.5 天
4. **Web 前端开发**：1 人 × 1.5 天
5. **测试工程师**：1 人 × 2 天

---

## 风险与缓解

### 风险 1：EXIF 提取影响上传速度
**概率**：低  
**影响**：中  
**缓解**：
- 压测验证（1000 张照片上传测试）
- 如延迟 > 200ms，改为异步提取

### 风险 2：腾讯地图 API 成本超预期
**概率**：低  
**影响**：低  
**缓解**：
- 设置 API 调用监控告警
- 免费额度 10 万次/天足够小型应用

### 风险 3：历史照片迁移失败率高
**概率**：中  
**影响**：低  
**缓解**：
- 接受部分照片无 EXIF 的现实
- 优先处理最近 6 个月的照片

---

## 成功指标

1. **功能覆盖率**：
   - ≥ 70% 的新上传照片成功提取 EXIF
   - ≥ 50% 的照片包含 GPS 信息

2. **性能指标**：
   - 照片上传耗时增加 < 100ms
   - 地理编码队列延迟 < 10 秒

3. **用户体验**：
   - Android 端信息显示清晰可读
   - 小程序/后台无 UI 错位或数据缺失

4. **稳定性**：
   - EXIF 提取错误率 < 5%
   - 地理编码 API 失败自动降级

---

## 开放问题

无。所有技术决策和需求已明确。

---

## 下一步行动

1. ✅ **brainstorm 完成** - 技术方案确定
2. ⏭️ **运行 `/ce:plan`** - 生成详细实施计划
3. ⏭️ **申请腾讯地图 API Key**
4. ⏭️ **准备测试照片样本集**
5. ⏭️ **开始后端开发**
