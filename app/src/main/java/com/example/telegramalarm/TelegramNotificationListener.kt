package com.example.telegramalarm

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class TelegramNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "TelegramListener"
        private const val TELEGRAM_PACKAGE = "org.telegram.messenger"

        private const val PREFS_NAME = "settings"
        private const val KEY_CHAT_NAME = "chat_key"
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        if (packageName != TELEGRAM_PACKAGE) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // ------------------------------
        //  ВОТ ЭТО ГЛАВНОЕ!!!
        //  Если отслеживание выключено — выходим.
        // ------------------------------
        val trackingEnabled = prefs.getBoolean(KEY_TRACKING_ENABLED, false)
        if (!trackingEnabled) {
            Log.d(TAG, "Отслеживание отключено — уведомление игнорируется")
            return
        }

        // читаем списки рабочих чатов
        val rawKeys = prefs.getString(KEY_CHAT_NAME, "") ?: ""
        val keysList = rawKeys
            .split(";")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        if (keysList.isEmpty()) {
            Log.d(TAG, "Ключи чатов не заданы — игнор")
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullText = (title + " " + text + " " + bigText).lowercase()

        // ------------------------------
        //  ПОИСК ЛЮБОГО ВСТРЕЧАЮЩЕГОСЯ ФРАГМЕНТА
        // ------------------------------
        val matched = keysList.any { fullText.contains(it) }

        if (matched) {
            Log.d(TAG, "Совпадение с рабочим чатом — запускаем будильник")
            startAlarm()
        } else {
            Log.d(TAG, "Совпадений не найдено")
        }
    }

    private fun startAlarm() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
