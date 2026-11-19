package com.example.transfer

import java.util.UUID

enum class PeerTransport {
    WIFI_DIRECT,
    LOCAL_WIFI,
    BLUETOOTH
}

enum class TransportPreference {
    AUTO_BOTH,
    WIFI_ONLY,
    BLUETOOTH_ONLY
}

data class NearbyPeer(
    val id: String,
    val name: String,
    val transport: PeerTransport,
    val ipAddress: String? = null,
    val port: Int = DEFAULT_TRANSFER_PORT,
    val wifiP2pAddress: String? = null,
    val bluetoothAddress: String? = null,
    val statusLabel: String = "Ready",
    val isConnected: Boolean = false
)

data class ReceiverEndpointInfo(
    val isListening: Boolean = false,
    val deviceName: String = "",
    val localIp: String = "0.0.0.0",
    val port: Int = DEFAULT_TRANSFER_PORT,
    val sessionPin: String = "0000",
    val wifiDirectReady: Boolean = false,
    val wifiDirectGroupFormed: Boolean = false,
    val bluetoothListening: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val bluetoothAddress: String? = null
)

data class IncomingTransferOffer(
    val offerId: String,
    val senderName: String,
    val fileCount: Int,
    val totalBytes: Long,
    val previewFileName: String,
    val transportLabel: String = "Wi-Fi"
)

sealed interface TransferSessionState {
    data object Idle : TransferSessionState

    data class Connecting(
        val peerName: String,
        val statusText: String
    ) : TransferSessionState

    data class WaitingApproval(
        val peerName: String,
        val fileCount: Int,
        val totalBytes: Long
    ) : TransferSessionState

    data class Transferring(
        val isSending: Boolean,
        val peerName: String,
        val currentFileName: String,
        val currentFileIndex: Int,
        val totalFiles: Int,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val etaSeconds: Long
    ) : TransferSessionState {
        val progressFraction: Float
            get() = if (totalBytes > 0L) {
                (bytesTransferred.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            } else 0f
    }

    data class Completed(
        val isSending: Boolean,
        val peerName: String,
        val fileCount: Int,
        val totalBytes: Long,
        val averageSpeedBytesPerSec: Long,
        val lastSavedUri: String?
    ) : TransferSessionState

    data class Error(
        val message: String
    ) : TransferSessionState
}

const val DEFAULT_TRANSFER_PORT = 8988
const val SHAREME_NSD_SERVICE_TYPE = "_shareme._tcp."
const val SHAREME_PROTOCOL_MAGIC = "SHAREME1"
const val SHAREME_BT_SERVICE_NAME = "ShareMeBluetooth"
val SHAREME_BT_SERVICE_UUID: UUID = UUID.fromString("8f5b2a10-6c3d-4e9a-9f1b-7d8c2a4e1001")
