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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.phincall.webrtc.MainActivity
import com.phincall.webrtc.R

/**
 * Keeps WebRTC media active when the screen locks or the app moves to the background.
 * Enforces Android 14-16 foreground service type permissions and handles background
 * start restrictions cleanly without crashing.
 */
class ActiveCallForegroundService : Service() {

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
        val notification = buildNotification()

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            stopSelf()
            return START_NOT_STICKY
        }

        val serviceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (isVideoCall && hasCameraPermission) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            types
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceTypes)
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val returnIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(R.drawable.ic_phincall_mark)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setContentText(getString(R.string.fg_notification_body))
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "phincall_active_call"
        private const val NOTIFICATION_ID = 4010
        private const val EXTRA_IS_VIDEO = "extra_is_video"

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
