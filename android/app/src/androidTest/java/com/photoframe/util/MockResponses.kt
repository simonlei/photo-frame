package com.photoframe.util

import okhttp3.mockwebserver.MockResponse

/**
 * 常用 MockWebServer 响应工厂，封装 JSON 格式以避免测试中手写 JSON。
 */
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
