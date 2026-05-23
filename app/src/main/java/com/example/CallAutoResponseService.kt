package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class CallAutoResponseService : Service() {

    private var callReceiver: CallReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        callReceiver = CallReceiver()
        val filter = IntentFilter("android.intent.action.PHONE_STATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(callReceiver, filter)
        }
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallAutoResponse:ServiceWakeLock")
    }

    override fun onDestroy() {
        super.onDestroy()
        callReceiver?.let {
            unregisterReceiver(it)
        }
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "RESPONDER_SERVICE_CHANNEL")
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Call Auto-Response est actif et sécurise vos déplacements")
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("CallAutoResponseService", "Failed to start foreground with custom type", e)
            try {
                startForeground(1001, notification)
            } catch (ex: Exception) {
                android.util.Log.e("CallAutoResponseService", "Absolute FGS fallback failed", ex)
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "RESPONDER_SERVICE_CHANNEL",
                "Service Actif",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
