package com.phincall.webrtc.webrtc

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import java.util.IdentityHashMap

/**
 * Proxies Application passed into the Avaya SDK so internal SDK BroadcastReceivers
 * (such as Bluetooth SCO state listeners that query TelephonyManager.getCallState()) cannot crash
 * the main thread with an uncaught SecurityException if runtime permissions were denied or revoked.
 */
class SdkSafeApplication(
    private val base: Application,
    private val onReceiverSecurityException: (action: String?, SecurityException) -> Unit
) : Application() {

    private val receiverLock = Any()
    private val wrappedReceivers = IdentityHashMap<BroadcastReceiver, BroadcastReceiver>()

    init {
        attachBaseContext(base)
    }

    override fun getApplicationContext(): Context = this

    override fun registerComponentCallbacks(callback: ComponentCallbacks?) {
        if (callback != null) {
            base.registerComponentCallbacks(callback)
        }
    }

    override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {
        if (callback != null) {
            base.unregisterComponentCallbacks(callback)
        }
    }

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks?) {
        if (callback != null) {
            base.registerActivityLifecycleCallbacks(callback)
        }
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks?) {
        if (callback != null) {
            base.unregisterActivityLifecycleCallbacks(callback)
        }
    }

    override fun registerOnProvideAssistDataListener(callback: OnProvideAssistDataListener?) {
        if (callback != null) {
            base.registerOnProvideAssistDataListener(callback)
        }
    }

    override fun unregisterOnProvideAssistDataListener(callback: OnProvideAssistDataListener?) {
        if (callback != null) {
            base.unregisterOnProvideAssistDataListener(callback)
        }
    }

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? =
        registerWrappedReceiver(receiver) { wrapped ->
            super.registerReceiver(wrapped, filter)
        }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        flags: Int
    ): Intent? =
        registerWrappedReceiver(receiver) { wrapped ->
            super.registerReceiver(wrapped, filter, flags)
        }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?
    ): Intent? =
        registerWrappedReceiver(receiver) { wrapped ->
            super.registerReceiver(wrapped, filter, broadcastPermission, scheduler)
        }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int
    ): Intent? =
        registerWrappedReceiver(receiver) { wrapped ->
            super.registerReceiver(wrapped, filter, broadcastPermission, scheduler, flags)
        }

    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        if (receiver == null) return
        val target = synchronized(receiverLock) {
            wrappedReceivers.remove(receiver)
        } ?: receiver
        try {
            super.unregisterReceiver(target)
        } catch (_: IllegalArgumentException) {
            // Ignore double-unregister during SDK cleanup after an interrupted audio route switch.
        }
    }

    private fun registerWrappedReceiver(
        receiver: BroadcastReceiver?,
        registerCall: (BroadcastReceiver?) -> Intent?
    ): Intent? {
        if (receiver == null) {
            return registerCall(null)
        }
        val wrapped = synchronized(receiverLock) {
            wrappedReceivers.getOrPut(receiver) {
                SafeBroadcastReceiver(receiver, onReceiverSecurityException)
            }
        }
        return registerCall(wrapped)
    }

    private class SafeBroadcastReceiver(
        private val delegate: BroadcastReceiver,
        private val onSecurityException: (action: String?, SecurityException) -> Unit
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runBroadcastSafely(intent?.action, onSecurityException) {
                delegate.onReceive(context, intent)
            }
        }
    }
}

internal inline fun runBroadcastSafely(
    action: String?,
    onSecurityException: (action: String?, SecurityException) -> Unit,
    block: () -> Unit
) {
    try {
        block()
    } catch (ex: SecurityException) {
        onSecurityException(action, ex)
    }
}
