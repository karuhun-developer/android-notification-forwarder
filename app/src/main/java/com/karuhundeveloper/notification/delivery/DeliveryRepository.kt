package com.karuhundeveloper.notification.delivery

import androidx.room.withTransaction
import com.karuhundeveloper.notification.data.local.AppDatabase
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.data.local.entity.NotificationDelivery
import java.util.UUID
import kotlinx.coroutines.flow.first

class DeliveryRepository(private val db: AppDatabase) {
    suspend fun capture(eventId: String, packageName: String, appName: String, title: String,
        text: String, capturedAt: Long, postedAt: Long): List<String> = db.withTransaction {
        val ids = mutableListOf<String>()
        val rules = db.interceptRuleDao().getAllRules().first()
        rules.filter { packageName in it.targetApps }.forEach { rule ->
            val item = NotificationDelivery(UUID.randomUUID().toString(), eventId, rule.id,
                packageName, appName, title, text, capturedAt, postedAt, rule.apiUrl,
                rule.apiMethod, rule.customHeaders, rule.autoRetry)
            if (db.notificationDeliveryDao().insert(item) != -1L) ids.add(item.id)
        }
        ids
    }

    suspend fun deleteRule(rule: InterceptRule) = db.withTransaction {
        db.notificationDeliveryDao().cancelForRule(rule.id, System.currentTimeMillis())
        db.interceptRuleDao().deleteRule(rule)
    }
}
