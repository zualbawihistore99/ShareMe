package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.DevicePreferences
import com.example.data.local.DevicePreferencesRepository
import com.example.data.local.TransferRecordEntity
import com.example.data.local.TransferRepository
import com.example.transfer.FileMetadataResolver
import com.example.transfer.FileTransferEngine
import com.example.transfer.IncomingTransferOffer
import com.example.transfer.LocalNetworkUtils
import com.example.transfer.NearbyDiscoveryManager
import com.example.transfer.NearbyPeer
import com.example.transfer.PeerTransport
import com.example.transfer.QrPairingPayload
import com.example.transfer.ReceiverEndpointInfo
import com.example.transfer.ShareableFile
import com.example.transfer.TransferSessionState
import com.example.transfer.TransportPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface PairedReceiverTarget {
    val displayName: String
    val targetId: String?

    data class Peer(val peer: NearbyPeer) : PairedReceiverTarget {
        override val displayName: String get() = peer.name
        override val targetId: String get() = peer.id
    }

    data class Qr(val payload: QrPairingPayload) : PairedReceiverTarget {
        override val displayName: String
            get() = payload.deviceName.ifBlank { payload.ip.ifBlank { "ShareMe Receiver" } }
        override val targetId: String? get() = null
    }
}

class ShareMeViewModel(
    private val transferRepository: TransferRepository,
    private val preferencesRepository: DevicePreferencesRepository,
    private val discoveryManager: NearbyDiscoveryManager,
    private val transferEngine: FileTransferEngine
) : ViewModel() {

    val devicePreferences: StateFlow<DevicePreferences> =
        preferencesRepository.preferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DevicePreferences(
                deviceName = "Android Phone",
                avatarColorIndex = 0,
                autoAcceptIncoming = false,
                preferWifiDirect = true,
                transportPreference = TransportPreference.AUTO_BOTH
            )
        )

    private val _queuedFiles = MutableStateFlow<List<ShareableFile>>(emptyList())
    val queuedFiles: StateFlow<List<ShareableFile>> = _queuedFiles.asStateFlow()

    private val _pairedTarget = MutableStateFlow<PairedReceiverTarget?>(null)
    val pairedTarget: StateFlow<PairedReceiverTarget?> = _pairedTarget.asStateFlow()

    val discoveredPeers: StateFlow<List<NearbyPeer>> = discoveryManager.peers
    val isScanning: StateFlow<Boolean> = discoveryManager.isScanning
    val discoveryStatus: StateFlow<String?> = discoveryManager.statusMessage
    val wifiEnabled: StateFlow<Boolean> = discoveryManager.wifiEnabled
    val wifiP2pEnabled: StateFlow<Boolean> = discoveryManager.wifiP2pEnabled
    val bluetoothEnabled: StateFlow<Boolean> = discoveryManager.bluetoothEnabled
    val bluetoothSupported: Boolean = discoveryManager.isBluetoothSupported()

    val sessionState: StateFlow<TransferSessionState> = transferEngine.sessionState
    val receiverEndpoint: StateFlow<ReceiverEndpointInfo> = transferEngine.endpointInfo
    val incomingOffer: StateFlow<IncomingTransferOffer?> = transferEngine.incomingOffer

    val allTransfers: StateFlow<List<TransferRecordEntity>> =
        transferRepository.allTransfers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentTransfers: StateFlow<List<TransferRecordEntity>> =
        transferRepository.recentTransfers(4).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var wifiDirectConnectJob: Job? = null

    init {
        viewModelScope.launch {
            devicePreferences.collect { prefs ->
                transferEngine.updateAutoAccept(prefs.autoAcceptIncoming)
            }
        }
    }

    fun addPickedUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                uris.mapNotNull { FileMetadataResolver.resolve(context, it) }
            }
            if (resolved.isEmpty()) return@launch
            _queuedFiles.update { current ->
                val existingUris = current.mapTo(HashSet()) { it.uri }
                current + resolved.filter { it.uri !in existingUris }
            }
            val target = _pairedTarget.value
            if (target != null && _queuedFiles.value.isNotEmpty()) {
                _pairedTarget.value = null
                when (target) {
                    is PairedReceiverTarget.Peer -> sendToPeer(target.peer)
                    is PairedReceiverTarget.Qr -> connectAndSendFromQr(target.payload)
                }
            }
        }
    }

    fun clearPairedTarget() {
        _pairedTarget.value = null
    }

    fun removeQueuedFile(fileId: String) {
        _queuedFiles.update { list -> list.filterNot { it.id == fileId } }
    }

    fun clearQueue() {
        _queuedFiles.value = emptyList()
    }

    fun refreshRadioStates() {
        discoveryManager.refreshRadioStates()
    }

    fun startNearbyScan() {
        discoveryManager.refreshRadioStates()
        val prefs = devicePreferences.value
        discoveryManager.startDiscovery(
            preferWifiDirect = prefs.preferWifiDirect,
            transportPreference = TransportPreference.AUTO_BOTH
        )
    }

    fun stopNearbyScan() {
        discoveryManager.stopDiscovery()
    }

    fun sendToPeer(peer: NearbyPeer, pinCode: String = "") {
        val files = _queuedFiles.value
        if (files.isEmpty()) {
            _pairedTarget.value = PairedReceiverTarget.Peer(peer)
            return
        }
        _pairedTarget.value = null
        val senderName = devicePreferences.value.deviceName

        when (peer.transport) {
            PeerTransport.BLUETOOTH -> {
                val btAddr = peer.bluetoothAddress ?: return
                transferEngine.sendFilesViaBluetooth(
                    files = files,
                    bluetoothAddress = btAddr,
                    peerName = peer.name,
                    senderDeviceName = senderName,
                    pinCode = pinCode
                )
            }

            PeerTransport.LOCAL_WIFI -> {
                val ip = peer.ipAddress ?: return
                transferEngine.sendFiles(
                    files = files,
                    targetIp = ip,
                    targetPort = peer.port,
                    peerName = peer.name,
                    senderDeviceName = senderName,
                    pinCode = pinCode,
                    transportLabel = "Wi-Fi LAN"
                )
            }

            PeerTransport.WIFI_DIRECT -> {
                val existingIp = peer.ipAddress
                if (peer.isConnected && !existingIp.isNullOrBlank()) {
                    transferEngine.sendFiles(
                        files = files,
                        targetIp = existingIp,
                        targetPort = peer.port,
                        peerName = peer.name,
                        senderDeviceName = senderName,
                        pinCode = pinCode,
                        transportLabel = "Wi-Fi Direct"
                    )
                    return
                }

                wifiDirectConnectJob?.cancel()
                wifiDirectConnectJob = viewModelScope.launch {
                    discoveryManager.connectWifiDirectPeer(peer) { initiated, _ ->
                        if (!initiated) {
                            viewModelScope.launch {
                                transferEngine.cancelActiveTransfer()
                            }
                        }
                    }
                    val info = withTimeoutOrNull(20_000L) {
                        discoveryManager.wifiDirectConnection
                            .filterNotNull()
                            .first { it.groupFormed && it.groupOwnerAddress != null }
                    }
                    val targetIp = info?.groupOwnerAddress?.hostAddress
                    if (!targetIp.isNullOrBlank()) {
                        transferEngine.sendFiles(
                            files = files,
                            targetIp = targetIp,
                            targetPort = peer.port,
                            peerName = peer.name,
                            senderDeviceName = senderName,
                            pinCode = pinCode,
                            transportLabel = "Wi-Fi Direct"
                        )
                    }
                }
            }
        }
    }

    fun sendDirectToIp(ip: String, port: Int, pinCode: String) {
        val files = _queuedFiles.value
        if (files.isEmpty()) return
        val cleanedIp = ip.trim()
        if (cleanedIp.isBlank()) return
        transferEngine.sendFiles(
            files = files,
            targetIp = cleanedIp,
            targetPort = port,
            peerName = cleanedIp,
            senderDeviceName = devicePreferences.value.deviceName,
            pinCode = pinCode.trim(),
            transportLabel = "Direct IP"
        )
    }

    fun connectAndSendFromQr(payload: QrPairingPayload) {
        val files = _queuedFiles.value
        if (files.isEmpty()) {
            _pairedTarget.value = PairedReceiverTarget.Qr(payload)
            return
        }
        _pairedTarget.value = null
        val senderName = devicePreferences.value.deviceName
        val targetName = payload.deviceName.ifBlank { payload.ip.ifBlank { "ShareMe Receiver" } }

        if (payload.ip.isNotBlank() && payload.ip != "0.0.0.0") {
            transferEngine.sendFiles(
                files = files,
                targetIp = payload.ip,
                targetPort = payload.port,
                peerName = targetName,
                senderDeviceName = senderName,
                pinCode = payload.pin,
                transportLabel = "QR Wi-Fi",
                fallbackBluetoothAddress = payload.bluetoothAddress
            )
        } else if (!payload.bluetoothAddress.isNullOrBlank()) {
            transferEngine.sendFilesViaBluetooth(
                files = files,
                bluetoothAddress = payload.bluetoothAddress,
                peerName = targetName,
                senderDeviceName = senderName,
                pinCode = payload.pin
            )
        }
    }

    fun startReceiveMode(context: Context) {
        discoveryManager.refreshRadioStates()
        val prefs = devicePreferences.value
        val hasWifiPerm = LocalNetworkUtils.hasWifiDiscoveryPermission(context)
        if (hasWifiPerm && prefs.preferWifiDirect) {
            discoveryManager.createWifiDirectReceiverGroup()
        }
        val p2pFormed = discoveryManager.wifiDirectConnection.value?.groupFormed == true
        transferEngine.startReceiverServer(
            deviceName = prefs.deviceName,
            wifiDirectReady = hasWifiPerm && discoveryManager.wifiP2pEnabled.value,
            wifiDirectGroupFormed = p2pFormed
        ) { boundPort ->
            discoveryManager.startAdvertising(prefs.deviceName, boundPort)
        }
    }

    fun stopReceiveMode() {
        discoveryManager.stopAdvertising()
        transferEngine.stopReceiverServer()
    }

    fun respondToIncomingOffer(accepted: Boolean) {
        transferEngine.respondToIncomingOffer(accepted)
    }

    fun cancelTransfer() {
        wifiDirectConnectJob?.cancel()
        transferEngine.cancelActiveTransfer()
    }

    fun resetTransferState() {
        transferEngine.resetSessionState()
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch { transferRepository.deleteRecord(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { transferRepository.clearHistory() }
    }

    fun updateDeviceName(name: String) {
        viewModelScope.launch { preferencesRepository.setDeviceName(name) }
    }

    fun updateAvatarColor(index: Int) {
        viewModelScope.launch { preferencesRepository.setAvatarColorIndex(index) }
    }

    fun setAutoAcceptIncoming(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoAcceptIncoming(enabled) }
    }

    fun setPreferWifiDirect(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPreferWifiDirect(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.cleanup()
        transferEngine.stopReceiverServer()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ShareMeViewModel(
                        transferRepository = container.transferRepository,
                        preferencesRepository = container.devicePreferencesRepository,
                        discoveryManager = container.discoveryManager,
                        transferEngine = container.transferEngine
                    ) as T
                }
            }
    }
}
