package com.karuhundeveloper.notification.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karuhundeveloper.notification.data.local.entity.NotificationDelivery
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(delivery: NotificationDelivery): Long

    @Query("SELECT * FROM notification_deliveries WHERE id = :id")
    suspend fun get(id: String): NotificationDelivery?

    @Query("SELECT * FROM notification_deliveries WHERE ruleId = :ruleId ORDER BY capturedAt DESC")
    fun observeForRule(ruleId: Int): Flow<List<NotificationDelivery>>

    @Query("SELECT * FROM notification_deliveries WHERE state IN ('QUEUED','RETRYING','SENDING')")
    suspend fun unfinished(): List<NotificationDelivery>

    @Query("UPDATE notification_deliveries SET state = 'SENDING', attempts = attempts + 1, leaseToken = :token, leaseUntil = :until, updatedAt = :now WHERE id = :id AND state IN ('QUEUED','RETRYING')")
    suspend fun claim(id: String, token: String, until: Long, now: Long): Int

    @Query("UPDATE notification_deliveries SET state = :state, httpStatus = :status, error = :error, leaseToken = NULL, leaseUntil = 0, updatedAt = :now WHERE id = :id AND state = 'SENDING' AND leaseToken = :token")
    suspend fun finish(id: String, token: String, state: String, status: Int?, error: String?, now: Long): Int

    @Query("UPDATE notification_deliveries SET state = :state, error = :error, leaseToken = NULL, leaseUntil = 0, updatedAt = :now WHERE id = :id AND state = 'SENDING' AND leaseUntil <= :now")
    suspend fun recoverExpired(id: String, state: String, error: String, now: Long): Int

    @Query("UPDATE notification_deliveries SET state = 'QUEUED', attempts = 0, error = NULL, httpStatus = NULL, updatedAt = :now WHERE id = :id AND state = 'FAILED'")
    suspend fun retryManually(id: String, now: Long): Int

    @Query("UPDATE notification_deliveries SET state = 'CANCELLED', leaseToken = NULL, leaseUntil = 0, updatedAt = :now WHERE ruleId = :ruleId AND state IN ('QUEUED','RETRYING','SENDING','FAILED')")
    suspend fun cancelForRule(ruleId: Int, now: Long)
}
