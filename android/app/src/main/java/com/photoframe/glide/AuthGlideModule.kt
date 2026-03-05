package com.photoframe.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.photoframe.data.AppPrefs
import okhttp3.OkHttpClient
import java.io.InputStream

/**
 * 自定义 Glide 网络层：为图片请求附加 Authorization header，
 * 解决服务端鉴权导致 Glide 加载图片 401 的问题。
 */
@GlideModule
class AuthGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val token = AppPrefs(context).userToken
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(req)
            }
            .build()
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(client)
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false
}
