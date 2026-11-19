package com.example.transfer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.wifi.WifiManager
import com.example.data.local.TransferDirection
import com.example.data.local.TransferRepository
import com.example.data.local.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.random.Random

class FileTransferEngine(
    private val context: Context,
    private val transferRepository: TransferRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _sessionState = MutableStateFlow<TransferSessionState>(TransferSessionState.Idle)
    val sessionState: StateFlow<TransferSessionState> = _sessionState.asStateFlow()

    private val _endpointInfo = MutableStateFlow(ReceiverEndpointInfo())
    val endpointInfo: StateFlow<ReceiverEndpointInfo> = _endpointInfo.asStateFlow()

    private val _incomingOffer = MutableStateFlow<IncomingTransferOffer?>(null)
    val incomingOffer: StateFlow<IncomingTransferOffer?> = _incomingOffer.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var btServerSocket: BluetoothServerSocket? = null
    private var btServerJob: Job? = null

    private var activeTransferJob: Job? = null
    private var activeClientSocket: Socket? = null
    private var activeBtClientSocket: BluetoothSocket? = null

    private var pendingApprovalDeferred: CompletableDeferred<Boolean>? = null
    private var autoAcceptEnabled: Boolean = false
    private var activePin: String = generatePin()

    fun updateAutoAccept(enabled: Boolean) {
        autoAcceptEnabled = enabled
    }

    @SuppressLint("MissingPermission")
    fun startReceiverServer(
        deviceName: String,
        wifiDirectReady: Boolean,
        wifiDirectGroupFormed: Boolean,
        onPortBound: (Int) -> Unit
    ) {
        val btEnabled = bluetoothAdapter?.isEnabled == true
        val canBtListen = btEnabled && LocalNetworkUtils.hasBluetoothConnectPermission(context)
        val btAddress = resolveOwnBluetoothAddress()

        if (serverJob?.isActive == true && serverSocket?.isClosed == false) {
            _endpointInfo.value = _endpointInfo.value.copy(
                deviceName = deviceName,
                localIp = LocalNetworkUtils.getLocalIpv4Address(),
                wifiDirectReady = wifiDirectReady,
                wifiDirectGroupFormed = wifiDirectGroupFormed,
                bluetoothEnabled = btEnabled,
                bluetoothAddress = btAddress
            )
            if (canBtListen && btServerJob?.isActive != true) {
                startBluetoothServerLoop()
            }
            onPortBound(_endpointInfo.value.port)
            return
        }

        stopReceiverServer()
        activePin = generatePin()

        // 1. Start Wi-Fi / LAN TCP ServerSocket
        serverJob = scope.launch {
            val socketServer = runCatching {
                ServerSocket(DEFAULT_TRANSFER_PORT)
            }.getOrElse {
                runCatching { ServerSocket(0) }.getOrNull()
            }

            if (socketServer == null) {
                _sessionState.value = TransferSessionState.Error("Unable to open Wi-Fi receiver socket")
                return@launch
            }

            serverSocket = socketServer
            val boundPort = socketServer.localPort
            _endpointInfo.value = _endpointInfo.value.copy(
                isListening = true,
                deviceName = deviceName,
                localIp = LocalNetworkUtils.getLocalIpv4Address(),
                port = boundPort,
                sessionPin = activePin,
                wifiDirectReady = wifiDirectReady,
                wifiDirectGroupFormed = wifiDirectGroupFormed,
                bluetoothEnabled = btEnabled,
                bluetoothAddress = btAddress
            )
            withContext(Dispatchers.Main) {
                onPortBound(boundPort)
            }

            while (isActive && !socketServer.isClosed) {
                val client = runCatching { socketServer.accept() }.getOrNull() ?: break
                launch {
                    try {
                        client.soTimeout = 65_000
                        client.tcpNoDelay = true
                        handleIncomingStreams(
                            rawInput = client.getInputStream(),
                            rawOutput = client.getOutputStream(),
                            transportLabel = "Wi-Fi"
                        )
                    } finally {
                        runCatching { client.close() }
                    }
                }
            }
        }

        // 2. Start Bluetooth RFCOMM ServerSocket concurrently
        if (canBtListen) {
            startBluetoothServerLoop()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothServerLoop() {
        btServerJob?.cancel()
        runCatching { btServerSocket?.close() }
        btServerSocket = null

        btServerJob = scope.launch {
            val adapter = bluetoothAdapter ?: return@launch
            if (!adapter.isEnabled || !LocalNetworkUtils.hasBluetoothConnectPermission(context)) {
                _endpointInfo.value = _endpointInfo.value.copy(bluetoothListening = false)
                return@launch
            }

            val server = runCatching {
                adapter.listenUsingRfcommWithServiceRecord(
                    SHAREME_BT_SERVICE_NAME,
                    SHAREME_BT_SERVICE_UUID
                )
            }.getOrNull()

            if (server == null) {
                _endpointInfo.value = _endpointInfo.value.copy(bluetoothListening = false)
                return@launch
            }

            btServerSocket = server
            _endpointInfo.value = _endpointInfo.value.copy(
                bluetoothListening = true,
                bluetoothEnabled = true
            )

            while (isActive) {
                val btClient = runCatching { server.accept() }.getOrNull() ?: break
                launch {
                    try {
                        handleIncomingStreams(
                            rawInput = btClient.inputStream,
                            rawOutput = btClient.outputStream,
                            transportLabel = "Bluetooth"
                        )
                    } finally {
                        runCatching { btClient.close() }
                    }
                }
            }
        }
    }

    fun stopReceiverServer() {
        pendingApprovalDeferred?.complete(false)
        pendingApprovalDeferred = null
        _incomingOffer.value = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null

        runCatching { btServerSocket?.close() }
        btServerSocket = null
        btServerJob?.cancel()
        btServerJob = null

        _endpointInfo.value = ReceiverEndpointInfo()
    }

    fun respondToIncomingOffer(accepted: Boolean) {
        pendingApprovalDeferred?.complete(accepted)
        _incomingOffer.value = null
    }

    fun sendFiles(
        files: List<ShareableFile>,
        targetIp: String,
        targetPort: Int,
        peerName: String,
        senderDeviceName: String,
        pinCode: String,
        transportLabel: String,
        fallbackBluetoothAddress: String? = null
    ) {
        if (files.isEmpty()) return
        activeTransferJob?.cancel()

        activeTransferJob = scope.launch {
            val wifiLock = acquireWifiLock()
            try {
                _sessionState.value = TransferSessionState.Connecting(
                    peerName = peerName,
                    statusText = "Connecting via $transportLabel ($targetIp:$targetPort)..."
                )

                val socket = Socket()
                activeClientSocket = socket
                socket.soTimeout = 65_000
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(targetIp, targetPort), 6_000)

                streamSendFiles(
                    rawOutput = socket.getOutputStream(),
                    rawInput = socket.getInputStream(),
                    files = files,
                    peerName = peerName,
                    senderDeviceName = senderDeviceName,
                    pinCode = pinCode,
                    transportLabel = transportLabel,
                    chunkSize = 65_536
                )
            } catch (_: CancellationException) {
                _sessionState.value = TransferSessionState.Error("Transfer cancelled")
            } catch (e: Exception) {
                val canFallbackBt = !fallbackBluetoothAddress.isNullOrBlank() &&
                    bluetoothAdapter?.isEnabled == true &&
                    LocalNetworkUtils.hasBluetoothConnectPermission(context)
                if (canFallbackBt) {
                    runCatching { activeClientSocket?.close() }
                    activeClientSocket = null
                    runCatching { wifiLock?.release() }
                    sendFilesViaBluetooth(
                        files = files,
                        bluetoothAddress = fallbackBluetoothAddress!!,
                        peerName = peerName,
                        senderDeviceName = senderDeviceName,
                        pinCode = pinCode
                    )
                    return@launch
                }
                _sessionState.value = TransferSessionState.Error(
                    e.localizedMessage ?: "Wi-Fi transfer connection failed"
                )
            } finally {
                runCatching { activeClientSocket?.close() }
                activeClientSocket = null
                runCatching { wifiLock?.release() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendFilesViaBluetooth(
        files: List<ShareableFile>,
        bluetoothAddress: String,
        peerName: String,
        senderDeviceName: String,
        pinCode: String = ""
    ) {
        if (files.isEmpty()) return
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _sessionState.value = TransferSessionState.Error("Bluetooth is turned off")
            return
        }
        if (!LocalNetworkUtils.hasBluetoothConnectPermission(context)) {
            _sessionState.value = TransferSessionState.Error("Bluetooth Connect permission required")
            return
        }

        activeTransferJob?.cancel()
        activeTransferJob = scope.launch {
            try {
                _sessionState.value = TransferSessionState.Connecting(
                    peerName = peerName,
                    statusText = "Connecting via Bluetooth RFCOMM..."
                )

                if (LocalNetworkUtils.hasBluetoothScanPermission(context)) {
                    runCatching {
                        if (adapter.isDiscovering) adapter.cancelDiscovery()
                    }
                }

                val device = adapter.getRemoteDevice(bluetoothAddress)
                val socket = connectBluetoothSocket(device)
                activeBtClientSocket = socket

                streamSendFiles(
                    rawOutput = socket.outputStream,
                    rawInput = socket.inputStream,
                    files = files,
                    peerName = peerName,
                    senderDeviceName = senderDeviceName,
                    pinCode = pinCode,
                    transportLabel = "Bluetooth",
                    chunkSize = 16_384
                )
            } catch (_: CancellationException) {
                _sessionState.value = TransferSessionState.Error("Bluetooth transfer cancelled")
            } catch (e: Exception) {
                _sessionState.value = TransferSessionState.Error(
                    e.localizedMessage
                        ?: "Bluetooth connection failed. Make sure ShareMe Receive is open on $peerName."
                )
            } finally {
                runCatching { activeBtClientSocket?.close() }
                activeBtClientSocket = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectBluetoothSocket(device: android.bluetooth.BluetoothDevice): BluetoothSocket {
        val secureSocket = runCatching {
            device.createRfcommSocketToServiceRecord(SHAREME_BT_SERVICE_UUID)
        }.getOrNull()

        if (secureSocket != null) {
            val connected = runCatching {
                secureSocket.connect()
                secureSocket
            }.getOrNull()
            if (connected != null) return connected
            runCatching { secureSocket.close() }
        }

        val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SHAREME_BT_SERVICE_UUID)
        insecureSocket.connect()
        return insecureSocket
    }

    private suspend fun streamSendFiles(
        rawOutput: OutputStream,
        rawInput: InputStream,
        files: List<ShareableFile>,
        peerName: String,
        senderDeviceName: String,
        pinCode: String,
        transportLabel: String,
        chunkSize: Int
    ) {
        val startTime = System.currentTimeMillis()
        var totalSentBytes = 0L
        val grandTotalBytes = files.sumOf { it.sizeBytes.coerceAtLeast(1L) }

        val output = DataOutputStream(BufferedOutputStream(rawOutput, chunkSize))
        val input = DataInputStream(BufferedInputStream(rawInput))

        output.write(SHAREME_PROTOCOL_MAGIC.toByteArray(Charsets.UTF_8))
        output.writeUTF(senderDeviceName)
        output.writeUTF(pinCode)
        output.writeInt(files.size)
        output.writeLong(grandTotalBytes)
        output.writeUTF(files.first().name)
        output.flush()

        _sessionState.value = TransferSessionState.WaitingApproval(
            peerName = peerName,
            fileCount = files.size,
            totalBytes = grandTotalBytes
        )

        val decision = input.readByte().toInt()
        if (decision == 2) {
            _sessionState.value = TransferSessionState.Error("Incorrect Session PIN")
            return
        }
        if (decision != 1) {
            _sessionState.value = TransferSessionState.Error("$peerName declined the transfer")
            return
        }

        val buffer = ByteArray(chunkSize)

        files.forEachIndexed { index, file ->
            if (!scope.isActive) throw CancellationException()

            output.writeUTF(file.name)
            output.writeUTF(file.mimeType)
            output.writeLong(file.sizeBytes)

            val inputStream = context.contentResolver.openInputStream(file.uri)
                ?: throw IllegalStateException("Cannot read ${file.name}")

            var fileBytesSent = 0L
            var lastUiUpdate = 0L

            inputStream.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    fileBytesSent += read
                    totalSentBytes += read

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 120L) {
                        lastUiUpdate = now
                        val elapsedSec = ((now - startTime).coerceAtLeast(1L)) / 1000.0
                        val speed = (totalSentBytes / elapsedSec).toLong()
                        val remainingBytes = (grandTotalBytes - totalSentBytes).coerceAtLeast(0L)
                        val eta = if (speed > 0L) remainingBytes / speed else 0L
                        _sessionState.value = TransferSessionState.Transferring(
                            isSending = true,
                            peerName = peerName,
                            currentFileName = file.name,
                            currentFileIndex = index + 1,
                            totalFiles = files.size,
                            bytesTransferred = totalSentBytes,
                            totalBytes = grandTotalBytes,
                            speedBytesPerSec = speed,
                            etaSeconds = eta
                        )
                    }
                }
            }
            output.flush()

            val ack = input.readByte().toInt()
            if (ack != 0x06) {
                throw IllegalStateException("Receiver failed to save ${file.name}")
            }

            val elapsedSec = ((System.currentTimeMillis() - startTime).coerceAtLeast(1L)) / 1000.0
            val avgSpeed = (totalSentBytes / elapsedSec).toLong()

            transferRepository.recordTransfer(
                fileName = file.name,
                mimeType = file.mimeType,
                fileSizeBytes = fileBytesSent,
                direction = TransferDirection.SENT,
                status = TransferStatus.COMPLETED,
                peerName = peerName,
                transportMethod = transportLabel,
                localUri = file.uri.toString(),
                averageSpeedBytesPerSec = avgSpeed
            )
        }

        val elapsedSec = ((System.currentTimeMillis() - startTime).coerceAtLeast(1L)) / 1000.0
        val finalSpeed = (totalSentBytes / elapsedSec).toLong()
        _sessionState.value = TransferSessionState.Completed(
            isSending = true,
            peerName = peerName,
            fileCount = files.size,
            totalBytes = totalSentBytes,
            averageSpeedBytesPerSec = finalSpeed,
            lastSavedUri = null
        )
    }

    private suspend fun handleIncomingStreams(
        rawInput: InputStream,
        rawOutput: OutputStream,
        transportLabel: String
    ) {
        val wifiLock = if (transportLabel != "Bluetooth") acquireWifiLock() else null
        try {
            val input = DataInputStream(BufferedInputStream(rawInput, 65_536))
            val output = DataOutputStream(BufferedOutputStream(rawOutput))

            val magicBytes = ByteArray(SHAREME_PROTOCOL_MAGIC.length)
            input.readFully(magicBytes)
            if (String(magicBytes, Charsets.UTF_8) != SHAREME_PROTOCOL_MAGIC) {
                return
            }

            val senderName = input.readUTF()
            val suppliedPin = input.readUTF()
            val fileCount = input.readInt().coerceIn(0, 500)
            val totalBytes = input.readLong().coerceAtLeast(0L)
            val previewFileName = input.readUTF()

            if (suppliedPin.isNotEmpty() && suppliedPin != activePin) {
                output.writeByte(2)
                output.flush()
                return
            }

            val pinVerified = suppliedPin.isNotEmpty() && suppliedPin == activePin
            val accepted = if (autoAcceptEnabled || pinVerified) {
                true
            } else {
                val deferred = CompletableDeferred<Boolean>()
                pendingApprovalDeferred = deferred
                _incomingOffer.value = IncomingTransferOffer(
                    offerId = UUID.randomUUID().toString(),
                    senderName = senderName,
                    fileCount = fileCount,
                    totalBytes = totalBytes,
                    previewFileName = previewFileName,
                    transportLabel = transportLabel
                )
                val result = withTimeoutOrNull(60_000L) { deferred.await() } ?: false
                _incomingOffer.value = null
                pendingApprovalDeferred = null
                result
            }

            if (!accepted) {
                output.writeByte(0)
                output.flush()
                return
            }

            output.writeByte(1)
            output.flush()

            val startTime = System.currentTimeMillis()
            var totalReceivedBytes = 0L
            var lastSavedUri: String? = null
            val buffer = ByteArray(65_536)

            for (index in 0 until fileCount) {
                val fileName = input.readUTF()
                val mimeType = input.readUTF()
                val fileSize = input.readLong().coerceAtLeast(0L)

                val destination = FileSaver.openDestination(context, fileName, mimeType)
                    ?: throw IllegalStateException("Cannot allocate storage for $fileName")

                var remaining = fileSize
                var writeSuccess = false
                var lastUiUpdate = 0L

                try {
                    while (remaining > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) throw IllegalStateException("Unexpected stream end")
                        destination.outputStream.write(buffer, 0, read)
                        remaining -= read
                        totalReceivedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate >= 120L) {
                            lastUiUpdate = now
                            val elapsedSec = ((now - startTime).coerceAtLeast(1L)) / 1000.0
                            val speed = (totalReceivedBytes / elapsedSec).toLong()
                            val eta = if (speed > 0L) {
                                ((totalBytes - totalReceivedBytes).coerceAtLeast(0L)) / speed
                            } else 0L
                            _sessionState.value = TransferSessionState.Transferring(
                                isSending = false,
                                peerName = "$senderName ($transportLabel)",
                                currentFileName = fileName,
                                currentFileIndex = index + 1,
                                totalFiles = fileCount,
                                bytesTransferred = totalReceivedBytes,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed,
                                etaSeconds = eta
                            )
                        }
                    }
                    destination.outputStream.flush()
                    writeSuccess = true
                } finally {
                    destination.finalizeWrite(writeSuccess)
                }

                lastSavedUri = destination.uri.toString()
                output.writeByte(0x06)
                output.flush()

                val elapsedSec = ((System.currentTimeMillis() - startTime).coerceAtLeast(1L)) / 1000.0
                val avgSpeed = (totalReceivedBytes / elapsedSec).toLong()

                transferRepository.recordTransfer(
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = fileSize,
                    direction = TransferDirection.RECEIVED,
                    status = TransferStatus.COMPLETED,
                    peerName = senderName,
                    transportMethod = transportLabel,
                    localUri = lastSavedUri,
                    averageSpeedBytesPerSec = avgSpeed
                )
            }

            val elapsedSec = ((System.currentTimeMillis() - startTime).coerceAtLeast(1L)) / 1000.0
            val finalSpeed = (totalReceivedBytes / elapsedSec).toLong()

            _sessionState.value = TransferSessionState.Completed(
                isSending = false,
                peerName = senderName,
                fileCount = fileCount,
                totalBytes = totalReceivedBytes,
                averageSpeedBytesPerSec = finalSpeed,
                lastSavedUri = lastSavedUri
            )
        } catch (e: Exception) {
            _sessionState.value = TransferSessionState.Error(
                e.localizedMessage ?: "Incoming $transportLabel transfer interrupted"
            )
        } finally {
            runCatching { wifiLock?.release() }
        }
    }

    fun cancelActiveTransfer() {
        activeTransferJob?.cancel()
        runCatching { activeClientSocket?.close() }
        activeClientSocket = null
        runCatching { activeBtClientSocket?.close() }
        activeBtClientSocket = null
        _sessionState.value = TransferSessionState.Idle
    }

    fun resetSessionState() {
        if (_sessionState.value !is TransferSessionState.Transferring) {
            _sessionState.value = TransferSessionState.Idle
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock(): WifiManager.WifiLock? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return runCatching {
            wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ShareMe:TransferLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun resolveOwnBluetoothAddress(): String? {
        val adapter = bluetoothAdapter ?: return null
        if (!adapter.isEnabled || !LocalNetworkUtils.hasBluetoothConnectPermission(context)) {
            return null
        }
        val raw = runCatching { adapter.address }.getOrNull()?.trim() ?: return null
        if (raw.isEmpty() || raw == "02:00:00:00:00:00") return null
        return raw
    }

    private fun generatePin(): String = Random.nextInt(1000, 9999).toString()
}
