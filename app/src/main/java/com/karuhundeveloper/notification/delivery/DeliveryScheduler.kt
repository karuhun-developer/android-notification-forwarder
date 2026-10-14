package com.karuhundeveloper.notification.delivery

import android.content.Context
import androidx.work.*
import com.karuhundeveloper.notification.data.local.AppDatabase
import com.karuhundeveloper.notification.data.local.dao.NotificationDeliveryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DeliveryScheduler(context: Context,
    private val dao: NotificationDeliveryDao = AppDatabase.getInstance(context.applicationContext).notificationDeliveryDao(),
    private val manager: WorkManager = WorkManager.getInstance(context.applicationContext)) {

    suspend fun enqueue(id: String) = enqueue(id, ExistingWorkPolicy.KEEP)

    private suspend fun enqueue(id: String, policy: ExistingWorkPolicy) = withContext(Dispatchers.IO) {
        val request = OneTimeWorkRequestBuilder<DeliveryWorker>()
            .setInputData(workDataOf("delivery_id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniqueWork("delivery:" + id, policy, request).result.get()
    }

    suspend fun recover() {
        dao.unfinished().forEach { item ->
            if (item.state == DeliveryState.SENDING && item.leaseUntil <= System.currentTimeMillis()) {
                val state = if (item.autoRetry && item.attempts < RetryPolicy.MAX_ATTEMPTS) DeliveryState.RETRYING else DeliveryState.FAILED
                dao.recoverExpired(item.id, state, "Previous request outcome unknown; delivery interrupted", System.currentTimeMillis())
                if (state == DeliveryState.FAILED) return@forEach
            }
            enqueue(item.id)
        }
    }

    suspend fun retryManually(id: String) = withContext(Dispatchers.IO) {
        if (dao.retryManually(id, System.currentTimeMillis()) > 0) {
            enqueue(id, ExistingWorkPolicy.REPLACE)
        }
    }

    fun installRecovery() {
        val request = PeriodicWorkRequestBuilder<DeliveryRecoveryWorker>(15, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork("delivery-recovery", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class DeliveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("delivery_id") ?: return Result.failure()
        val state = DeliveryEngine(AppDatabase.getInstance(applicationContext).notificationDeliveryDao(),
            com.karuhundeveloper.notification.network.NotificationPusher()).deliver(id)
        return if (state == DeliveryState.RETRYING) Result.retry() else Result.success()
    }
}

class DeliveryRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        DeliveryScheduler(applicationContext).recover()
        return Result.success()
    }
}
