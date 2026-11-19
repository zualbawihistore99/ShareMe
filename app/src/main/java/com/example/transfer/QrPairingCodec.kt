package com.example.transfer

import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

data class QrPairingPayload(
    val ip: String,
    val port: Int,
    val pin: String,
    val deviceName: String,
    val bluetoothAddress: String? = null
)

object QrPairingCodec {

    private const val SCHEME = "shareme"
    private const val HOST = "connect"
    private const val PLACEHOLDER_BT_MAC = "02:00:00:00:00:00"

    fun encode(endpoint: ReceiverEndpointInfo): String {
        val builder = Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("port", endpoint.port.toString())
            .appendQueryParameter("pin", endpoint.sessionPin)
            .appendQueryParameter("name", endpoint.deviceName.ifBlank { "ShareMe Receiver" })

        if (endpoint.localIp.isNotBlank() && endpoint.localIp != "0.0.0.0") {
            builder.appendQueryParameter("ip", endpoint.localIp)
        }
        val bt = endpoint.bluetoothAddress?.trim()
        if (!bt.isNullOrEmpty() && bt != PLACEHOLDER_BT_MAC) {
            builder.appendQueryParameter("bt", bt)
        }
        return builder.build().toString()
    }

    fun parse(raw: String): QrPairingPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        if (!SCHEME.equals(uri.scheme, ignoreCase = true) ||
            !HOST.equals(uri.host, ignoreCase = true)
        ) {
            return null
        }

        val ip = uri.getQueryParameter("ip")?.trim().orEmpty().let {
            if (it == "0.0.0.0") "" else it
        }
        val port = uri.getQueryParameter("port")
            ?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: DEFAULT_TRANSFER_PORT
        val pin = uri.getQueryParameter("pin")?.trim().orEmpty()
        val deviceName = uri.getQueryParameter("name")?.trim().orEmpty()
        val bt = uri.getQueryParameter("bt")?.trim()
            ?.takeIf { it.isNotEmpty() && it != PLACEHOLDER_BT_MAC }

        if (ip.isBlank() && bt.isNullOrBlank()) {
            return null
        }

        return QrPairingPayload(
            ip = ip,
            port = port,
            pin = pin,
            deviceName = deviceName,
            bluetoothAddress = bt
        )
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val clampedSize = sizePx.coerceIn(128, 1024)
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, clampedSize, clampedSize, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        val darkColor = 0xFF0F172A.toInt()
        val lightColor = 0xFFFFFFFF.toInt()
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (matrix[x, y]) darkColor else lightColor
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
