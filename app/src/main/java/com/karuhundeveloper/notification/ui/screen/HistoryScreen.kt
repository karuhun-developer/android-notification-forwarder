package com.karuhundeveloper.notification.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karuhundeveloper.notification.R
import com.karuhundeveloper.notification.data.local.entity.NotificationDelivery
import com.karuhundeveloper.notification.delivery.DeliveryState
import com.karuhundeveloper.notification.data.local.entity.InterceptHistory
import com.karuhundeveloper.notification.ui.theme.*
import com.karuhundeveloper.notification.ui.viewmodel.RulesViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    ruleId: Int,
    ruleName: String,
    onNavigateBack: () -> Unit,
    viewModel: RulesViewModel = viewModel()
) {
    val deliveries by viewModel.getDeliveriesForRule(ruleId).collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val rule = rules.firstOrNull { it.id == ruleId }
    val history by viewModel.getHistoryForRule(ruleId).collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        containerColor = NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.history), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(ruleName, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = TextPrimary)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistoryForRule(ruleId) }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_legacy_history), tint = ErrorRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDeep)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            rule?.let { current ->
                item(key = "retry-setting") {
                    Card(colors = CardDefaults.cardColors(containerColor = NavyCard)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.auto_retry_future_notifications), color = TextPrimary,
                                    modifier = Modifier.weight(1f))
                                Switch(checked = current.autoRetry,
                                    onCheckedChange = { viewModel.setAutoRetry(ruleId, it) })
                            }
                            Text(stringResource(R.string.auto_retry_future_hint),
                                color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (history.isEmpty() && deliveries.isEmpty()) {
                item { EmptyHistoryPlaceholder(modifier = Modifier.height(240.dp)) }
            }
            if (deliveries.isNotEmpty()) {
                item { Text(stringResource(R.string.deliveries), color = TextPrimary, fontWeight = FontWeight.Bold) }
                items(deliveries, key = { "delivery:" + it.id }) { item ->
                    DeliveryCard(item, onRetry = { viewModel.retryDelivery(item.id) })
                }
            }
            if (history.isNotEmpty()) {
                item { Text(stringResource(R.string.legacy_history_note), color = TextSecondary, fontSize = 12.sp) }
                items(history, key = { "history:" + it.id }) { item -> HistoryItemCard(item) }
            }
        }
    }
}

@Composable
private fun formatTimestamp(timestamp: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
        .format(Date(timestamp))
}

@Composable
private fun HistoryItemCard(item: InterceptHistory) {
    val dateStr = formatTimestamp(item.timestamp)
    val errorDetail = when {
        item.errorMessage != null -> item.errorMessage
        item.httpStatusCode != null -> stringResource(R.string.status_with_code, item.httpStatusCode)
        else -> stringResource(R.string.status_unknown)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(isSuccess = item.isSuccess)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(if (item.isSuccess) R.string.success else R.string.failed),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isSuccess) CyanAccent else ErrorRed
                )
                Spacer(Modifier.weight(1f))
                Text(dateStr, fontSize = 10.sp, color = TextSecondary)
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(stringResource(R.string.notification_label), fontSize = 10.sp, color = IndigoLight, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.app_notification_title, item.appName, item.notificationTitle),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(item.notificationText, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)

            if (!item.isSuccess || item.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.error_with_detail, errorDetail),
                        fontSize = 11.sp,
                        color = ErrorRed
                    )
                }
            } else if (item.httpStatusCode != null) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.http_status, item.httpStatusCode), fontSize = 10.sp, color = CyanAccent.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun StatusDot(isSuccess: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (isSuccess) CyanAccent else ErrorRed)
    )
}

@Composable
private fun EmptyHistoryPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_history_for_rule), color = TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun DeliveryCard(item: NotificationDelivery, onRetry: () -> Unit) {
    val label = when (item.state) {
        DeliveryState.QUEUED -> stringResource(R.string.status_queued)
        DeliveryState.SENDING -> stringResource(R.string.status_sending)
        DeliveryState.RETRYING -> stringResource(R.string.status_retrying)
        DeliveryState.DELIVERED -> stringResource(R.string.status_delivered)
        DeliveryState.FAILED -> stringResource(R.string.status_failed_review)
        DeliveryState.CANCELLED -> stringResource(R.string.status_cancelled)
        else -> item.state
    }
    Card(colors = CardDefaults.cardColors(containerColor = NavyCard)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = if (item.state == DeliveryState.DELIVERED) CyanAccent else TextPrimary)
            Text(stringResource(R.string.app_notification_title, item.appName, item.title), color = TextPrimary)
            Text(item.text, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.attempts_auto_retry, item.attempts, stringResource(if (item.autoRetry) R.string.on else R.string.off)), color = TextSecondary, fontSize = 11.sp)
            Text(formatTimestamp(item.capturedAt), color = TextSecondary, fontSize = 11.sp)
            item.httpStatus?.let { Text(stringResource(R.string.http_status, it), color = TextSecondary, fontSize = 11.sp) }
            item.error?.let { Text(it, color = ErrorRed, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (item.state == DeliveryState.FAILED) {
                Text(stringResource(R.string.check_receiver_before_retry), color = TextSecondary, fontSize = 11.sp)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_delivery)) }
            }
        }
    }
}
