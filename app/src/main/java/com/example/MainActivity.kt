package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.screens.*
import com.example.ui.theme.FinTraceTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.service.AlertSoundPlayer.initTts(applicationContext)

        setContent {
            // Collect theme mode reactively
            val currentTheme by viewModel.themeMode.collectAsState()

            FinTraceTheme(mode = currentTheme) {
                // Persistent State Management
                var currentRoute by remember { mutableStateOf("wizard_check") }
                var currentTab by remember { mutableStateOf("prices") }
                var selectedSymbolForDetail by remember { mutableStateOf("") }

                // Check completed wizard setting
                val states by viewModel.symbolStates.collectAsState()
                val alerts by viewModel.alertList.collectAsState()

                LaunchedEffect(states) {
                    if (currentRoute == "wizard_check") {
                        val monitor = com.example.data.repository.PriceMonitorManager.getInstance(applicationContext)
                        val completedSetting = monitor.getSetting("setup_completed")
                        currentRoute = if (completedSetting == "true") "home" else "wizard"
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentRoute) {
                        "wizard_check" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        "wizard" -> {
                            SetupWizardScreen(
                                viewModel = viewModel,
                                onSetupComplete = {
                                    viewModel.saveSettingGeneric("setup_completed", "true")
                                    currentRoute = "home"
                                }
                            )
                        }

                        "home" -> {
                            Scaffold(
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        tonalElevation = 0.dp,
                                        windowInsets = WindowInsets.navigationBars
                                    ) {
                                        val navItemColors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val tabs = listOf(
                                            Triple("prices", Icons.Default.TrendingUp, "Prices"),
                                            Triple("alerts", Icons.Default.Notifications, "Alerts"),
                                            Triple("logs", Icons.Default.History, "Logs"),
                                            Triple("settings", Icons.Default.Settings, "Settings")
                                        )
                                        tabs.forEach { (route, icon, label) ->
                                            NavigationBarItem(
                                                selected = currentTab == route,
                                                onClick = { currentTab = route },
                                                icon = { Icon(icon, contentDescription = label) },
                                                label = { Text(label) },
                                                colors = navItemColors
                                            )
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    when (currentTab) {
                                        "prices" -> {
                                            DashboardScreen(
                                                viewModel = viewModel,
                                                onSymbolSelected = { sym ->
                                                    selectedSymbolForDetail = sym
                                                    currentRoute = "detail"
                                                },
                                                onQuickAlertRequest = { sym ->
                                                    selectedSymbolForDetail = sym
                                                    currentTab = "alerts"
                                                }
                                            )
                                        }

                                        "alerts" -> {
                                            AlertListScreen(viewModel = viewModel)
                                         }

                                         "logs" -> {
                                             LogsScreen(viewModel = viewModel)
                                        }

                                        "settings" -> {
                                            SettingsScreen(
                                                viewModel = viewModel,
                                                onNavigateToAboutApp = { currentRoute = "about_app" },
                                                onNavigateToAboutDeveloper = { currentRoute = "about_dev" },
                                                onNavigateToPermissions = { currentRoute = "permissions" }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "detail" -> {
                            SymbolDetailScreen(
                                symbol = selectedSymbolForDetail,
                                viewModel = viewModel,
                                onBack = { currentRoute = "home" }
                            )
                        }

                        "about_app" -> {
                            AboutAppScreen(onBack = { currentRoute = "home"; currentTab = "settings" })
                        }

                        "about_dev" -> {
                            AboutDeveloperScreen(onBack = { currentRoute = "home"; currentTab = "settings" })
                        }

                        "permissions" -> {
                            PermissionHelperScreen(onBack = { currentRoute = "home"; currentTab = "settings" })
                        }
                    }
                }
            }
        }
    }

    // Overriding dispatchKeyEvent to silence active alerts via the hardware volume keys.
    // Lint's RestrictedApi check false-positives on the super call from a non-androidx module.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                com.example.service.AlertSoundPlayer.stopPlayback()
                com.example.service.NotificationHelper.cancelVibration(applicationContext)
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
