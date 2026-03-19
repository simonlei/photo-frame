---
date: 2026-03-19
topic: android-test-automation
---

# Android App 测试自动化

## Problem Frame

PhotoFrame 安卓应用目前零测试覆盖——无测试目录、无测试文件、无测试依赖。所有业务逻辑直接写在 Activity 中，代码不可单元测试。开发者每次修改 UI 交互（轮播、设置页、绑定流程）都缺乏信心，担心引入回归。项目计划搭建 CI/CD，测试方案需 CI 友好。

## Requirements

### 前置重构：可测试性改造

- R1. 为 BindActivity、MainActivity、SettingsActivity 各抽取 ViewModel，业务逻辑迁移到 ViewModel，Activity 只负责 UI 绑定和生命周期。ViewModel 使用 StateFlow 暴露状态
- R2. 抽取 Repository 接口层（PhotoRepository、DeviceRepository），封装网络调用，使依赖可 Mock。生产代码使用 RemoteXxxRepository 实现，测试使用 FakeXxxRepository
- R3. 重构不改变现有功能行为和 UI 表现，纯结构调整
- R4. 重构按 SettingsActivity → MainActivity → BindActivity 顺序逐个拆解，每拆完一个 Activity 立刻手动验证功能不变，并提交独立 git commit
- R5. 不引入依赖注入框架（Hilt/Dagger/Koin），ViewModel 依赖通过 ViewModelProvider.Factory 手动注入

### 第一层：JVM 单元测试

- R6. 引入 JUnit 5 + MockK + Turbine 测试依赖，创建 `app/src/test/` 目录
- R7. BindViewModel 单元测试：设备注册流程、绑定状态判断、Token 存储逻辑
- R8. MainViewModel 单元测试：照片列表加载、增量同步判断、播放模式切换（顺序/随机）、自动翻页索引计算
- R9. SettingsViewModel 单元测试：设置项读写、播放速度/动画效果变更生效、服务器地址变更触发重新绑定
- R10. 工具类单元测试：ScreenScheduler 的时间判断逻辑（含跨午夜）、AutoUpdater 的版本比较与 SHA-256 校验逻辑
- R11. 所有单元测试在纯 JVM 上运行，不依赖 Android 框架，单次执行秒级完成
- R12. 每个 ViewModel 拆完后立刻补写单元测试，形成"拆→测→拆→测"正循环

### 第二层：UI 端到端测试

- R13. 引入 Espresso + MockWebServer 依赖，创建 `app/src/androidTest/` 目录
- R14. 绑定流程测试：启动应用 → MockWebServer 返回注册响应 → 显示二维码 → 轮询返回 bound=true → 跳转主界面
- R15. 轮播流程测试：MockWebServer 返回照片列表 → 照片展示 → 自动翻页生效 → 手势操作进入设置页
- R16. 设置流程测试：修改播放速度/模式/动画 → 保存 → 验证设置生效
- R17. 异常场景测试：MockWebServer 模拟 500 错误、401 Token 过期（触发重新绑定）、网络超时、空照片列表等
- R18. 所有 UI 测试通过 MockWebServer 拦截网络请求，不依赖真实后端。简单线性流程用 enqueue 模式，复杂场景用 Dispatcher 按 URL 路径匹配
- R19. 测试 @Before 中通过设置 `prefs.serverBaseUrl` 指向 MockWebServer 地址来重定向网络请求，无需修改生产代码

### CI 友好

- R20. UI 测试支持通过 Gradle Managed Devices 在无物理设备环境下运行
- R21. 提供 Gradle 命令分别运行单元测试（`./gradlew test`）和 UI 测试（`./gradlew connectedAndroidTest`）

### 第三层：截图测试（可选，延后实施）

- R22. 待第一二层稳定后，可引入 Roborazzi 对关键界面做视觉快照回归测试

## Success Criteria

- 开发者修改 UI 交互代码后，运行单元测试 + UI 测试即可确认无回归，信心显著提升
- 单元测试在 JVM 上 10 秒内完成全量运行
- UI 测试覆盖三大核心流程（绑定、轮播、设置），不依赖真实后端即可运行
- 测试可在未来 CI 环境中无人值守执行

## Scope Boundaries

- 不做性能测试、压力测试
- 不覆盖 Glide 图片加载的视觉正确性（属于第三方库职责）
- 不覆盖 BootReceiver 开机自启（需要系统级操作，难以自动化）
- 不改变应用的功能和 UI 设计
- 截图测试（R22）为延后目标，不在首期范围内

## Key Decisions

- **重构先于测试**：先做可测试性改造（R1-R5），再写测试，否则在 Activity-Centric 架构上写测试事倍功半
- **重构顺序 SettingsActivity → MainActivity → BindActivity**：SettingsActivity 最简单（纯表单读写），适合练手；MainActivity 最复杂（同步、轮播、夜间模式），放中间；BindActivity 有独立轮询但相对独立
- **逐个拆解 + 即时测试**：每拆一个 ViewModel 就写对应单元测试，形成正循环，避免大规模重构后才发现问题
- **StateFlow 暴露状态**：ViewModel 使用 Kotlin StateFlow（而非 LiveData），与协程生态一致，JVM 单元测试更方便
- **不引入 DI 框架**：App 只有 3 个 Activity，手动通过 ViewModelProvider.Factory 注入依赖，避免过度工程化
- **MockK 而非 Mockito**：MockK 对 Kotlin 协程、密封类、扩展函数支持更好，是 Kotlin 项目的更优选择
- **MockWebServer 隔离网络**：UI 测试不依赖真实后端，通过设置 `prefs.serverBaseUrl` 重定向请求，无需修改生产代码。简单流程用 enqueue，复杂场景用 Dispatcher
- **Gradle Managed Devices**：面向未来 CI 环境，无需手动管理模拟器

## Dependencies / Assumptions

- 重构 ViewModel 时依赖 androidx.lifecycle（项目已有 viewmodel-ktx 依赖，无需新增）
- MockWebServer 来自 OkHttp 生态（项目已用 OkHttp 4.12.0），版本兼容无问题
- BindActivity 中直接用 `OkHttpClient()` 发请求（未走 ApiClient），但因 URL 拼接使用 `prefs.serverBaseUrl`，MockWebServer 方案仍然生效
- 假设 Gradle 8.5 + AGP 8.3.0 对 JUnit 5、Gradle Managed Devices 支持良好

## Outstanding Questions

### Deferred to Planning

- [Affects R1][Technical] MainViewModel 中自动翻页定时器的实现方式（Handler 迁移到 ViewModel 的 viewModelScope 还是保留在 Activity）
- [Affects R2][Technical] BindActivity 的 OkHttpClient 直接调用是否需要统一收归到 Repository 层
- [Affects R13][Technical] MockWebServer 的 fixture 数据如何组织（内联 JSON vs 资源文件）
- [Affects R20][Needs research] Gradle Managed Devices 的 API level 选择和 CI 环境配置细节

## Next Steps

→ `/ce:plan` for structured implementation planning
