package com.photoframe.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

object QrCodeHelper {
    fun buildBindUrl(qrToken: String) = "photoframe://bind?qr_token=$qrToken"

    fun generateBitmap(qrToken: String, size: Int = 256): Bitmap? =
        runCatching {
            BarcodeEncoder().encodeBitmap(buildBindUrl(qrToken), BarcodeFormat.QR_CODE, size, size)
        }.getOrNull()
}
