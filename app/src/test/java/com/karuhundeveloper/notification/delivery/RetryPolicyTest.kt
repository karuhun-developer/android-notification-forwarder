package com.karuhundeveloper.notification.delivery

import com.karuhundeveloper.notification.network.PushResult
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RetryPolicyTest {
    @Test fun transientFailuresRetryOnlyWhenRuleOptsInAndBudgetRemains() {
        val failures = listOf(PushResult.NetworkError(IOException("offline")),
            PushResult.HttpError(408, "timeout"), PushResult.HttpError(429, "busy"), PushResult.HttpError(503, "down"))
        failures.forEach {
            assertEquals(DeliveryState.RETRYING, RetryPolicy.nextState(it, true, 1))
            assertEquals(DeliveryState.FAILED, RetryPolicy.nextState(it, false, 1))
            assertEquals(DeliveryState.FAILED, RetryPolicy.nextState(it, true, 10))
        }
    }
    @Test fun permanentFailuresNeverRetryAutomatically() {
        listOf(PushResult.HttpError(401, "auth"), PushResult.HttpError(404, "endpoint"),
            PushResult.UnsupportedMethod("PURGE"), PushResult.ConfigurationError("url")).forEach {
            assertEquals(DeliveryState.FAILED, RetryPolicy.nextState(it, true, 1))
        }
    }
    @Test fun successOnLastAttemptIsDelivered() {
        assertEquals(DeliveryState.DELIVERED, RetryPolicy.nextState(PushResult.Success(204, ""), true, 10))
    }
}
