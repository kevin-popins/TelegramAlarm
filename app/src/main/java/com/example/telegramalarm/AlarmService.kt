package com.example.telegramalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        const val ACTION_START_ALARM = "com.example.telegramalarm.START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.telegramalarm.STOP_ALARM"

        private const val CHANNEL_ID = "alarm_channel"
        private const val NOTIF_ID = 1002
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> startAlarm()
            ACTION_STOP_ALARM -> stopAlarm()
        }
        return START_STICKY
    }

    private fun startAlarm() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) return

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val uriString = prefs.getString(MainActivity.KEY_SOUND_URI, null)

        if (uriString.isNullOrBlank()) {
            // звук не выбран — просто не запускаем ор
            return
        }

        val uri = Uri.parse(uriString)

        // Устанавливаем громкость под выбранный процент
        applySelectedVolume()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
            start()
        }

        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_alarm_title))
            .setContentText(getString(R.string.notif_alarm_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, getString(R.string.action_stop), stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notif)
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(true)
        stopSelf()
    }

    /**
     * Подгоняем громкость потока будильника (и при желании — медиапотока)
     * под сохранённый процент в настройках.
     */
    private fun applySelectedVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Читаем % из настроек
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val percent = prefs
            .getInt(MainActivity.KEY_ALARM_VOLUME, 100)
            .coerceIn(0, 100)

        // Основной поток, через который идёт наш MediaPlayer с USAGE_ALARM
        val alarmStream = AudioManager.STREAM_ALARM
        val maxAlarm = audioManager.getStreamMaxVolume(alarmStream)

        val targetAlarm =
            if (percent == 0) 0
            else (maxAlarm * percent + 99) / 100   // округляем вверх

        audioManager.setStreamVolume(alarmStream, targetAlarm, 0)

        // Опционально: поджать/поднять и медиапоток, если хочешь,
        // чтобы звук был слышен и там, если вдруг система что-то перекинет.
        /*
        val musicStream = AudioManager.STREAM_MUSIC
        val maxMusic = audioManager.getStreamMaxVolume(musicStream)
        val targetMusic =
            if (percent == 0) 0
            else (maxMusic * percent + 99) / 100
        audioManager.setStreamVolume(musicStream, targetMusic, 0)
        */
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_alarm_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.channel_alarm_desc)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
