package com.karuhundeveloper.notification.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single notification interception rule stored in the local database.
 *
 * @property id Auto-generated primary key.
 * @property ruleName Human-readable name for this rule.
 * @property targetApps List of package names this rule applies to (e.g. "com.whatsapp").
 * @property apiUrl The endpoint URL where the notification payload will be forwarded.
 * @property apiMethod HTTP method to use when calling the API (e.g. "POST", "GET").
 * @property customHeaders Additional HTTP headers to include in the API request.
 */
@Entity(tableName = "intercept_rules")
data class InterceptRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val ruleName: String,

    // Stored as a JSON array string; converted by StringListConverter
    val targetApps: List<String>,

    val apiUrl: String,

    val apiMethod: String,

    // Stored as a JSON object string; converted by StringMapConverter
    val customHeaders: Map<String, String>,

    @androidx.room.ColumnInfo(defaultValue = "0")
    val autoRetry: Boolean = false
)
