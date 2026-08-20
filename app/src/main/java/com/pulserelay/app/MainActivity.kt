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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PulseRelayTheme { PulseRelayApp() } }
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
private fun PulseRelayApp() {
    val context = LocalContext.current
    val configStore = remember { LocalConfigStore(context) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD.name) }
    var relayEnabled by rememberSaveable { mutableStateOf(configStore.relayEnabled) }
    var redactSensitive by rememberSaveable { mutableStateOf(configStore.redactSensitiveData) }
    var selectedSenders by remember { mutableStateOf(configStore.selectedSenders) }
    var activityEntries by remember { mutableStateOf(configStore.activityHistory()) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
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
        hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions() {
        val needed = buildList {
            if (!hasSmsPermission) add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
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
                    Column {
                        Text(
                            text = "PULSERELAY",
                            style = MaterialTheme.typography.labelMedium,
                            color = PulseColors.mint,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = when (AppTab.valueOf(selectedTab)) {
                                AppTab.DASHBOARD -> "Your relay, at a glance"
                                AppTab.RULES -> "Choose what gets through"
                                AppTab.ACTIVITY -> "Redacted delivery log"
                                AppTab.SETTINGS -> "Private by design"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
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
        when (AppTab.valueOf(selectedTab)) {
            AppTab.DASHBOARD -> DashboardScreen(
                modifier = Modifier.padding(padding),
                relayEnabled = relayEnabled,
                onRelayToggle = {
                    relayEnabled = it
                    configStore.relayEnabled = it
                },
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
                selectedSenders = selectedSenders,
                onSenderToggle = { address, checked ->
                    val normalized = MessageFilter.normalizeSender(address)
                    selectedSenders = if (checked) selectedSenders + normalized else selectedSenders - normalized
                    configStore.selectedSenders = selectedSenders
                },
                onSelectSuggested = { addresses ->
                    selectedSenders = selectedSenders + addresses.map(MessageFilter::normalizeSender)
                    configStore.selectedSenders = selectedSenders
                },
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
                onRelayToggle = {
                    relayEnabled = it
                    configStore.relayEnabled = it
                },
                hasSmsPermission = hasSmsPermission,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermissions = { requestPermissions() },
            )
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
                MetricCard(Modifier.weight(1f), "$activeSenders", "Active senders", PulseColors.mint)
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
                    "Only approved wallet alerts are matched on this device. Everything else stays private.",
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
    selectedSenders: Set<String>,
    onSenderToggle: (String, Boolean) -> Unit,
    onSelectSuggested: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var senders by remember { mutableStateOf<List<InboxSender>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        senders = SmsInboxReader(context).uniqueSenders()
        loading = false
    }

    val suggested = senders.filter { MessageFilter.detectProvider(it.address) != null }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Approved senders", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Pick the senders whose receipts should be relayed. Only selected senders can ever reach Telegram.", color = PulseColors.muted)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PulseColors.surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quick select", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { reloadKey++ }) {
                            Text("Refresh")
                        }
                    }
                    Text("Automatically select senders that look like bKash, Nagad, or Rocket.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { onSelectSuggested(suggested.map { it.address }.toSet()) },
                        enabled = suggested.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Select bKash · Nagad · Rocket")
                    }
                }
            }
        }
        item {
            SettingCard(
                icon = Icons.Default.Shield,
                title = "Redact sensitive numbers",
                description = "Hide phone numbers, OTP-like sequences, and PIN content before delivery.",
                checked = redactSensitive,
                onCheckedChange = onRedactionToggle,
                accent = PulseColors.mint,
            )
        }
        when {
            loading -> item {
                Text("Reading your inbox…", color = PulseColors.muted, style = MaterialTheme.typography.bodyMedium)
            }
            senders.isEmpty() -> item {
                EmptySenderCard()
            }
            else -> items(senders) { sender ->
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
                    Text("Privacy first. OTP and PIN messages are blocked even for selected senders.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
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
) {
    val context = LocalContext.current
    val configStore = remember { LocalConfigStore(context) }
    var botToken by rememberSaveable { mutableStateOf(configStore.botToken) }
    var channelId by rememberSaveable { mutableStateOf(configStore.channelId) }
    var saved by rememberSaveable { mutableStateOf(false) }

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
                            Text("Telegram destination", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(if (relayEnabled) "Bot connected • channel protected" else "Not connected yet", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
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
                            configStore.botToken = botToken
                            configStore.channelId = channelId
                            saved = true
                            onRelayToggle(botToken.isNotBlank() && channelId.isNotBlank())
                        },
                        enabled = botToken.isNotBlank() && channelId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (saved) "Saved securely" else "Save and connect")
                    }
                    Text("Keep this bot restricted to a private channel. The token is stored using Android encrypted storage and is never displayed in the activity feed.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            PermissionCard(
                icon = Icons.Default.Sms,
                title = "SMS access",
                description = if (hasSmsPermission) "Incoming wallet alerts can be detected." else "Required to detect incoming wallet alerts on this device.",
                granted = hasSmsPermission,
                onRequest = onRequestPermissions,
                accent = PulseColors.amber,
            )
        }
        item {
            PermissionCard(
                icon = Icons.Default.NotificationsActive,
                title = "Notifications",
                description = if (hasNotificationPermission) "Delivery status can be shown." else "Allows a small status notification for delivered alerts.",
                granted = hasNotificationPermission,
                onRequest = onRequestPermissions,
                accent = PulseColors.blue,
            )
        }
        item {
            AnimatedVisibility(visible = relayEnabled) {
                Text("Telegram credentials are stored locally using Android encrypted storage.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
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
            Text("No SMS messages found. Grant SMS access, then refresh.", color = PulseColors.muted, style = MaterialTheme.typography.bodyMedium)
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
                    Text("Redacted history. Raw message content is never stored.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
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
            Text("No relay activity yet.", color = PulseColors.muted, style = MaterialTheme.typography.bodyMedium)
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
                Text("SMS access needed", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Allow SMS access so wallet receipts can be detected.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
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
                Text("Filtering and redaction happen before the network request.", color = PulseColors.muted, style = MaterialTheme.typography.bodySmall)
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
    val background = Color(0xFF0B1018)
    val surface = Color(0xFF141C27)
    val surfaceElevated = Color(0xFF1D2938)
    val heroStart = Color(0xFF163B42)
    val heroEnd = Color(0xFF182438)
    val mint = Color(0xFF56D6B0)
    val blue = Color(0xFF77A9FF)
    val amber = Color(0xFFFFC46B)
    val ink = Color(0xFF10201E)
    val muted = Color(0xFF91A0B4)
}

@Composable
private fun PulseRelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = PulseColors.mint,
            onPrimary = PulseColors.ink,
            secondary = PulseColors.blue,
            background = PulseColors.background,
            surface = PulseColors.surface,
            onSurface = Color.White,
        ),
        content = content,
    )
}
