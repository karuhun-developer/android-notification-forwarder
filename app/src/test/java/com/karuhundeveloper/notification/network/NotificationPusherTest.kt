package com.karuhundeveloper.notification.network

import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.data.model.NotificationPayload
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NotificationPusherTest {
    private val payload = NotificationPayload("Merchant", "Paid", "Rp 1000", 1000)
    private fun rule(url: String, method: String = "POST") = InterceptRule(ruleName = "test",
        targetApps = listOf("merchant"), apiUrl = url, apiMethod = method, customHeaders = emptyMap())

    @Test fun malformedEndpointProducesAnInspectableFailureInsteadOfThrowing() = runTest {
        assertTrue(NotificationPusher().push(rule("broken endpoint"), payload) is PushResult.ConfigurationError)
    }

    @Test fun redirectsAreReturnedWithoutForwardingSensitiveHeadersToAnotherEndpoint() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/other")))
            val result = NotificationPusher().push(rule(server.url("/input").toString()), payload)
            assertTrue(result is PushResult.HttpError)
            assertEquals(302, (result as PushResult.HttpError).statusCode)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun getKeepsLegacyParametersAndPostKeepsLegacyJsonFields() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            NotificationPusher().push(rule(server.url("/input").toString(), "GET"), payload)
            val get = server.takeRequest()
            assertEquals("1000", get.requestUrl!!.queryParameter("timestamp_ms"))
            assertEquals("Rp 1000", get.requestUrl!!.queryParameter("notification_text"))
            server.enqueue(MockResponse().setResponseCode(200))
            NotificationPusher().push(rule(server.url("/input").toString()), payload)
            assertEquals("{\"app_name\":\"Merchant\",\"notification_title\":\"Paid\",\"notification_text\":\"Rp 1000\",\"timestamp_ms\":1000}", server.takeRequest().body.readUtf8())
        } finally { server.shutdown() }
    }

    @Test fun immediate503RetryDoesNotBypassTheDeliveryAttemptBudget() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            for (method in listOf("POST", "GET")) {
                server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "0"))
                server.enqueue(MockResponse().setResponseCode(200))
                val before = server.requestCount
                val result = NotificationPusher().push(rule(server.url("/input").toString(), method), payload)
                assertTrue("503 must reach the delivery policy for " + method, result is PushResult.HttpError)
                assertEquals(503, (result as PushResult.HttpError).statusCode)
                assertEquals(before + 1, server.requestCount)
                assertTrue(NotificationPusher().push(rule(server.url("/drain").toString()), payload) is PushResult.Success)
            }
        } finally { server.shutdown() }
    }
}
