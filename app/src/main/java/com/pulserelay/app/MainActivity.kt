package com.pulserelay.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.pulserelay.app.data.ActivityEntry
import com.pulserelay.app.data.InboxSender
import com.pulserelay.app.data.LocalConfigStore
import com.pulserelay.app.data.SmsInboxReader
import com.pulserelay.app.domain.MessageFilter
import com.pulserelay.app.network.TelegramBotClient
import com.pulserelay.app.network.TelegramStatusMessages
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmsForwarderTheme { SmsForwarderApp() } }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Dashboard),
    RULES("Rules", Icons.AutoMirrored.Filled.Rule),
    ACTIVITY("Activity", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsForwarderApp() {
    val context = LocalContext.current
    val configStore = remember { LocalConfigStore(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD.name) }
    var relayEnabled by rememberSaveable { mutableStateOf(configStore.relayEnabled) }
    var redactSensitive by rememberSaveable { mutableStateOf(configStore.redactSensitiveData) }
    var blockOtpContent by rememberSaveable { mutableStateOf(configStore.blockOtpContent) }
    var hideBalance by rememberSaveable { mutableStateOf(configStore.hideBalance) }
    var selectedSenders by remember { mutableStateOf(configStore.selectedSenders) }
    var activityEntries by remember { mutableStateOf(configStore.activityHistory()) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasReadSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
        hasReadSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions() {
        val needed = buildList {
            if (!hasSmsPermission) add(Manifest.permission.RECEIVE_SMS)
            if (!hasReadSmsPermission) add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    var showSetupGuide by rememberSaveable { mutableStateOf(!configStore.hasCompletedSetupGuide) }
    if (showSetupGuide) {
        SetupGuideScreen(
            onComplete = { openTelegramSettings ->
                configStore.hasCompletedSetupGuide = true
                showSetupGuide = false
                if (openTelegramSettings) selectedTab = AppTab.SETTINGS.name
            },
        )
        return
    }

    fun updateRelayState(enabled: Boolean) {
        if (configStore.relayEnabled == enabled && relayEnabled == enabled) return

        val botToken = configStore.botToken
        val channelId = configStore.channelId
        if (botToken.isBlank() || channelId.isBlank()) {
            relayEnabled = false
            configStore.relayEnabled = false
            Toast.makeText(context, "Configure Telegram before changing relay status", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                TelegramBotClient().sendMessage(
                    botToken = botToken,
                    channelId = channelId,
                    text = if (enabled) TelegramStatusMessages.RELAY_ON else TelegramStatusMessages.RELAY_OFF,
                )
            }
            if (enabled) {
                if (result.isSuccess) {
                    relayEnabled = true
                    configStore.relayEnabled = true
                    Toast.makeText(context, "SMS forwarding is ON", Toast.LENGTH_SHORT).show()
                } else {
                    relayEnabled = false
                    configStore.relayEnabled = false
                    Toast.makeText(context, "Failed to turn SMS forwarding ON", Toast.LENGTH_SHORT).show()
                }
            } else {
                relayEnabled = false
                configStore.relayEnabled = false
                Toast.makeText(
                    context,
                    if (result.isSuccess) "SMS forwarding is OFF" else "SMS forwarding is OFF; Telegram status failed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // Ask for every required runtime permission up front, on each launch, until granted.
    LaunchedEffect(Unit) {
        requestPermissions()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.DASHBOARD.name || selectedTab == AppTab.ACTIVITY.name) {
            activityEntries = configStore.activityHistory()
        }
    }

    Scaffold(
        containerColor = PulseColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            tint = PulseColors.mint,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                text = "SMS FORWARDER",
                                style = MaterialTheme.typography.labelMedium,
                                color = PulseColors.mint,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                text = when (AppTab.valueOf(selectedTab)) {
                                    AppTab.DASHBOARD -> "Your private relay, at a glance"
                                    AppTab.RULES -> "Choose your sources and filters"
                                    AppTab.ACTIVITY -> "Redacted delivery log"
                                    AppTab.SETTINGS -> "Connection and privacy"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
                actions = {
                    Text(
                        text = if (relayEnabled) "Live" else "Paused",
                        color = if (relayEnabled) PulseColors.mint else PulseColors.amber,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(6.dp))
                    StatusDot(connected = relayEnabled)
                    Spacer(Modifier.size(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PulseColors.background),
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = PulseColors.surface,
                tonalElevation = 0.dp,
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab.name,
                        onClick = { selectedTab = tab.name },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = AppTab.valueOf(selectedTab), label = "tab") { tab ->
            when (tab) {
            AppTab.DASHBOARD -> DashboardScreen(
                modifier = Modifier.padding(padding),
                relayEnabled = relayEnabled,
                onRelayToggle = ::updateRelayState,
                activeSenders = selectedSenders.size,
                scamAlerts = configStore.scamAlertCount,
                recentEntries = activityEntries,
                onViewAll = { selectedTab = AppTab.ACTIVITY.name },
                hasSmsPermission = hasSmsPermission,
                onRequestPermissions = { requestPermissions() },
            )
            AppTab.RULES -> RulesScreen(
                modifier = Modifier.padding(padding),
                redactSensitive = redactSensitive,
                onRedactionToggle = {
                    redactSensitive = it
                    configStore.redactSensitiveData = it
                },
                blockOtpContent = blockOtpContent,
                onBlockOtpToggle = {
                    blockOtpContent = it
                    configStore.blockOtpContent = it
                },
                hideBalance = hideBalance,
                onHideBalanceToggle = {
                    hideBalance = it
                    configStore.hideBalance = it
                },
                selectedSenders = selectedSenders,
                onSenderToggle = { address, checked ->
                    val normalized = MessageFilter.normalizeSender(address)
                    selectedSenders = if (checked) selectedSenders + normalized else selectedSenders - normalized
                    configStore.selectedSenders = selectedSenders
                },
                onAddSenders = { addresses ->
                    selectedSenders = selectedSenders + addresses.map(MessageFilter::normalizeSender)
                    configStore.selectedSenders = selectedSenders
                },
                onRemoveAllSenders = {
                    selectedSenders = emptySet()
                    configStore.selectedSenders = emptySet()
                },
                hasReadSmsPermission = hasReadSmsPermission,
                onRequestPermissions = { requestPermissions() },
            )
            AppTab.ACTIVITY -> ActivityScreen(
                modifier = Modifier.padding(padding),
                entries = activityEntries,
                onClear = {
                    configStore.clearActivity()
                    activityEntries = emptyList()
                },
            )
            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                relayEnabled = relayEnabled,
                onRelayToggle = ::updateRelayState,
                hasSmsPermission = hasSmsPermission,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermissions = { requestPermissions() },
                onReplayGuide = {
                    configStore.hasCompletedSetupGuide = false
                    showSetupGuide = true
                },
                onDataImported = {
                    relayEnabled = configStore.relayEnabled
                    redactSensitive = configStore.redactSensitiveData
                    blockOtpContent = configStore.blockOtpContent
                    hideBalance = configStore.hideBalance
                    selectedSenders = configStore.selectedSenders
                    activityEntries = configStore.activityHistory()
                },
            )
            }
        }
    }
}

private data class SetupGuidePage(
    val icon: ImageVector,
    val eyebrow: String,
    val title: String,
    val description: String,
    val prompt: String,
)

@Composable
private fun SetupGuideScreen(onComplete: (Boolean) -> Unit) {
    val pages = listOf(
        SetupGuidePage(
            icon = Icons.Default.Shield,
            eyebrow = "WELCOME",
            title = "Private SMS forwarding, step by step",
            description = "SMS FORWARDER filters messages on your phone and only forwards alerts from senders you approve.",
            prompt = "Keep this app and the destination Telegram channel under your control. You can pause forwarding at any time.",
        ),
        SetupGuidePage(
            icon = Icons.AutoMirrored.Filled.Send,
            eyebrow = "STEP 1 · TELEGRAM",
            title = "Connect your Telegram channel",
            description = "Create a bot with BotFather, add it to your private channel, then enter the bot token and channel ID in Settings.",
            prompt = "Tap “Set up Telegram now” at the end of this guide. The app sends a test status message before saving the connection.",
        ),
        SetupGuidePage(
            icon = Icons.Default.Sms,
            eyebrow = "STEP 2 · SENDERS",
            title = "Choose which senders can forward",
            description = "Open Rules and select only trusted SMS senders, such as your bKash, Nagad, or Rocket notification numbers.",
            prompt = "Use Select all only when every visible sender is trusted. Unknown senders remain blocked by default.",
        ),
        SetupGuidePage(
            icon = Icons.Default.Lock,
            eyebrow = "STEP 3 · PROTECTION",
            title = "Protect sensitive information",
            description = "Keep phone-number redaction and code/password blocking enabled. You can also hide balances before delivery.",
            prompt = "After Telegram and senders are ready, enable the relay from Home. The app will announce ON or OFF in Telegram.",
        ),
    )
    var pageIndex by rememberSaveable { mutableStateOf(0) }
    val page = pages[pageIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PulseColors.background)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = PulseColors.mint,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    "SMS FORWARDER",
                    color = PulseColors.mint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text("Setup guide", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            pages.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (index <= pageIndex) PulseColors.mint else PulseColors.surfaceElevated),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Step ${pageIndex + 1} of ${pages.size}", color = PulseColors.muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PulseColors.surface),
            shape = RoundedCornerShape(26.dp),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(page.icon, contentDescription = null, tint = PulseColors.mint, modifier = Modifier.size(34.dp))
                Text(page.eyebrow, color = PulseColors.mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(page.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(page.description, color = PulseColors.muted, style = MaterialTheme.typography.bodyMedium)
                Card(
                    colors = CardDefaults.cardColors(containerColor = PulseColors.heroStart.copy(alpha = .55f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PulseColors.mint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(page.prompt, color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (pageIndex == pages.lastIndex) {
            Button(
                onClick = { onComplete(true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Set up Telegram now", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onComplete(false) }, modifier = Modifier.fillMaxWidth()) {
                Text("Finish guide")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { onComplete(false) }, modifier = Modifier.weight(1f)) {
                    Text("Skip")
                }
                if (pageIndex > 0) {
                    OutlinedButton(onClick = { pageIndex-- }, modifier = Modifier.weight(1f)) {
                        Text("Back")
                    }
                }
                Button(onClick = { pageIndex++ }, modifier = Modifier.weight(1f)) {
                    Text("Next", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    modifier: Modifier = Modifier,
    relayEnabled: Boolean,
    onRelayToggle: (Boolean) -> Unit,
    activeSenders: Int,
    scamAlerts: Int,
    recentEntries: List<ActivityEntry>,
    onViewAll: () -> Unit,
    hasSmsPermission: Boolean,
    onRequestPermissions: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroCard(relayEnabled = relayEnabled, onRelayToggle = onRelayToggle)
        }
        if (!hasSmsPermission) {
            item {
                PermissionBanner(onRequest = onRequestPermissions)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(Modifier.weight(1f), "$activeSenders", "Senders monitored", PulseColors.mint)
                MetricCard(Modifier.weight(1f), "$scamAlerts", "Scam alerts", PulseColors.amber)
            }
        }
        item {
            SectionHeading("Recent activity", "View all", onActionClick = onViewAll)
        }
        if (recentEntries.isEmpty()) {
            item {
                EmptyActivityCard()
            }
        } else {
            items(recentEntries.take(3)) { entry ->
                ActivityCard(
                    provider = entry.provider,
                    detail = entry.summary,
                    time = formatActivityTime(entry.timestamp),
                    icon = if (entry.isScam) Icons.Default.Warning else Icons.Default.CheckCircle,
                    tint = if (entry.isScam) PulseColors.amber else PulseColors.mint,
                )
            }
        }
        item {
            PrivacyBanner()
        }
    }
}

@Composable
private fun HeroCard(relayEnabled: Boolean, onRelayToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(PulseColors.heroStart, PulseColors.heroEnd)),
                )
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, tint = PulseColors.mint)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Telegram relay", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
                        Text(if (relayEnabled) "Live and protected" else "Ready to connect", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Only senders you approve are matched on this device. Nothing else ever leaves your phone.",
                    color = Color.White.copy(alpha = .76f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onRelayToggle(!relayEnabled) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (relayEnabled) Color.White.copy(alpha = .14f) else PulseColors.mint,
                            contentColor = if (relayEnabled) Color.White else PulseColors.ink,
                        ),
                    ) {
                        Icon(if (relayEnabled) Icons.Default.CloudOff else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (relayEnabled) "Pause relay" else "Enable relay", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (relayEnabled) "ON" else "OFF", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RulesScreen(
    modifier: Modifier,
    redactSensitive: Boolean,
    onRedactionToggle: (Boolean) -> Unit,
    blockOtpContent: Boolean,
    onBlockOtpToggle: (Boolean) -> Unit,
    hideBalance: Boolean,
    onHideBalanceToggle: (Boolean) -> Unit,
    selectedSenders: Set<String>,
    onSenderToggle: (String, Boolean) -> Unit,
    onAddSenders: (Set<String>) -> Unit,
    onRemoveAllSenders: () -> Unit,
    hasReadSmsPermission: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    var senders by remember { mutableStateOf<List<InboxSender>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(hasReadSmsPermission) {
        if (hasReadSmsPermission) {
            loading = true
            senders = SmsInboxReader(context).uniqueSenders()
            loading = false
        } else {
            loading = false
            senders = emptyList()
        }
    }

    val filteredSenders = remember(query, senders) {
        if (query.isBlank()) senders else senders.filter { it.address.contains(query, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Choose your senders", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Select the senders you trust. Only these senders can reach your Telegram channel.", color = PulseColors.muted)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search senders") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onAddSenders(senders.map { it.address }.toSet()) },
                    enabled = senders.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Select all")
                }
                OutlinedButton(
                    onClick = onRemoveAllSenders,
                    enabled = selectedSenders.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear all")
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("Privacy filters", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
        item {
            SettingCard(
                icon = Icons.Default.Shield,
                title = "Hide phone numbers",
                description = "Replace phone numbers and long identifiers with placeholders before delivery.",
                checked = redactSensitive,
                onCheckedChange = onRedactionToggle,
                accent = PulseColors.mint,
            )
        }
        item {
            SettingCard(
                icon = Icons.Default.Lock,
                title = "Block codes & passwords",
                description = "Never relay messages that look like one-time codes, PINs, or passwords.",
                checked = blockOtpContent,
                onCheckedChange = onBlockOtpToggle,
                accent = PulseColors.amber,
            )
        }
        item {
            SettingCard(
                icon = Icons.Default.Shield,
                title = "Hide balances",
                description = "Replace BDT, Tk, and Taka amounts with a placeholder.",
                checked = hideBalance,
                onCheckedChange = onHideBalanceToggle,
                accent = PulseColors.blue,
            )
        }
        when {
            !hasReadSmsPermission -> item {
                SmsReadGrantCard(onRequest = onRequestPermissions)
            }
            loading -> item {
                Text("Reading your messages…", color = PulseColors.muted, style = MaterialTheme.typography.bodyMedium)
            }
            senders.isEmpty() -> item {
                EmptySenderCard()
            }
            filteredSenders.isEmpty() -> item {
                Text("No senders match \"$query\".", color = PulseColors.muted, style = MaterialTheme.typography.bodyMedium)
            }
            else -> items(filteredSenders) { sender ->
                SenderRow(
                    sender = sender,
                    selected = MessageFilter.normalizeSender(sender.address) in selectedSenders,
                    onToggle = { checked -> onSenderToggle(sender.address, checked) },
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PulseColors.surface),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lock, null, tint = PulseColors.mint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("Privacy first. Codes, passwords, and other sensitive content are filtered before anything reaches Telegram.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    relayEnabled: Boolean,
    onRelayToggle: (Boolean) -> Unit,
    hasSmsPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onReplayGuide: () -> Unit,
    onDataImported: () -> Unit,
) {
    val context = LocalContext.current
    val configStore = remember { LocalConfigStore(context) }
    val scope = rememberCoroutineScope()
    var botToken by rememberSaveable { mutableStateOf(configStore.botToken) }
    var channelId by rememberSaveable { mutableStateOf(configStore.channelId) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var setupStatus by remember { mutableStateOf<String?>(null) }
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(configStore.exportBackup().toByteArray())
                        } ?: error("Cannot open backup file")
                    }.isSuccess
                }
                backupStatus = if (ok) "Backup exported" else "Export failed"
                Toast.makeText(context, backupStatus, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val message = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            .orEmpty()
                        configStore.importBackup(json).getOrThrow()
                        "Backup imported"
                    }.getOrElse { "Import failed" }
                }
                backupStatus = message
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                if (message == "Backup imported") {
                    botToken = configStore.botToken
                    channelId = configStore.channelId
                    saved = true
                    onDataImported()
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = PulseColors.blue, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Telegram channel", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(if (relayEnabled) "Connected • channel protected" else "Not connected", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        StatusDot(connected = relayEnabled)
                    }
                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it; saved = false },
                        label = { Text("Bot token") },
                        placeholder = { Text("123456:ABC…") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = channelId,
                        onValueChange = { channelId = it; saved = false },
                        label = { Text("Channel ID") },
                        placeholder = { Text("-1001234567890") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val candidateToken = botToken.trim()
                            val candidateChannelId = channelId.trim()
                            val previousToken = configStore.botToken
                            val previousChannelId = configStore.channelId
                            saved = false
                            setupStatus = "Testing Telegram API…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    TelegramBotClient().sendMessage(
                                        botToken = candidateToken,
                                        channelId = candidateChannelId,
                                        text = TelegramStatusMessages.API_SETUP_SUCCESS,
                                    )
                                }
                                if (result.isSuccess) {
                                    configStore.botToken = candidateToken
                                    configStore.channelId = candidateChannelId
                                    saved = true
                                    setupStatus = "Telegram API setup succeeded"
                                    onRelayToggle(true)
                                } else {
                                    setupStatus = "Telegram API setup failed"
                                    if (previousToken.isNotBlank() && previousChannelId.isNotBlank()) {
                                        withContext(Dispatchers.IO) {
                                            TelegramBotClient().sendMessage(
                                                botToken = previousToken,
                                                channelId = previousChannelId,
                                                text = TelegramStatusMessages.API_SETUP_FAILED,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        enabled = botToken.isNotBlank() && channelId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (saved) "Saved securely" else "Save and connect")
                    }
                    setupStatus?.let { status ->
                        Text(
                            status,
                            color = if (status.contains("failed", ignoreCase = true)) PulseColors.amber else PulseColors.mint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("Keep this bot restricted to a private channel. The token is stored using Android encrypted storage and is never displayed in the activity feed.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onReplayGuide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open setup guide")
            }
        }
        item {
            PermissionCard(
                icon = Icons.Default.Sms,
                title = "SMS access",
                description = if (hasSmsPermission) "Wallet receipts can be detected." else "Required to detect incoming wallet receipts.",
                granted = hasSmsPermission,
                onRequest = onRequestPermissions,
                accent = PulseColors.amber,
            )
        }
        item {
            PermissionCard(
                icon = Icons.Default.NotificationsActive,
                title = "Notifications",
                description = if (hasNotificationPermission) "Delivery alerts can be shown." else "Allows a small notification when a receipt is delivered.",
                granted = hasNotificationPermission,
                onRequest = onRequestPermissions,
                accent = PulseColors.blue,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = PulseColors.blue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Text("Backup & restore", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("Move your settings and data to another phone. The backup file contains your bot token, so keep it private.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { exportLauncher.launch("sms-forwarder-backup.json") }, modifier = Modifier.weight(1f)) {
                            Text("Export")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                            Text("Import")
                        }
                    }
                    backupStatus?.let { status ->
                        Text(
                            status,
                            color = if (status == "Export failed" || status == "Import failed") PulseColors.amber else PulseColors.mint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            AnimatedVisibility(visible = relayEnabled) {
                Text("Your token is stored in Android encrypted storage and is only ever sent to Telegram.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SenderRow(sender: InboxSender, selected: Boolean, onToggle: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sender.address, color = Color.White, fontWeight = FontWeight.Bold)
                    MessageFilter.detectProvider(sender.address)?.let { provider ->
                        Spacer(Modifier.size(8.dp))
                        Text(provider.label, color = PulseColors.mint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${sender.messageCount} messages · ${sender.preview}",
                    color = PulseColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(12.dp))
            Checkbox(checked = selected, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun EmptySenderCard() {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sms, null, tint = PulseColors.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Text("No messages found. Allow SMS access, then tap Refresh.", color = PulseColors.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SmsReadGrantCard(onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.amber.copy(alpha = .12f)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sms, null, tint = PulseColors.amber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("SMS reading is off", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Allow message access to list senders and choose which ones to relay.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Grant SMS reading")
            }
        }
    }
}

@Composable
private fun SettingCard(icon: ImageVector, title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(description, color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, value: String, label: String, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActivityCard(provider: String, detail: String, time: String, icon: ImageVector, tint: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(provider, color = Color.White, fontWeight = FontWeight.Bold)
                Text(detail, color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(time, color = PulseColors.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ActivityScreen(
    modifier: Modifier,
    entries: List<ActivityEntry>,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Delivery log", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("A redacted history of relayed receipts and alerts. Raw message content is never stored.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                    Text("Clear")
                }
            }
        }
        if (entries.isEmpty()) {
            item { EmptyActivityCard() }
        } else {
            items(entries) { entry ->
                ActivityCard(
                    provider = entry.provider,
                    detail = entry.summary,
                    time = formatActivityTime(entry.timestamp),
                    icon = if (entry.isScam) Icons.Default.Warning else Icons.Default.CheckCircle,
                    tint = if (entry.isScam) PulseColors.amber else PulseColors.mint,
                )
            }
        }
    }
}

@Composable
private fun EmptyActivityCard() {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = PulseColors.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Text("No relay activity yet. Delivered receipts will appear here.", color = PulseColors.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit,
    accent: Color,
) {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(description, color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.size(12.dp))
            if (granted) {
                Text("Granted", color = PulseColors.mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            } else {
                Button(onClick = onRequest) {
                    Text("Grant")
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onActionClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            action,
            color = PulseColors.mint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(onClick = onActionClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.amber.copy(alpha = .12f)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sms, null, tint = PulseColors.amber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("SMS access required", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Allow SMS access so SMS FORWARDER can detect wallet receipts.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.size(12.dp))
            Button(onClick = onRequest) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun PrivacyBanner() {
    Card(colors = CardDefaults.cardColors(containerColor = PulseColors.mint.copy(alpha = .10f)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, null, tint = PulseColors.mint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Protected on this phone", color = PulseColors.mint, fontWeight = FontWeight.Bold)
                Text("Filtering and redaction happen on this device, before anything is sent.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (connected) PulseColors.mint else PulseColors.amber),
    )
}

private fun formatActivityTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private object PulseColors {
    val background = Color(0xFF07111F)
    val surface = Color(0xFF101B2A)
    val surfaceElevated = Color(0xFF18263A)
    val heroStart = Color(0xFF0B3340)
    val heroEnd = Color(0xFF101A35)
    val mint = Color(0xFF63E6BE)
    val blue = Color(0xFF77A9FF)
    val amber = Color(0xFFFFC46B)
    val ink = Color(0xFF0D221F)
    val muted = Color(0xFF91A0B4)
}

private val PulseTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.1.sp),
)

@Composable
private fun SmsForwarderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = PulseColors.mint,
            onPrimary = PulseColors.ink,
            secondary = PulseColors.blue,
            background = PulseColors.background,
            surface = PulseColors.surface,
            onSurface = Color.White,
        ),
        typography = PulseTypography,
        content = content,
    )
}
