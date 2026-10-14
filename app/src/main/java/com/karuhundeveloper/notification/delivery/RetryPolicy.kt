package com.karuhundeveloper.notification.delivery

import com.karuhundeveloper.notification.network.PushResult

object RetryPolicy {
    const val MAX_ATTEMPTS = 10
    fun nextState(result: PushResult, autoRetry: Boolean, attempts: Int): String {
        if (result is PushResult.Success) return DeliveryState.DELIVERED
        val transient = when (result) {
            is PushResult.NetworkError -> true
            is PushResult.HttpError -> result.statusCode == 408 || result.statusCode == 429 || result.statusCode in 500..599
            else -> false
        }
        return if (autoRetry && transient && attempts < MAX_ATTEMPTS) DeliveryState.RETRYING else DeliveryState.FAILED
    }
}
