package com.karuhundeveloper.notification.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karuhundeveloper.notification.data.local.AppDatabase
import com.karuhundeveloper.notification.data.local.entity.NotificationDelivery
import com.karuhundeveloper.notification.delivery.DeliveryRepository
import com.karuhundeveloper.notification.delivery.DeliveryScheduler
import com.karuhundeveloper.notification.data.local.entity.InterceptHistory
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the main rules list screen.
 *
 * Exposes [rules] as a [StateFlow] so the Compose UI recomposes automatically whenever
 * the database table changes. All write operations run on [viewModelScope] (IO dispatcher
 * is used inside the Room DAO suspend functions).
 *
 * Uses [AndroidViewModel] to safely hold an application [Context] without leaking an Activity.
 */
class RulesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val ruleDao = db.interceptRuleDao()
    private val historyDao = db.interceptHistoryDao()

    /** Live list of all saved [InterceptRule]s, ordered newest-first. */
    val rules: StateFlow<List<InterceptRule>> = ruleDao.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun insertRule(rule: InterceptRule) {
        viewModelScope.launch { ruleDao.insertRule(rule) }
    }

    fun deleteRule(rule: InterceptRule) {
        viewModelScope.launch { DeliveryRepository(db).deleteRule(rule) }
    }

    fun setAutoRetry(ruleId: Int, enabled: Boolean) {
        viewModelScope.launch { ruleDao.setAutoRetry(ruleId, enabled) }
    }

    fun getHistoryForRule(ruleId: Int): Flow<List<InterceptHistory>> {
        return historyDao.getHistoryForRule(ruleId)
    }

    fun getDeliveriesForRule(ruleId: Int): Flow<List<NotificationDelivery>> =
        db.notificationDeliveryDao().observeForRule(ruleId)

    fun retryDelivery(id: String) {
        viewModelScope.launch { DeliveryScheduler(getApplication()).retryManually(id) }
    }

    fun clearHistoryForRule(ruleId: Int) {
        viewModelScope.launch { historyDao.clearHistoryForRule(ruleId) }
    }
}
