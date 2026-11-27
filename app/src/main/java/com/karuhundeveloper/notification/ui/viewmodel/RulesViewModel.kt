package com.karuhundeveloper.notification.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karuhundeveloper.notification.data.local.AppDatabase
import com.karuhundeveloper.notification.data.local.entity.InterceptHistory
import com.karuhundeveloper.notification.data.local.entity.InterceptRule
import com.karuhundeveloper.notification.data.local.entity.NotificationDelivery
import com.karuhundeveloper.notification.delivery.DeliveryRepository
import com.karuhundeveloper.notification.delivery.DeliveryScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RulesUiState {
    data object Loading : RulesUiState
    data class Ready(val rules: List<InterceptRule>) : RulesUiState
    data class Failed(val detail: String?) : RulesUiState
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Ready(
        val deliveries: List<NotificationDelivery>,
        val legacy: List<InterceptHistory>
    ) : HistoryUiState

    data class Failed(val detail: String?) : HistoryUiState
}

/**
 * ViewModel for the rules list and delivery log.
 *
 * The UI needs to tell loading, empty, and failed states apart, so both reads expose
 * an explicit state instead of starting with an empty list.
 */
class RulesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val ruleDao = db.interceptRuleDao()
    private val historyDao = db.interceptHistoryDao()
    private val reloadTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rules: StateFlow<RulesUiState> = reloadTrigger
        .flatMapLatest {
            ruleDao.getAllRules()
                .map<List<InterceptRule>, RulesUiState> { RulesUiState.Ready(it) }
                .catch { emit(RulesUiState.Failed(it.message)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RulesUiState.Loading
        )

    fun reload() {
        reloadTrigger.value += 1
    }

    fun insertRule(rule: InterceptRule) {
        viewModelScope.launch { ruleDao.insertRule(rule) }
    }

    fun deleteRule(rule: InterceptRule) {
        viewModelScope.launch { DeliveryRepository(db).deleteRule(rule) }
    }

    fun setAutoRetry(ruleId: Int, enabled: Boolean) {
        viewModelScope.launch { ruleDao.setAutoRetry(ruleId, enabled) }
    }

    fun getHistoryForRule(ruleId: Int): Flow<HistoryUiState> {
        val history: Flow<HistoryUiState> = combine(
            db.notificationDeliveryDao().observeForRule(ruleId),
            historyDao.getHistoryForRule(ruleId)
        ) { deliveries, legacy ->
            HistoryUiState.Ready(deliveries, legacy)
        }
        return history.catch {
            emit(HistoryUiState.Failed(it.message))
        }
    }

    fun retryDelivery(id: String) {
        viewModelScope.launch { DeliveryScheduler(getApplication()).retryManually(id) }
    }

    fun clearHistoryForRule(ruleId: Int) {
        viewModelScope.launch { historyDao.clearHistoryForRule(ruleId) }
    }
}
