package com.karuhundeveloper.notification.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.karuhundeveloper.notification.data.local.dao.NotificationDeliveryDao
import com.karuhundeveloper.notification.data.local.entity.NotificationDelivery
import com.karuhundeveloper.notification.data.local.converter.Converters
import com.karuhundeveloper.notification.data.local.dao.InterceptHistoryDao
import com.karuhundeveloper.notification.data.local.dao.InterceptRuleDao
import com.karuhundeveloper.notification.data.local.entity.InterceptHistory
import com.karuhundeveloper.notification.data.local.entity.InterceptRule

/**
 * The single Room database for the NotificationInterceptor app.
 *
 * ### Schema migrations
 * Increment [version] whenever you change the schema and provide a [Migration] object in
 * the database builder. Explicit migrations preserve existing rules and history.
 *
 * ### Usage
 * Obtain the singleton via [AppDatabase.getInstance] and call the relevant DAO method.
 * Always perform database operations off the main thread (e.g. inside a coroutine or
 * via a Repository that injects a [CoroutineDispatcher]).
 */
@Database(
    entities = [InterceptRule::class, InterceptHistory::class, NotificationDelivery::class],
    version = 3,
    exportSchema = true          // keeps a JSON schema history in app/schemas/ for migration audits
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun interceptRuleDao(): InterceptRuleDao
    abstract fun notificationDeliveryDao(): NotificationDeliveryDao
    abstract fun interceptHistoryDao(): InterceptHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS intercept_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ruleId INTEGER NOT NULL, timestamp INTEGER NOT NULL, appName TEXT NOT NULL, notificationTitle TEXT NOT NULL, notificationText TEXT NOT NULL, isSuccess INTEGER NOT NULL, httpStatusCode INTEGER, errorMessage TEXT, FOREIGN KEY(ruleId) REFERENCES intercept_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intercept_history_ruleId ON intercept_history(ruleId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE intercept_rules ADD COLUMN autoRetry INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS notification_deliveries (id TEXT NOT NULL PRIMARY KEY, eventId TEXT NOT NULL, ruleId INTEGER NOT NULL, packageName TEXT NOT NULL, appName TEXT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, capturedAt INTEGER NOT NULL, postedAt INTEGER NOT NULL, apiUrl TEXT NOT NULL, apiMethod TEXT NOT NULL, customHeaders TEXT NOT NULL, autoRetry INTEGER NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, httpStatus INTEGER, error TEXT, leaseToken TEXT, leaseUntil INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_deliveries_eventId_ruleId ON notification_deliveries(eventId, ruleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_deliveries_state ON notification_deliveries(state)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_deliveries_ruleId ON notification_deliveries(ruleId)")
            }
        }
        private const val DATABASE_NAME = "notification_interceptor.db"

        // Volatile ensures that the INSTANCE write is immediately visible to all threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the existing singleton or builds a new one using double-checked locking.
         *
         * @param context Use the application context to avoid memory leaks.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
