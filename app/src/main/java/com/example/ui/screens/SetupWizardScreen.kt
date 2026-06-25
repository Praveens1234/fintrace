package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import com.example.ui.animation.AnimSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SymbolInfo
import com.example.data.provider.PriceProvider
import com.example.ui.theme.ConnectionLive
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.ui.theme.MinTouchTarget
import com.example.viewmodel.MainViewModel
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    viewModel: MainViewModel,
    onSetupComplete: () -> Unit
) {
    var currentStep by rememberSaveable { mutableStateOf(1) }
    val totalSteps = 5

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FinTrace Setup") },
                navigationIcon = {
                    if (currentStep > 1) {
                        IconButton(onClick = { currentStep-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { currentStep.toFloat() / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step Content — animated directional slide per step
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(AnimSpec.PageSpring) { it / 3 * dir } +
                        fadeIn(AnimSpec.FadeTween)) togetherWith
                    (slideOutHorizontally(AnimSpec.PageSpring) { -it / 4 * dir } +
                        fadeOut(AnimSpec.FastFade))
                },
                label = "wizard_step",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    1 -> StepWelcome()
                    2 -> StepProviderAndKey(viewModel)
                    3 -> StepSymbols(viewModel)
                    4 -> StepPermissions()
                    5 -> StepReady(viewModel, onSetupComplete)
                    else -> {}
                }
            }

            // Navigation Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step $currentStep of $totalSteps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        if (currentStep < totalSteps) {
                            currentStep++
                        } else {
                            onSetupComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (currentStep == totalSteps) "Finish" else "Next")
                    if (currentStep < totalSteps) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(Radius.xl))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
    }
}

@Composable
fun StepWelcome() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HeroIcon(Icons.AutoMirrored.Filled.TrendingUp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to FinTrace",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your real-time, independent financial price tracker and alert dispatcher.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun StepProviderAndKey(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activeProvider by viewModel.activeProvider.collectAsState()

    // Pre-select the currently active provider (or default to TWELVE_DATA)
    var selectedProvider by remember {
        mutableStateOf(
            PriceProvider.entries.find { it.name == activeProvider } ?: PriceProvider.TWELVE_DATA
        )
    }

    // Per-provider API key state
    val existingTwelveDataKey by viewModel.apiKey.collectAsState()
    val existingFinnhubKey by viewModel.finnhubApiKey.collectAsState()
    val existingAlphaVantageKey by viewModel.alphaVantageApiKey.collectAsState()
    val existingTraderMadeKey by viewModel.traderMadeApiKey.collectAsState()
    val existingOandaKey by viewModel.oandaApiKey.collectAsState()
    val existingOandaAccountId by viewModel.oandaAccountId.collectAsState()
    val existingOandaEnv by viewModel.oandaEnvironment.collectAsState()
    val existingAllTickKey by viewModel.allTickApiKey.collectAsState()
    val existingPolygonKey by viewModel.polygonApiKey.collectAsState()

    // Get the current API key for the selected provider
    var inputKey by remember(selectedProvider) {
        mutableStateOf(
            when (selectedProvider) {
                PriceProvider.TWELVE_DATA   -> existingTwelveDataKey
                PriceProvider.FINNHUB       -> existingFinnhubKey
                PriceProvider.ALPHA_VANTAGE -> existingAlphaVantageKey
                PriceProvider.TRADERMADE    -> existingTraderMadeKey
                PriceProvider.OANDA_V20     -> existingOandaKey
                PriceProvider.ALLTICK       -> existingAllTickKey
                PriceProvider.POLYGON       -> existingPolygonKey
            }
        )
    }
    var oandaAccountInput by remember { mutableStateOf(existingOandaAccountId) }
    var oandaEnvIsPractice by remember { mutableStateOf(existingOandaEnv != "live") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Choose Your Data Source",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Select a price provider. You can change this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Provider selection cards
            items(PriceProvider.entries) { provider ->
                val isSelected = selectedProvider == provider
                Surface(
                    onClick = {
                        selectedProvider = provider
                        viewModel.saveActiveProvider(provider.name)
                    },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedProvider = provider
                                viewModel.saveActiveProvider(provider.name)
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                // Type badges
                                if (provider.supportsWebSocket) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(Radius.pill)
                                    ) {
                                        Text(
                                            "WS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (provider.supportsRest) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(Radius.pill)
                                    ) {
                                        Text(
                                            "REST",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = provider.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Expanded API key section — animated, shown below the list
            item {
                AnimatedVisibility(visible = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider()

                        Text(
                            text = "${selectedProvider.displayName} API Key",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = inputKey,
                            onValueChange = { key ->
                                inputKey = key
                                // Save to the correct provider key
                                viewModel.saveSettingGeneric(selectedProvider.apiKeySettingKey, key)
                                // Also call saveApiKey for Twelve Data (ViewModel special case)
                                if (selectedProvider == PriceProvider.TWELVE_DATA) viewModel.saveApiKey(key)
                            },
                            label = { Text("API Key (Optional)") },
                            placeholder = { Text("Leave blank for Demo Mode") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // OANDA extra fields
                        if (selectedProvider == PriceProvider.OANDA_V20) {
                            OutlinedTextField(
                                value = oandaAccountInput,
                                onValueChange = { id ->
                                    oandaAccountInput = id
                                    viewModel.saveSettingGeneric(PriceProvider.OANDA_ACCOUNT_ID_KEY, id)
                                },
                                label = { Text("OANDA Account ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Environment:", style = MaterialTheme.typography.bodyMedium)
                                listOf("Practice" to true, "Live" to false).forEach { (label, isPractice) ->
                                    FilterChip(
                                        selected = oandaEnvIsPractice == isPractice,
                                        onClick = {
                                            oandaEnvIsPractice = isPractice
                                            viewModel.saveSettingGeneric(
                                                PriceProvider.OANDA_ENVIRONMENT_KEY,
                                                if (isPractice) "practice" else "live"
                                            )
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }
                        }

                        // Sign-up link
                        val signUpUrl = PriceProvider.signUpUrl(selectedProvider)
                        Text(
                            text = "Get a free ${selectedProvider.displayName} key →",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(signUpUrl))
                                    )
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepSymbols(viewModel: MainViewModel) {
    val states by viewModel.symbolStates.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Choose Assets",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Select default financial assets you wish to monitor immediately on your home dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(SymbolInfo.ALL) { s ->
                val state = states.find { it.symbol == s.symbol }
                val isChecked = state?.isActive ?: false

                ListItem(
                    headlineContent = { Text(s.symbol, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(s.name) },
                    leadingContent = {
                        val icon = when (s.category) {
                            "Metals" -> Icons.Default.Brightness5
                            "Majors" -> Icons.Default.AttachMoney
                            else -> Icons.AutoMirrored.Filled.CompareArrows
                        }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleSymbolActive(s.symbol, it) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.toggleSymbolActive(s.symbol, !isChecked) }
                )
            }
        }
    }
}

@Composable
fun StepPermissions() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Background Permissions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "FinTrace operates a Foreground Service to evaluate price crossings continuously. To ensure Android does not kill the app background processes, please configure settings appropriately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Trigger Battery Settings Deep-link
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Launch Battery Permissions")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(
                onClick = {
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allow Alerts & Notifications")
            }
        }
    }
}

@Composable
fun StepReady(
    viewModel: MainViewModel,
    onSetupComplete: () -> Unit
) {
    val activeSub by viewModel.activeSymbols.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HeroIcon(Icons.Default.CheckCircle, tint = ConnectionLive)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "You are Ready!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Setup is complete. FinTrace background monitoring service has started automatically. Currently watching ${activeSub.size} assets.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSetupComplete,
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).height(52.dp)
        ) {
            Text("Start FinTrace Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
