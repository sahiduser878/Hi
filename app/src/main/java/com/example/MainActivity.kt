package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.FileCategory
import com.example.ui.AppScreen
import com.example.ui.BottomTab
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNavBar
import com.example.ui.screens.ActiveTransferScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RadarSearchScreen
import com.example.ui.screens.ReceiveBeaconScreen
import com.example.ui.screens.SendSelectorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SwiftShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwiftShareTheme {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                // Request storage / media permissions at runtime
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    viewModel.onPermissionsResult(result)
                }

                LaunchedEffect(Unit) {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO
                        )
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionsLauncher.launch(permissions)
                }

                LaunchedEffect(uiState.userNotification) {
                    uiState.userNotification?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.dismissNotification()
                    }
                }

                val showBottomNav = uiState.currentScreen in listOf(
                    AppScreen.HOME,
                    AppScreen.SELECT_FILES,
                    AppScreen.HISTORY,
                    AppScreen.SETTINGS
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showBottomNav) {
                            BottomNavBar(
                                currentTab = uiState.currentTab,
                                onTabSelected = { viewModel.setBottomTab(it) }
                            )
                        }
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = uiState.currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ScreenTransition",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) { screen ->
                        when (screen) {
                            AppScreen.HOME -> {
                                HomeScreen(
                                    uiState = uiState,
                                    onNavigate = { viewModel.navigateTo(it) },
                                    onSelectCategory = {
                                        viewModel.selectCategory(it)
                                        viewModel.navigateTo(AppScreen.SELECT_FILES)
                                    },
                                    onOpenFile = { viewModel.openReceivedFile(it) }
                                )
                            }

                            AppScreen.SEND_SEARCH -> {
                                RadarSearchScreen(
                                    uiState = uiState,
                                    onBack = { viewModel.navigateTo(AppScreen.HOME) },
                                    onNavigateToSelect = {
                                        viewModel.selectCategory(it)
                                        viewModel.navigateTo(AppScreen.SELECT_FILES)
                                    },
                                    onNavigateToReceiveQr = {
                                        viewModel.navigateTo(AppScreen.RECEIVE_FILES)
                                    },
                                    onSendToPeer = { viewModel.sendFilesToPeer(it) },
                                    onConnectManualIp = { viewModel.sendFilesToPeer(com.example.model.PeerDevice("direct", "Receiver ($it)", it, 8888)) },
                                    onOpenHotspotSettings = { viewModel.openHotspotSettings() },
                                    onOpenWifiSettings = { viewModel.openWifiSettings() }
                                )
                            }

                            AppScreen.RECEIVE_FILES -> {
                                ReceiveBeaconScreen(
                                    uiState = uiState,
                                    onBack = {
                                        viewModel.stopServer()
                                        viewModel.navigateTo(AppScreen.HOME)
                                    },
                                    onConnectToSender = { viewModel.connectToSender(it) },
                                    onSimulateReceive = { viewModel.simulateIncomingTransfer() },
                                    onOpenHotspotSettings = { viewModel.openHotspotSettings() },
                                    onOpenWifiSettings = { viewModel.openWifiSettings() }
                                )
                            }

                            AppScreen.SELECT_FILES -> {
                                SendSelectorScreen(
                                    uiState = uiState,
                                    onBack = { viewModel.navigateTo(AppScreen.HOME) },
                                    onSelectCategory = { viewModel.selectCategory(it) },
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onToggleSelectFile = { viewModel.toggleFileSelection(it) },
                                    onSelectAll = { viewModel.selectAllVisibleFiles() },
                                    onAddPickedFile = { viewModel.addPickedFile(it) },
                                    onSend = { viewModel.navigateTo(AppScreen.SEND_SEARCH) }
                                )
                            }

                            AppScreen.TRANSFERRING -> {
                                ActiveTransferScreen(
                                    uiState = uiState,
                                    onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                )
                            }

                            AppScreen.HISTORY -> {
                                HistoryScreen(
                                    uiState = uiState,
                                    onBack = { viewModel.navigateTo(AppScreen.HOME) },
                                    onFilterChange = { viewModel.setHistoryFilter(it) },
                                    onOpenFile = { viewModel.openReceivedFile(it) },
                                    onClearAll = { viewModel.clearAllHistory() }
                                )
                            }

                            AppScreen.SETTINGS -> {
                                SettingsScreen(
                                    uiState = uiState,
                                    onBack = { viewModel.navigateTo(AppScreen.HOME) },
                                    onToggleAutoAccept = { viewModel.toggleAutoAccept(it) },
                                    onToggleWifiOnly = { viewModel.toggleWifiOnly(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
