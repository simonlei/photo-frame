package com.photoframe.data

import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
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
    @SerializedName("apk_sha256") val apkSha256: String?,
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
    private const val TAG = "ApiClient"
    private var _service: ApiService? = null

    fun init(url: String, token: String?) {
        Log.d(TAG, "init() token=${if (token != null) "present(${token.take(8)}...)" else "null"}")
        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .addInterceptor { chain ->
                val req = if (token != null) {
                    Log.d(TAG, "attaching Authorization header to ${chain.request().url}")
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    Log.w(TAG, "no token — sending request without Authorization: ${chain.request().url}")
                    chain.request()
                }
                chain.proceed(req)
            }
            .build()
        _service = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val service: ApiService
        get() = checkNotNull(_service) {
            "ApiClient.init() must be called before accessing service. " +
            "Call it in Application.onCreate()."
        }
}
