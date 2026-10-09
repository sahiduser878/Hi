package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FileManagerRepository
import com.example.model.FileCategory
import com.example.model.PeerDevice
import com.example.model.ShareFileItem
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.model.TransferStats
import com.example.model.TransferStatus
import com.example.network.NetworkUtils
import com.example.network.SwiftTransferClient
import com.example.network.SwiftTransferServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BottomTab {
    HOME,
    FILES,
    HISTORY,
    ME
}

enum class AppScreen {
    HOME,
    SEND_SEARCH,    // Screen 2: Looking for nearby devices... + 2x2 category grid
    RECEIVE_FILES,  // Screen 3: Waiting for sender... + Device Name & Scan QR
    SELECT_FILES,   // Screen 4: Select Files with category tabs, search & checkboxes
    TRANSFERRING,   // Screen 5: Active phone-to-phone transfer
    HISTORY,        // Screen 6: Transfer history
    SETTINGS        // Screen 7: Settings / Me
}

enum class HistoryFilter {
    ALL,
    SEND,
    RECEIVE
}

data class UiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val currentTab: BottomTab = BottomTab.HOME,
    val deviceName: String = if (!Build.MODEL.isNullOrBlank()) Build.MODEL else "Android Device",
    val userName: String = "Sahid",
    val userEmail: String = "sahiduser878@shareit.com",
    val selectedCategory: FileCategory = FileCategory.PHOTOS,
    val searchQuery: String = "",
    val availableFiles: List<ShareFileItem> = emptyList(),
    val selectedFiles: Set<ShareFileItem> = emptySet(),
    val isLoadingFiles: Boolean = false,

    // Real category item counts
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val musicCount: Int = 0,
    val docCount: Int = 0,
    val appCount: Int = 0,

    // Network & Server
    val localIp: String = "127.0.0.1",
    val localPort: Int = 8888,
    val wifiSsid: String = "Wi-Fi Network",
    val isWifiConnected: Boolean = true,
    val isServerRunning: Boolean = false,
    val isScanningRadar: Boolean = false,
    val discoveredPeers: List<PeerDevice> = emptyList(),
    val targetPeer: PeerDevice? = null,

    // Active Transfer
    val activeTransfers: List<TransferItem> = emptyList(),
    val currentTransferringItem: TransferItem? = null,
    val overallTransferProgress: Float = 0f,
    val currentSpeedBytesPerSec: Long = 0L,
    val totalTransferBytes: Long = 0L,
    val currentTransferredBytes: Long = 0L,
    val etaSeconds: Int = 0,

    // History & Settings
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val receivedFiles: List<ShareFileItem> = emptyList(),
    val sentFiles: List<TransferItem> = emptyList(),
    val stats: TransferStats = TransferStats(),
    val autoAccept: Boolean = true,
    val wifiOnly: Boolean = true,
    val defaultSavePath: String = "Internal Storage/SwiftShare",
    val userNotification: String? = null
) {
    val totalSelectedBytes: Long
        get() = selectedFiles.sumOf { it.size }

    val formattedSelectedBytes: String
        get() = ShareFileItem.formatBytes(totalSelectedBytes)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileManagerRepository(application)
    private val client = SwiftTransferClient(application)
    private var server: SwiftTransferServer? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var radarJob: Job? = null
    private var transferJob: Job? = null

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
            AppScreen.HOME -> _uiState.update { it.copy(currentTab = BottomTab.HOME) }
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

    fun setHistoryFilter(filter: HistoryFilter) {
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun refreshNetworkInfo() {
        val context = getApplication<Application>()
        val ip = NetworkUtils.getLocalIpAddress(context)
        val ssid = NetworkUtils.getWifiSsid(context)
        val connected = NetworkUtils.isWifiOrHotspotConnected(context)
        _uiState.update { it.copy(localIp = ip, wifiSsid = ssid, isWifiConnected = connected) }
    }

    private fun loadRealCategoryCounts() {
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
            state.copy(selectedFiles = updated)
        }
    }

    fun clearSelectedFiles() {
        _uiState.update { it.copy(selectedFiles = emptySet()) }
    }

    fun addPickedFile(uri: Uri) {
        viewModelScope.launch {
            val item = repository.resolvePickedUri(uri)
            if (item != null) {
                _uiState.update { state ->
                    val files = listOf(item) + state.availableFiles
                    val selected = state.selectedFiles + item
                    state.copy(availableFiles = files, selectedFiles = selected, userNotification = "Selected ${item.name}")
                }
            }
        }
    }

    private fun loadFilesForCategory(category: FileCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFiles = true) }
            val files = when (category) {
                FileCategory.APPS -> repository.loadInstalledApps()
                else -> repository.loadMediaFiles(category)
            }
            _uiState.update { it.copy(availableFiles = files, isLoadingFiles = false) }
        }
    }

    fun startRadarScan() {
        radarJob?.cancel()
        _uiState.update { it.copy(isScanningRadar = true, discoveredPeers = emptyList()) }
        radarJob = viewModelScope.launch {
            val ip = _uiState.value.localIp
            val peers = client.scanLocalSubnet(ip, _uiState.value.localPort)
            delay(1000)
            _uiState.update { it.copy(discoveredPeers = peers, isScanningRadar = false) }
        }
    }

    fun startReceiveServer() {
        if (server?.isRunning == true) return
        refreshNetworkInfo()
        val context = getApplication<Application>()

        server = SwiftTransferServer(
            context = context,
            port = 8888,
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
                        state.copy(
                            receivedFiles = newReceived,
                            stats = newStats,
                            userNotification = "Received $originalName"
                        )
                    }
                }
            },
            onPeerConnected = { clientIp ->
                _uiState.update { it.copy(userNotification = "Sender connected ($clientIp)") }
            }
        )

        val started = server!!.start()
        _uiState.update { it.copy(isServerRunning = started, localPort = server!!.actualPort) }
    }

    fun stopServer() {
        server?.stop()
        server = null
        _uiState.update { it.copy(isServerRunning = false) }
    }

    fun sendFilesToPeer(peer: PeerDevice) {
        val filesToSend = _uiState.value.selectedFiles.toList()
        if (filesToSend.isEmpty()) return

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

            for ((index, file) in filesToSend.withIndex()) {
                val currentItem = transferItems[index]
                _uiState.update { state ->
                    val updated = state.activeTransfers.toMutableList()
                    if (index in updated.indices) {
                        updated[index] = updated[index].copy(status = TransferStatus.TRANSFERRING)
                    }
                    state.copy(activeTransfers = updated, currentTransferringItem = updated[index])
                }

                // Execute real transfer using client
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

                cumulativeTransferred += file.size
                recordFileSent(file, peer.name)
            }

            _uiState.update { state ->
                val finalItems = state.activeTransfers.map { it.copy(status = TransferStatus.COMPLETED) }
                state.copy(
                    activeTransfers = finalItems,
                    overallTransferProgress = 1f,
                    currentSpeedBytesPerSec = 0L,
                    etaSeconds = 0,
                    userNotification = "Files successfully transferred!"
                )
            }
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
        server?.stop()
    }
}
