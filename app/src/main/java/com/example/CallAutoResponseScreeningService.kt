package com.example

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallAutoResponseScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val repository = StatusRepository(this)
        val state = repository.state.value
        
        Log.d("ScreeningService", "onScreenCall triggered. State enabled: ${state.isEnabled}")
        
        if (!state.isEnabled) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        
        val handle = callDetails.handle
        var incomingNumber = ""
        if (handle != null) {
            incomingNumber = handle.schemeSpecificPart ?: ""
            if (incomingNumber.isBlank()) {
                incomingNumber = handle.toString().substringAfter("tel:").substringBefore("?")
            }
        }
        
        Log.d("ScreeningService", "Incoming call number: $incomingNumber")
        
        if (incomingNumber.isEmpty()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        
        val message = repository.getActiveMessage()
        if (message == null) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        
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
            Log.d("ScreeningService", "Number in exception list, bypassing intercept")
        }
        
        if (shouldIntercept) {
            Log.d("ScreeningService", "Intercepting and rejecting call from $incomingNumber")
            
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
                
            respondToCall(callDetails, response)
            
            if (repository.canSendSms(incomingNumber)) {
                sendSms(incomingNumber, message)
                repository.recordSmsSent(incomingNumber)
                repository.addHistory(incomingNumber, message)
                NotificationHelper.showSmsSentNotification(this, incomingNumber)
                Log.d("ScreeningService", "Sent auto-reply to $incomingNumber")
            } else {
                Log.d("ScreeningService", "Skipped auto-reply SMS due to rate-limit")
            }
        } else {
            respondToCall(callDetails, CallResponse.Builder().build())
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
    
    private fun sendSms(phoneNumber: String, message: String) {
        if (phoneNumber.isBlank() || message.isBlank()) return
        Log.d("ScreeningService", "Attempting to send SMS to $phoneNumber")
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                getSystemService(android.telephony.SmsManager::class.java) ?: android.telephony.SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Log.d("ScreeningService", "SMS sent successfully via SmsManager")
        } catch (e: Exception) {
            Log.e("ScreeningService", "Failed to send SMS", e)
        }
    }
}
