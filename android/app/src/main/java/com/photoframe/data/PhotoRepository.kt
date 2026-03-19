package com.photoframe.data

/**
 * 照片数据仓库接口，抽象网络调用以支持测试 Mock
 */
interface PhotoRepository {
    suspend fun getPhotos(deviceId: String, since: String?): List<Photo>
}
