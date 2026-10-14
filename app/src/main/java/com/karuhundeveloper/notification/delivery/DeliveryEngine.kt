package com.karuhundeveloper.notification.delivery

import com.karuhundeveloper.notification.data.local.dao.NotificationDeliveryDao
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.data.model.NotificationPayload
import com.karuhundeveloper.notification.network.NotificationPusher
import com.karuhundeveloper.notification.network.PushResult
import kotlinx.coroutines.CancellationException
import java.util.UUID

class DeliveryEngine(private val dao: NotificationDeliveryDao, private val pusher: NotificationPusher,
    private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun deliver(id: String): String {
        val existing = dao.get(id) ?: return DeliveryState.CANCELLED
        val now = clock()
        if (existing.state == DeliveryState.SENDING && existing.leaseUntil > now) return DeliveryState.RETRYING
        if (existing.state == DeliveryState.SENDING) {
            val state = if (existing.autoRetry && existing.attempts < RetryPolicy.MAX_ATTEMPTS) DeliveryState.RETRYING else DeliveryState.FAILED
            dao.recoverExpired(id, state, "Previous request outcome unknown; delivery interrupted", now)
        }
        val token = UUID.randomUUID().toString()
        if (dao.claim(id, token, now + LEASE_MS, now) == 0) return dao.get(id)?.state ?: DeliveryState.CANCELLED
        val item = dao.get(id) ?: return DeliveryState.CANCELLED
        if (item.state != DeliveryState.SENDING || item.leaseToken != token) return item.state
        val rule = InterceptRule(item.ruleId, "delivery", listOf(item.packageName), item.apiUrl,
            item.apiMethod, item.customHeaders, item.autoRetry)
        try {
            val result = pusher.push(rule, NotificationPayload(item.appName, item.title, item.text, item.capturedAt),
                item.eventId, item.id, item.packageName)
            val state = RetryPolicy.nextState(result, item.autoRetry, item.attempts)
            val status = when (result) {
                is PushResult.Success -> result.statusCode
                is PushResult.HttpError -> result.statusCode
                else -> null
            }
            val error = when (result) {
                is PushResult.HttpError -> result.errorBody.take(2000)
                is PushResult.NetworkError -> result.cause.message
                is PushResult.UnsupportedMethod -> "Unsupported method: " + result.method
                is PushResult.ConfigurationError -> result.message
                is PushResult.Success -> null
            }
            dao.finish(id, token, state, status, error, clock())
            return dao.get(id)?.state ?: DeliveryState.CANCELLED
        } catch (e: CancellationException) {
            throw e
        }
    }

    companion object { const val LEASE_MS = 120_000L }
}
