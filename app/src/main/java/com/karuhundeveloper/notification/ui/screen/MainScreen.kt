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
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karuhundeveloper.notification.R
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.service.AppNotificationListener
import com.karuhundeveloper.notification.ui.component.AppTag
import com.karuhundeveloper.notification.ui.component.EmptyState
import com.karuhundeveloper.notification.ui.component.InlineNotice
import com.karuhundeveloper.notification.ui.component.LoadingBlock
import com.karuhundeveloper.notification.ui.component.MethodLabel
import com.karuhundeveloper.notification.ui.component.NoticeTone
import com.karuhundeveloper.notification.ui.component.RelayTag
import com.karuhundeveloper.notification.ui.component.ScreenHeader
import com.karuhundeveloper.notification.ui.component.SectionLabel
import com.karuhundeveloper.notification.ui.component.StatusLine
import com.karuhundeveloper.notification.ui.theme.RelayMono
import com.karuhundeveloper.notification.ui.theme.RelayMonoSmall
import com.karuhundeveloper.notification.ui.theme.RelayShapes
import com.karuhundeveloper.notification.ui.theme.RelayTheme
import com.karuhundeveloper.notification.ui.viewmodel.RulesUiState
import com.karuhundeveloper.notification.ui.viewmodel.RulesViewModel
import com.karuhundeveloper.notification.util.ServiceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DUMMY_CHANNEL_ID = "dummy_test_channel"
private const val DUMMY_NOTIFICATION_ID = 9_001

/**
 * Returns true if this app currently has Notification Listener permission granted.
 * Uses the enabled-listener flat list rather than AppOpsManager for compatibility down to API 24.
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

/**
 * Creates the test notification channel and posts a notification from this app.
 * The listener receives it like any other notification, so a rule targeting this
 * package can exercise the whole delivery path.
 */
fun postDummyNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            DUMMY_CHANNEL_ID,
            context.getString(R.string.test_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.test_notification_channel_description)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, DUMMY_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(context.getString(R.string.test_notification_title))
        .setContentText(context.getString(R.string.test_notification_body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(DUMMY_NOTIFICATION_ID, notification)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onAddRuleClick: () -> Unit,
    onRuleClick: (InterceptRule) -> Unit,
    viewModel: RulesViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = RelayTheme.colors
    val rulesState by viewModel.rules.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val testSentMessage = stringResource(R.string.test_sent)

    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val serviceConnected by AppNotificationListener.isConnected.collectAsStateWithLifecycle()
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }
    var isRepairing by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = isNotificationListenerEnabled(context)
                ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)

                if (listenerEnabled && !serviceConnected && !isRepairing) {
                    ServiceUtils.toggleNotificationListenerService(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(serviceConnected) {
        if (serviceConnected) isRepairing = false
    }

    LaunchedEffect(isRepairing) {
        if (isRepairing) {
            repeat(10) {
                if (AppNotificationListener.isConnected.value) {
                    isRepairing = false
                    return@repeat
                }
                delay(1_000)
            }
            isRepairing = false
        }
    }

    var notifyPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifyPermissionGranted = granted }

    var showDeleteDialog by remember { mutableStateOf<InterceptRule?>(null) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.ink,
                    contentColor = colors.background,
                    shape = RelayShapes.small
                )
            }
        },
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.main_subtitle),
                actions = {
                    IconButton(onClick = { showLanguageSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(R.string.language),
                            tint = colors.ink
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRuleClick,
                containerColor = colors.ink,
                contentColor = colors.background,
                shape = RelayShapes.small,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        text = stringResource(R.string.add_rule),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 104.dp
            )
        ) {
            item(key = "status") {
                StatusBlock(
                    listenerEnabled = listenerEnabled,
                    serviceConnected = serviceConnected,
                    batteryOptimized = ignoringBatteryOptimizations,
                    isRepairing = isRepairing,
                    onGrant = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onRepair = {
                        isRepairing = true
                        ServiceUtils.toggleNotificationListenerService(context)
                    },
                    onBatterySettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                )
            }

            item(key = "test") {
                Spacer(Modifier.height(12.dp))
                TestNotificationRow(
                    permissionGranted = notifyPermissionGranted,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onSend = {
                        postDummyNotification(context)
                        scope.launch { snackbarHostState.showSnackbar(testSentMessage) }
                    }
                )
            }

            item(key = "rules-header") {
                Spacer(Modifier.height(28.dp))
                SectionLabel(
                    text = stringResource(R.string.rules),
                    trailing = (rulesState as? RulesUiState.Ready)?.rules?.size?.toString()
                )
            }

            when (val state = rulesState) {
                RulesUiState.Loading -> item(key = "rules-loading") {
                    LoadingBlock(
                        label = stringResource(R.string.loading_rules),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                is RulesUiState.Failed -> item(key = "rules-error") {
                    Spacer(Modifier.height(12.dp))
                    InlineNotice(
                        title = stringResource(R.string.error_reading_rules),
                        body = state.detail ?: stringResource(R.string.error_unknown),
                        tone = NoticeTone.Error,
                        actionLabel = stringResource(R.string.try_again),
                        onAction = viewModel::reload
                    )
                }

                is RulesUiState.Ready -> {
                    if (state.rules.isEmpty()) {
                        item(key = "rules-empty") {
                            EmptyState(
                                title = stringResource(R.string.no_rules_yet),
                                body = stringResource(R.string.no_rules_message),
                                actionLabel = stringResource(R.string.create_first_rule),
                                onAction = onAddRuleClick
                            )
                        }
                    } else {
                        items(state.rules, key = { it.id }) { rule ->
                            RouteRow(
                                rule = rule,
                                onHistoryClick = { onRuleClick(rule) },
                                onDeleteClick = { showDeleteDialog = rule }
                            )
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { rule ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = colors.surface,
            shape = RelayShapes.medium,
            title = {
                Text(
                    text = stringResource(R.string.delete_rule),
                    color = colors.ink
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_rule_confirmation, rule.ruleName),
                    color = colors.inkMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(rule)
                        showDeleteDialog = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = colors.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = colors.inkMuted,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    if (showLanguageSheet) {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val selectedLanguage = if (appLocales.isEmpty) "" else appLocales[0]?.language.orEmpty()

        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            containerColor = colors.surface,
            contentColor = colors.ink
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_language),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                Spacer(Modifier.height(10.dp))
                listOf(
                    "" to R.string.follow_system,
                    "id" to R.string.language_indonesian,
                    "en" to R.string.language_english
                ).forEach { (tag, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RelayShapes.small)
                            .clickable {
                                showLanguageSheet = false
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(tag)
                                )
                            }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedLanguage == tag) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.ink
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBlock(
    listenerEnabled: Boolean,
    serviceConnected: Boolean,
    batteryOptimized: Boolean,
    isRepairing: Boolean,
    onGrant: () -> Unit,
    onRepair: () -> Unit,
    onBatterySettings: () -> Unit
) {
    Column {
        if (listenerEnabled && serviceConnected && batteryOptimized) {
            StatusLine(
                title = stringResource(R.string.relay_active),
                detail = stringResource(R.string.relay_active_detail),
                live = true
            )
        } else {
            if (!listenerEnabled) {
                InlineNotice(
                    title = stringResource(R.string.listener_access_required),
                    body = stringResource(R.string.listener_permission_message),
                    tone = NoticeTone.Error,
                    actionLabel = stringResource(R.string.grant),
                    onAction = onGrant
                )
            } else if (!serviceConnected) {
                InlineNotice(
                    title = stringResource(
                        if (isRepairing) R.string.repairing_listener else R.string.service_stalled
                    ),
                    body = stringResource(
                        if (isRepairing) R.string.waiting_for_rebind else R.string.service_stalled_message
                    ),
                    tone = NoticeTone.Warning,
                    actionLabel = if (isRepairing) null else stringResource(R.string.repair),
                    onAction = if (isRepairing) null else onRepair
                )
            }

            if (listenerEnabled && !batteryOptimized) {
                Spacer(Modifier.height(8.dp))
                InlineNotice(
                    title = stringResource(R.string.battery_optimization),
                    body = stringResource(R.string.battery_optimization_message),
                    tone = NoticeTone.Warning,
                    actionLabel = stringResource(R.string.settings),
                    onAction = onBatterySettings
                )
            }
        }
    }
}

@Composable
private fun TestNotificationRow(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onSend: () -> Unit
) {
    val colors = RelayTheme.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RelayShapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.hairline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.test_notification),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.test_notification_help,
                        LocalContext.current.packageName
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )
            }
            Spacer(Modifier.width(12.dp))
            if (permissionGranted) {
                Button(
                    onClick = onSend,
                    shape = RelayShapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.background
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.send_test),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onRequestPermission,
                    shape = RelayShapes.small,
                    border = BorderStroke(1.dp, colors.hairline),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.allow_notifications),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.ink
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteRow(
    rule: InterceptRule,
    onHistoryClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colors = RelayTheme.colors
    var expanded by rememberSaveable(rule.id) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rule.ruleName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (rule.autoRetry) {
                RelayTag(text = stringResource(R.string.auto_retry_short))
                Spacer(Modifier.width(4.dp))
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        tint = colors.inkMuted
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = colors.surface
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.delete),
                                color = colors.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = colors.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            MethodLabel(method = rule.apiMethod)
            Spacer(Modifier.width(8.dp))
            Text(
                text = rule.apiUrl,
                style = RelayMono,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rule.targetApps.take(2).forEach { packageName ->
                    AppTag(packageName = packageName)
                }
                if (rule.targetApps.size > 2) {
                    RelayTag(
                        text = stringResource(
                            R.string.more_apps_count,
                            rule.targetApps.size - 2
                        )
                    )
                }
            }
            TextButton(
                onClick = onHistoryClick,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(
                    text = stringResource(R.string.history),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(Modifier.padding(top = 10.dp)) {
                HorizontalDivider(color = colors.hairline)
                Spacer(Modifier.height(10.dp))
                DetailLine(
                    label = stringResource(R.string.target_apps),
                    value = rule.targetApps.joinToString("\n")
                )
                if (rule.customHeaders.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    DetailLine(
                        label = stringResource(R.string.headers),
                        value = rule.customHeaders.entries.joinToString("\n") {
                            "${it.key}: ${it.value}"
                        }
                    )
                }
                if (rule.autoRetry) {
                    Spacer(Modifier.height(10.dp))
                    DetailLine(
                        label = stringResource(R.string.delivery),
                        value = stringResource(R.string.automatic_retry_on)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colors.hairline)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = RelayTheme.colors

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = RelayMonoSmall,
            color = colors.ink
        )
    }
}
