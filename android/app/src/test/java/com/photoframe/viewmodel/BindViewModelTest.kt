package com.photoframe.viewmodel

import com.photoframe.data.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class BindViewModelTest {

    private val prefs = mockk<AppPrefs>(relaxed = true)
    private val deviceRepo = mockk<DeviceRepository>()

    @BeforeEach
    fun setup() {
        every { prefs.isBound } returns false
        every { prefs.userToken } returns null
        every { prefs.deviceId } returns null
        every { prefs.qrToken } returns null
        every { prefs.serverBaseUrl } returns "http://test.com"
    }

    @Test
    fun `already bound emits AlreadyBound`() = runTest {
        every { prefs.isBound } returns true
        every { prefs.userToken } returns "existing-token"

        // backgroundScope 在 runTest 结束时被取消但不会被等待
        val vm = BindViewModel(prefs, deviceRepo, externalScope = backgroundScope)

        vm.checkBindingStatus()
        assertEquals(BindUiState.AlreadyBound, vm.uiState.value)
    }

    @Test
    fun `register success emits ShowQrCode`() = runTest {
        coEvery { deviceRepo.registerDevice(any()) } returns
            DeviceRegisterResult("dev-1", "token-abc")
        coEvery { deviceRepo.checkBindStatus(any(), any()) } returns
            BindStatusResult(bound = false, userToken = null)

        val vm = BindViewModel(prefs, deviceRepo, externalScope = backgroundScope)
        vm.checkBindingStatus()
        advanceTimeBy(100)
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state is BindUiState.ShowQrCode, "Expected ShowQrCode but got $state")
        assertEquals("token-abc", (state as BindUiState.ShowQrCode).qrToken)
        verify { prefs.deviceId = "dev-1" }
        verify { prefs.qrToken = "token-abc" }
    }

    @Test
    fun `register failure emits Error`() = runTest {
        coEvery { deviceRepo.registerDevice(any()) } throws
            RuntimeException("网络错误")

        val vm = BindViewModel(prefs, deviceRepo, externalScope = backgroundScope)
        vm.checkBindingStatus()
        advanceTimeBy(100)
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state is BindUiState.Error, "Expected Error but got $state")
        assertTrue((state as BindUiState.Error).message.contains("网络错误"))
    }

    @Test
    fun `poll success emits BindSuccess and saves token`() = runTest {
        coEvery { deviceRepo.registerDevice(any()) } returns
            DeviceRegisterResult("dev-1", "token-abc")
        coEvery { deviceRepo.checkBindStatus(any(), any()) } returns
            BindStatusResult(bound = true, userToken = "jwt-xyz")

        val vm = BindViewModel(prefs, deviceRepo, externalScope = backgroundScope)
        vm.checkBindingStatus()
        advanceTimeBy(3_500)
        runCurrent()

        assertEquals(BindUiState.BindSuccess, vm.uiState.value)
        verify { prefs.userToken = "jwt-xyz" }
        verify { prefs.isBound = true }
    }

    @Test
    fun `reuses existing deviceId and qrToken`() = runTest {
        every { prefs.deviceId } returns "existing-dev"
        every { prefs.qrToken } returns "existing-token"
        coEvery { deviceRepo.checkBindStatus(any(), "existing-dev") } returns
            BindStatusResult(bound = true, userToken = "jwt-123")

        val vm = BindViewModel(prefs, deviceRepo, externalScope = backgroundScope)
        vm.checkBindingStatus()
        advanceTimeBy(3_500)
        runCurrent()

        coVerify(exactly = 0) { deviceRepo.registerDevice(any()) }
        assertEquals(BindUiState.BindSuccess, vm.uiState.value)
    }

    @Test
    fun `poll retries on exception and succeeds later`() = runTest {
        coEvery { deviceRepo.registerDevice(any()) } returns
            DeviceRegisterResult("dev-1", "token-abc")
        coEvery { deviceRepo.checkBindStatus(any(), any()) } throws
            RuntimeException("timeout") andThen
            BindStatusResult(bound = true, userToken = "jwt-retry")

        val vm = BindViewModel(prefs, deviceRepo, externalScope = backgroundScope)
        vm.checkBindingStatus()
        // 第一次 poll: delay(3000) + 失败
        advanceTimeBy(3_500)
        runCurrent()
        assertTrue(vm.uiState.value is BindUiState.ShowQrCode)

        // 第二次 poll: 再推进 3000ms
        advanceTimeBy(3_500)
        runCurrent()

        assertEquals(BindUiState.BindSuccess, vm.uiState.value)
        verify { prefs.userToken = "jwt-retry" }
    }

    @Test
    fun `poll emits Error after max retries`() = runTest {
        coEvery { deviceRepo.registerDevice(any()) } returns
            DeviceRegisterResult("dev-1", "token-abc")
        coEvery { deviceRepo.checkBindStatus(any(), any()) } throws
            RuntimeException("server down")

        val vm = BindViewModel(prefs, deviceRepo, externalScope = backgroundScope)
        vm.checkBindingStatus()

        // 快进超过最大重试次数 (200 次 × 3s = 600s，前 5 次 3s，之后 10s)
        // 5 次 × 3s = 15s, 然后 195 次 × 10s = 1950s, 共 1965s
        advanceTimeBy(2_000_000)
        runCurrent()

        val state = vm.uiState.value
        assertTrue(state is BindUiState.Error, "Expected Error after max retries but got $state")
        assertTrue((state as BindUiState.Error).message.contains("超时"))
    }
}
