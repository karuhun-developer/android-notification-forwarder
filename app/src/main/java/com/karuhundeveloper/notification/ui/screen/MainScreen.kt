package com.karuhundeveloper.notification.ui.screen

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.service.AppNotificationListener
import com.karuhundeveloper.notification.ui.component.MethodBadge
import com.karuhundeveloper.notification.ui.theme.*
import com.karuhundeveloper.notification.ui.viewmodel.RulesViewModel
import com.karuhundeveloper.notification.util.ServiceUtils
import kotlinx.coroutines.delay

private const val DUMMY_CHANNEL_ID   = "dummy_test_channel"
private const val DUMMY_NOTIFICATION_ID = 9_001

// ─────────────────────────────────────────────────────────────────────────────
// Helper: check Notification Listener access
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns true if this app currently has Notification Listener permission granted.
 * Uses the enabled-listener flat list rather than [AppOpsManager] for compatibility
 * down to API 24.
 */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return flat.contains(context.packageName)
}

/**
 * Returns true if the app is currently excluded from battery optimizations.
 */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: post dummy notification
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Creates the required [NotificationChannel] (idempotent on repeated calls) then posts
 * a test notification whose package name appears to come from this app — the
 * [AppNotificationListener] will receive it exactly like any real notification.
 */
fun postDummyNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) return

    // Create channel (required API 26+, no-op on lower)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            DUMMY_CHANNEL_ID,
            "Test Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Channel for dummy test notifications" }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, DUMMY_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Test Notification")
        .setContentText("This is a dummy notification for testing rules.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    // The listener service sees this app's package name as the source;
    // add a rule with targetApp = context.packageName to intercept it.
    NotificationManagerCompat.from(context).notify(DUMMY_NOTIFICATION_ID, notification)
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onAddRuleClick: () -> Unit,
    onRuleClick: (InterceptRule) -> Unit,
    viewModel: RulesViewModel = viewModel()
) {
    val context = LocalContext.current
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val serviceConnected by AppNotificationListener.isConnected.collectAsStateWithLifecycle()
    var ignoringBatteryOptimizations by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var isRepairing by remember { mutableStateOf(false) }

    // Re-check listener permission and battery optimization every time the screen comes back to foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isNotificationListenerEnabled(context)
                ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)

                // If permission is granted but service is NOT connected, try a quick toggle
                // to kickstart the system binding.
                if (listenerEnabled && !serviceConnected && !isRepairing) {
                    ServiceUtils.toggleNotificationListenerService(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-reset repairing state when service connects
    LaunchedEffect(serviceConnected) {
        if (serviceConnected) {
            isRepairing = false
        }
    }

    // Manual polling if repair is active but system is slow to report
    LaunchedEffect(isRepairing) {
        if (isRepairing) {
            // Keep checking every second for 10 seconds or until connected
            repeat(10) {
                if (AppNotificationListener.isConnected.value) {
                    isRepairing = false
                    return@repeat
                }
                delay(1000)
            }
            isRepairing = false
        }
    }

    // Runtime permission launcher for POST_NOTIFICATIONS (Android 13+)
    var notifyPermGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifyPermGranted = granted }

    var showDeleteDialog by remember { mutableStateOf<InterceptRule?>(null) }

    Scaffold(
        containerColor = NavyDeep,
        topBar = { MainTopBar() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRuleClick,
                containerColor = IndigoAccent,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add rule") },
                text = { Text("Add Rule", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // ── Listener permission / Stalled service banner ──────────────────
            item {
                AnimatedVisibility(
                    visible = !listenerEnabled || !serviceConnected || !ignoringBatteryOptimizations,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut(tween(200))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!listenerEnabled) {
                            ListenerPermissionBanner {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } else if (!serviceConnected) {
                            ServiceStalledBanner(isRepairing = isRepairing) {
                                isRepairing = true
                                ServiceUtils.toggleNotificationListenerService(context)
                            }
                        }

                        if (listenerEnabled && !ignoringBatteryOptimizations) {
                            BatteryOptimizationBanner {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    }
                }
            }

            // ── Test button card ──────────────────────────────────────────────
            item {
                TestNotificationCard(
                    permissionGranted = notifyPermGranted,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onSendDummy = { postDummyNotification(context) }
                )
            }

            // ── Section header ────────────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Intercept Rules",
                    count = rules.size
                )
            }

            // ── Rules list ────────────────────────────────────────────────────
            if (rules.isEmpty()) {
                item { EmptyRulesPlaceholder() }
            } else {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onDeleteClick = { showDeleteDialog = rule },
                        onHistoryClick = { onRuleClick(rule) }
                    )
                }
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    showDeleteDialog?.let { rule ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = NavyCard,
            icon = { Icon(Icons.Default.DeleteForever, tint = ErrorRed, contentDescription = null) },
            title = { Text("Delete Rule", color = TextPrimary) },
            text = { Text("Delete \"${rule.ruleName}\"? This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRule(rule)
                    showDeleteDialog = null
                }) { Text("Delete", color = ErrorRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(IndigoAccent, CyanAccent))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Notification Interceptor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        "Forward notifications via API rules",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep)
    )
}

@Composable
private fun BatteryOptimizationBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyElevated),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CyanAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.BatteryAlert, tint = CyanAccent, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Battery Optimization",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    "Disable optimization to prevent the system from killing the listener.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
            }
        }
    }
}

@Composable
private fun ServiceStalledBanner(isRepairing: Boolean, onRepair: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2D1A)), // Dark Amber
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFA000).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isRepairing) Icons.Default.Sync else Icons.Default.SyncProblem,
                    tint = Color(0xFFFFA000),
                    contentDescription = null
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isRepairing) "Repairing listener..." else "Service stalled",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    if (isRepairing) "Waiting for system to re-bind..." else "The system disconnected the listener. Tap Repair to restart it.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onRepair,
                enabled = !isRepairing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFA000),
                    disabledContainerColor = Color(0xFFFFA000).copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    if (isRepairing) "..." else "Repair",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun ListenerPermissionBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1A1A)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ErrorRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.NotificationsOff, tint = ErrorRed, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Listener access required",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    "Grant Notification Access so the app can intercept notifications.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TestNotificationCard(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onSendDummy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🧪  Test Pipeline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Posts a local notification using this app's package name.\nAdd a rule targeting \"${LocalContext.current.packageName}\" to intercept it.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            if (!permissionGranted) {
                OutlinedButton(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Allow", color = CyanAccent, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onSendDummy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(IndigoAccent, CyanAccent))
                        ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Send Dummy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = IndigoAccent.copy(alpha = 0.2f)
        ) {
            Text(
                count.toString(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = IndigoLight
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: InterceptRule,
    onDeleteClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header row ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Method badge
                MethodBadge(method = rule.apiMethod)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rule.ruleName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        rule.apiUrl,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onHistoryClick) {
                    Icon(Icons.Default.History, tint = IndigoLight, contentDescription = "History")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, tint = ErrorRed.copy(alpha = 0.7f), contentDescription = "Delete")
                }
            }

            // ── Expandable detail section ─────────────────────────────────────
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Divider)
                    Spacer(Modifier.height(10.dp))

                    DetailRow(label = "Target Apps", value = rule.targetApps.joinToString(", "))

                    if (rule.customHeaders.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        DetailRow(
                            label = "Headers",
                            value = rule.customHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        )
                    }
                }
            }

            // ── App chips ─────────────────────────────────────────────────────
            if (rule.targetApps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rule.targetApps.take(3).forEach { pkg ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = NavyElevated,
                            modifier = Modifier.border(1.dp, Divider, RoundedCornerShape(20.dp))
                        ) {
                            Text(
                                pkg.substringAfterLast('.'),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    if (rule.targetApps.size > 3) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = IndigoAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "+${rule.targetApps.size - 3}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                color = IndigoLight
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = IndigoLight, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
    }
}

@Composable
private fun EmptyRulesPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(NavyCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Rule,
                contentDescription = null,
                tint = IndigoAccent.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("No rules yet", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap  +  Add Rule  to create your first intercept rule.",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}
