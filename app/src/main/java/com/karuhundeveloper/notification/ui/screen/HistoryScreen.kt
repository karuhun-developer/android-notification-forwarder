package com.karuhundeveloper.notification.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karuhundeveloper.notification.R
import com.karuhundeveloper.notification.data.local.entity.InterceptHistory
import com.karuhundeveloper.notification.data.local.entity.NotificationDelivery
import com.karuhundeveloper.notification.delivery.DeliveryState
import com.karuhundeveloper.notification.ui.component.EmptyState
import com.karuhundeveloper.notification.ui.component.InlineNotice
import com.karuhundeveloper.notification.ui.component.LoadingBlock
import com.karuhundeveloper.notification.ui.component.MethodLabel
import com.karuhundeveloper.notification.ui.component.NoticeTone
import com.karuhundeveloper.notification.ui.component.ScreenHeader
import com.karuhundeveloper.notification.ui.component.SectionLabel
import com.karuhundeveloper.notification.ui.theme.RelayMonoSmall
import com.karuhundeveloper.notification.ui.theme.RelayShapes
import com.karuhundeveloper.notification.ui.theme.RelayTheme
import com.karuhundeveloper.notification.ui.viewmodel.HistoryUiState
import com.karuhundeveloper.notification.ui.viewmodel.RulesUiState
import com.karuhundeveloper.notification.ui.viewmodel.RulesViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    ruleId: Int,
    ruleName: String,
    onNavigateBack: () -> Unit,
    viewModel: RulesViewModel = viewModel()
) {
    val colors = RelayTheme.colors
    val rulesState by viewModel.rules.collectAsStateWithLifecycle()
    val rule = (rulesState as? RulesUiState.Ready)
        ?.rules
        ?.firstOrNull { it.id == ruleId }

    var historyKey by remember { mutableStateOf(0) }
    val historyFlow = remember(ruleId, historyKey) { viewModel.getHistoryForRule(ruleId) }
    val historyState by historyFlow.collectAsStateWithLifecycle(
        initialValue = HistoryUiState.Loading
    )
    val legacy = (historyState as? HistoryUiState.Ready)?.legacy.orEmpty()

    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.delivery_log),
                subtitle = ruleName.ifBlank { null },
                onBack = onNavigateBack,
                actions = {
                    if (legacy.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_legacy_history),
                                tint = colors.inkMuted
                            )
                        }
                    }
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
                top = 8.dp,
                bottom = 32.dp
            )
        ) {
            if (rule != null) {
                item(key = "retry-setting") {
                    RetrySetting(
                        checked = rule.autoRetry,
                        onCheckedChange = { viewModel.setAutoRetry(ruleId, it) }
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }

            when (val state = historyState) {
                HistoryUiState.Loading -> item(key = "loading") {
                    LoadingBlock(label = stringResource(R.string.loading_deliveries))
                }

                is HistoryUiState.Failed -> item(key = "failed") {
                    InlineNotice(
                        title = stringResource(R.string.error_reading_deliveries),
                        body = state.detail ?: stringResource(R.string.error_unknown),
                        tone = NoticeTone.Error,
                        actionLabel = stringResource(R.string.try_again),
                        onAction = { historyKey += 1 }
                    )
                }

                is HistoryUiState.Ready -> {
                    if (state.deliveries.isEmpty() && state.legacy.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                title = stringResource(R.string.no_history_for_rule),
                                body = stringResource(R.string.no_history_body)
                            )
                        }
                    }

                    if (state.deliveries.isNotEmpty()) {
                        item(key = "deliveries-header") {
                            SectionLabel(
                                text = stringResource(R.string.deliveries),
                                trailing = state.deliveries.size.toString()
                            )
                        }

                        itemsIndexed(
                            state.deliveries,
                            key = { _, delivery -> "delivery:${delivery.id}" }
                        ) { index, delivery ->
                            DeliveryTimelineRow(
                                delivery = delivery,
                                isFirst = index == 0,
                                isLast = index == state.deliveries.lastIndex,
                                onRetry = { viewModel.retryDelivery(delivery.id) }
                            )
                        }
                    }

                    if (state.legacy.isNotEmpty()) {
                        item(key = "legacy-header") {
                            Spacer(Modifier.height(24.dp))
                            SectionLabel(
                                text = stringResource(R.string.legacy_history_note),
                                trailing = state.legacy.size.toString()
                            )
                        }
                        items(
                            state.legacy,
                            key = { "legacy:${it.id}" }
                        ) { item ->
                            LegacyRow(item)
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = colors.surface,
            shape = RelayShapes.medium,
            title = {
                Text(
                    text = stringResource(R.string.clear_history_title),
                    color = colors.ink
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_history_body),
                    color = colors.inkMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistoryForRule(ruleId)
                        showClearDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.clear),
                        color = colors.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = colors.inkMuted,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

@Composable
private fun RetrySetting(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = RelayTheme.colors

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auto_retry_future_notifications),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.auto_retry_future_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.background,
                    checkedTrackColor = colors.ink,
                    checkedBorderColor = colors.ink,
                    uncheckedThumbColor = colors.inkMuted,
                    uncheckedTrackColor = colors.surfaceRaised,
                    uncheckedBorderColor = colors.hairline
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.hairline)
    }
}

@Composable
private fun DeliveryTimelineRow(
    delivery: NotificationDelivery,
    isFirst: Boolean,
    isLast: Boolean,
    onRetry: () -> Unit
) {
    val colors = RelayTheme.colors
    val stateColor = deliveryStateColor(delivery.state)
    val stateLabel = deliveryStateLabel(delivery.state)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        TimelineRail(
            nodeColor = stateColor,
            isFirst = isFirst,
            isLast = isLast
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = stateColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTimestamp(delivery.capturedAt),
                    style = RelayMonoSmall,
                    color = colors.inkMuted
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.app_notification_title,
                    delivery.appName,
                    delivery.title
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (delivery.text.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = delivery.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodLabel(method = delivery.apiMethod)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = delivery.apiUrl,
                    style = RelayMonoSmall,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.attempts_auto_retry,
                        delivery.attempts,
                        delivery.attempts,
                        stringResource(if (delivery.autoRetry) R.string.on else R.string.off)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )
                delivery.httpStatus?.let { status ->
                    Text(
                        text = stringResource(R.string.http_status, status),
                        style = RelayMonoSmall,
                        color = colors.inkMuted
                    )
                }
            }

            delivery.error?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.error_with_detail, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (delivery.state == DeliveryState.FAILED) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.check_receiver_before_retry),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(
                        text = stringResource(R.string.retry_delivery),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.ink
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.hairline)
        }
    }
}

@Composable
private fun TimelineRail(
    nodeColor: Color,
    isFirst: Boolean,
    isLast: Boolean
) {
    val colors = RelayTheme.colors

    Box(
        modifier = Modifier
            .width(22.dp)
            .fillMaxHeight()
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width / 2f
            val nodeY = 8.dp.toPx()
            val stroke = 1.dp.toPx()

            if (!isFirst) {
                drawLine(
                    color = colors.hairline,
                    start = Offset(x, 0f),
                    end = Offset(x, nodeY),
                    strokeWidth = stroke
                )
            }
            if (!isLast) {
                drawLine(
                    color = colors.hairline,
                    start = Offset(x, nodeY),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Butt
                )
            }
            drawCircle(
                color = nodeColor,
                radius = 4.dp.toPx(),
                center = Offset(x, nodeY)
            )
        }
    }
}

@Composable
private fun LegacyRow(item: InterceptHistory) {
    val colors = RelayTheme.colors
    val stateColor = if (item.isSuccess) colors.accent else colors.error
    val detail = when {
        item.errorMessage != null -> item.errorMessage
        item.httpStatusCode != null -> stringResource(
            R.string.status_with_code,
            item.httpStatusCode
        )
        else -> stringResource(R.string.status_unknown)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (item.isSuccess) R.string.success else R.string.failed),
                style = MaterialTheme.typography.titleSmall,
                color = stateColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatTimestamp(item.timestamp),
                style = RelayMonoSmall,
                color = colors.inkMuted
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.app_notification_title,
                item.appName,
                item.notificationTitle
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (item.notificationText.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.notificationText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = RelayMonoSmall,
            color = if (item.isSuccess) colors.inkMuted else colors.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.hairline)
    }
}

@Composable
private fun deliveryStateLabel(state: String): String = when (state) {
    DeliveryState.QUEUED -> stringResource(R.string.status_queued)
    DeliveryState.SENDING -> stringResource(R.string.status_sending)
    DeliveryState.RETRYING -> stringResource(R.string.status_retrying)
    DeliveryState.DELIVERED -> stringResource(R.string.status_delivered)
    DeliveryState.FAILED -> stringResource(R.string.status_failed_review)
    DeliveryState.CANCELLED -> stringResource(R.string.status_cancelled)
    else -> state
}

@Composable
private fun deliveryStateColor(state: String): Color {
    val colors = RelayTheme.colors
    return when (state) {
        DeliveryState.DELIVERED -> colors.accent
        DeliveryState.FAILED -> colors.error
        DeliveryState.SENDING -> colors.ink
        else -> colors.inkMuted
    }
}

@Composable
private fun formatTimestamp(timestamp: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
        .format(Date(timestamp))
}
