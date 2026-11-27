package com.karuhundeveloper.notification.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfoItem(
    val name: String,
    val packageName: String
)

class AppPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val _installedApps = MutableStateFlow<List<AppInfoItem>>(emptyList())
    val installedApps: StateFlow<List<AppInfoItem>> = _installedApps

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadInstalledApps()
    }

    fun refresh() {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val packageManager = getApplication<Application>().packageManager
                    packageManager
                        .getInstalledApplications(PackageManager.GET_META_DATA)
                        .map { app ->
                            AppInfoItem(
                                name = app.loadLabel(packageManager).toString(),
                                packageName = app.packageName
                            )
                        }
                        .sortedBy { it.name.lowercase() }
                }
            }

            result.onSuccess { _installedApps.value = it }
                .onFailure { _error.value = it.localizedMessage ?: it.javaClass.simpleName }
            _isLoading.value = false
        }
    }
}
