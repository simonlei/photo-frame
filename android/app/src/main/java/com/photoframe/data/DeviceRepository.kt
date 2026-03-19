package com.photoframe.data

/**
 * 设备注册/绑定数据仓库接口，抽象网络调用以支持测试 Mock
 */
interface DeviceRepository {
    suspend fun registerDevice(serverBaseUrl: String): DeviceRegisterResult
    suspend fun checkBindStatus(serverBaseUrl: String, deviceId: String): BindStatusResult
}

data class DeviceRegisterResult(val deviceId: String, val qrToken: String)
data class BindStatusResult(val bound: Boolean, val userToken: String?)
