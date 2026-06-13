package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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

                // ── System back handling ──────────────────────────────────────────
                // Navigation is driven by manual state, so the system Back gesture/key must be
                // intercepted; otherwise it propagates to the OS and exits the app. Each handler
                // is only enabled for the routes it applies to (the most specific wins because
                // Compose dispatches Back to the last-registered enabled handler).
                BackHandler(enabled = currentRoute == "detail") {
                    currentRoute = "home" // return to the Prices tab the user came from
                }
                BackHandler(enabled = currentRoute == "about_app" || currentRoute == "about_dev" || currentRoute == "permissions") {
                    currentRoute = "home"
                    currentTab = "settings"
                }
                // On the home shell, Back from any non-Prices tab returns to Prices (the home tab);
                // Back from Prices itself falls through to the OS (exit), the expected Android behavior.
                BackHandler(enabled = currentRoute == "home" && currentTab != "prices") {
                    currentTab = "prices"
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentRoute,
                        transitionSpec = {
                            val enterTransition: EnterTransition
                            val exitTransition: ExitTransition
                            when {
                                targetState == "detail" -> {
                                    enterTransition = slideInHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        initialOffsetX = { it / 3 }
                                    ) + fadeIn(tween(280))
                                    exitTransition = slideOutHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        targetOffsetX = { -it / 6 }
                                    ) + fadeOut(tween(180))
                                }
                                initialState == "detail" -> {
                                    enterTransition = slideInHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        initialOffsetX = { -it / 6 }
                                    ) + fadeIn(tween(280))
                                    exitTransition = slideOutHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        targetOffsetX = { it / 3 }
                                    ) + fadeOut(tween(180))
                                }
                                targetState == "wizard" || initialState == "wizard" -> {
                                    enterTransition = fadeIn(tween(320))
                                    exitTransition = fadeOut(tween(200))
                                }
                                else -> {
                                    enterTransition = fadeIn(tween(240))
                                    exitTransition = fadeOut(tween(160))
                                }
                            }
                            enterTransition togetherWith exitTransition
                        },
                        label = "route_anim"
                    ) { route ->
                        when (route) {
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
                                            tabs.forEach { (tabRoute, icon, label) ->
                                                NavigationBarItem(
                                                    selected = currentTab == tabRoute,
                                                    onClick = { currentTab = tabRoute },
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
                                        AnimatedContent(
                                            targetState = currentTab,
                                            transitionSpec = {
                                                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                                            },
                                            label = "tab_anim"
                                        ) { tab ->
                                            when (tab) {
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

                                                else -> {}
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

                            else -> {}
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
