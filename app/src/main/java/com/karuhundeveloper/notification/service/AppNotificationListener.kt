package com.karuhundeveloper.notification.service

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.karuhundeveloper.notification.data.local.AppDatabase
import com.karuhundeveloper.notification.delivery.DeliveryRepository
import com.karuhundeveloper.notification.delivery.DeliveryScheduler
import com.karuhundeveloper.notification.delivery.EventIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AppNotificationListener : NotificationListenerService() {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val repository by lazy { DeliveryRepository(database) }
    private val scheduler by lazy { DeliveryScheduler(applicationContext) }
    private val installationId by lazy {
        val prefs = getSharedPreferences("listener_identity", MODE_PRIVATE)
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("id", it).commit()) { "Cannot persist listener identity" }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        connected.value = true
        scope.launch {
            try {
                scheduler.installRecovery()
                scheduler.recover()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Cannot recover delivery queue", e) }
        }
        try {
            activeNotifications?.forEach { capture(it) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access unavailable during catch-up", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected.value = false
        requestRebind(ComponentName(this, AppNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { capture(it) }
    }

    private fun capture(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val capturedAt = System.currentTimeMillis()
        scope.launch {
            try {
                val eventId = EventIdentity.create(installationId, sbn.key, sbn.postTime, title, text)
                val ids = repository.capture(eventId, sbn.packageName, resolveAppLabel(sbn.packageName),
                    title, text, capturedAt, sbn.postTime)
                ids.forEach { scheduler.enqueue(it) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Capture or scheduling failed for package=" + sbn.packageName, e) }
        }
    }

    override fun onDestroy() {
        connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    private fun resolveAppLabel(packageName: String): String = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) { packageName }

    companion object {
        private const val TAG = "AppNotificationListener"
        private val connected = MutableStateFlow(false)
        val isConnected = connected.asStateFlow()
    }
}
