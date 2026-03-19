package com.photoframe.data

/**
 * PhotoRepository 的生产实现，通过 ApiService 从远程服务器获取照片列表
 */
class RemotePhotoRepository(
    private val apiService: ApiService,
    private val serverBaseUrl: String
) : PhotoRepository {

    override suspend fun getPhotos(deviceId: String, since: String?): List<Photo> {
        val response = apiService.listPhotos(deviceId, since)
        val baseUrl = serverBaseUrl.trimEnd('/')
        return response.photos.map { dto ->
            // 服务端可能返回相对路径（如 /uploads/xxx.jpg），需要补全为绝对 URL
            val fullUrl = if (dto.url.startsWith("http")) dto.url else "$baseUrl${dto.url}"
            Photo(
                id = dto.id,
                url = fullUrl,
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
