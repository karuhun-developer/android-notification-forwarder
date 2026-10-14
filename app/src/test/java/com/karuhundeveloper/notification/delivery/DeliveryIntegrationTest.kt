package com.karuhundeveloper.notification.delivery

import android.content.Context
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.karuhundeveloper.notification.data.local.AppDatabase
import com.karuhundeveloper.notification.data.local.dao.NotificationDeliveryDao
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.data.local.entity.InterceptHistory
import com.karuhundeveloper.notification.network.NotificationPusher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DeliveryIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "delivery-integration.db"
    private var now = 1000L

    @Before fun setup() {
        context.deleteDatabase(databaseName)
        db = openDatabase()
        server = MockWebServer().also { it.start(InetAddress.getByName("127.0.0.1"), 0) }
    }
    @After fun teardown() { db.close(); server.shutdown(); context.deleteDatabase(databaseName) }
    private fun openDatabase() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
    private fun engine() = DeliveryEngine(db.notificationDeliveryDao(), NotificationPusher()) { now }
    private suspend fun addRule(autoRetry: Boolean = false): InterceptRule {
        val rule = InterceptRule(ruleName = "merchant", targetApps = listOf("merchant.app"),
            apiUrl = server.url("/notifications").newBuilder().host("127.0.0.1").build().toString(), apiMethod = "POST",
            customHeaders = mapOf("Authorization" to "Bearer device-test", "X-Delivery-Id" to "spoofed"),
            autoRetry = autoRetry)
        return rule.copy(id = db.interceptRuleDao().insertRule(rule).toInt())
    }
    private suspend fun capture(event: String = "event-1") = DeliveryRepository(db)
        .capture(event, "merchant.app", "Merchant", "Paid", "Rp 1000", 1000, 900)

    @Test fun captureIsDurableBeforeSchedulingAndReplayDoesNotCreateAnotherDelivery() = runTest {
        val rule = addRule()
        val id = capture().single()
        assertEquals(0, server.requestCount)
        db.close()
        db = openDatabase()
        assertEquals(DeliveryState.QUEUED, db.notificationDeliveryDao().get(id)?.state)
        assertTrue(capture().isEmpty())
        assertEquals(1, capture("event-2").size)
        assertEquals(2, db.notificationDeliveryDao().observeForRule(rule.id).first().size)
    }

    @Test fun acceptedRequestWithLostResponseRetriesWithIdenticalPayloadAndIdentity() = runTest {
        addRule(autoRetry = true)
        val id = capture().single()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setResponseCode(200).setBody("stored"))
        assertEquals(DeliveryState.RETRYING, engine().deliver(id))
        assertEquals(1, server.requestCount)
        assertEquals(DeliveryState.DELIVERED, engine().deliver(id))
        val first = server.takeRequest(2, TimeUnit.SECONDS)!!
        val second = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
        assertEquals("event-1", first.getHeader("X-Notification-Event-Id"))
        assertEquals(id, first.getHeader("X-Delivery-Id"))
        assertEquals(id, second.getHeader("X-Delivery-Id"))
        assertEquals("merchant.app", second.getHeader("X-Notification-Package"))
        assertEquals("Bearer device-test", second.getHeader("Authorization"))
        assertEquals(2, db.notificationDeliveryDao().get(id)?.attempts)
    }

    @Test fun retryIsOptInAndManualRetryKeepsTheDeliveryIdentity() = runTest {
        addRule()
        val id = capture().single()
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        assertEquals(DeliveryState.FAILED, engine().deliver(id))
        assertEquals(DeliveryState.FAILED, engine().deliver(id))
        assertEquals(1, server.requestCount)
        assertEquals(1, db.notificationDeliveryDao().retryManually(id, now))
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(DeliveryState.DELIVERED, engine().deliver(id))
        server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals(id, server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("X-Delivery-Id"))
    }

    @Test fun permanentHttpFailureDoesNotRetryEvenWhenEnabled() = runTest {
        addRule(true)
        val id = capture().single()
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        assertEquals(DeliveryState.FAILED, engine().deliver(id))
        assertEquals(401, db.notificationDeliveryDao().get(id)?.httpStatus)
        assertEquals("unauthorized", db.notificationDeliveryDao().get(id)?.error)
        assertEquals(1, server.requestCount)
    }

    @Test fun invalidConfigurationIsPersistedAsFailedInsteadOfLeavingSending() = runTest {
        val rule = addRule(true)
        db.interceptRuleDao().updateRule(rule.copy(apiUrl = "invalid endpoint"))
        val id = capture().single()
        assertEquals(DeliveryState.FAILED, engine().deliver(id))
        assertNotNull(db.notificationDeliveryDao().get(id)?.error)
        assertEquals(0, server.requestCount)
    }

    @Test fun parallelClaimsHaveOneOwnerAndStaleOwnerCannotOverwriteItsReplacement() = runTest {
        addRule(true)
        val id = capture().single()
        val dao = db.notificationDeliveryDao()
        val claims = listOf("a", "b").map { token -> async { dao.claim(id, token, 2000, now) } }.awaitAll()
        assertEquals(1, claims.sum())
        val oldToken = dao.get(id)!!.leaseToken!!
        now = 3000
        assertEquals(1, dao.recoverExpired(id, DeliveryState.RETRYING, "unknown", now))
        assertEquals(1, dao.claim(id, "new-owner", 5000, now))
        assertEquals(0, dao.finish(id, oldToken, DeliveryState.DELIVERED, 200, null, now))
        assertEquals(DeliveryState.SENDING, dao.get(id)?.state)
        assertEquals(1, dao.finish(id, "new-owner", DeliveryState.DELIVERED, 200, null, now))
    }

    @Test fun expiredSendingWithoutOptInRequiresReviewAndDoesNotSendAgain() = runTest {
        addRule()
        val id = capture().single()
        val dao = db.notificationDeliveryDao()
        dao.claim(id, "interrupted", 2000, now)
        now = 3000
        assertEquals(DeliveryState.FAILED, engine().deliver(id))
        assertTrue(dao.get(id)!!.error!!.contains("unknown"))
        assertEquals(0, server.requestCount)
    }

    @Test fun expiredSendingWithOptInRecoversAndRespectsTheAttemptBudget() = runTest {
        addRule(true)
        val id = capture().single()
        val dao = db.notificationDeliveryDao()
        dao.claim(id, "interrupted", 2000, now)
        now = 3000
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(DeliveryState.DELIVERED, engine().deliver(id))
        assertEquals(id, server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("X-Delivery-Id"))
        val exhausted = dao.get(id)!!.copy(id = "exhausted", eventId = "event-budget",
            state = DeliveryState.SENDING, attempts = RetryPolicy.MAX_ATTEMPTS, leaseUntil = 2000)
        dao.insert(exhausted)
        assertEquals(DeliveryState.FAILED, engine().deliver("exhausted"))
        assertEquals(1, server.requestCount)
    }

    @Test fun deletingRuleCancelsQueueAndHistoryClearPreservesDeduplication() = runTest {
        val rule = addRule()
        val id = capture().single()
        db.interceptHistoryDao().insertHistory(InterceptHistory(ruleId = rule.id, appName = "Merchant",
            notificationTitle = "Paid", notificationText = "Rp 1000", isSuccess = true, httpStatusCode = 200))
        db.interceptHistoryDao().clearHistoryForRule(rule.id)
        assertTrue(capture().isEmpty())
        DeliveryRepository(db).deleteRule(rule)
        assertEquals(DeliveryState.CANCELLED, db.notificationDeliveryDao().get(id)?.state)
        assertEquals(DeliveryState.CANCELLED, engine().deliver(id))
        assertNull(db.interceptRuleDao().getRuleById(rule.id))
        assertEquals(0, server.requestCount)
    }

    @Test fun deletionAfterClaimPreventsStartingAnHttpRequest() = runTest {
        val rule = addRule()
        val id = capture().single()
        val dao = db.notificationDeliveryDao()
        val cancelAfterClaim = object : NotificationDeliveryDao by dao {
            override suspend fun claim(id: String, token: String, until: Long, now: Long): Int {
                val result = dao.claim(id, token, until, now)
                dao.cancelForRule(rule.id, now)
                return result
            }
        }
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(DeliveryState.CANCELLED, DeliveryEngine(cancelAfterClaim, NotificationPusher()) { now }.deliver(id))
        assertEquals(0, server.requestCount)
    }
}
