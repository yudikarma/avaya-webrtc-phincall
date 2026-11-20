package com.phincall.webrtc.webrtc

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.phincall.webrtc.BuildConfig
import com.phincall.webrtc.R
import com.phincall.webrtc.ui.incall.InCallActivity

/**
 * Keeps WebRTC audio, WebSocket signalling, and Bluetooth audio links active when the screen locks
 * or the app moves to the background. Enforces Android 14-16 foreground service type permissions,
 * degrades gracefully if camera/device types are restricted in background, and holds bounded CPU
 * and low-latency Wi-Fi locks while a call is running.
 */
class ActiveCallForegroundService : Service() {

    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fg_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isVideoCall = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false
        val notification = buildNotification(isVideoCall)

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            stopSelf()
            return START_NOT_STICKY
        }

        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasBluetoothConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            buildForegroundServiceTypeCandidates(
                isVideoCall = isVideoCall,
                hasMicPermission = hasMicPermission,
                hasCameraPermission = hasCameraPermission,
                hasBluetoothConnect = hasBluetoothConnect
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            listOf(0)
        }

        val started = startForegroundWithFallback(notification, candidates)
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireBackgroundCallLocks()
        return START_NOT_STICKY
    }

    private fun startForegroundWithFallback(
        notification: Notification,
        typeCandidates: List<Int>
    ): Boolean {
        for (serviceTypes in typeCandidates) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceTypes)
                return true
            } catch (ex: SecurityException) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "FGS startForeground SecurityException for types=$serviceTypes", ex)
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "FGS startForeground failed for types=$serviceTypes", ex)
                }
            }
        }
        return false
    }

    private fun acquireBackgroundCallLocks() {
        runCatching {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && cpuWakeLock == null) {
                cpuWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PhinCall:ActiveCallCpu"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            cpuWakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire(MAX_CALL_WAKELOCK_MS)
                }
            }
        }

        runCatching {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null && wifiLock == null) {
                val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager.createWifiLock(wifiMode, "PhinCall:ActiveCallWifi").apply {
                    setReferenceCounted(false)
                }
            }
            wifiLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire()
                }
            }
        }
    }

    private fun releaseBackgroundCallLocks() {
        runCatching {
            cpuWakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        }
        cpuWakeLock = null

        runCatching {
            wifiLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        }
        wifiLock = null
    }

    override fun onDestroy() {
        releaseBackgroundCallLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(isVideoCall: Boolean): Notification {
        val returnIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (isVideoCall) {
            "Video interaction active. Tap to return to call."
        } else {
            getString(R.string.fg_notification_body)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(R.drawable.ic_phincall_mark)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "ActiveCallFGS"
        private const val CHANNEL_ID = "phincall_active_call"
        private const val NOTIFICATION_ID = 4010
        private const val EXTRA_IS_VIDEO = "extra_is_video"
        private const val MAX_CALL_WAKELOCK_MS = 4 * 60 * 60 * 1000L

        internal fun buildForegroundServiceTypeCandidates(
            isVideoCall: Boolean,
            hasMicPermission: Boolean,
            hasCameraPermission: Boolean,
            hasBluetoothConnect: Boolean
        ): List<Int> {
            var baseTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (hasMicPermission) {
                baseTypes = baseTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (hasBluetoothConnect) {
                baseTypes = baseTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }

            return buildList {
                if (isVideoCall && hasCameraPermission) {
                    add(baseTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                }
                add(baseTypes)
                if (hasBluetoothConnect) {
                    var withoutBt = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    if (hasMicPermission) {
                        withoutBt = withoutBt or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    add(withoutBt)
                }
                add(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }.distinct()
        }

        fun start(context: Context, isVideo: Boolean) {
            val intent = Intent(context, ActiveCallForegroundService::class.java).apply {
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (ex: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ex is ForegroundServiceStartNotAllowedException
                ) {
                    // Gracefully degrade if the OS blocks background FGS start.
                    return
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ActiveCallForegroundService::class.java))
        }
    }
}
