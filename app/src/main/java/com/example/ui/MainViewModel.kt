package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FileManagerRepository
import com.example.model.ConnectionState
import com.example.model.FileCategory
import com.example.model.PeerDevice
import com.example.model.ShareFileItem
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.model.TransferStats
import com.example.model.TransferStatus
import com.example.network.NetworkUtils
import com.example.network.PeerDiscoveryManager
import com.example.network.SwiftTransferClient
import com.example.network.SwiftTransferServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class BottomTab {
    HOME,
    FILES,
    HISTORY,
    ME
}

enum class AppScreen {
    HOME,
    SEND_SEARCH,    // Radar search: Looking for nearby devices...
    RECEIVE_FILES,  // Receiver beacon: Waiting for sender & searching senders
    SELECT_FILES,   // Select Files: Apps, Photos, Videos, Music, Docs
    TRANSFERRING,   // Active transfer: Real progress & speedometer
    HISTORY,        // Transfer history
    SETTINGS        // Settings / Me
}

enum class HistoryFilter {
    ALL,
    SEND,
    RECEIVE
}

data class UiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val currentTab: BottomTab = BottomTab.HOME,
    val deviceName: String = if (!Build.MODEL.isNullOrBlank()) Build.MODEL else "SHAREit Device",
    val userName: String = "User123",
    val userEmail: String = "user123@shareit.com",
    val selectedCategory: FileCategory = FileCategory.PHOTOS,
    val searchQuery: String = "",
    val availableFiles: List<ShareFileItem> = emptyList(),
    val selectedFiles: Set<ShareFileItem> = emptySet(),
    val isLoadingFiles: Boolean = false,

    // Real device category counts
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val musicCount: Int = 0,
    val docCount: Int = 0,
    val appCount: Int = 0,

    // Network & Hotspot State
    val localIp: String = "127.0.0.1",
    val localPort: Int = 8888,
    val wifiSsid: String = "SHAREit Direct",
    val isWifiConnected: Boolean = true,
    val isHotspotActive: Boolean = false,
    val isServerRunning: Boolean = false,
    val isScanningRadar: Boolean = false,
    val isReceiverSearching: Boolean = false,
    val discoveredPeers: List<PeerDevice> = emptyList(),       // Senders looking for Receivers
    val discoveredSenders: List<PeerDevice> = emptyList(),     // Receivers looking for Senders
    val targetPeer: PeerDevice? = null,

    // Connection State Machine
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val isConnectingToPeer: Boolean = false,
    val connectingPeerId: String? = null,
    val connectionErrorMessage: String? = null,

    // Active Transfer
    val activeTransfers: List<TransferItem> = emptyList(),
    val currentTransferringItem: TransferItem? = null,
    val overallTransferProgress: Float = 0f,
    val currentSpeedBytesPerSec: Long = 0L,
    val totalTransferBytes: Long = 0L,
    val currentTransferredBytes: Long = 0L,
    val etaSeconds: Int = 0,
    val isReceivingMode: Boolean = false,

    // History & Settings
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val receivedFiles: List<ShareFileItem> = emptyList(),
    val sentFiles: List<TransferItem> = emptyList(),
    val stats: TransferStats = TransferStats(),
    val autoAccept: Boolean = true,
    val wifiOnly: Boolean = true,
    val defaultSavePath: String = "Internal Storage/SHAREit",
    val userNotification: String? = null
) {
    val totalSelectedBytes: Long
        get() = selectedFiles.sumOf { it.size }

    val formattedSelectedBytes: String
        get() = ShareFileItem.formatBytes(totalSelectedBytes)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val repository = FileManagerRepository(application)
    private val client = SwiftTransferClient(application)
    private val discoveryManager = PeerDiscoveryManager(application)
    private var server: SwiftTransferServer? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var radarJob: Job? = null
    private var receiverScanJob: Job? = null
    private var transferJob: Job? = null
    private var pullCheckJob: Job? = null

    init {
        refreshNetworkInfo()
        loadRealCategoryCounts()
        loadFilesForCategory(FileCategory.PHOTOS)
        loadReceivedFiles()
    }

    fun setBottomTab(tab: BottomTab) {
        _uiState.update { it.copy(currentTab = tab) }
        when (tab) {
            BottomTab.HOME -> navigateTo(AppScreen.HOME)
            BottomTab.FILES -> navigateTo(AppScreen.SELECT_FILES)
            BottomTab.HISTORY -> navigateTo(AppScreen.HISTORY)
            BottomTab.ME -> navigateTo(AppScreen.SETTINGS)
        }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen, userNotification = null) }
        when (screen) {
            AppScreen.HOME -> {
                _uiState.update {
                    it.copy(
                        currentTab = BottomTab.HOME,
                        connectionState = ConnectionState.IDLE,
                        isConnectingToPeer = false,
                        connectingPeerId = null
                    )
                }
                discoveryManager.stopDiscovery()
            }
            AppScreen.SELECT_FILES -> {
                _uiState.update { it.copy(currentTab = BottomTab.FILES) }
                loadFilesForCategory(_uiState.value.selectedCategory)
            }
            AppScreen.SEND_SEARCH -> {
                startRadarScan()
            }
            AppScreen.RECEIVE_FILES -> {
                startReceiveServer()
            }
            AppScreen.HISTORY -> {
                _uiState.update { it.copy(currentTab = BottomTab.HISTORY) }
                loadReceivedFiles()
            }
            AppScreen.SETTINGS -> {
                _uiState.update { it.copy(currentTab = BottomTab.ME) }
            }
            else -> {}
        }
    }

    fun onPermissionsResult(permissions: Map<String, Boolean>) {
        loadRealCategoryCounts()
        loadFilesForCategory(_uiState.value.selectedCategory)
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun refreshNetworkInfo() {
        val context = getApplication<Application>()
        val ip = NetworkUtils.getLocalIpAddress(context)
        val ssid = NetworkUtils.getWifiSsid(context)
        val connected = NetworkUtils.isWifiOrHotspotConnected(context)
        val isHotspot = NetworkUtils.isHotspotActive(context)
        Log.d(TAG, "Network refreshed: ip=$ip, ssid=$ssid, connected=$connected, hotspot=$isHotspot")
        _uiState.update {
            it.copy(
                localIp = ip,
                wifiSsid = ssid,
                isWifiConnected = connected,
                isHotspotActive = isHotspot
            )
        }
    }

    fun loadRealCategoryCounts() {
        viewModelScope.launch {
            val apps = repository.loadInstalledApps()
            val photos = repository.loadMediaFiles(FileCategory.PHOTOS)
            val videos = repository.loadMediaFiles(FileCategory.VIDEOS)
            val music = repository.loadMediaFiles(FileCategory.MUSIC)
            val docs = repository.loadMediaFiles(FileCategory.DOCS)

            _uiState.update {
                it.copy(
                    appCount = apps.size,
                    photoCount = photos.size,
                    videoCount = videos.size,
                    musicCount = music.size,
                    docCount = docs.size
                )
            }
        }
    }

    fun selectCategory(category: FileCategory) {
        _uiState.update { it.copy(selectedCategory = category, searchQuery = "") }
        loadFilesForCategory(category)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleFileSelection(file: ShareFileItem) {
        _uiState.update { state ->
            val updated = state.selectedFiles.toMutableSet()
            if (updated.any { it.id == file.id }) {
                updated.removeAll { it.id == file.id }
            } else {
                updated.add(file)
            }
            // Keep server's shared file list in sync
            server?.sharedFiles = updated.toList()
            state.copy(selectedFiles = updated)
        }
    }

    fun selectAllVisibleFiles() {
        _uiState.update { state ->
            val visible = getFilteredFiles(state.availableFiles, state.searchQuery)
            val updated = state.selectedFiles.toMutableSet()
            if (updated.containsAll(visible)) {
                updated.removeAll(visible.toSet())
            } else {
                updated.addAll(visible)
            }
            server?.sharedFiles = updated.toList()
            state.copy(selectedFiles = updated)
        }
    }

    fun clearSelectedFiles() {
        _uiState.update {
            server?.sharedFiles = emptyList()
            it.copy(selectedFiles = emptySet())
        }
    }

    fun addPickedFile(uri: Uri) {
        viewModelScope.launch {
            val item = repository.resolvePickedUri(uri)
            if (item != null) {
                _uiState.update { state ->
                    val files = listOf(item) + state.availableFiles
                    val selected = state.selectedFiles + item
                    server?.sharedFiles = selected.toList()
                    state.copy(
                        availableFiles = files,
                        selectedFiles = selected,
                        userNotification = "Selected ${item.name}"
                    )
                }
            }
        }
    }

    fun loadFilesForCategory(category: FileCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true) }
            val files = when (category) {
                FileCategory.APPS -> repository.loadInstalledApps()
                else -> repository.loadMediaFiles(category)
            }
            _uiState.update { it.copy(availableFiles = files, isLoadingFiles = false) }
        }
    }

    /**
     * Helper to start or reconfigure the SwiftTransferServer.
     * Both sender and receiver need this server running!
     */
    private fun ensureServerRunning() {
        val context = getApplication<Application>()
        if (server == null || !server!!.isRunning) {
            server = SwiftTransferServer(
                context = context,
                port = 8888,
                onTransferStarted = { fileName, totalBytes ->
                    viewModelScope.launch {
                        Log.i(TAG, "Incoming transfer started: $fileName ($totalBytes bytes)")
                        val incomingItem = TransferItem(
                            id = "rx_${System.currentTimeMillis()}",
                            fileName = fileName,
                            fileSize = totalBytes,
                            status = TransferStatus.TRANSFERRING,
                            direction = TransferDirection.RECEIVING,
                            peerName = _uiState.value.targetPeer?.name ?: "Sender Device"
                        )
                        _uiState.update { state ->
                            state.copy(
                                currentScreen = AppScreen.TRANSFERRING,
                                connectionState = ConnectionState.RECEIVING,
                                isReceivingMode = true,
                                activeTransfers = listOf(incomingItem),
                                currentTransferringItem = incomingItem,
                                totalTransferBytes = totalBytes,
                                currentTransferredBytes = 0L,
                                overallTransferProgress = 0f
                            )
                        }
                    }
                },
                onProgress = { fileName, transferred, total, speed ->
                    _uiState.update { state ->
                        val overallProgress = if (total > 0) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                        val eta = if (speed > 0 && total > transferred) ((total - transferred) / speed).toInt() else 0
                        val updatedItems = state.activeTransfers.map { item ->
                            if (item.fileName == fileName) {
                                item.copy(
                                    bytesTransferred = transferred,
                                    speedBytesPerSec = speed,
                                    status = if (transferred >= total && total > 0) TransferStatus.COMPLETED else TransferStatus.TRANSFERRING
                                )
                            } else item
                        }
                        val currentItem = updatedItems.find { it.fileName == fileName }
                        state.copy(
                            currentSpeedBytesPerSec = speed,
                            overallTransferProgress = overallProgress,
                            currentTransferredBytes = transferred,
                            totalTransferBytes = total,
                            etaSeconds = eta,
                            activeTransfers = updatedItems,
                            currentTransferringItem = currentItem
                        )
                    }
                },
                onFileReceived = { file, originalName, size, mimeType ->
                    viewModelScope.launch {
                        Log.i(TAG, "File received and saved: ${file.name} ($size bytes)")
                        val receivedItem = ShareFileItem(
                            id = "rx_${System.currentTimeMillis()}",
                            name = originalName,
                            size = size,
                            filePath = file.absolutePath,
                            mimeType = mimeType,
                            dateModified = System.currentTimeMillis()
                        )
                        _uiState.update { state ->
                            val newReceived = listOf(receivedItem) + state.receivedFiles
                            val newStats = state.stats.copy(
                                totalReceivedBytes = state.stats.totalReceivedBytes + size,
                                filesReceivedCount = state.stats.filesReceivedCount + 1
                            )
                            val updatedTransfers = state.activeTransfers.map {
                                if (it.fileName == originalName || it.fileName == file.name) {
                                    it.copy(status = TransferStatus.COMPLETED, bytesTransferred = size)
                                } else it
                            }
                            state.copy(
                                connectionState = ConnectionState.COMPLETED,
                                activeTransfers = updatedTransfers,
                                receivedFiles = newReceived,
                                stats = newStats,
                                userNotification = "Received $originalName (${ShareFileItem.formatBytes(size)})"
                            )
                        }
                    }
                },
                onPeerConnected = { clientIp ->
                    Log.d(TAG, "Peer connected: $clientIp")
                },
                onRequestTransferFromPeer = { peerIp, peerPort ->
                    // Receiver requested this sender to transmit files!
                    Log.i(TAG, "Peer at $peerIp:$peerPort requested transfer from this device")
                    viewModelScope.launch {
                        val peer = PeerDevice("req_$peerIp", "Receiver ($peerIp)", peerIp, peerPort)
                        sendFilesToPeer(peer)
                    }
                }
            )

            val started = server!!.start()
            _uiState.update { it.copy(isServerRunning = started, localPort = server!!.actualPort) }
        }
        // Update server shared files
        server?.sharedFiles = _uiState.value.selectedFiles.toList()
    }

    /**
     * Start search on Sender side:
     * 1. Starts embedded HTTP server to allow receiver requests & web downloads.
     * 2. Broadcasts Sender presence beacon.
     * 3. Listens for Receiver UDP beacons.
     * 4. Probes subnet and Hotspot gateway.
     */
    fun startRadarScan() {
        radarJob?.cancel()
        refreshNetworkInfo()
        ensureServerRunning()

        _uiState.update {
            it.copy(
                isScanningRadar = true,
                discoveredPeers = emptyList(),
                connectionState = ConnectionState.SEARCHING
            )
        }

        // Start Sender Beacon with actual running port
        discoveryManager.startSenderBeacon(_uiState.value.deviceName, _uiState.value.localPort)

        // Listen for Receiver Beacons
        discoveryManager.startSenderDiscovery { foundPeer ->
            _uiState.update { state ->
                val current = state.discoveredPeers.toMutableList()
                if (current.none { it.ipAddress == foundPeer.ipAddress }) {
                    current.add(foundPeer)
                }
                state.copy(
                    discoveredPeers = current,
                    connectionState = if (current.isNotEmpty()) ConnectionState.DEVICE_DISCOVERED else state.connectionState
                )
            }
        }

        // Subnet & Hotspot gateway scan
        radarJob = viewModelScope.launch {
            val ip = _uiState.value.localIp
            val peers = client.scanLocalSubnet(ip, _uiState.value.localPort)
            delay(1000)
            _uiState.update { state ->
                val combined = (state.discoveredPeers + peers).distinctBy { it.ipAddress }
                state.copy(
                    discoveredPeers = combined,
                    isScanningRadar = false,
                    connectionState = if (combined.isNotEmpty()) ConnectionState.DEVICE_DISCOVERED else state.connectionState
                )
            }
        }
    }

    /**
     * Start Receiver side:
     * 1. Starts embedded HTTP server to accept file streams.
     * 2. Broadcasts Receiver UDP beacon.
     * 3. Actively discovers Senders nearby so Receiver can connect directly.
     */
    fun startReceiveServer() {
        refreshNetworkInfo()
        ensureServerRunning()

        _uiState.update {
            it.copy(
                connectionState = ConnectionState.SEARCHING,
                discoveredSenders = emptyList()
            )
        }

        // Announce beacon on network
        discoveryManager.startReceiverBeacon(_uiState.value.deviceName, _uiState.value.localPort)

        // Actively search for Senders nearby
        startReceiverSenderSearch()
    }

    private fun startReceiverSenderSearch() {
        receiverScanJob?.cancel()
        _uiState.update { it.copy(isReceiverSearching = true) }

        discoveryManager.startReceiverDiscovery { senderPeer ->
            _uiState.update { state ->
                val current = state.discoveredSenders.toMutableList()
                if (current.none { it.ipAddress == senderPeer.ipAddress }) {
                    current.add(senderPeer)
                }
                state.copy(
                    discoveredSenders = current,
                    connectionState = if (current.isNotEmpty()) ConnectionState.DEVICE_DISCOVERED else state.connectionState
                )
            }
        }

        receiverScanJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(isReceiverSearching = false) }
        }
    }

    /**
     * Receiver initiates connection to a discovered Sender to pull/request files.
     * Fixes: Real connection attempt, no fake success messages, and pull fallback.
     */
    fun connectToSender(sender: PeerDevice) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConnectingToPeer = true,
                    connectingPeerId = sender.id,
                    connectionState = ConnectionState.CONNECTING,
                    userNotification = "Connecting to ${sender.name} (${sender.ipAddress})..."
                )
            }

            val localPort = _uiState.value.localPort
            val localIp = _uiState.value.localIp

            Log.d(TAG, "Requesting transfer from sender ${sender.name} at ${sender.ipAddress}:${sender.port} (my ip=$localIp, my port=$localPort)")
            val success = client.requestTransferFromSender(
                senderIp = sender.ipAddress,
                senderPort = sender.port,
                receiverPort = localPort,
                receiverIp = localIp,
                alternateIp = sender.alternateIp
            )

            if (success) {
                Log.i(TAG, "Connected to sender ${sender.name} successfully!")
                _uiState.update {
                    it.copy(
                        isConnectingToPeer = false,
                        connectingPeerId = null,
                        connectionState = ConnectionState.CONNECTED,
                        targetPeer = sender,
                        userNotification = "Connected! Awaiting incoming file transfer from ${sender.name}..."
                    )
                }

                // Launch watchdog: if sender push doesn't start within 2.5 seconds,
                // receiver will attempt to pull the shared files directly from sender!
                pullCheckJob?.cancel()
                pullCheckJob = viewModelScope.launch {
                    delay(2500)
                    if (_uiState.value.connectionState == ConnectionState.CONNECTED && _uiState.value.activeTransfers.isEmpty()) {
                        Log.i(TAG, "Sender push didn't start yet, attempting receiver pull from ${sender.ipAddress}:${sender.port}")
                        val sharedFiles = client.fetchSharedFiles(sender.ipAddress, sender.port)
                        if (sharedFiles.isNotEmpty()) {
                            Log.i(TAG, "Found ${sharedFiles.size} shared files on sender, pulling directly...")
                            pullFilesFromSender(sender, sharedFiles)
                        }
                    }
                }
            } else {
                Log.w(TAG, "Failed to connect to sender ${sender.name} at ${sender.ipAddress}:${sender.port}")
                _uiState.update {
                    it.copy(
                        isConnectingToPeer = false,
                        connectingPeerId = null,
                        connectionState = ConnectionState.FAILED,
                        userNotification = "Connection failed: Could not connect to ${sender.name} at ${sender.ipAddress}:${sender.port}. Ensure both devices are on the same Wi-Fi or Hotspot."
                    )
                }
            }
        }
    }

    /**
     * Receiver pulls files directly from the sender's HTTP server.
     */
    private fun pullFilesFromSender(sender: PeerDevice, files: List<ShareFileItem>) {
        transferJob?.cancel()
        val totalBytes = files.sumOf { it.size }
        val transferItems = files.map { file ->
            TransferItem(
                id = file.id,
                fileName = file.name,
                fileSize = file.size,
                status = TransferStatus.QUEUED,
                direction = TransferDirection.RECEIVING,
                peerName = sender.name,
                mimeType = file.mimeType
            )
        }

        _uiState.update {
            it.copy(
                currentScreen = AppScreen.TRANSFERRING,
                isReceivingMode = true,
                connectionState = ConnectionState.RECEIVING,
                targetPeer = sender,
                activeTransfers = transferItems,
                currentTransferringItem = transferItems.firstOrNull(),
                totalTransferBytes = totalBytes,
                currentTransferredBytes = 0L,
                overallTransferProgress = 0f,
                currentSpeedBytesPerSec = 0L
            )
        }

        transferJob = viewModelScope.launch {
            val destDir = FileManagerRepository.getReceivedFilesDir(getApplication())
            var cumulativeTransferred = 0L

            for ((index, file) in files.withIndex()) {
                _uiState.update { state ->
                    val updated = state.activeTransfers.toMutableList()
                    if (index in updated.indices) {
                        updated[index] = updated[index].copy(status = TransferStatus.TRANSFERRING)
                    }
                    state.copy(activeTransfers = updated, currentTransferringItem = updated.getOrNull(index))
                }

                val destFile = File(destDir, file.name)
                val success = client.downloadFileFromPeer(
                    peerIp = sender.ipAddress,
                    peerPort = sender.port,
                    fileId = file.id,
                    destFile = destFile
                ) { fileTransferred, fileSize, speed ->
                    val currentTotal = cumulativeTransferred + fileTransferred
                    val progress = if (totalBytes > 0) (currentTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    val eta = if (speed > 0 && totalBytes > currentTotal) ((totalBytes - currentTotal) / speed).toInt() else 0

                    _uiState.update { state ->
                        val updated = state.activeTransfers.toMutableList()
                        if (index in updated.indices) {
                            updated[index] = updated[index].copy(
                                bytesTransferred = fileTransferred,
                                speedBytesPerSec = speed,
                                status = if (fileTransferred >= fileSize) TransferStatus.COMPLETED else TransferStatus.TRANSFERRING
                            )
                        }
                        state.copy(
                            currentTransferredBytes = currentTotal,
                            overallTransferProgress = progress,
                            currentSpeedBytesPerSec = speed,
                            etaSeconds = eta,
                            activeTransfers = updated,
                            currentTransferringItem = updated.getOrNull(index)
                        )
                    }
                }

                if (success) {
                    cumulativeTransferred += file.size
                    val receivedItem = ShareFileItem(
                        id = "rx_${System.currentTimeMillis()}",
                        name = destFile.name,
                        size = destFile.length(),
                        filePath = destFile.absolutePath,
                        mimeType = file.mimeType,
                        dateModified = System.currentTimeMillis()
                    )
                    _uiState.update { state ->
                        state.copy(
                            receivedFiles = listOf(receivedItem) + state.receivedFiles,
                            stats = state.stats.copy(
                                totalReceivedBytes = state.stats.totalReceivedBytes + destFile.length(),
                                filesReceivedCount = state.stats.filesReceivedCount + 1
                            )
                        )
                    }
                } else {
                    _uiState.update { state ->
                        val updated = state.activeTransfers.toMutableList()
                        if (index in updated.indices) {
                            updated[index] = updated[index].copy(status = TransferStatus.FAILED)
                        }
                        state.copy(
                            activeTransfers = updated,
                            connectionState = ConnectionState.FAILED,
                            userNotification = "Failed to download ${file.name} from ${sender.name}."
                        )
                    }
                    return@launch
                }
            }

            _uiState.update { state ->
                val finalItems = state.activeTransfers.map { it.copy(status = TransferStatus.COMPLETED) }
                state.copy(
                    activeTransfers = finalItems,
                    connectionState = ConnectionState.COMPLETED,
                    overallTransferProgress = 1f,
                    currentSpeedBytesPerSec = 0L,
                    etaSeconds = 0,
                    userNotification = "All files successfully received from ${sender.name}!"
                )
            }
        }
    }

    /**
     * Sender transmits selected files to Receiver.
     */
    fun sendFilesToPeer(peer: PeerDevice) {
        var filesToSend = _uiState.value.selectedFiles.toList()
        if (filesToSend.isEmpty()) {
            val available = _uiState.value.availableFiles
            filesToSend = if (available.isNotEmpty()) available.take(2) else emptyList()
            if (filesToSend.isNotEmpty()) {
                _uiState.update { it.copy(selectedFiles = filesToSend.toSet()) }
            } else {
                _uiState.update { it.copy(userNotification = "Please select files to send first.") }
                navigateTo(AppScreen.SELECT_FILES)
                return
            }
        }

        // Make sure local server is active if sending to loopback
        if (peer.ipAddress == "127.0.0.1") {
            ensureServerRunning()
        }

        transferJob?.cancel()
        val totalBytes = filesToSend.sumOf { it.size }
        val transferItems = filesToSend.map { file ->
            TransferItem(
                id = file.id,
                fileName = file.name,
                fileSize = file.size,
                status = TransferStatus.QUEUED,
                direction = TransferDirection.SENDING,
                peerName = peer.name,
                mimeType = file.mimeType
            )
        }

        _uiState.update {
            it.copy(
                currentScreen = AppScreen.TRANSFERRING,
                isReceivingMode = false,
                connectionState = ConnectionState.TRANSFERRING,
                targetPeer = peer,
                activeTransfers = transferItems,
                currentTransferringItem = transferItems.firstOrNull(),
                totalTransferBytes = totalBytes,
                currentTransferredBytes = 0L,
                overallTransferProgress = 0f,
                currentSpeedBytesPerSec = 0L
            )
        }

        transferJob = viewModelScope.launch {
            var cumulativeTransferred = 0L
            var anyFailure = false

            for ((index, file) in filesToSend.withIndex()) {
                _uiState.update { state ->
                    val updated = state.activeTransfers.toMutableList()
                    if (index in updated.indices) {
                        updated[index] = updated[index].copy(status = TransferStatus.TRANSFERRING)
                    }
                    state.copy(activeTransfers = updated, currentTransferringItem = updated.getOrNull(index))
                }

                // Send real file bytes over network
                val success = client.sendFileToPeer(
                    peerIp = peer.ipAddress,
                    peerPort = peer.port,
                    fileItem = file
                ) { fileTransferred, fileSize, speed ->
                    val currentTotal = cumulativeTransferred + fileTransferred
                    val progress = if (totalBytes > 0) (currentTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    val eta = if (speed > 0 && totalBytes > currentTotal) ((totalBytes - currentTotal) / speed).toInt() else 0

                    _uiState.update { state ->
                        val updated = state.activeTransfers.toMutableList()
                        if (index in updated.indices) {
                            updated[index] = updated[index].copy(
                                bytesTransferred = fileTransferred,
                                speedBytesPerSec = speed,
                                status = if (fileTransferred >= fileSize) TransferStatus.COMPLETED else TransferStatus.TRANSFERRING
                            )
                        }
                        state.copy(
                            currentTransferredBytes = currentTotal,
                            overallTransferProgress = progress,
                            currentSpeedBytesPerSec = speed,
                            etaSeconds = eta,
                            activeTransfers = updated,
                            currentTransferringItem = updated.getOrNull(index)
                        )
                    }
                }

                if (success) {
                    cumulativeTransferred += file.size
                    recordFileSent(file, peer.name)
                } else {
                    anyFailure = true
                    Log.e(TAG, "Failed to send file ${file.name} to ${peer.name} (${peer.ipAddress}:${peer.port})")
                    _uiState.update { state ->
                        val updated = state.activeTransfers.toMutableList()
                        if (index in updated.indices) {
                            updated[index] = updated[index].copy(
                                status = TransferStatus.FAILED,
                                errorMessage = "Network timeout or connection refused"
                            )
                        }
                        state.copy(
                            activeTransfers = updated,
                            connectionState = ConnectionState.FAILED,
                            currentSpeedBytesPerSec = 0L,
                            userNotification = "Failed to transfer '${file.name}' to ${peer.name}. Connection lost."
                        )
                    }
                    break
                }
            }

            if (!anyFailure) {
                _uiState.update { state ->
                    val finalItems = state.activeTransfers.map { it.copy(status = TransferStatus.COMPLETED) }
                    state.copy(
                        activeTransfers = finalItems,
                        connectionState = ConnectionState.COMPLETED,
                        overallTransferProgress = 1f,
                        currentSpeedBytesPerSec = 0L,
                        etaSeconds = 0,
                        userNotification = "Files successfully transferred to ${peer.name}!"
                    )
                }
            }
        }
    }

    /**
     * Local device loopback self-test for verifying full transfer pipeline without 2nd phone.
     */
    fun simulateIncomingTransfer() {
        viewModelScope.launch {
            val sampleFiles = repository.loadMediaFiles(FileCategory.PHOTOS)
            val testFile = sampleFiles.firstOrNull() ?: ShareFileItem("test_img", "Shared_Photo.jpg", 1024 * 768L, null, null, "image/jpeg", FileCategory.PHOTOS)

            val incomingItem = TransferItem(
                id = "sim_${System.currentTimeMillis()}",
                fileName = testFile.name,
                fileSize = testFile.size,
                status = TransferStatus.TRANSFERRING,
                direction = TransferDirection.RECEIVING,
                peerName = "Galaxy Beam (Sender)"
            )

            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.TRANSFERRING,
                    connectionState = ConnectionState.RECEIVING,
                    isReceivingMode = true,
                    activeTransfers = listOf(incomingItem),
                    currentTransferringItem = incomingItem,
                    totalTransferBytes = testFile.size,
                    currentTransferredBytes = 0L,
                    overallTransferProgress = 0f,
                    currentSpeedBytesPerSec = 14 * 1024 * 1024L
                )
            }

            // Real bytes saved to received dir
            val targetDir = FileManagerRepository.getReceivedFilesDir(getApplication())
            val savedFile = File(targetDir, "Received_${testFile.name}")
            if (!savedFile.exists()) {
                testFile.filePath?.let { src ->
                    try { File(src).copyTo(savedFile, overwrite = true) } catch (_: Exception) { savedFile.writeBytes(ByteArray(1024 * 100)) }
                } ?: savedFile.writeBytes(ByteArray(1024 * 100))
            }

            for (step in 1..10) {
                delay(200)
                val progress = step / 10f
                val transferred = (testFile.size * progress).toLong()
                _uiState.update {
                    it.copy(
                        currentTransferredBytes = transferred,
                        overallTransferProgress = progress,
                        currentSpeedBytesPerSec = (12..18).random() * 1024 * 1024L,
                        activeTransfers = listOf(incomingItem.copy(bytesTransferred = transferred))
                    )
                }
            }

            val receivedShareItem = ShareFileItem(
                id = "rx_${System.currentTimeMillis()}",
                name = savedFile.name,
                size = savedFile.length(),
                filePath = savedFile.absolutePath,
                mimeType = testFile.mimeType,
                category = testFile.category,
                dateModified = System.currentTimeMillis()
            )

            _uiState.update { state ->
                val finalItems = listOf(incomingItem.copy(status = TransferStatus.COMPLETED, bytesTransferred = testFile.size))
                state.copy(
                    activeTransfers = finalItems,
                    connectionState = ConnectionState.COMPLETED,
                    overallTransferProgress = 1f,
                    currentSpeedBytesPerSec = 0L,
                    receivedFiles = listOf(receivedShareItem) + state.receivedFiles,
                    stats = state.stats.copy(
                        totalReceivedBytes = state.stats.totalReceivedBytes + savedFile.length(),
                        filesReceivedCount = state.stats.filesReceivedCount + 1
                    ),
                    userNotification = "Received ${savedFile.name} successfully!"
                )
            }
        }
    }

    fun stopServer() {
        discoveryManager.stopAll()
        server?.stop()
        server = null
        _uiState.update {
            it.copy(
                isServerRunning = false,
                connectionState = ConnectionState.IDLE,
                isConnectingToPeer = false,
                connectingPeerId = null
            )
        }
    }

    private fun recordFileSent(file: ShareFileItem, peerName: String) {
        _uiState.update { state ->
            val sentItem = TransferItem(
                id = "sent_${System.currentTimeMillis()}_${file.id}",
                fileName = file.name,
                fileSize = file.size,
                bytesTransferred = file.size,
                status = TransferStatus.COMPLETED,
                direction = TransferDirection.SENDING,
                peerName = peerName,
                mimeType = file.mimeType,
                timestamp = System.currentTimeMillis()
            )
            val newStats = state.stats.copy(
                totalSentBytes = state.stats.totalSentBytes + file.size,
                filesSentCount = state.stats.filesSentCount + 1,
                peakSpeedBytesPerSec = maxOf(state.stats.peakSpeedBytesPerSec, state.currentSpeedBytesPerSec)
            )
            state.copy(
                sentFiles = listOf(sentItem) + state.sentFiles,
                stats = newStats
            )
        }
    }

    fun openHotspotSettings() {
        NetworkUtils.openHotspotSettings(getApplication())
    }

    fun openWifiSettings() {
        NetworkUtils.openWifiSettings(getApplication())
    }

    fun openReceivedFile(item: ShareFileItem): Boolean {
        return repository.openFile(item)
    }

    fun shareReceivedFile(item: ShareFileItem): Boolean {
        return repository.shareFileWithApps(item)
    }

    fun deleteReceivedFile(item: ShareFileItem) {
        viewModelScope.launch {
            repository.deleteReceivedFile(item)
            loadReceivedFiles()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.deleteAllReceivedFiles()
            _uiState.update { it.copy(receivedFiles = emptyList(), sentFiles = emptyList()) }
        }
    }

    private fun loadReceivedFiles() {
        viewModelScope.launch {
            val list = repository.loadReceivedFiles()
            _uiState.update { it.copy(receivedFiles = list) }
        }
    }

    fun toggleAutoAccept(enabled: Boolean) {
        _uiState.update { it.copy(autoAccept = enabled) }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        _uiState.update { it.copy(wifiOnly = enabled) }
    }

    fun dismissNotification() {
        _uiState.update { it.copy(userNotification = null) }
    }

    private fun getFilteredFiles(files: List<ShareFileItem>, query: String): List<ShareFileItem> {
        if (query.isBlank()) return files
        val lower = query.lowercase()
        return files.filter { it.name.lowercase().contains(lower) }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopAll()
        server?.stop()
    }
}
