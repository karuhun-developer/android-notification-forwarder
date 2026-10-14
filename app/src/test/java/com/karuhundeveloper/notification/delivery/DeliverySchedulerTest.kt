package com.karuhundeveloper.notification.delivery

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.karuhundeveloper.notification.data.local.AppDatabase
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DeliverySchedulerTest {
    private lateinit var db: AppDatabase
    private lateinit var manager: WorkManager
    private lateinit var scheduler: DeliveryScheduler
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        manager = WorkManager.getInstance(context)
        scheduler = DeliveryScheduler(context, db.notificationDeliveryDao(), manager)
    }

    @After fun teardown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
        db.close()
    }

    private suspend fun capture(): String {
        db.interceptRuleDao().insertRule(InterceptRule(ruleName = "test", targetApps = listOf("merchant"),
            apiUrl = "https://example.invalid/notifications", apiMethod = "POST", customHeaders = emptyMap()))
        return DeliveryRepository(db).capture("event", "merchant", "Merchant", "Paid", "Rp 1000", 1000, 900).single()
    }

    private fun work(id: String) = manager.getWorkInfosForUniqueWork("delivery:" + id).get().single()

    @Test fun staleManualRetryLeavesNewlyQueuedWorkIntact() = runTest {
        val id = capture()
        scheduler.enqueue(id)
        val before = work(id)
        assertEquals(WorkInfo.State.ENQUEUED, before.state)
        scheduler.retryManually(id)
        val after = work(id)
        assertEquals(before.id, after.id)
        assertEquals(WorkInfo.State.ENQUEUED, after.state)
    }

    @Test fun recoverySchedulesACaptureThatWasCommittedWithoutWork() = runTest {
        val id = capture()
        assertTrue(manager.getWorkInfosForUniqueWork("delivery:" + id).get().isEmpty())
        scheduler.recover()
        val first = work(id)
        scheduler.recover()
        assertEquals(first.id, work(id).id)
        assertEquals(WorkInfo.State.ENQUEUED, work(id).state)
    }
}
