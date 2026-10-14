package com.karuhundeveloper.notification.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.karuhundeveloper.notification.delivery.DeliveryState

@Entity(tableName = "notification_deliveries", indices = [
    Index(value = ["eventId", "ruleId"], unique = true), Index("state"), Index("ruleId")
])
data class NotificationDelivery(
    @PrimaryKey val id: String,
    val eventId: String,
    val ruleId: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val capturedAt: Long,
    val postedAt: Long,
    val apiUrl: String,
    val apiMethod: String,
    val customHeaders: Map<String, String>,
    val autoRetry: Boolean,
    val state: String = DeliveryState.QUEUED,
    val attempts: Int = 0,
    val httpStatus: Int? = null,
    val error: String? = null,
    val leaseToken: String? = null,
    val leaseUntil: Long = 0,
    val updatedAt: Long = capturedAt
)
