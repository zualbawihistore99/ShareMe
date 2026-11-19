package com.example.transfer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NearbyDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val wifiP2pChannel: WifiP2pManager.Channel? =
        wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _wifiP2pEnabled = MutableStateFlow(false)
    val wifiP2pEnabled: StateFlow<Boolean> = _wifiP2pEnabled.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _wifiDirectConnection = MutableStateFlow<WifiP2pInfo?>(null)
    val wifiDirectConnection: StateFlow<WifiP2pInfo?> = _wifiDirectConnection.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var registeredServiceName: String? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var p2pReceiverRegistered = false
    private var btReceiverRegistered = false

    private val p2pReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )
                    _wifiP2pEnabled.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (LocalNetworkUtils.hasWifiDiscoveryPermission(context)) {
                        wifiP2pChannel?.let { channel ->
                            runCatching {
                                wifiP2pManager?.requestPeers(channel) { deviceList ->
                                    updateWifiDirectPeers(deviceList.deviceList.toList())
                                }
                            }
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO,
                            NetworkInfo::class.java
                        )
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    }

                    if (networkInfo?.isConnected == true) {
                        wifiP2pChannel?.let { channel ->
                            runCatching {
                                wifiP2pManager?.requestConnectionInfo(channel) { info ->
                                    _wifiDirectConnection.value = info
                                    markWifiDirectConnected(info)
                                }
                            }
                        }
                    } else {
                        _wifiDirectConnection.value = null
                    }
                }
            }
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _bluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                }

                BluetoothDevice.ACTION_FOUND -> {
                    if (!LocalNetworkUtils.hasBluetoothConnectPermission(context)) return
                    @Suppress("DEPRECATION")
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    } ?: return

                    val address = device.address ?: return
                    val name = runCatching { device.name }.getOrNull()
                    if (name.isNullOrBlank()) return

                    val peer = NearbyPeer(
                        id = "bt:$address",
                        name = name,
                        transport = PeerTransport.BLUETOOTH,
                        bluetoothAddress = address,
                        statusLabel = if (device.bondState == BluetoothDevice.BOND_BONDED) {
                            "Bluetooth • Paired"
                        } else {
                            "Bluetooth • Nearby"
                        }
                    )
                    _peers.update { existing ->
                        if (existing.any { it.id == peer.id }) existing else existing + peer
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Keep NSD/Wi-Fi Direct active if running
                }
            }
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun refreshRadioStates() {
        _bluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    fun startAdvertising(deviceName: String, port: Int) {
        stopAdvertising()
        val manager = nsdManager ?: return
        val serviceName = "ShareMe-$deviceName"
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SHAREME_NSD_SERVICE_TYPE
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                registeredServiceName = nsdServiceInfo.serviceName
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _statusMessage.value = "LAN advertising unavailable (code $errorCode)"
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stopAdvertising() {
        val listener = registrationListener ?: return
        registrationListener = null
        registeredServiceName = null
        runCatching { nsdManager?.unregisterService(listener) }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(
        preferWifiDirect: Boolean,
        transportPreference: TransportPreference = TransportPreference.AUTO_BOTH
    ) {
        registerP2pReceiverIfNeeded()
        registerBtReceiverIfNeeded()
        refreshRadioStates()

        _isScanning.value = true
        _statusMessage.value = null

        val useWifi = transportPreference == TransportPreference.AUTO_BOTH ||
            transportPreference == TransportPreference.WIFI_ONLY
        val useBluetooth = transportPreference == TransportPreference.AUTO_BOTH ||
            transportPreference == TransportPreference.BLUETOOTH_ONLY

        // Clear peers belonging to inactive transports
        _peers.update { list ->
            list.filter { peer ->
                when (peer.transport) {
                    PeerTransport.BLUETOOTH -> useBluetooth
                    PeerTransport.LOCAL_WIFI, PeerTransport.WIFI_DIRECT -> useWifi
                }
            }
        }

        val activeLabels = mutableListOf<String>()

        if (useWifi) {
            startNsdDiscovery()
            activeLabels.add("Wi-Fi LAN")
            if (preferWifiDirect && LocalNetworkUtils.hasWifiDiscoveryPermission(context)) {
                val channel = wifiP2pChannel
                val manager = wifiP2pManager
                if (manager != null && channel != null) {
                    runCatching {
                        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {}
                            override fun onFailure(reason: Int) {}
                        })
                    }
                    activeLabels.add("Wi-Fi Direct")
                }
            }
        } else {
            stopNsdDiscovery()
            runCatching {
                wifiP2pChannel?.let { wifiP2pManager?.stopPeerDiscovery(it, null) }
            }
        }

        if (useBluetooth) {
            startBluetoothDiscovery()?.let { btLabel ->
                activeLabels.add(btLabel)
            }
        } else {
            stopBluetoothDiscovery()
        }

        _statusMessage.value = when {
            !LocalNetworkUtils.hasDiscoveryPermission(context) ->
                "Grant Nearby Devices permission for Wi-Fi Direct & Bluetooth"
            activeLabels.isNotEmpty() ->
                "Scanning ${activeLabels.joinToString(" • ")}..."
            else ->
                "Enable Wi-Fi or Bluetooth to discover nearby phones"
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery(): String? {
        val adapter = bluetoothAdapter ?: return null
        if (!adapter.isEnabled) return null
        if (!LocalNetworkUtils.hasBluetoothConnectPermission(context)) return null

        // Surface paired Bluetooth devices immediately
        val bondedPeers = runCatching {
            adapter.bondedDevices?.mapNotNull { device ->
                val name = device.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val address = device.address ?: return@mapNotNull null
                NearbyPeer(
                    id = "bt:$address",
                    name = name,
                    transport = PeerTransport.BLUETOOTH,
                    bluetoothAddress = address,
                    statusLabel = "Bluetooth • Paired"
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())

        if (bondedPeers.isNotEmpty()) {
            _peers.update { existing ->
                val nonBt = existing.filterNot { it.id.startsWith("bt:") }
                val existingBt = existing.filter { it.id.startsWith("bt:") }
                val mergedBt = (bondedPeers + existingBt).distinctBy { it.id }
                nonBt + mergedBt
            }
        }

        if (LocalNetworkUtils.hasBluetoothScanPermission(context)) {
            runCatching {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                adapter.startDiscovery()
            }
        }
        return "Bluetooth"
    }

    @SuppressLint("MissingPermission")
    private fun stopBluetoothDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (LocalNetworkUtils.hasBluetoothScanPermission(context)) {
            runCatching {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun createWifiDirectReceiverGroup() {
        registerP2pReceiverIfNeeded()
        if (!LocalNetworkUtils.hasWifiDiscoveryPermission(context)) return
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        runCatching {
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _statusMessage.value = "Wi-Fi Direct group ready"
                }

                override fun onFailure(reason: Int) {}
            })
        }
    }

    @SuppressLint("MissingPermission")
    fun connectWifiDirectPeer(peer: NearbyPeer, onInitiated: (Boolean, String?) -> Unit) {
        val deviceAddress = peer.wifiP2pAddress ?: run {
            onInitiated(false, "Missing Wi-Fi Direct address")
            return
        }
        if (!LocalNetworkUtils.hasWifiDiscoveryPermission(context)) {
            onInitiated(false, "Nearby Devices permission is required")
            return
        }
        val manager = wifiP2pManager
        val channel = wifiP2pChannel
        if (manager == null || channel == null) {
            onInitiated(false, "Wi-Fi Direct is unavailable on this device")
            return
        }

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
        }
        runCatching {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onInitiated(true, null)
                }

                override fun onFailure(reason: Int) {
                    onInitiated(false, "Wi-Fi Direct connect failed (code $reason)")
                }
            })
        }.onFailure {
            onInitiated(false, it.localizedMessage)
        }
    }

    fun stopDiscovery() {
        _isScanning.value = false
        stopNsdDiscovery()
        stopBluetoothDiscovery()
        runCatching {
            wifiP2pChannel?.let { wifiP2pManager?.stopPeerDiscovery(it, null) }
        }
    }

    fun teardownWifiDirectGroup() {
        runCatching {
            wifiP2pChannel?.let { wifiP2pManager?.removeGroup(it, null) }
        }
        _wifiDirectConnection.value = null
    }

    fun cleanup() {
        stopDiscovery()
        stopAdvertising()
        if (p2pReceiverRegistered) {
            runCatching { context.unregisterReceiver(p2pReceiver) }
            p2pReceiverRegistered = false
        }
        if (btReceiverRegistered) {
            runCatching { context.unregisterReceiver(btReceiver) }
            btReceiverRegistered = false
        }
    }

    private fun registerP2pReceiverIfNeeded() {
        if (p2pReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            p2pReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        p2pReceiverRegistered = true
    }

    private fun registerBtReceiverIfNeeded() {
        if (btReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            context,
            btReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        btReceiverRegistered = true
    }

    private fun startNsdDiscovery() {
        stopNsdDiscovery()
        val manager = nsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("_shareme._tcp")) return
                if (service.serviceName == registeredServiceName) return
                resolveNsdService(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _peers.update { list ->
                    list.filterNot { it.id == "nsd:${service.serviceName}" }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(SHAREME_NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun stopNsdDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { nsdManager?.stopServiceDiscovery(listener) }
    }

    @Suppress("DEPRECATION")
    private fun resolveNsdService(serviceInfo: NsdServiceInfo) {
        val manager = nsdManager ?: return
        runCatching {
            manager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val hostIp = info.host?.hostAddress ?: return
                        if (hostIp == LocalNetworkUtils.getLocalIpv4Address()) return
                        val cleanName = info.serviceName.removePrefix("ShareMe-").ifBlank { info.serviceName }
                        val peer = NearbyPeer(
                            id = "nsd:${info.serviceName}",
                            name = cleanName,
                            transport = PeerTransport.LOCAL_WIFI,
                            ipAddress = hostIp,
                            port = info.port,
                            statusLabel = "Wi-Fi LAN • $hostIp",
                            isConnected = false
                        )
                        _peers.update { existing ->
                            existing.filterNot { it.id == peer.id } + peer
                        }
                    }
                }
            )
        }
    }

    private fun updateWifiDirectPeers(devices: List<WifiP2pDevice>) {
        val p2pPeers = devices.map { device ->
            val label = when (device.status) {
                WifiP2pDevice.CONNECTED -> "Connected • Wi-Fi Direct"
                WifiP2pDevice.INVITED -> "Inviting..."
                WifiP2pDevice.AVAILABLE -> "Wi-Fi Direct Ready"
                else -> "Wi-Fi Direct"
            }
            NearbyPeer(
                id = "p2p:${device.deviceAddress}",
                name = device.deviceName.ifBlank { "Nearby Phone" },
                transport = PeerTransport.WIFI_DIRECT,
                ipAddress = if (device.status == WifiP2pDevice.CONNECTED) {
                    _wifiDirectConnection.value?.groupOwnerAddress?.hostAddress
                } else null,
                port = DEFAULT_TRANSFER_PORT,
                wifiP2pAddress = device.deviceAddress,
                statusLabel = label,
                isConnected = device.status == WifiP2pDevice.CONNECTED
            )
        }
        _peers.update { existing ->
            val nonP2p = existing.filter { it.transport != PeerTransport.WIFI_DIRECT }
            nonP2p + p2pPeers
        }
    }

    private fun markWifiDirectConnected(info: WifiP2pInfo) {
        val hostIp = info.groupOwnerAddress?.hostAddress ?: return
        _peers.update { list ->
            list.map { peer ->
                if (peer.transport == PeerTransport.WIFI_DIRECT) {
                    peer.copy(
                        ipAddress = hostIp,
                        statusLabel = "Connected • $hostIp",
                        isConnected = true
                    )
                } else peer
            }
        }
    }
}
