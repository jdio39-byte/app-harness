package ai.harness.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var lastShownSecond = -1L
    private lateinit var nm: NotificationManager

    private var phase = Phase.WORK
    private var completed = 0
    private var remainingMs = Phase.WORK.durationMs
    private var deadline = 0L

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        channels()
        _state.value = TimerUiState(phase, remainingMs, false, completed)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null, ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_SKIP -> { advance(); start() }
            ACTION_RESET -> reset()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun running() = ticker?.isActive == true

    private fun start() {
        if (running()) return
        deadline = SystemClock.elapsedRealtime() + remainingMs
        foreground()
        ticker = scope.launch {
            while (true) {
                remainingMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                publish()
                if (remainingMs == 0L) { finish(); break }
                delay(TICK)
            }
        }
    }

    private fun pause() {
        if (!running()) return
        remainingMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        ticker?.cancel()
        ticker = null
        publish()
    }

    private fun reset() {
        ticker?.cancel()
        ticker = null
        phase = Phase.WORK
        completed = 0
        remainingMs = phase.durationMs
        publish()
        foreground()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finish() {
        vibrate()
        advance()
        nm.notify(ALERT_ID, alert())
        start()
    }

    private fun advance() {
        ticker?.cancel()
        ticker = null
        if (phase == Phase.WORK) completed++
        phase = when {
            phase != Phase.WORK -> Phase.WORK
            completed % 4 == 0 -> Phase.LONG_REST
            else -> Phase.REST
        }
        remainingMs = phase.durationMs
        publish()
    }

    private fun publish() {
        _state.value = TimerUiState(phase, remainingMs, running(), completed)
        if (running()) {
            val second = remainingMs / 1000
            if (second != lastShownSecond) {
                lastShownSecond = second
                nm.notify(NOTIF_ID, notification())
            }
        }
    }

    private fun foreground() {
        val n = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun channels() {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                setSound(null, null)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 120, 180)
            }
        )
    }

    private fun toActivity(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun toService(id: Int, action: String): PendingIntent = PendingIntent.getService(
        this, id, Intent(this, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ONGOING)
        .setSmallIcon(android.R.drawable.ic_menu_recent_history)
        .setContentTitle(getString(R.string.notif_title, getString(phase.labelRes)))
        .setContentText(format(remainingMs))
        .setContentIntent(toActivity())
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .addAction(0, getString(if (running()) R.string.pause else R.string.start),
            toService(1, if (running()) ACTION_PAUSE else ACTION_START))
        .addAction(0, getString(R.string.skip), toService(2, ACTION_SKIP))
        .build()

    private fun alert(): Notification {
        val title = if (phase == Phase.WORK) R.string.alert_work else R.string.alert_rest
        return NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(title))
            .setContentText(format(remainingMs))
            .setContentIntent(toActivity())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }

    companion object {
        const val ACTION_START = "ai.harness.app.START"
        const val ACTION_PAUSE = "ai.harness.app.PAUSE"
        const val ACTION_SKIP = "ai.harness.app.SKIP"
        const val ACTION_RESET = "ai.harness.app.RESET"

        private const val CHANNEL_ONGOING = "ongoing"
        private const val CHANNEL_ALERTS = "alerts"
        private const val NOTIF_ID = 1
        private const val ALERT_ID = 2
        private const val TICK = 250L

        private val _state = MutableStateFlow(TimerUiState())
        val state = _state.asStateFlow()

        fun format(ms: Long): String {
            val total = (ms + 999) / 1000
            return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
        }
    }
}
