package com.photoframe.data

data class Photo(
    val id: Long,
    val url: String,
    
    // 时间信息
    val takenAt: String?,           // EXIF 拍摄时间
    val uploadedAt: String,
    
    // 地理位置（新增）
    val latitude: Double?,
    val longitude: Double?,
    val locationAddress: String?,   // 详细地址
    
    // 相机信息（新增）
    val cameraMake: String?,
    val cameraModel: String?,
    
    // 上传者信息
    val uploaderName: String
)

