package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SelectedContact(val id: String, val name: String, val phoneNumber: String) {
    fun serialize(): String = "$id||$name||$phoneNumber"
    companion object {
        fun deserialize(str: String): SelectedContact? {
            val parts = str.split("||")
            if (parts.size == 3) {
                return SelectedContact(parts[0], parts[1], parts[2])
            }
            return null
        }
    }
}

data class HistoryItem(val number: String, val timestamp: Long, val message: String) {
    fun serialize() = "$number||$timestamp||$message"
    companion object {
        fun deserialize(str: String): HistoryItem? {
            val parts = str.split("||")
            if (parts.size >= 3) {
                return HistoryItem(parts[0], parts[1].toLongOrNull() ?: 0L, parts.drop(2).joinToString("||"))
            }
            return null
        }
    }
}

data class ResponderStatus(
    val id: String,
    val name: String,
    val defaultMessage: String
)

data class ResponderState(
    val isEnabled: Boolean = false,
    val selectedStatusId: String = "driving",
    val customMessages: Map<String, String> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val selectedContacts: List<SelectedContact> = emptyList(),
    val exceptedContacts: List<SelectedContact> = emptyList(),
    val interceptOnlySelected: Boolean = false,
    val history: List<HistoryItem> = emptyList()
)

class StatusRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("responder_prefs", Context.MODE_PRIVATE)
    
    val availableStatuses = listOf(
        ResponderStatus("driving", "Au volant", "Je suis actuellement au volant. Je vous rappelle dès que je m'arrête."),
        ResponderStatus("surgery", "En salle d'opération", "Je suis actuellement indisponible (en salle d'opération). En cas d'urgence, contactez le secrétariat."),
        ResponderStatus("meeting", "En réunion", "En réunion importante, je vous recontacte rapidement."),
        ResponderStatus("praying", "En Prière", "Je suis actuellement en prière. Je vous rappelle dès que je finis."),
        ResponderStatus("other", "Autre", "Je suis indisponible pour le moment.")
    )

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<ResponderState> = _state.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "history_logs") {
            val historyStringSet = prefs.getStringSet("history_logs", emptySet()) ?: emptySet()
            val history = historyStringSet.mapNotNull { HistoryItem.deserialize(it) }.sortedByDescending { it.timestamp }
            _state.value = _state.value.copy(history = history)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun loadState(): ResponderState {
        val isEnabled = prefs.getBoolean("is_enabled", false)
        val selectedStatusId = prefs.getString("selected_status_id", "driving") ?: "driving"
        val themeModeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try { ThemeMode.valueOf(themeModeStr) } catch (e: Exception) { ThemeMode.SYSTEM }
        
        val customMessages = mutableMapOf<String, String>()
        availableStatuses.forEach { status ->
            val customMsg = prefs.getString("msg_${status.id}", null)
            if (customMsg != null) {
                customMessages[status.id] = customMsg
            }
        }
        
        val contactsStringSet = prefs.getStringSet("selected_contacts", emptySet()) ?: emptySet()
        val selectedContacts = contactsStringSet.mapNotNull { SelectedContact.deserialize(it) }
        
        val exceptedStringSet = prefs.getStringSet("excepted_contacts", emptySet()) ?: emptySet()
        val exceptedContacts = exceptedStringSet.mapNotNull { SelectedContact.deserialize(it) }
        
        val interceptOnlySelected = prefs.getBoolean("intercept_only_selected", false)
        
        val historyStringSet = prefs.getStringSet("history_logs", emptySet()) ?: emptySet()
        val history = historyStringSet.mapNotNull { HistoryItem.deserialize(it) }.sortedByDescending { it.timestamp }
        
        return ResponderState(isEnabled, selectedStatusId, customMessages, themeMode, selectedContacts, exceptedContacts, interceptOnlySelected, history)
    }

    fun setInterceptOnlySelected(value: Boolean) {
        prefs.edit().putBoolean("intercept_only_selected", value).apply()
        _state.value = _state.value.copy(interceptOnlySelected = value)
    }

    fun addSelectedContact(contact: SelectedContact) {
        val current = _state.value.selectedContacts.toMutableList()
        if (current.none { it.id == contact.id }) {
            current.add(contact)
            saveContacts(current)
        }
    }
    
    fun removeSelectedContact(contactId: String) {
        val current = _state.value.selectedContacts.filter { it.id != contactId }
        saveContacts(current)
    }

    private fun saveContacts(contacts: List<SelectedContact>) {
        val set = contacts.map { it.serialize() }.toSet()
        prefs.edit().putStringSet("selected_contacts", set).apply()
        _state.value = _state.value.copy(selectedContacts = contacts)
    }

    fun addExceptedContact(contact: SelectedContact) {
        val current = _state.value.exceptedContacts.toMutableList()
        if (current.none { it.id == contact.id }) {
            current.add(contact)
            saveExceptedContacts(current)
        }
    }
    
    fun removeExceptedContact(contactId: String) {
        val current = _state.value.exceptedContacts.filter { it.id != contactId }
        saveExceptedContacts(current)
    }

    private fun saveExceptedContacts(contacts: List<SelectedContact>) {
        val set = contacts.map { it.serialize() }.toSet()
        prefs.edit().putStringSet("excepted_contacts", set).apply()
        _state.value = _state.value.copy(exceptedContacts = contacts)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_enabled", enabled).apply()
        _state.value = _state.value.copy(isEnabled = enabled)
        
        val serviceIntent = android.content.Intent(appContext, CallAutoResponseService::class.java)
        
        if (enabled) {
            ReminderReceiver.setupReminder(appContext)
            NotificationHelper.showReminderNotification(appContext)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }
        } else {
            ReminderReceiver.cancelReminder(appContext)
            NotificationHelper.cancelReminderNotification(appContext)
            appContext.stopService(serviceIntent)
        }
    }

    fun setSelectedStatus(id: String) {
        prefs.edit().putString("selected_status_id", id).apply()
        _state.value = _state.value.copy(selectedStatusId = id)
    }

    fun setCustomMessage(id: String, message: String) {
        prefs.edit().putString("msg_$id", message).apply()
        val newMessages = _state.value.customMessages.toMutableMap()
        newMessages[id] = message
        _state.value = _state.value.copy(customMessages = newMessages)
    }
    
    fun getActiveMessage(): String? {
        val currentState = loadState()
        if (!currentState.isEnabled) return null
        
        val activeStatus = availableStatuses.find { it.id == currentState.selectedStatusId } ?: return null
        return currentState.customMessages[activeStatus.id] ?: activeStatus.defaultMessage
    }
    
    fun recordSmsSent(number: String) {
        val time = System.currentTimeMillis()
        prefs.edit().putLong("last_sms_$number", time).apply()
    }
    
    fun canSendSms(number: String): Boolean {
        val lastSent = prefs.getLong("last_sms_$number", 0)
        val currentTime = System.currentTimeMillis()
        // 10 seconds for easier testing = 10,000 milliseconds
        return (currentTime - lastSent) > 10_000
    }
    
    fun addHistory(number: String, message: String) {
        val currentSet = prefs.getStringSet("history_logs", emptySet()) ?: emptySet()
        val newSet = currentSet.toMutableSet()
        newSet.add(HistoryItem(number, System.currentTimeMillis(), message).serialize())
        prefs.edit().putStringSet("history_logs", newSet).apply()
    }
    
    fun clearHistory() {
        prefs.edit().remove("history_logs").apply()
        _state.value = _state.value.copy(history = emptyList())
    }
}
