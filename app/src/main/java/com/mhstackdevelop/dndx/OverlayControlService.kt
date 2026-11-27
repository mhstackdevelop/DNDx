package com.mhstackdevelop.dndx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo

class OverlayControlService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayAdded = false

    private val refreshOverlay = object : Runnable {
        override fun run() {
            updateOverlay()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.KEY_OVERLAY_ENABLED, false)
                .apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(MainActivity.KEY_OVERLAY_ENABLED, false)
                .apply()
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
        if (!overlayAdded) {
            try {
                addOverlay()
            } catch (_: SecurityException) {
                failOverlayStart()
                return START_NOT_STICKY
            } catch (_: WindowManager.BadTokenException) {
                failOverlayStart()
                return START_NOT_STICKY
            }
        }
        handler.removeCallbacks(refreshOverlay)
        handler.post(refreshOverlay)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshOverlay)
        if (overlayAdded) {
            overlayView?.let(windowManager::removeView)
            overlayAdded = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun addOverlay() {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.rgb(38, 38, 48))
                setStroke(dp(1), Color.rgb(112, 112, 132))
            }
        }

        val status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            contentDescription = "DNDx status"
        }
        val toggle = Button(this).apply {
            textSize = 12f
            minHeight = dp(40)
            minWidth = dp(72)
            setOnClickListener {
                when (DndController.toggle(this@OverlayControlService)) {
                    DndController.ToggleResult.ENABLED -> Unit
                    DndController.ToggleResult.DISABLED -> Unit
                    DndController.ToggleResult.POLICY_ACCESS_REQUIRED -> {
                        Toast.makeText(
                            this@OverlayControlService,
                            "Opening DNDx to grant DND policy access.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(
                            Intent(this@OverlayControlService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    DndController.ToggleResult.ACCESS_DENIED ->
                        Toast.makeText(
                            this@OverlayControlService,
                            "Android denied the DND change.",
                            Toast.LENGTH_LONG
                        ).show()
                }
                updateOverlay()
            }
        }
        val close = TextView(this).apply {
            text = "×"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Close floating DND control"
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener {
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(MainActivity.KEY_OVERLAY_ENABLED, false)
                    .apply()
                stopSelf()
            }
        }
        root.addView(status)
        root.addView(toggle)
        root.addView(close)
        root.setOnTouchListener(DragListener(density))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(120)
        }

        windowManager.addView(root, params)
        overlayView = root
        layoutParams = params
        overlayAdded = true
        updateOverlay()
    }

    private fun updateOverlay() {
        val root = overlayView as? LinearLayout ?: return
        val status = root.getChildAt(0) as TextView
        val toggle = root.getChildAt(1) as Button
        val active = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_ACTIVE, false)
        status.text = if (active) " DNDx · ON " else " DNDx · OFF "
        val policyGranted = getSystemService(NotificationManager::class.java)
            .isNotificationPolicyAccessGranted
        toggle.text = when {
            !policyGranted -> "Grant access"
            active -> "Turn off"
            else -> "Turn on"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Floating DND control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the user-enabled floating DND control available."
            }
        )
    }

    private fun failOverlayStart() {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(MainActivity.KEY_OVERLAY_ENABLED, false)
            .apply()
        Toast.makeText(
            this,
            "Android could not display the floating control. Check overlay access.",
            Toast.LENGTH_LONG
        ).show()
        stopSelf()
    }

    private fun createNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopOverlay = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayControlService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("DNDx floating control is on")
            .setContentText("Tap to open DNDx or stop the floating control.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(0, "Stop", stopOverlay)
            .build()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private inner class DragListener(private val density: Float) : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var initialX = 0
        private var initialY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ((event.rawX - startX) / density).toInt()
                    val dy = ((event.rawY - startY) / density).toInt()
                    if (dx != 0 || dy != 0) moved = true
                    params.x = (initialX - dx).coerceAtLeast(0)
                    params.y = (initialY + dy).coerceAtLeast(0)
                    windowManager.updateViewLayout(view, params)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return moved
            }
            return false
        }
    }

    companion object {
        const val ACTION_STOP = "com.mhstackdevelop.dndx.STOP_OVERLAY"
        private const val NOTIFICATION_CHANNEL = "dndx_overlay"
        private const val NOTIFICATION_ID = 1301
        private const val REFRESH_INTERVAL_MS = 1_500L
    }
}
