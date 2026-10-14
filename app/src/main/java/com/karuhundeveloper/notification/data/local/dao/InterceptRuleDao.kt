package com.karuhundeveloper.notification.data.local.dao

import androidx.room.*
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [InterceptRule].
 *
 * All write operations are suspend functions so they must be called from a coroutine or
 * another suspend function, keeping the main thread safe.
 *
 * [getAllRules] returns a [Flow] so the UI layer can observe live database changes without
 * manual polling.
 */
@Dao
interface InterceptRuleDao {

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Emits the full list of rules whenever the table changes.
     * Ordered by [InterceptRule.id] descending so newest rules appear first.
     */
    @Query("SELECT * FROM intercept_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<InterceptRule>>

    /**
     * Returns a single rule by its primary key, or null if not found.
     */
    @Query("SELECT * FROM intercept_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Int): InterceptRule?

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Inserts a new rule. If a rule with the same primary key already exists the old
     * row is replaced (upsert-style behaviour).
     *
     * @return The row-id of the newly inserted rule.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: InterceptRule): Long

    /**
     * Updates an existing rule matched by its primary key.
     */
    @Update
    suspend fun updateRule(rule: InterceptRule)

    @Query("UPDATE intercept_rules SET autoRetry = :enabled WHERE id = :id")
    suspend fun setAutoRetry(id: Int, enabled: Boolean)

    /**
     * Deletes a specific rule.
     */
    @Delete
    suspend fun deleteRule(rule: InterceptRule)

    /**
     * Deletes a rule by its primary key.
     */
    @Query("DELETE FROM intercept_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)

    /**
     * Removes all rules from the table. Use with caution.
     */
    @Query("DELETE FROM intercept_rules")
    suspend fun deleteAllRules()
}
