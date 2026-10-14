package com.karuhundeveloper.notification.delivery

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.google.gson.JsonParser
import com.karuhundeveloper.notification.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DatabaseMigrationTest {
    @Test fun versionOnePreservesRulesAndDefaultsRetryOff() = verifyMigration(1)
    @Test fun versionTwoPreservesRulesAndExistingHistory() = verifyMigration(2)

    private fun verifyMigration(version: Int) = runTest {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-$version.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = JsonParser.parseString(File("schemas/com.karuhundeveloper.notification.data.local.AppDatabase/$version.json").readText())
            .asJsonObject.getAsJsonObject("database")
        val tablePlaceholder = "$" + "{TABLE_NAME}"
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            schema.getAsJsonArray("entities").forEach { value ->
                val entity = value.asJsonObject
                val table = entity.get("tableName").asString
                old.execSQL(entity.get("createSql").asString.replace(tablePlaceholder, table))
                entity.getAsJsonArray("indices")?.forEach { index ->
                    old.execSQL(index.asJsonObject.get("createSql").asString.replace(tablePlaceholder, table))
                }
            }
            schema.getAsJsonArray("setupQueries").forEach { old.execSQL(it.asString) }
            old.execSQL("INSERT INTO intercept_rules VALUES(1,'existing','[\"merchant.app\"]','https://example.com','POST','{}')")
            if (version == 2) old.execSQL("INSERT INTO intercept_history VALUES(1,1,1000,'Merchant','Paid','Rp 1000',1,200,NULL)")
            old.version = version
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
        try {
            val rule = db.interceptRuleDao().getRuleById(1)!!
            assertEquals("existing", rule.ruleName)
            assertEquals(listOf("merchant.app"), rule.targetApps)
            assertFalse(rule.autoRetry)
            val history = db.interceptHistoryDao().getHistoryForRule(1).first()
            assertEquals(if (version == 2) 1 else 0, history.size)
            if (version == 2) assertEquals("Rp 1000", history.single().notificationText)
            assertTrue(db.notificationDeliveryDao().unfinished().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
