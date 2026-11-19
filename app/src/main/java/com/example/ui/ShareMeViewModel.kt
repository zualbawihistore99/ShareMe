package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.AppThemeMode
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    private val _pairedPeer = MutableStateFlow<NearbyPeer?>(null)
    val pairedPeer: StateFlow<NearbyPeer?> = _pairedPeer.asStateFlow()
    private val _pairedQrPayload = MutableStateFlow<QrPairingPayload?>(null)

    val discoveredPeers: StateFlow<List<NearbyPeer>> =
        combine(discoveryManager.peers, _pairedPeer) { discovered, paired ->
            if (paired == null) {
                discovered
            } else {
                listOf(paired) + discovered.filterNot { it.id == paired.id }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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

    fun addPickedUris(context: Context, uris: List<Uri>, autoSendIfPaired: Boolean = false) {
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
            if (autoSendIfPaired && sessionState.value is TransferSessionState.Idle) {
                val qr = _pairedQrPayload.value
                val peer = _pairedPeer.value
                val files = _queuedFiles.value
                if (qr != null && files.isNotEmpty()) {
                    transferEngine.sendFilesFromQr(
                        files = files,
                        payload = qr,
                        senderDeviceName = devicePreferences.value.deviceName
                    )
                } else if (peer != null && files.isNotEmpty()) {
                    sendToPeer(peer)
                }
            }
        }
    }

    fun removeQueuedFile(fileId: String) {
        _queuedFiles.update { list -> list.filterNot { it.id == fileId } }
    }

    fun clearQueue() {
        _queuedFiles.value = emptyList()
    }

    fun clearPairedPeer() {
        _pairedPeer.value = null
        _pairedQrPayload.value = null
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
        if (files.isEmpty()) return
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

    fun onPeerClicked(peer: NearbyPeer, onNeedFiles: () -> Unit) {
        _pairedPeer.value = peer.copy(isConnected = true)
        if (peer.id.startsWith("qr:").not()) {
            _pairedQrPayload.value = null
        }
        val files = _queuedFiles.value
        if (files.isNotEmpty()) {
            val qr = _pairedQrPayload.value
            if (qr != null && peer.id.startsWith("qr:")) {
                transferEngine.sendFilesFromQr(
                    files = files,
                    payload = qr,
                    senderDeviceName = devicePreferences.value.deviceName
                )
            } else {
                sendToPeer(peer)
            }
        } else {
            onNeedFiles()
        }
    }

    fun connectAndSendFromQr(payload: QrPairingPayload, onNeedFiles: () -> Unit) {
        val senderName = devicePreferences.value.deviceName
        val targetName = payload.deviceName.ifBlank { payload.ip.ifBlank { "ShareMe Receiver" } }
        val hasValidIp = payload.ip.isNotBlank() && payload.ip != "0.0.0.0"
        val qrPeer = NearbyPeer(
            id = "qr:${payload.ip.ifBlank { payload.bluetoothAddress ?: targetName }}",
            name = targetName,
            transport = if (hasValidIp) PeerTransport.LOCAL_WIFI else PeerTransport.BLUETOOTH,
            ipAddress = payload.ip.takeIf { hasValidIp },
            port = payload.port,
            bluetoothAddress = payload.bluetoothAddress,
            statusLabel = "Connected via QR",
            isConnected = true
        )

        _pairedQrPayload.value = payload
        val files = _queuedFiles.value

        if (files.isNotEmpty()) {
            _pairedPeer.value = qrPeer
            transferEngine.sendFilesFromQr(
                files = files,
                payload = payload,
                senderDeviceName = senderName
            )
        } else {
            transferEngine.probeAndPairQr(
                payload = payload,
                senderDeviceName = senderName
            ) { connected, transport, _ ->
                if (connected) {
                    _pairedPeer.value = qrPeer.copy(transport = transport)
                    onNeedFiles()
                }
            }
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

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun toggleDayNightTheme(currentlyDark: Boolean) {
        val next = if (currentlyDark) AppThemeMode.DAY else AppThemeMode.NIGHT
        viewModelScope.launch { preferencesRepository.setThemeMode(next) }
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
