# 测试运行指南

## JVM 单元测试（快速，无需设备）

```bash
./gradlew test
```

**覆盖范围：**
- `SettingsViewModelTest` — 设置加载/保存/服务器变更清除绑定
- `MainViewModelTest` — 照片去重/顺序随机模式/循环翻页/空列表边界
- `BindViewModelTest` — 已绑定跳过/注册成功失败/轮询成功/复用 deviceId
- `TimeUtilsTest` — 同日/跨午夜时间判断/边界条件
- `VersionUtilsTest` — 语义化版本号比较

## UI 端到端测试（需要模拟器）

### 本地运行（使用连接的设备/模拟器）

```bash
./gradlew connectedAndroidTest
```

### CI 运行（使用 Gradle Managed Devices，无需手动管理模拟器）

```bash
./gradlew pixel2Api30Check
```

**覆盖范围：**
- `BindFlowTest` — 绑定流程（显示二维码→绑定成功跳转/已绑定跳过）
- `SlideshowFlowTest` — 轮播流程（照片展示/自动翻页/长按进设置）
- `SettingsFlowTest` — 设置流程（显示当前值/修改播放模式/修改服务器清除绑定）
- `ErrorFlowTest` — 异常场景（服务器错误/空照片列表）

## 全量测试

```bash
./gradlew test pixel2Api30Check
```

## 注意事项

- JVM 单元测试使用 JUnit 5 + MockK + kotlinx-coroutines-test
- UI 测试使用 JUnit 4 + Espresso + MockWebServer（所有 API 请求指向本地 Mock 服务器）
- Gradle Managed Devices 使用 `aosp-atd` 镜像（约 600MB），首次运行需下载
- CI 环境可缓存 `~/.android/avd/` 加速后续运行
