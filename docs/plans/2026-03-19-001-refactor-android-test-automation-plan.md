---
title: "refactor: Android 测试自动化——可测试性重构 + 分层测试体系"
type: refactor
status: active
date: 2026-03-19
origin: docs/brainstorms/2026-03-19-android-test-automation-requirements.md
---

# ♻️ refactor: Android 测试自动化——可测试性重构 + 分层测试体系

## Overview

为 PhotoFrame Android 应用建立从零到一的自动化测试体系。当前应用 14 个 Kotlin 源文件零测试覆盖，所有业务逻辑耦合在 Activity 中，不可单元测试。

本计划分两大步骤：
1. **可测试性重构**：逐个 Activity 抽取 ViewModel + Repository 接口层，使业务逻辑脱离 Android 框架
2. **分层测试建设**：JVM 单元测试（快速反馈）→ UI 端到端测试（流程守护）→ 截图测试（延后）

核心技术选型：JUnit 5 + MockK + Turbine + Espresso + MockWebServer + Gradle Managed Devices (see origin: docs/brainstorms/2026-03-19-android-test-automation-requirements.md)

## Problem Statement / Motivation

- **改代码缺乏信心**：开发者修改 UI 交互（轮播、设置、绑定）时担心引入回归，不敢碰核心逻辑
- **零测试覆盖**：无测试目录、无测试文件、无测试依赖，连最基本的冒烟测试都没有
- **不可测架构**：所有业务逻辑写在 Activity `onCreate()` 里（SettingsActivity.onCreate 118行、MainActivity.onCreate 78行），private 方法无法直接测试
- **未来 CI 需求**：计划搭建 CI/CD 流水线，测试自动化是前置条件

## Proposed Solution

### 总体策略：重构先行 + 分层递进 + 逐步闭环

```
Phase 1: 测试基础设施搭建
    ↓
Phase 2: SettingsActivity 重构 + 单元测试（最简单，练手）
    ↓
Phase 3: MainActivity 重构 + 单元测试（最复杂，核心）
    ↓
Phase 4: BindActivity 重构 + 单元测试（独立轮询）
    ↓
Phase 5: UI 端到端测试（四大核心流程：绑定、轮播、设置、异常）
    ↓
Phase 6: CI 集成 + Gradle Managed Devices 配置
```

重构顺序 SettingsActivity → MainActivity → BindActivity 的理由 (see origin):
- SettingsActivity 最简单（纯表单读写，无后台任务），适合验证重构模式
- MainActivity 最复杂（同步回调、轮播控制、夜间模式），放中间积累经验后处理
- BindActivity 有独立轮询逻辑但相对独立，放最后

## Technical Approach

### Architecture

**重构前架构：**
```
Activity（巨型 God Object）
├── 直接操作 View
├── 直接管理业务逻辑（照片列表、同步、设置读写…）
├── 直接持有 Service 引用（PhotoSyncService, ScreenScheduler, AutoUpdater）
└── 直接操作 AppPrefs / ApiClient
```

**重构后架构：**
```
Activity（纯 UI 层）
├── 观察 ViewModel.stateFlow → 更新 View
├── 用户事件 → 调用 ViewModel 方法
└── 生命周期管理（Service 启停、沉浸式）

ViewModel（业务逻辑层）
├── StateFlow 暴露 UI 状态
├── 处理业务逻辑（去重、排序、翻页计算…）
└── 调用 Repository 接口

Repository 接口（数据层抽象）
├── RemoteXxxRepository（生产实现，调 ApiClient/OkHttp）
└── FakeXxxRepository（测试实现，返回预设数据）
```

**关键设计决策：**
- **StateFlow（非 LiveData）**暴露状态，与 Kotlin 协程生态一致，JVM 测试更方便 (see origin)
- **不引入 DI 框架**，手动 `ViewModelProvider.Factory` 注入，避免过度工程化 (see origin)
- **Repository 接口层**只封装网络调用，AppPrefs 可直接在 ViewModel 中使用（通过构造注入）

### Implementation Phases

---

#### Phase 1: 测试基础设施搭建

**目标**：添加所有测试依赖，创建测试目录结构，确保 `./gradlew test` 能运行。

**任务清单：**

- [ ] 1.1 修改 `app/build.gradle`，添加测试依赖：

```groovy
// app/build.gradle — 新增依赖

// JUnit 5
testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.2'
testImplementation 'org.junit.jupiter:junit-jupiter-engine:5.10.2'
testImplementation 'org.junit.jupiter:junit-jupiter-params:5.10.2'

// MockK（Kotlin 原生 Mock 框架）
testImplementation 'io.mockk:mockk:1.13.9'

// Kotlin 协程测试
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'

// Turbine（Flow 测试辅助）
testImplementation 'app.cash.turbine:turbine:1.1.0'

// Espresso + AndroidX Test
androidTestImplementation 'androidx.test.ext:junit:1.1.5'
androidTestImplementation 'androidx.test:runner:1.5.2'
androidTestImplementation 'androidx.test:rules:1.5.0'
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
androidTestImplementation 'androidx.test.espresso:espresso-intents:3.5.1'
androidTestImplementation 'androidx.test.espresso:espresso-contrib:3.5.1'

// MockWebServer
androidTestImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'

// MockK for Android instrumented tests
androidTestImplementation 'io.mockk:mockk-android:1.13.9'

// Coroutines test for Android
androidTestImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
```

- [ ] 1.2 在 `app/build.gradle` 的 `android {}` 块中启用 JUnit 5：

```groovy
android {
    // ...existing config...

    testOptions {
        unitTests.all {
            useJUnitPlatform()
        }
    }
}
```

- [ ] 1.3 创建测试目录结构：

```
app/src/
├── test/java/com/photoframe/          # JVM 单元测试
│   ├── viewmodel/                     # ViewModel 测试
│   ├── service/                       # Service 逻辑测试
│   ├── updater/                       # AutoUpdater 测试
│   └── util/                          # 工具类测试
└── androidTest/java/com/photoframe/   # UI 端到端测试
    ├── flow/                          # 流程测试
    └── util/                          # 测试工具（MockWebServer 配置等）
```

- [ ] 1.4 创建一个 Smoke Test 验证基础设施可用：

```kotlin
// app/src/test/java/com/photoframe/SmokeTest.kt
package com.photoframe

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class SmokeTest {
    @Test
    fun `test infrastructure works`() {
        assertTrue(true, "JUnit 5 is working")
    }
}
```

- [ ] 1.5 运行 `./gradlew test` 确认基础设施正常
- [ ] 1.6 提交 git commit：`chore: add testing dependencies and directory structure`

**验证标准**：`./gradlew test` 执行成功，SmokeTest 通过。

---

#### Phase 2: SettingsActivity 重构 + 单元测试

**目标**：把 SettingsActivity 的业务逻辑抽到 SettingsViewModel，并写全面的单元测试。

**2A. 提取 SettingsViewModel**

- [ ] 2.1 创建 `SettingsViewModel.kt`：

```kotlin
// app/src/main/java/com/photoframe/viewmodel/SettingsViewModel.kt
package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import com.photoframe.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettingsUiState(
    val serverBaseUrl: String = "",
    val slideDurationSec: Int = 15,
    val playMode: String = "sequential",
    val transitionEffect: String = "fade",
    val showPhotoInfo: Boolean = true,
    val nightModeEnabled: Boolean = false,
    val nightModeStartHour: Int = 22,
    val nightModeStartMinute: Int = 0,
    val nightModeEndHour: Int = 8,
    val nightModeEndMinute: Int = 0,
    val deviceId: String? = null
)

sealed class SettingsSaveResult {
    object Success : SettingsSaveResult()
    data class ServerUrlChanged(val newUrl: String) : SettingsSaveResult()
}

class SettingsViewModel(private val prefs: AppPrefs) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun loadSettings() {
        _uiState.value = SettingsUiState(
            serverBaseUrl = prefs.serverBaseUrl,
            slideDurationSec = prefs.slideDurationSec,
            playMode = prefs.playMode,
            transitionEffect = prefs.transitionEffect,
            showPhotoInfo = prefs.showPhotoInfo,
            nightModeEnabled = prefs.nightModeEnabled,
            nightModeStartHour = prefs.nightModeStartHour,
            nightModeStartMinute = prefs.nightModeStartMinute,
            nightModeEndHour = prefs.nightModeEndHour,
            nightModeEndMinute = prefs.nightModeEndMinute,
            deviceId = prefs.deviceId
        )
    }

    fun saveSettings(newState: SettingsUiState): SettingsSaveResult {
        val serverChanged = newState.serverBaseUrl != prefs.serverBaseUrl

        prefs.serverBaseUrl = newState.serverBaseUrl
        prefs.slideDurationSec = newState.slideDurationSec
        prefs.playMode = newState.playMode
        prefs.transitionEffect = newState.transitionEffect
        prefs.showPhotoInfo = newState.showPhotoInfo
        prefs.nightModeEnabled = newState.nightModeEnabled
        prefs.nightModeStartHour = newState.nightModeStartHour
        prefs.nightModeStartMinute = newState.nightModeStartMinute
        prefs.nightModeEndHour = newState.nightModeEndHour
        prefs.nightModeEndMinute = newState.nightModeEndMinute

        if (serverChanged) {
            prefs.isBound = false
            prefs.userToken = null
            prefs.deviceId = null
            prefs.qrToken = null
            prefs.lastSyncTime = null
            return SettingsSaveResult.ServerUrlChanged(newState.serverBaseUrl)
        }
        return SettingsSaveResult.Success
    }
}
```

- [ ] 2.2 创建 `SettingsViewModelFactory.kt`：

```kotlin
// app/src/main/java/com/photoframe/viewmodel/SettingsViewModelFactory.kt
package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.photoframe.data.AppPrefs

class SettingsViewModelFactory(private val prefs: AppPrefs) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(prefs) as T
    }
}
```

- [ ] 2.3 重构 `SettingsActivity`：删除 `onCreate()` 中的业务逻辑，改为观察 `viewModel.uiState` 并在保存时调 `viewModel.saveSettings()`。Activity 只保留 UI 绑定、控件事件监听、startActivity 跳转。

- [ ] 2.4 编译通过 + 手动验证：打开设置页 → 各项显示正确 → 修改后保存 → 返回主界面生效 → 服务器地址变更触发重新绑定

- [ ] 2.5 提交 git commit：`refactor: extract SettingsViewModel from SettingsActivity`

**2B. SettingsViewModel 单元测试**

- [ ] 2.6 创建 `SettingsViewModelTest.kt`：

```kotlin
// app/src/test/java/com/photoframe/viewmodel/SettingsViewModelTest.kt
package com.photoframe.viewmodel

import com.photoframe.data.AppPrefs
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class SettingsViewModelTest {

    private val prefs = mockk<AppPrefs>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setup() {
        every { prefs.serverBaseUrl } returns "http://example.com"
        every { prefs.slideDurationSec } returns 15
        every { prefs.playMode } returns "sequential"
        every { prefs.transitionEffect } returns "fade"
        every { prefs.showPhotoInfo } returns true
        every { prefs.nightModeEnabled } returns false
        // ... 其他默认值
        viewModel = SettingsViewModel(prefs)
    }

    @Test
    fun `loadSettings populates uiState from prefs`() {
        viewModel.loadSettings()
        val state = viewModel.uiState.value
        assertEquals("http://example.com", state.serverBaseUrl)
        assertEquals(15, state.slideDurationSec)
        assertEquals("sequential", state.playMode)
    }

    @Test
    fun `saveSettings without server change returns Success`() {
        val newState = SettingsUiState(
            serverBaseUrl = "http://example.com",
            slideDurationSec = 10,
            playMode = "random"
        )
        val result = viewModel.saveSettings(newState)
        assertTrue(result is SettingsSaveResult.Success)
        verify { prefs.slideDurationSec = 10 }
        verify { prefs.playMode = "random" }
    }

    @Test
    fun `saveSettings with server change clears binding and returns ServerUrlChanged`() {
        val newState = SettingsUiState(
            serverBaseUrl = "http://new-server.com",
            slideDurationSec = 15,
            playMode = "sequential"
        )
        val result = viewModel.saveSettings(newState)
        assertTrue(result is SettingsSaveResult.ServerUrlChanged)
        verify { prefs.isBound = false }
        verify { prefs.userToken = null }
        verify { prefs.deviceId = null }
        verify { prefs.qrToken = null }
        verify { prefs.lastSyncTime = null }
    }

    @Test
    fun `saveSettings persists night mode schedule`() {
        val newState = SettingsUiState(
            serverBaseUrl = "http://example.com",
            nightModeEnabled = true,
            nightModeStartHour = 23,
            nightModeStartMinute = 30,
            nightModeEndHour = 7,
            nightModeEndMinute = 0
        )
        viewModel.saveSettings(newState)
        verify { prefs.nightModeEnabled = true }
        verify { prefs.nightModeStartHour = 23 }
        verify { prefs.nightModeStartMinute = 30 }
        verify { prefs.nightModeEndHour = 7 }
        verify { prefs.nightModeEndMinute = 0 }
    }
}
```

- [ ] 2.7 运行 `./gradlew test` 确认所有测试通过
- [ ] 2.8 提交 git commit：`test: add SettingsViewModel unit tests`

**验证标准**：
- SettingsActivity 功能行为与重构前完全一致
- SettingsViewModelTest 覆盖：加载设置、保存设置、服务器地址变更触发清除、夜间模式时间段持久化

---

#### Phase 3: MainActivity 重构 + 单元测试

**目标**：抽取 MainViewModel 管理照片列表、自动翻页、播放模式等核心逻辑。这是最复杂的一步。

**3A. 抽取 Repository 接口**

- [ ] 3.1 创建 `PhotoRepository` 接口和实现（`Photo` data class 和 `ApiService` 接口已存在于 `data/Photo.kt` 和 `data/ApiClient.kt`，无需新建）：

```kotlin
// app/src/main/java/com/photoframe/data/PhotoRepository.kt
package com.photoframe.data

interface PhotoRepository {
    suspend fun getPhotos(deviceId: String, since: String?): List<Photo>
}

// app/src/main/java/com/photoframe/data/RemotePhotoRepository.kt
class RemotePhotoRepository(private val apiService: ApiService) : PhotoRepository {
    override suspend fun getPhotos(deviceId: String, since: String?): List<Photo> {
        val response = apiService.listPhotos(deviceId, since)
        return response.photos.map { dto ->
            Photo(
                id = dto.id,
                url = dto.url,  // URL 补全逻辑从 PhotoSyncService 迁移到这里
                takenAt = dto.takenAt,
                uploadedAt = dto.uploadedAt,
                latitude = dto.latitude,
                longitude = dto.longitude,
                locationAddress = dto.locationAddress,
                cameraMake = dto.cameraMake,
                cameraModel = dto.cameraModel,
                uploaderName = dto.uploaderName
            )
        }
    }
}
```

- [ ] 3.2 提交 git commit：`refactor: extract PhotoRepository interface`

**3B. 提取 MainViewModel**

- [ ] 3.3 创建 `MainViewModel.kt`：

```kotlin
// app/src/main/java/com/photoframe/viewmodel/MainViewModel.kt
package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoframe.data.AppPrefs
import com.photoframe.data.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MainUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val isNightMode: Boolean = false,
    val showPhotoInfo: Boolean = true,
    val slideDurationMs: Long = 15_000L,
    val transitionEffect: String = "fade"
)

class MainViewModel(private val prefs: AppPrefs) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun loadPreferences() {
        _uiState.value = _uiState.value.copy(
            showPhotoInfo = prefs.showPhotoInfo,
            slideDurationMs = prefs.slideDurationSec * 1_000L,
            transitionEffect = prefs.transitionEffect
        )
    }

    /**
     * 收到新照片时调用（来自 PhotoSyncService 回调）
     * 去重 + 按 playMode 排序
     */
    fun onNewPhotos(newPhotos: List<Photo>) {
        val current = _uiState.value.photos
        val existingIds = current.map { it.id }.toSet()
        val fresh = newPhotos.filter { it.id !in existingIds }
        if (fresh.isNotEmpty()) {
            val updated = current + fresh
            val sorted = if (prefs.playMode == "random") updated.shuffled() else updated
            _uiState.value = _uiState.value.copy(photos = sorted)
        }
    }

    /**
     * 计算下一页索引（循环播放）
     */
    fun nextPageIndex(): Int {
        val size = _uiState.value.photos.size
        if (size == 0) return 0
        val next = (_uiState.value.currentIndex + 1) % size
        _uiState.value = _uiState.value.copy(currentIndex = next)
        return next
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
    }

    fun setNightMode(isNight: Boolean) {
        _uiState.value = _uiState.value.copy(isNightMode = isNight)
    }
}
```

- [ ] 3.4 创建 `MainViewModelFactory.kt`

- [ ] 3.5 重构 `MainActivity`：
  - 移除 `allPhotos` 成员变量，改为 collect `viewModel.uiState.photos`
  - `autoSlideRunnable` 调用 `viewModel.nextPageIndex()`
  - 同步回调改为 `viewModel.onNewPhotos(newPhotos)`
  - 保留在 Activity 中：全屏沉浸式、GestureDetector、Service 生命周期管理、Handler autoSlide

  **关于自动翻页定时器** (see origin Outstanding Questions)：
  > Handler+Runnable 保留在 Activity 中，因为它需要与 ViewPager2 UI 交互。ViewModel 只提供 `nextPageIndex()` 和 `slideDurationMs` 供 Activity 使用。这保持了 ViewModel 的纯净性（不持有 UI 引用）。

- [ ] 3.6 编译通过 + 手动验证：照片正常轮播 → 自动翻页 → 手势进设置 → 夜间模式 → 新照片同步后追加

- [ ] 3.7 提交 git commit：`refactor: extract MainViewModel from MainActivity`

**3C. MainViewModel 单元测试**

- [ ] 3.8 创建 `MainViewModelTest.kt`：

```kotlin
// app/src/test/java/com/photoframe/viewmodel/MainViewModelTest.kt
package com.photoframe.viewmodel

import com.photoframe.data.AppPrefs
import com.photoframe.data.Photo
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class MainViewModelTest {

    private val prefs = mockk<AppPrefs>(relaxed = true)
    private lateinit var viewModel: MainViewModel

    private fun makePhoto(id: Long, name: String = "test") = Photo(
        id = id, url = "http://img/$id.jpg",
        takenAt = null, uploadedAt = "2026-03-19",
        latitude = null, longitude = null, locationAddress = null,
        cameraMake = null, cameraModel = null,
        uploaderName = name
    )

    @BeforeEach
    fun setup() {
        every { prefs.playMode } returns "sequential"
        every { prefs.showPhotoInfo } returns true
        every { prefs.slideDurationSec } returns 15
        every { prefs.transitionEffect } returns "fade"
        viewModel = MainViewModel(prefs)
    }

    @Test
    fun `onNewPhotos adds photos in sequential mode`() {
        val photos = listOf(makePhoto(1), makePhoto(2))
        viewModel.onNewPhotos(photos)
        assertEquals(2, viewModel.uiState.value.photos.size)
        assertEquals(1L, viewModel.uiState.value.photos[0].id)
        assertEquals(2L, viewModel.uiState.value.photos[1].id)
    }

    @Test
    fun `onNewPhotos deduplicates by id`() {
        viewModel.onNewPhotos(listOf(makePhoto(1), makePhoto(2)))
        viewModel.onNewPhotos(listOf(makePhoto(2), makePhoto(3)))
        assertEquals(3, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `onNewPhotos shuffles in random mode`() {
        every { prefs.playMode } returns "random"
        viewModel = MainViewModel(prefs)
        val photos = (1L..100L).map { makePhoto(it) }
        viewModel.onNewPhotos(photos)
        // 100张照片 shuffle 后不太可能与原序一致
        val ids = viewModel.uiState.value.photos.map { it.id }
        assertNotEquals((1L..100L).toList(), ids,
            "100 photos should be shuffled (extremely unlikely to remain in order)")
    }

    @Test
    fun `onNewPhotos ignores empty new list`() {
        viewModel.onNewPhotos(listOf(makePhoto(1)))
        viewModel.onNewPhotos(emptyList())
        assertEquals(1, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `onNewPhotos ignores all-duplicate list`() {
        viewModel.onNewPhotos(listOf(makePhoto(1)))
        viewModel.onNewPhotos(listOf(makePhoto(1)))
        assertEquals(1, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `nextPageIndex cycles through photos`() {
        viewModel.onNewPhotos(listOf(makePhoto(1), makePhoto(2), makePhoto(3)))
        assertEquals(1, viewModel.nextPageIndex())
        assertEquals(2, viewModel.nextPageIndex())
        assertEquals(0, viewModel.nextPageIndex()) // wrap around
    }

    @Test
    fun `nextPageIndex returns 0 when no photos`() {
        assertEquals(0, viewModel.nextPageIndex())
    }

    @Test
    fun `loadPreferences updates ui state from prefs`() {
        every { prefs.slideDurationSec } returns 30
        every { prefs.transitionEffect } returns "zoom"
        every { prefs.showPhotoInfo } returns false
        viewModel.loadPreferences()
        assertEquals(30_000L, viewModel.uiState.value.slideDurationMs)
        assertEquals("zoom", viewModel.uiState.value.transitionEffect)
        assertFalse(viewModel.uiState.value.showPhotoInfo)
    }

    @Test
    fun `setNightMode updates state`() {
        viewModel.setNightMode(true)
        assertTrue(viewModel.uiState.value.isNightMode)
        viewModel.setNightMode(false)
        assertFalse(viewModel.uiState.value.isNightMode)
    }
}
```

- [ ] 3.9 运行 `./gradlew test` 确认所有测试通过
- [ ] 3.10 提交 git commit：`test: add MainViewModel unit tests`

**验证标准**：
- MainActivity 功能与重构前完全一致
- MainViewModelTest 覆盖：照片加载、去重、顺序/随机模式、循环翻页、空列表边界、偏好加载

---

#### Phase 4: BindActivity 重构 + 单元测试

**目标**：抽取 BindViewModel，处理设备注册和绑定轮询逻辑。

**4A. 抽取 DeviceRepository 接口**

- [ ] 4.1 创建 `DeviceRepository` 接口和实现：

```kotlin
// app/src/main/java/com/photoframe/data/DeviceRepository.kt
package com.photoframe.data

interface DeviceRepository {
    suspend fun registerDevice(serverBaseUrl: String): DeviceRegisterResult
    suspend fun checkBindStatus(serverBaseUrl: String, deviceId: String): BindStatusResult
}

data class DeviceRegisterResult(val deviceId: String, val qrToken: String)
data class BindStatusResult(val bound: Boolean, val userToken: String?)

// app/src/main/java/com/photoframe/data/RemoteDeviceRepository.kt
class RemoteDeviceRepository(private val httpClient: OkHttpClient) : DeviceRepository {
    // 从 BindActivity 迁移注册和轮询的网络调用逻辑
    override suspend fun registerDevice(serverBaseUrl: String): DeviceRegisterResult {
        // POST $serverBaseUrl/api/device/register
        // ...
    }

    override suspend fun checkBindStatus(serverBaseUrl: String, deviceId: String): BindStatusResult {
        // GET $serverBaseUrl/api/device/bind-status?device_id=$deviceId
        // ...
    }
}
```

> **关于 BindActivity 的 OkHttpClient** (see origin Outstanding Questions)：统一收归到 `RemoteDeviceRepository`，使其可 Mock。BindActivity 不再直接持有 OkHttpClient。

- [ ] 4.2 提交 git commit：`refactor: extract DeviceRepository interface`

**4B. 提取 BindViewModel**

- [ ] 4.3 创建 `BindViewModel.kt`：

```kotlin
// app/src/main/java/com/photoframe/viewmodel/BindViewModel.kt
package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoframe.data.AppPrefs
import com.photoframe.data.DeviceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class BindUiState {
    object Loading : BindUiState()
    object AlreadyBound : BindUiState()
    data class ShowQrCode(val qrToken: String) : BindUiState()
    object BindSuccess : BindUiState()
    data class Error(val message: String) : BindUiState()
}

class BindViewModel(
    private val prefs: AppPrefs,
    private val deviceRepo: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BindUiState>(BindUiState.Loading)
    val uiState: StateFlow<BindUiState> = _uiState

    private var pollJob: Job? = null

    fun checkBindingStatus() {
        if (prefs.isBound && !prefs.userToken.isNullOrEmpty()) {
            _uiState.value = BindUiState.AlreadyBound
            return
        }
        registerDevice()
    }

    private fun registerDevice() {
        viewModelScope.launch {
            try {
                // 如有已保存的 deviceId + qrToken，直接复用
                val existingDeviceId = prefs.deviceId
                val existingQrToken = prefs.qrToken
                if (!existingDeviceId.isNullOrEmpty() && !existingQrToken.isNullOrEmpty()) {
                    _uiState.value = BindUiState.ShowQrCode(existingQrToken)
                    startPolling(existingDeviceId)
                    return@launch
                }

                val result = deviceRepo.registerDevice(prefs.serverBaseUrl)
                prefs.deviceId = result.deviceId
                prefs.qrToken = result.qrToken
                _uiState.value = BindUiState.ShowQrCode(result.qrToken)
                startPolling(result.deviceId)
            } catch (e: Exception) {
                _uiState.value = BindUiState.Error(e.message ?: "注册失败")
            }
        }
    }

    private fun startPolling(deviceId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(3_000)
                try {
                    val status = deviceRepo.checkBindStatus(prefs.serverBaseUrl, deviceId)
                    if (status.bound && !status.userToken.isNullOrEmpty()) {
                        prefs.userToken = status.userToken
                        prefs.isBound = true
                        _uiState.value = BindUiState.BindSuccess
                        break
                    }
                } catch (_: Exception) {
                    // 轮询失败静默忽略，继续重试
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
```

- [ ] 4.4 创建 `BindViewModelFactory.kt`

- [ ] 4.5 重构 `BindActivity`：移除 `registerDevice()`, `startPollingBind()` 方法，改为 collect `viewModel.uiState`：
  - `AlreadyBound` → `goMain()`
  - `ShowQrCode` → 生成 QR 码 Bitmap、更新 UI
  - `BindSuccess` → 重新初始化 ApiClient、`goMain()`
  - `Error` → 显示错误提示

- [ ] 4.6 编译通过 + 手动验证：首次启动显示二维码 → 绑定后跳转 → 已绑定直接跳过

- [ ] 4.7 提交 git commit：`refactor: extract BindViewModel from BindActivity`

**4C. BindViewModel 单元测试**

- [ ] 4.8 创建 `BindViewModelTest.kt`：

```kotlin
// app/src/test/java/com/photoframe/viewmodel/BindViewModelTest.kt
package com.photoframe.viewmodel

import app.cash.turbine.test
import com.photoframe.data.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class BindViewModelTest {

    private val prefs = mockk<AppPrefs>(relaxed = true)
    private val deviceRepo = mockk<DeviceRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { prefs.isBound } returns false
        every { prefs.userToken } returns null
        every { prefs.deviceId } returns null
        every { prefs.qrToken } returns null
        every { prefs.serverBaseUrl } returns "http://test.com"
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `already bound emits AlreadyBound`() = runTest {
        every { prefs.isBound } returns true
        every { prefs.userToken } returns "existing-token"
        val vm = BindViewModel(prefs, deviceRepo)

        vm.uiState.test {
            vm.checkBindingStatus()
            // skip Loading
            val state = awaitItem()
            assertTrue(state is BindUiState.AlreadyBound)
        }
    }

    @Test
    fun `register success emits ShowQrCode`() = runTest {
        coEvery { deviceRepo.registerDevice(any()) } returns
            DeviceRegisterResult("dev-1", "token-abc")

        val vm = BindViewModel(prefs, deviceRepo)
        vm.uiState.test {
            vm.checkBindingStatus()
            skipItems(1) // Loading
            val state = awaitItem()
            assertTrue(state is BindUiState.ShowQrCode)
            assertEquals("token-abc", (state as BindUiState.ShowQrCode).qrToken)
        }
    }

    @Test
    fun `register failure emits Error`() = runTest {
        coEvery { deviceRepo.registerDevice(any()) } throws
            RuntimeException("网络错误")

        val vm = BindViewModel(prefs, deviceRepo)
        vm.uiState.test {
            vm.checkBindingStatus()
            skipItems(1) // Loading
            val state = awaitItem()
            assertTrue(state is BindUiState.Error)
        }
    }

    @Test
    fun `poll success emits BindSuccess and saves token`() = runTest {
        coEvery { deviceRepo.registerDevice(any()) } returns
            DeviceRegisterResult("dev-1", "token-abc")
        coEvery { deviceRepo.checkBindStatus(any(), any()) } returns
            BindStatusResult(bound = true, userToken = "jwt-xyz")

        val vm = BindViewModel(prefs, deviceRepo)
        vm.uiState.test {
            vm.checkBindingStatus()
            skipItems(2) // Loading + ShowQrCode
            val state = awaitItem()
            assertTrue(state is BindUiState.BindSuccess)
            verify { prefs.userToken = "jwt-xyz" }
            verify { prefs.isBound = true }
        }
    }

    @Test
    fun `reuses existing deviceId and qrToken`() = runTest {
        every { prefs.deviceId } returns "existing-dev"
        every { prefs.qrToken } returns "existing-token"
        coEvery { deviceRepo.checkBindStatus(any(), "existing-dev") } returns
            BindStatusResult(bound = true, userToken = "jwt-123")

        val vm = BindViewModel(prefs, deviceRepo)
        vm.uiState.test {
            vm.checkBindingStatus()
            skipItems(1) // Loading
            val show = awaitItem()
            assertTrue(show is BindUiState.ShowQrCode)
            assertEquals("existing-token", (show as BindUiState.ShowQrCode).qrToken)
            // 不应调用 registerDevice
            coVerify(exactly = 0) { deviceRepo.registerDevice(any()) }
        }
    }
}
```

- [ ] 4.9 运行 `./gradlew test` 确认通过
- [ ] 4.10 提交 git commit：`test: add BindViewModel unit tests`

**验证标准**：
- BindActivity 功能与重构前完全一致
- BindViewModelTest 覆盖：已绑定跳过、注册成功/失败、轮询成功、复用已有 deviceId

---

#### Phase 4.5: 工具类单元测试

> **注意**：本 Phase 仅依赖 Phase 1（测试基础设施），可与 Phase 2-4 并行执行。放在 4 之后只是编号便利，不代表必须串行等待。

**目标**：为无需重构即可测试的工具类添加单元测试。

- [ ] 4.11 提取 `ScreenScheduler` 的时间判断为纯函数并测试：

```kotlin
// app/src/main/java/com/photoframe/util/TimeUtils.kt
package com.photoframe.util

fun isInNightPeriod(
    currentHour: Int, currentMinute: Int,
    startHour: Int, startMinute: Int,
    endHour: Int, endMinute: Int
): Boolean {
    val current = currentHour * 60 + currentMinute
    val start = startHour * 60 + startMinute
    val end = endHour * 60 + endMinute

    return if (start <= end) {
        current in start until end      // 同日：如 08:00~22:00
    } else {
        current >= start || current < end // 跨午夜：如 22:00~08:00
    }
}
```

```kotlin
// app/src/test/java/com/photoframe/util/TimeUtilsTest.kt
class TimeUtilsTest {
    @Test fun `same day period - inside`()   { assertTrue(isInNightPeriod(10, 0, 8, 0, 22, 0)) }
    @Test fun `same day period - outside`()  { assertFalse(isInNightPeriod(23, 0, 8, 0, 22, 0)) }
    @Test fun `cross midnight - night side`(){ assertTrue(isInNightPeriod(23, 0, 22, 0, 8, 0)) }
    @Test fun `cross midnight - day side`()  { assertFalse(isInNightPeriod(12, 0, 22, 0, 8, 0)) }
    @Test fun `cross midnight - early morning`(){ assertTrue(isInNightPeriod(3, 0, 22, 0, 8, 0)) }
    @Test fun `exact start boundary`()       { assertTrue(isInNightPeriod(22, 0, 22, 0, 8, 0)) }
    @Test fun `exact end boundary`()         { assertFalse(isInNightPeriod(8, 0, 22, 0, 8, 0)) }
}
```

- [ ] 4.12 提取 `AutoUpdater.String.isNewerThan()` 为工具函数并测试：

```kotlin
// app/src/main/java/com/photoframe/util/VersionUtils.kt
package com.photoframe.util

fun isNewerVersion(remote: String, current: String): Boolean {
    val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(remoteParts.size, currentParts.size)
    for (i in 0 until maxLen) {
        val r = remoteParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (r > c) return true
        if (r < c) return false
    }
    return false
}
```

```kotlin
// app/src/test/java/com/photoframe/util/VersionUtilsTest.kt
class VersionUtilsTest {
    @Test fun `newer major version`()     { assertTrue(isNewerVersion("2.0.0", "1.0.0")) }
    @Test fun `newer minor version`()     { assertTrue(isNewerVersion("1.1.0", "1.0.0")) }
    @Test fun `newer patch version`()     { assertTrue(isNewerVersion("1.0.1", "1.0.0")) }
    @Test fun `same version`()            { assertFalse(isNewerVersion("1.0.0", "1.0.0")) }
    @Test fun `older version`()           { assertFalse(isNewerVersion("1.0.0", "2.0.0")) }
    @Test fun `different length versions`(){ assertTrue(isNewerVersion("1.0.0.1", "1.0.0")) }
}
```

- [ ] 4.13 运行 `./gradlew test`，提交 git commit：`test: add TimeUtils and VersionUtils unit tests`

---

#### Phase 5: UI 端到端测试

**目标**：用 Espresso + MockWebServer 覆盖四大核心 UI 流程（绑定、轮播、设置、异常处理）。

**5A. MockWebServer 测试基础设施**

- [ ] 5.1 创建测试基类，封装 MockWebServer 生命周期管理：

```kotlin
// app/src/androidTest/java/com/photoframe/util/MockServerTestBase.kt
package com.photoframe.util

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.photoframe.data.AppPrefs
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before

abstract class MockServerTestBase {
    protected val server = MockWebServer()
    protected lateinit var prefs: AppPrefs

    @Before
    open fun setUp() {
        server.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = AppPrefs(context)
        // 重定向所有请求到 MockWebServer
        prefs.serverBaseUrl = server.url("/").toString()
    }

    @After
    open fun tearDown() {
        server.shutdown()
    }
}
```

- [ ] 5.2 创建常用 Mock 响应工厂：

```kotlin
// app/src/androidTest/java/com/photoframe/util/MockResponses.kt
package com.photoframe.util

import okhttp3.mockwebserver.MockResponse

object MockResponses {
    fun registerDevice(deviceId: String = "dev-test", qrToken: String = "qr-test") =
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"device_id":"$deviceId","qr_token":"$qrToken"}""")

    fun bindStatusUnbound() =
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"bound":false}""")

    fun bindStatusBound(userToken: String = "jwt-test") =
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"bound":true,"user_token":"$userToken"}""")

    fun photoList(serverUrl: String, count: Int = 2) =
        MockResponse()
            .setResponseCode(200)
            .setBody(buildPhotoListJson(serverUrl, count))

    fun emptyPhotoList() =
        MockResponse()
            .setResponseCode(200)
            .setBody("""{"photos":[]}""")

    fun serverError() = MockResponse().setResponseCode(500)

    fun unauthorized() = MockResponse().setResponseCode(401)

    private fun buildPhotoListJson(serverUrl: String, count: Int): String {
        val photos = (1..count).joinToString(",") { i ->
            """{"id":$i,"url":"${serverUrl}img/$i.jpg","uploader_name":"用户$i","uploaded_at":"2026-03-19","taken_at":null,"latitude":null,"longitude":null,"location_address":null,"camera_make":null,"camera_model":null}"""
        }
        return """{"photos":[$photos]}"""
    }
}
```

**5B. 绑定流程 E2E 测试**

- [ ] 5.3 创建绑定流程测试：

```kotlin
// app/src/androidTest/java/com/photoframe/flow/BindFlowTest.kt
package com.photoframe.flow

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.photoframe.BindActivity
import com.photoframe.MainActivity
import com.photoframe.R
import com.photoframe.util.MockResponses
import com.photoframe.util.MockServerTestBase
import org.junit.Rule
import org.junit.Test

class BindFlowTest : MockServerTestBase() {

    override fun setUp() {
        super.setUp()
        // 清除绑定状态
        prefs.isBound = false
        prefs.deviceId = null
        prefs.qrToken = null
        prefs.userToken = null
        Intents.init()
    }

    override fun tearDown() {
        Intents.release()
        super.tearDown()
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(BindActivity::class.java)

    @Test
    fun bindFlow_showsQrCode_thenNavigatesToMain() {
        // 预设响应
        server.enqueue(MockResponses.registerDevice())
        server.enqueue(MockResponses.bindStatusUnbound())
        server.enqueue(MockResponses.bindStatusBound())

        // 验证二维码显示
        onView(withId(R.id.iv_qr)).check(matches(isDisplayed()))

        // TODO: 替换为 IdlingResource 等待轮询完成，避免 Thread.sleep 导致 flaky
        Thread.sleep(5_000) // 临时方案：等待轮询周期
        Intents.intended(hasComponent(MainActivity::class.java.name))
    }

    @Test
    fun bindFlow_alreadyBound_skipsToMain() {
        prefs.isBound = true
        prefs.userToken = "existing-token"

        // 应直接跳转，不发网络请求
        Intents.intended(hasComponent(MainActivity::class.java.name))
    }
}
```

**5C. 轮播流程 E2E 测试**

- [ ] 5.4 创建轮播流程测试（对应 origin R15）：

```kotlin
// app/src/androidTest/java/com/photoframe/flow/SlideshowFlowTest.kt
package com.photoframe.flow

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.photoframe.MainActivity
import com.photoframe.R
import com.photoframe.SettingsActivity
import com.photoframe.util.MockResponses
import com.photoframe.util.MockServerTestBase
import org.junit.Rule
import org.junit.Test

class SlideshowFlowTest : MockServerTestBase() {

    override fun setUp() {
        super.setUp()
        // 预设已绑定状态
        prefs.isBound = true
        prefs.userToken = "test-token"
        prefs.deviceId = "dev-test"
        Intents.init()
    }

    override fun tearDown() {
        Intents.release()
        super.tearDown()
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun slideshow_displaysPhotosFromServer() {
        // MockWebServer 返回照片列表
        server.enqueue(MockResponses.photoList(server.url("/").toString(), count = 3))

        // 验证 ViewPager2 显示照片（照片容器可见）
        onView(withId(R.id.view_pager)).check(matches(isDisplayed()))
    }

    @Test
    fun slideshow_autoAdvancesPage() {
        server.enqueue(MockResponses.photoList(server.url("/").toString(), count = 3))
        prefs.slideDurationSec = 3 // 设置较短的翻页间隔便于测试

        // TODO: 替换为 IdlingResource 等待自动翻页事件，避免 Thread.sleep
        Thread.sleep(5_000) // 等待至少一次自动翻页

        // 验证当前页不再是第 0 页（即发生了翻页）
        // 具体验证方式取决于 ViewPager2 的状态获取，可通过 ActivityScenario 访问
        activityRule.scenario.onActivity { activity ->
            val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.view_pager)
            assert(viewPager.currentItem > 0) { "Auto-advance should have moved past first page" }
        }
    }

    @Test
    fun slideshow_longPressOpensSettings() {
        server.enqueue(MockResponses.photoList(server.url("/").toString(), count = 2))

        // 长按手势进入设置页
        // 注意：GestureDetector 的长按通过 Espresso longClick 触发
        onView(withId(R.id.view_pager)).perform(
            androidx.test.espresso.action.ViewActions.longClick()
        )
        Intents.intended(hasComponent(SettingsActivity::class.java.name))
    }
}
```

**5D. 设置流程 E2E 测试**

- [ ] 5.5 创建设置流程测试（对应 origin R16）：

```kotlin
// app/src/androidTest/java/com/photoframe/flow/SettingsFlowTest.kt
package com.photoframe.flow

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.photoframe.R
import com.photoframe.SettingsActivity
import com.photoframe.util.MockServerTestBase
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SettingsFlowTest : MockServerTestBase() {

    override fun setUp() {
        super.setUp()
        prefs.isBound = true
        prefs.userToken = "test-token"
        prefs.deviceId = "dev-test"
        prefs.slideDurationSec = 15
        prefs.playMode = "sequential"
        prefs.transitionEffect = "fade"
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(SettingsActivity::class.java)

    @Test
    fun settings_displayCurrentValues() {
        // 验证当前设置值正确渲染到 UI
        // （具体 View ID 根据实际布局调整，以下为示例）
        onView(withId(R.id.et_server_url)).check(
            matches(withText(server.url("/").toString()))
        )
    }

    @Test
    fun settings_changePlayMode_persistsToPrefs() {
        // 点击随机播放选项
        onView(withId(R.id.rb_random)).perform(click())
        // 点击保存
        onView(withId(R.id.btn_save)).perform(click())
        // 验证 AppPrefs 已更新
        assertEquals("random", prefs.playMode)
    }

    @Test
    fun settings_changeServerUrl_clearsBind() {
        // 修改服务器地址
        onView(withId(R.id.et_server_url)).perform(
            clearText(),
            typeText("http://new-server.com")
        )
        onView(withId(R.id.btn_save)).perform(click())
        // 验证绑定状态已清除
        assertFalse(prefs.isBound)
        assertNull(prefs.userToken)
        assertNull(prefs.deviceId)
    }
}
```

**5E. 异常场景测试**

- [ ] 5.6 创建异常场景测试（对应 origin R17）：

```kotlin
// app/src/androidTest/java/com/photoframe/flow/ErrorFlowTest.kt
package com.photoframe.flow

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.photoframe.BindActivity
import com.photoframe.MainActivity
import com.photoframe.R
import com.photoframe.util.MockResponses
import com.photoframe.util.MockServerTestBase
import org.junit.Rule
import org.junit.Test

class ErrorFlowTest : MockServerTestBase() {

    override fun setUp() {
        super.setUp()
        Intents.init()
    }

    override fun tearDown() {
        Intents.release()
        super.tearDown()
    }

    @Test
    fun serverError_showsErrorState() {
        // 清除绑定让 app 走注册流程
        prefs.isBound = false
        prefs.deviceId = null
        prefs.qrToken = null
        prefs.userToken = null

        server.enqueue(MockResponses.serverError())

        val scenario = androidx.test.core.app.ActivityScenario.launch(BindActivity::class.java)
        // 验证错误状态：注册失败后应显示错误提示
        // TODO: 确认实际 error View 的 ID（如 tv_error、Snackbar 等）
        onView(withId(R.id.tv_status)).check(matches(isDisplayed()))
        scenario.close()
    }

    @Test
    fun unauthorized_redirectsToBindActivity() {
        prefs.isBound = true
        prefs.userToken = "expired-token"
        prefs.deviceId = "dev-test"

        // 照片列表返回 401
        server.enqueue(MockResponses.unauthorized())

        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        // TODO: 替换为 IdlingResource 等待 401 回调处理完成
        Thread.sleep(3_000)
        // 验证 401 触发跳转到 BindActivity 重新绑定
        Intents.intended(hasComponent(BindActivity::class.java.name))
        scenario.close()
    }

    @Test
    fun emptyPhotoList_showsEmptyState() {
        prefs.isBound = true
        prefs.userToken = "test-token"
        prefs.deviceId = "dev-test"

        server.enqueue(MockResponses.emptyPhotoList())

        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        // 验证空照片列表不崩溃，ViewPager2 仍然可见
        onView(withId(R.id.view_pager)).check(matches(isDisplayed()))
        scenario.close()
    }
}
```

- [ ] 5.7 运行 `./gradlew connectedAndroidTest` 验证 UI 测试通过
- [ ] 5.8 提交 git commit：`test: add UI E2E tests for bind, slideshow, settings, and error flows`

**验证标准**：
- 绑定流程、轮播流程、设置流程、异常场景测试全部通过
- MockWebServer fixture 数据组织为复用工具类 (`MockResponses`)
- 所有 UI 测试不依赖真实后端

---

#### Phase 6: CI 配置 + Gradle Managed Devices

**目标**：配置 Gradle Managed Devices，使 UI 测试可在无物理设备的 CI 环境运行。

- [ ] 6.1 在 `app/build.gradle` 中配置 Gradle Managed Devices：

```groovy
android {
    testOptions {
        managedDevices {
            devices {
                pixel2Api30(com.android.build.api.dsl.ManagedVirtualDevice) {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"  // Automated Test Device image, smaller & faster
                }
            }
        }
    }
}
```

- [ ] 6.2 验证 Managed Device 测试运行：`./gradlew pixel2Api30Check`

- [ ] 6.3 创建 `android/CI.md` 文档说明测试运行命令：

```markdown
# 测试运行指南

## JVM 单元测试（快速，无需设备）
./gradlew test

## UI 端到端测试（需要模拟器）

### 本地运行（使用连接的设备/模拟器）
./gradlew connectedAndroidTest

### CI 运行（使用 Gradle Managed Devices，无需手动管理模拟器）
./gradlew pixel2Api30Check

## 全量测试
./gradlew test pixel2Api30Check
```

- [ ] 6.4 提交 git commit：`ci: configure Gradle Managed Devices for CI testing`

**验证标准**：
- `./gradlew test` 秒级完成，所有单元测试通过
- `./gradlew pixel2Api30Check` 能自动下载镜像、启动虚拟设备、运行 UI 测试

---

## Alternative Approaches Considered

| 方案 | 为何未选用 |
|------|----------|
| **直接在 Activity 上写 Espresso 测试（不重构）** | Activity 方法多为 private，逻辑紧耦合 UI，测试会极其脆弱。重构投入一次，长期受益。(see origin: Key Decisions) |
| **引入 Hilt/Dagger 依赖注入** | App 只有 3 个 Activity，手动 Factory 注入足够简单。DI 框架增加学习成本和编译时间。(see origin: R5) |
| **使用 LiveData 而非 StateFlow** | StateFlow 与 Kotlin 协程生态一致，JVM 单元测试不需要 InstantTaskExecutorRule。(see origin: Key Decisions) |
| **使用 Robolectric 替代 Espresso** | Robolectric 在 JVM 上模拟 Android 环境，但对 ViewPager2、Glide 等组件支持不完善。Espresso + 真实模拟器更可靠。 |
| **使用 Mockito 而非 MockK** | MockK 对 Kotlin 特性（协程、密封类、扩展函数）支持更好。(see origin: Key Decisions) |

## System-Wide Impact

### Interaction Graph

```
重构影响链：
Activity.onCreate() → 业务逻辑移至 ViewModel
  ├── SettingsActivity → SettingsViewModel.loadSettings() / saveSettings()
  ├── MainActivity → MainViewModel.onNewPhotos() / nextPageIndex()
  │     └── PhotoSyncService callback → viewModel.onNewPhotos()
  └── BindActivity → BindViewModel.checkBindingStatus()
        └── DeviceRepository.registerDevice() / checkBindStatus()

全局回调影响：
  ApiClient.onUnauthorized（在 MainActivity 注册）→ 重构后保持不变（Activity 层注册）
  BootReceiver → 不受影响（启动 BindActivity，入口不变）
```

### Error & Failure Propagation

- **网络错误**：RemoteXxxRepository 抛异常 → ViewModel catch → 更新 StateFlow 为 Error 状态 → Activity 显示错误 UI
- **401 未授权**：ApiClient 拦截器 → `onUnauthorized` 回调 → Activity 跳转 BindActivity（逻辑保持不变）
- **SharedPreferences 读写**：AppPrefs 是同步 API，不会抛异常。ViewModel 直接调用安全。

### State Lifecycle Risks

- **ViewModel 中的 Job 取消**：`BindViewModel.pollJob` 在 `onCleared()` 中取消，防止 Activity 销毁后继续轮询
- **PhotoSyncService 的 CoroutineScope**：仍在 Activity 中启停，ViewModel 不管理 Service 生命周期
- **配置变更（屏幕旋转）**：当前 app 已通过 `android:configChanges` 禁用旋转重建（docs/solutions 记录），ViewModel 的状态保持是额外保险

### API Surface Parity

- `ApiClient.service`：只被 `PhotoSyncService` 和 `AutoUpdater` 使用。重构后 PhotoSyncService 改用 PhotoRepository（或暂时保持不变，Phase 3 中决定）
- `AppPrefs`：所有 Activity + Service 都直接使用，重构后 ViewModel 也通过构造注入使用，API 不变

### Integration Test Scenarios

1. **绑定 → 同步 → 轮播完整流程**：MockWebServer 预设注册 + 绑定 + 照片列表响应，验证从首次启动到照片轮播的全链路
2. **401 触发重新绑定**：MockWebServer 返回 401，验证 app 从 MainActivity 跳转到 BindActivity
3. **设置变更立即生效**：修改播放速度后返回 MainActivity，验证自动翻页间隔变化
4. **空照片列表**：MockWebServer 返回空列表，验证 ViewPager2 不崩溃
5. **网络超时恢复**：首次请求超时，后续请求成功，验证 app 能自动恢复

## Acceptance Criteria

### Functional Requirements

- [ ] SettingsActivity、MainActivity、BindActivity 各有对应 ViewModel，Activity 只负责 UI 绑定和生命周期
- [ ] Repository 接口层（PhotoRepository、DeviceRepository）已抽取，生产/测试实现分离
- [ ] 所有现有功能行为与重构前完全一致，无回归
- [ ] SettingsViewModel 单元测试覆盖：加载设置、保存设置、服务器变更清除绑定
- [ ] MainViewModel 单元测试覆盖：照片去重、顺序/随机模式、循环翻页、空列表边界
- [ ] BindViewModel 单元测试覆盖：已绑定跳过、注册成功/失败、轮询成功、复用 deviceId
- [ ] 工具类测试覆盖：跨午夜时间判断、版本号比较
- [ ] UI E2E 测试覆盖：绑定流程、轮播流程（照片展示 + 自动翻页 + 手势进设置）、设置流程、异常场景（500、401、空列表）
- [ ] 所有 UI 测试通过 MockWebServer 隔离网络，不依赖真实后端

### Non-Functional Requirements

- [ ] JVM 单元测试全量运行 ≤ 10 秒
- [ ] 测试可通过 Gradle Managed Devices 在无物理设备环境运行
- [ ] 每个重构步骤有独立 git commit，可单独 revert

### Quality Gates

- [ ] `./gradlew test` 全部通过
- [ ] `./gradlew connectedAndroidTest` 全部通过
- [ ] 每个 Phase 完成后手动验证对应 Activity 功能无回归

## Success Metrics

(see origin: Success Criteria)
- 开发者修改 UI 交互代码后，运行 `./gradlew test` 即可确认核心逻辑无回归
- 单元测试在 JVM 上 10 秒内完成全量运行
- UI E2E 测试覆盖四大核心流程（绑定、轮播、设置、异常处理），不依赖真实后端即可运行
- 测试可在未来 CI 环境中通过 Gradle Managed Devices 无人值守执行

## Dependencies & Prerequisites

- **已有依赖**：`androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0`（ViewModel），`okhttp3:4.12.0`（MockWebServer 兼容），`kotlinx-coroutines-android:1.7.3`
- **新增依赖**：JUnit 5 (5.10.2)、MockK (1.13.9)、Turbine (1.1.0)、Espresso (3.5.1)、MockWebServer (4.12.0)
- **构建工具**：Gradle 8.5 + AGP 8.3.0，JVM Target Java 17
- **BindActivity 特殊依赖**：直接使用 OkHttpClient（非 Retrofit），重构时收归 DeviceRepository (see origin: Dependencies / Assumptions)

## Risk Analysis & Mitigation

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 重构引入功能回归 | 中 | 高 | 逐个 Activity 拆解 + 每步手动验证 + 独立 commit 可 revert |
| Espresso 测试不稳定（flaky） | 中 | 中 | MockWebServer 消除网络不确定性；用 `IdlingResource` 替代 `Thread.sleep` 处理异步等待（计划中 Thread.sleep 处已标注 TODO） |
| JUnit 5 与 AGP 8.3 兼容问题 | 低 | 中 | AGP 8+ 原生支持 JUnit 5；若有问题可回退到 JUnit 4 |
| Gradle Managed Devices 下载慢/CI 缓存问题 | 低 | 低 | API 30 ATD 镜像较小（~600MB）；CI 可缓存 `~/.android/avd/` |
| ViewModel 中 StateFlow 收集时序问题 | 低 | 中 | 使用 Turbine 的 `test {}` 块精确控制 Flow 收集时序 |

## Resource Requirements

- **时间估算**：
  - Phase 1（基础设施）：0.5 天
  - Phase 2（Settings 重构+测试）：1 天
  - Phase 3（Main 重构+测试）：1.5 天
  - Phase 4（Bind 重构+测试）：1 天
  - Phase 4.5（工具类测试）：0.5 天
  - Phase 5（UI E2E 测试）：1.5 天
  - Phase 6（CI 配置）：0.5 天
  - **总计：约 6.5 个工作日**

- **设备需求**：Android 模拟器或真机（API 30+），用于 UI 测试验证

## Future Considerations

- **截图测试 (R22)**：Phase 1-6 稳定后，引入 Roborazzi 对关键界面做视觉快照回归测试。JVM 上运行，CI 友好。
- **代码覆盖率**：引入 JaCoCo 生成覆盖率报告，设置门槛（如 ViewModel 层 ≥ 80%）
- **CI/CD 流水线**：基于本方案的测试命令搭建 GitHub Actions / Jenkins pipeline
- **Jetpack Compose 迁移**：未来如果 UI 迁移到 Compose，测试可平滑过渡到 Compose Testing

## Sources & References

### Origin

- **Origin document:** [docs/brainstorms/2026-03-19-android-test-automation-requirements.md](../brainstorms/2026-03-19-android-test-automation-requirements.md) — 22 条需求，覆盖重构（R1-R5）、单元测试（R6-R12）、UI 测试（R13-R19）、CI（R20-R21）

### Internal References

- `android/app/src/main/java/com/photoframe/MainActivity.kt` — 188行，最复杂的重构目标
- `android/app/src/main/java/com/photoframe/BindActivity.kt` — 163行，含 OkHttpClient 直接调用
- `android/app/src/main/java/com/photoframe/SettingsActivity.kt` — 160行，最简单的重构起点
- `android/app/src/main/java/com/photoframe/data/ApiClient.kt` — 115行，Retrofit 单例 + 401 回调
- `android/app/src/main/java/com/photoframe/data/AppPrefs.kt` — 77行，SharedPreferences 封装
- `docs/solutions/multi-category/photo-frame-comprehensive-code-review.md` — OkHttp ResponseBody 泄漏、CoroutineScope 未取消等已知问题
- `docs/solutions/multi-category/android-qrcode-main-thread-token-leak-config-issues.md` — configChanges 处理旋转

### External References

- [MockK 官方文档](https://mockk.io/)
- [Turbine Flow 测试库](https://github.com/cashapp/turbine)
- [MockWebServer 指南](https://github.com/square/okhttp/tree/master/mockwebserver)
- [Gradle Managed Devices 文档](https://developer.android.com/studio/test/gradle-managed-devices)
- [Espresso 测试指南](https://developer.android.com/training/testing/espresso)

## Implementation Timeline

| Phase | 任务 | 工期 | 前置依赖 |
|-------|------|------|---------|
| 1 | 测试基础设施搭建 | 0.5 天 | 无 |
| 2 | SettingsActivity 重构 + 单元测试 | 1 天 | Phase 1 |
| 3 | MainActivity 重构 + 单元测试 | 1.5 天 | Phase 2 |
| 4 | BindActivity 重构 + 单元测试 | 1 天 | Phase 3 |
| 4.5 | 工具类单元测试 | 0.5 天 | Phase 1（可与 2-4 并行） |
| 5 | UI 端到端测试（绑定、轮播、设置、异常） | 1.5 天 | Phase 4 |
| 6 | CI 配置 | 0.5 天 | Phase 5 |
| **总计** | | **6.5 天** | |
