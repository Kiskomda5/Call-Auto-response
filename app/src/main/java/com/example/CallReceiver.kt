package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            Log.d("CallReceiver", "Phone state changed: $phoneState")
            
            if (phoneState == TelephonyManager.EXTRA_STATE_RINGING) {
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                Log.d("CallReceiver", "Incoming number: $incomingNumber")
                
                if (incomingNumber != null && incomingNumber.isNotEmpty()) {
                    val pendingResult = goAsync()
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    val wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "StatusResponder:CallReceiverWakeLock")
                    wakeLock.acquire(10000)
                    
                    Thread {
                        try {
                            handleIncomingCall(context, incomingNumber)
                        } finally {
                            if (wakeLock.isHeld) {
                                wakeLock.release()
                            }
                            pendingResult.finish()
                        }
                    }.start()
                } else {
                    Log.w("CallReceiver", "Incoming number is null or empty. READ_CALL_LOG permission might be missing.")
                }
            }
        }
    }
    
    private fun handleIncomingCall(context: Context, incomingNumber: String) {
        val repository = StatusRepository(context)
        val state = repository.state.value
        if (!state.isEnabled) return
        
        val message = repository.getActiveMessage() ?: return
        
        var shouldIntercept = true
        if (state.interceptOnlySelected) {
            shouldIntercept = state.selectedContacts.any { 
                comparePhoneNumbers(it.phoneNumber, incomingNumber)
            }
        }
        
        val isExcepted = state.exceptedContacts.any {
            comparePhoneNumbers(it.phoneNumber, incomingNumber)
        }
        if (isExcepted) {
            shouldIntercept = false
            Log.d("CallReceiver", "Number in exception list, bypassing intercept")
        }
        
        if (shouldIntercept) {
            rejectCall(context)
            if (repository.canSendSms(incomingNumber)) {
                sendSms(context, incomingNumber, message)
                repository.recordSmsSent(incomingNumber)
                repository.addHistory(incomingNumber, message)
                NotificationHelper.showSmsSentNotification(context, incomingNumber)
                Log.d("CallReceiver", "Sent auto-reply to $incomingNumber")
            } else {
                Log.d("CallReceiver", "Skipped auto-reply due to rate-limit")
            }
        } else {
            Log.d("CallReceiver", "Skipped auto-reply. (Not in selected contacts)")
        }
    }
    
    private fun comparePhoneNumbers(number1: String, number2: String): Boolean {
        val clean1 = number1.replace(Regex("\\D"), "")
        val clean2 = number2.replace(Regex("\\D"), "")
        if (clean1.length >= 8 && clean2.length >= 8) {
            return clean1.substring(clean1.length - 8) == clean2.substring(clean2.length - 8)
        }
        return clean1 == clean2
    }
    
    private fun rejectCall(context: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val success = telecomManager.endCall()
                    Log.d("CallReceiver", "TelecomManager.endCall() returned $success")
                } else {
                    Log.w("CallReceiver", "Missing ANSWER_PHONE_CALLS permission")
                }
            }
        } catch (e: Exception) {
            Log.e("CallReceiver", "Failed to reject call", e)
        }
    }
    
    private fun sendSms(context: Context, phoneNumber: String, message: String) {
        if (phoneNumber.isBlank() || message.isBlank()) return
        Log.d("CallReceiver", "Attempting to send SMS to $phoneNumber")
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null as java.util.ArrayList<android.app.PendingIntent>?, null as java.util.ArrayList<android.app.PendingIntent>?)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Log.d("CallReceiver", "SMS sent successfully via SmsManager")
        } catch (e: Exception) {
            Log.e("CallReceiver", "Failed to send SMS", e)
        }
    }
}
