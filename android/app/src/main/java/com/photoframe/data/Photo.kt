package com.photoframe.data

data class Photo(
    val id: Long,
    val url: String,
    val takenAt: String?,
    val uploaderName: String,
    val uploadedAt: String
)
