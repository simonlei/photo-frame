package com.photoframe.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ---------- 数据类 ----------

data class PhotoListResponse(
    @SerializedName("photos") val photos: List<PhotoDto>
)

data class PhotoDto(
    @SerializedName("id") val id: Long,
    @SerializedName("url") val url: String,
    @SerializedName("taken_at") val takenAt: String?,
    @SerializedName("uploader_name") val uploaderName: String,
    @SerializedName("uploaded_at") val uploadedAt: String
)

data class VersionResponse(
    @SerializedName("version") val version: String?,
    @SerializedName("apk_url") val apkUrl: String?,
    @SerializedName("changelog") val changelog: String?
)

data class DeviceRegisterResponse(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("qr_token") val qrToken: String
)

// ---------- Retrofit 接口 ----------

interface ApiService {
    @GET("api/photos")
    suspend fun listPhotos(
        @Query("device_id") deviceId: String,
        @Query("since") since: String? = null,
        @Query("limit") limit: Int = 200
    ): PhotoListResponse

    @GET("api/version/latest")
    suspend fun latestVersion(): VersionResponse
}

// ---------- 单例工厂 ----------

object ApiClient {
    private var baseUrl: String = "https://your-server.com/"

    fun init(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
