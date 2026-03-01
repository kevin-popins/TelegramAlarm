package com.example.telegramalarm

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_NOTIF_PERMISSION = 1001

        const val PREFS_NAME = "settings"
        const val KEY_CHAT_NAME = "chat_key"
        const val KEY_SOUND_URI = "sound_uri"
        const val KEY_TRACKING_ENABLED = "tracking_enabled"
        const val KEY_ALARM_VOLUME = "alarm_volume"
        const val KEY_APP_LANGUAGE = "app_language" // "ru" | "en"

        private const val STATUS_CHANNEL_ID = "listener_status"
        private const val STATUS_NOTIF_ID = 2001

        // Свайпы между вкладками делаем менее чувствительными, чтобы вертикальный скролл
        // на экране настроек не приводил к случайным переключениям.
        private const val SWIPE_THRESHOLD = 220
        private const val SWIPE_VELOCITY_THRESHOLD = 900
        private const val SWIPE_OFF_AXIS_RATIO = 1.8f
    }

    private var glowAnimator: ObjectAnimator? = null

    private lateinit var infoText: TextView
    private lateinit var chatNameEdit: EditText
    private lateinit var currentSoundText: TextView
    private lateinit var trackingStatusText: TextView
    private lateinit var trackingToggle: ImageButton

    private lateinit var homeContainer: View
    private lateinit var settingsContainer: View
    private lateinit var infoContainer: View

    private lateinit var tabHome: TextView
    private lateinit var tabSettings: TextView
    private lateinit var tabInfo: TextView
    private lateinit var iconHome: ImageView
    private lateinit var iconSettings: ImageView
    private lateinit var iconInfo: ImageView

    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeLabel: TextView

    private lateinit var languageButton: MaterialButton

    private var trackingEnabled = false
    private var currentPage = 0

    private lateinit var gestureDetector: GestureDetector

    private val pickSoundLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                prefs.edit().putString(KEY_SOUND_URI, uri.toString()).apply()
                currentSoundText.text = getString(R.string.sound_selected, uri.toString())

                showStatusNotification()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Применяем выбранный язык ДО загрузки layout, чтобы тексты сразу подхватились.
        applySavedLanguageIfAny()

        // ------------------------
        //   ПРОВЕРКА АКТИВАЦИИ
        // ------------------------


        // ------------------------
        //   ОСНОВНОЙ UI
        // ------------------------
        setContentView(R.layout.activity_main)

        homeContainer = findViewById(R.id.homeContainer)
        settingsContainer = findViewById(R.id.settingsContainer)
        infoContainer = findViewById(R.id.infoContainer)

        tabHome = findViewById(R.id.tabHome)
        tabSettings = findViewById(R.id.tabSettings)
        tabInfo = findViewById(R.id.tabInfo)

        iconHome = findViewById(R.id.iconHome)
        iconSettings = findViewById(R.id.iconSettings)
        iconInfo = findViewById(R.id.iconInfo)

        infoText = findViewById(R.id.currentChatInfo)
        chatNameEdit = findViewById(R.id.chatNameEdit)
        currentSoundText = findViewById(R.id.selectedSoundText)
        trackingStatusText = findViewById(R.id.trackingStatusText)
        trackingToggle = findViewById(R.id.trackingToggle)

        volumeSeekBar = findViewById(R.id.alarmVolumeSeekBar)
        volumeLabel = findViewById(R.id.volumeLabel)

        val saveChatNameButton: MaterialButton = findViewById(R.id.saveChatButton)
        val selectSoundButton: MaterialButton = findViewById(R.id.selectSoundButton)
        val openNotifSettingsButton: MaterialButton =
            findViewById(R.id.openNotificationSettingsButton)
        val disableBatteryButton: MaterialButton =
            findViewById(R.id.disableBatteryOptimizationButton)

        languageButton = findViewById(R.id.languageButton)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // восстановление tracking_enabled
        trackingEnabled = prefs.getBoolean(KEY_TRACKING_ENABLED, false)

        // восстановление названий чатов
        val rawSaved = prefs.getString(KEY_CHAT_NAME, "") ?: ""
        val savedList = rawSaved.split(";").map { it.trim() }.filter { it.isNotEmpty() }

        if (savedList.isNotEmpty()) {
            chatNameEdit.setText(rawSaved)
            infoText.text = if (savedList.size == 1) {
                getString(R.string.current_chat_single, savedList[0])
            } else {
                getString(R.string.current_chats_multi, savedList.joinToString("; "))
            }
        } else {
            infoText.text = getString(R.string.settings_intro)
        }

        // восстановление звука
        val savedSoundUri = prefs.getString(KEY_SOUND_URI, null)
        currentSoundText.text =
            if (savedSoundUri != null) getString(R.string.sound_selected, savedSoundUri)
            else getString(R.string.sound_not_selected)

        // восстановление громкости
        val volumePercent = prefs.getInt(KEY_ALARM_VOLUME, 100).coerceIn(0, 100)
        volumeSeekBar.progress = volumePercent
        volumeLabel.text = getString(R.string.volume_label_format, volumePercent)

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(0, 100)
                volumeLabel.text = getString(R.string.volume_label_format, value)
                prefs.edit().putInt(KEY_ALARM_VOLUME, value).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // состояние кота
        if (trackingEnabled) {
            trackingToggle.setImageResource(R.drawable.kevin_vkl)
            trackingToggle.setBackgroundResource(R.drawable.toggle_button_on)
            trackingStatusText.text = getString(R.string.tracking_on)
            startGlowAnimation()
        } else {
            trackingToggle.setImageResource(R.drawable.kevin_off)
            trackingToggle.setBackgroundResource(R.drawable.toggle_button_off)
            trackingStatusText.text = getString(R.string.tracking_off)
        }

        trackingToggle.setOnClickListener {
            trackingEnabled = !trackingEnabled
            prefs.edit().putBoolean(KEY_TRACKING_ENABLED, trackingEnabled).apply()

            if (trackingEnabled) {
                trackingToggle.setImageResource(R.drawable.kevin_vkl)
                trackingToggle.setBackgroundResource(R.drawable.toggle_button_on)
                trackingStatusText.text = getString(R.string.tracking_on)
                startGlowAnimation()
                showStatusNotification()
            } else {
                trackingToggle.setImageResource(R.drawable.kevin_off)
                trackingToggle.setBackgroundResource(R.drawable.toggle_button_off)
                trackingStatusText.text = getString(R.string.tracking_off)
                stopGlowAnimation()
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(STATUS_NOTIF_ID)
            }
        }

        saveChatNameButton.setOnClickListener {
            val rawInput = chatNameEdit.text.toString()
            val names = rawInput.split(";").map { it.trim() }.filter { it.isNotEmpty() }

            if (names.isNotEmpty()) {
                val normalized = names.joinToString("; ")
                prefs.edit().putString(KEY_CHAT_NAME, normalized).apply()

                infoText.text = if (names.size == 1) {
                    getString(R.string.saved_chat_single, names[0])
                } else {
                    getString(R.string.saved_chats_multi, names.joinToString("; "))
                }

                showStatusNotification()
            } else {
                prefs.edit().putString(KEY_CHAT_NAME, "").apply()
                infoText.text = getString(R.string.chats_cleared)
            }
        }

        selectSoundButton.setOnClickListener {
            pickSoundLauncher.launch(arrayOf("audio/*"))
        }

        openNotifSettingsButton.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        disableBatteryButton.setOnClickListener {
            ensureBatteryOptimizationDisabled()
            showStatusNotification()
        }

        // Язык приложения (RU/EN)
        updateLanguageButtonText()
        languageButton.setOnClickListener {
            showLanguageDialog()
        }

        askNotificationPermissionIfNeeded()

        tabHome.setOnClickListener { switchToHome() }
        iconHome.setOnClickListener { switchToHome() }
        tabSettings.setOnClickListener { switchToSettings() }
        iconSettings.setOnClickListener { switchToSettings() }
        tabInfo.setOnClickListener { switchToInfo() }
        iconInfo.setOnClickListener { switchToInfo() }

        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val startX = e1?.x ?: e2.x
                    val startY = e1?.y ?: e2.y
                    val diffX = e2.x - startX
                    val diffY = e2.y - startY

                    val absDiffX = kotlin.math.abs(diffX)
                    val absDiffY = kotlin.math.abs(diffY)

                    // Требуем выраженное горизонтальное движение и достаточную скорость.
                    // Дополнительно отсеиваем жесты, где вертикальная компонента сопоставима
                    // с горизонтальной (типичный скролл/флинг списка).
                    if (absDiffX > SWIPE_THRESHOLD &&
                        absDiffX > absDiffY * SWIPE_OFF_AXIS_RATIO &&
                        kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD &&
                        kotlin.math.abs(velocityX) > kotlin.math.abs(velocityY)
                    ) {
                        if (diffX < 0 && currentPage < 2) {
                            switchToPage(currentPage + 1)
                        } else if (diffX > 0 && currentPage > 0) {
                            switchToPage(currentPage - 1)
                        }
                        return true
                    }
                    return false
                }
            }
        )

        switchToHome()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun switchToHome() = switchToPage(0)
    private fun switchToSettings() = switchToPage(1)
    private fun switchToInfo() = switchToPage(2)

    private fun switchToPage(pageIndex: Int) {
        currentPage = pageIndex

        homeContainer.visibility = if (pageIndex == 0) View.VISIBLE else View.GONE
        settingsContainer.visibility = if (pageIndex == 1) View.VISIBLE else View.GONE
        infoContainer.visibility = if (pageIndex == 2) View.VISIBLE else View.GONE

        val active = Color.BLACK
        val inactive = 0x80FFFFFF.toInt()

        tabHome.setTextColor(if (pageIndex == 0) active else inactive)
        tabSettings.setTextColor(if (pageIndex == 1) active else inactive)
        tabInfo.setTextColor(if (pageIndex == 2) active else inactive)

        iconHome.setColorFilter(if (pageIndex == 0) active else inactive)
        iconSettings.setColorFilter(if (pageIndex == 1) active else inactive)
        iconInfo.setColorFilter(if (pageIndex == 2) active else inactive)
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF_PERMISSION
                )
            }
        }
    }

    private fun ensureBatteryOptimizationDisabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                startActivity(intent)
            }
        }
    }

    private fun showStatusNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(STATUS_CHANNEL_ID) == null
        ) {
            val channel = NotificationChannel(
                STATUS_CHANNEL_ID,
                getString(R.string.channel_status_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val notif = NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_status_title))
            .setContentText(getString(R.string.notif_status_text))
            .setOngoing(true)
            .build()

        manager.notify(STATUS_NOTIF_ID, notif)
    }

    private fun startGlowAnimation() {
        glowAnimator?.cancel()

        glowAnimator = ObjectAnimator.ofFloat(trackingToggle, "alpha", 1f, 0.7f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopGlowAnimation() {
        glowAnimator?.cancel()
        glowAnimator = null
        trackingToggle.alpha = 1f
    }

    private fun applySavedLanguageIfAny() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val tag = prefs.getString(KEY_APP_LANGUAGE, null)?.trim().orEmpty()
        if (tag.isNotEmpty()) {
            val locales = LocaleListCompat.forLanguageTags(tag)
            // setApplicationLocales сам решает, нужно ли пересоздавать Activity.
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    private fun updateLanguageButtonText() {
        val lang = resources.configuration.locales[0]?.language ?: "ru"
        languageButton.text = if (lang.equals("ru", ignoreCase = true)) {
            getString(R.string.language_button_ru)
        } else {
            getString(R.string.language_button_en)
        }
    }

    private fun showLanguageDialog() {
        val items = arrayOf(getString(R.string.language_ru), getString(R.string.language_en))

        val current = resources.configuration.locales[0]?.language ?: "ru"
        val checkedItem = if (current.equals("en", ignoreCase = true)) 1 else 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.language_dialog_title))
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                val tag = if (which == 1) "en" else "ru"
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_APP_LANGUAGE, tag)
                    .apply()

                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
