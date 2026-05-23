package com.example

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val repository = remember { StatusRepository(applicationContext) }
      val state by repository.state.collectAsState()
      val isDarkTheme = when (state.themeMode) {
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
          ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
      }
        
      MyApplicationTheme(darkTheme = isDarkTheme) {
        StatusResponderApp(repository, state)
      }
    }
  }
}

@SuppressLint("Range")
fun retrieveContact(context: Context, uri: Uri): SelectedContact? {
    var contactId = ""
    var name = ""
    var phoneNumber = ""
    
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            contactId = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID)) ?: ""
            name = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)) ?: "Nom inconnu"
            val hasPhone = it.getInt(it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
            if (hasPhone) {
                val phoneCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(contactId),
                    null
                )
                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        phoneNumber = pc.getString(pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                    }
                }
            }
        }
    }
    
    if (phoneNumber.isNotEmpty() && contactId.isNotEmpty()) {
        return SelectedContact(contactId, name, phoneNumber)
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusResponderApp(repository: StatusRepository, state: ResponderState) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    var missingPermissions by remember { mutableStateOf(emptyList<String>()) }
    var showHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val ungranted = permissions.filter { !it.value }.keys.toList()
        missingPermissions = ungranted
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    val contactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val contact = retrieveContact(context, uri)
            if (contact != null) {
                repository.addSelectedContact(contact)
                coroutineScope.launch { snackbarHostState.showSnackbar("Contact ajouté à la liste noire.") }
            } else {
                coroutineScope.launch { snackbarHostState.showSnackbar("Impossible de récupérer le numéro de ce contact.") }
            }
        }
    }
    
    val exceptedContactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val contact = retrieveContact(context, uri)
            if (contact != null) {
                repository.addExceptedContact(contact)
                coroutineScope.launch { snackbarHostState.showSnackbar("Contact ajouté aux exceptions.") }
            } else {
                coroutineScope.launch { snackbarHostState.showSnackbar("Impossible de récupérer le numéro de ce contact.") }
            }
        }
    }
    
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
            // This will update the role status state immediately when returning to the app
        }
    }
    
    LaunchedEffect(Unit) {
        val requiredPerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
        }
        permissionLauncher.launch(requiredPerms)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(androidx.compose.ui.res.stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Historique")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "À propos")
                    }
                    val isDarkTheme = when (state.themeMode) {
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    }
                    TextButton(onClick = { 
                        repository.setThemeMode(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK) 
                    }) {
                        Text(
                            text = if (isDarkTheme) "Clair" else "Sombre",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission Alert
            AnimatedVisibility(visible = missingPermissions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "L'application nécessite les permissions Téléphone et SMS pour fonctionner.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Top Status Banner
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (state.isEnabled) 1.05f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            // Battery Optimization & Call Screening Check
            var showBatteryDialog by remember { mutableStateOf(false) }
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            var isIgnoringBatteryOptimizations by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }
            
            var hasCallScreeningRole by remember {
                mutableStateOf(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                        roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true
                    } else {
                        true
                    }
                )
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                            hasCallScreeningRole = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val requestCallScreeningRole = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                    if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                        roleLauncher.launch(intent)
                    }
                }
            }

            if (!isIgnoringBatteryOptimizations) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable { showBatteryDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Battery Warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Appuyez ici pour autoriser l'exécution en arrière-plan (Optimisation Batterie).",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && !hasCallScreeningRole) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable { requestCallScreeningRole() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Call Screening",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Activer le filtrage d'appels intelligent (Recommandé pour Android 10+).",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (showBatteryDialog) {
                AlertDialog(
                    onDismissRequest = { showBatteryDialog = false },
                    title = { Text("Optimisation de la batterie") },
                    text = { Text("Pour assurer un filtrage fiable de vos appels même quand l'écran est éteint, veuillez désactiver l'optimisation de la batterie pour cette application.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showBatteryDialog = false
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                intent.data = android.net.Uri.parse("package:${context.packageName}")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val backupIntent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(backupIntent)
                                } catch (e2: Exception) {
                                    // Ignore if both fail
                                }
                            }
                        }) {
                            Text("Configurer")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatteryDialog = false }) {
                            Text("Plus tard")
                        }
                    }
                )
            }

            Surface(
                color = if (state.isEnabled) Color(0xFFE8F5E9) else Color(0xFFF5F5F5),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (state.isEnabled) "Service Actif" else "Service Inactif",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isEnabled) Color(0xFF2E7D32) else Color.Gray
                        )
                        if (state.isEnabled) {
                            val activeStatus = repository.availableStatuses.find { it.id == state.selectedStatusId }
                            Text(
                                text = "Statut actuel : ${activeStatus?.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E7D32).copy(alpha = 0.8f)
                            )
                        }
                    }
                    Switch(
                        checked = state.isEnabled,
                        onCheckedChange = { 
                            repository.setEnabled(it) 
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFFE0E0E0)
                        ),
                        modifier = Modifier.testTag("enable_responder_switch")
                    )
                }
            }
            
            // Custom Message Editor
            val currentStatus = repository.availableStatuses.find { it.id == state.selectedStatusId }
            if (currentStatus != null) {
                var messageInput by remember(state.selectedStatusId, state.customMessages) { 
                    mutableStateOf(state.customMessages[currentStatus.id] ?: currentStatus.defaultMessage) 
                }
                val scope = rememberCoroutineScope()
                
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Message pour : ${currentStatus.name}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_message_input_${currentStatus.id}"),
                            shape = RoundedCornerShape(8.dp),
                            minLines = 3,
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { 
                                repository.setCustomMessage(currentStatus.id, messageInput) 
                                scope.launch {
                                    snackbarHostState.showSnackbar("Message enregistré avec succès")
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enregistrer")
                        }
                    }
                }
            }
            
            Text(
                text = "Sélectionnez votre statut",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(repository.availableStatuses) { status ->
                    StatusCard(
                        status = status,
                        isSelected = state.selectedStatusId == status.id,
                        onClick = { repository.setSelectedStatus(status.id) }
                    )
                }
            }
            
            Button(
                onClick = { showFilterSheet = true },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Règles de filtrage des appels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth().navigationBarsPadding()) {
                    Text(
                        text = "Règle de filtrage des appels",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { repository.setInterceptOnlySelected(false) },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (!state.interceptOnlySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (!state.interceptOnlySelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                Text("Rejeter tout", style = MaterialTheme.typography.labelLarge, fontWeight = if (!state.interceptOnlySelected) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        
                        Surface(
                            onClick = { repository.setInterceptOnlySelected(true) },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.interceptOnlySelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (state.interceptOnlySelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                Text("Personnalisé\n(Liste noire)", style = MaterialTheme.typography.labelLarge, fontWeight = if (state.interceptOnlySelected) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (state.interceptOnlySelected) "Laisse sonner, sauf pour la liste noire." else "Rejette et répond à tout, sauf exceptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (state.interceptOnlySelected) {
                        Text(
                            text = "Contacts à toujours rejeter (Liste noire)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { contactPickerLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ajouter à la liste noire")
                        }
                        
                        if (state.selectedContacts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(state.selectedContacts) { contact ->
                                    ContactRow(contact, onRemove = { repository.removeSelectedContact(contact.id) })
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Aucun contact bloqué.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            text = "Contacts à ne jamais rejeter (Exceptions)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { exceptedContactPickerLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ajouter une exception")
                        }
                        
                        if (state.exceptedContacts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(state.exceptedContacts) { contact ->
                                    ContactRow(contact, onRemove = { repository.removeExceptedContact(contact.id) })
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Aucune exception.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        
        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                title = { Text("Historique et Statistiques") },
                text = {
                    if (state.history.isEmpty()) {
                        Text("Aucun message automatique envoyé pour le moment.")
                    } else {
                        Column {
                            WeeklyStatsChart(state.history)
                            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                                items(state.history) { item ->
                                    val dateStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                        Text("À : ${item.number}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(item.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistory = false }) {
                        Text("Fermer")
                    }
                },
                dismissButton = {
                    if (state.history.isNotEmpty()) {
                        TextButton(onClick = { repository.clearHistory() }) {
                            Text("Effacer", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }

        if (showAbout) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text("À propos de Call Auto-Response", fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            Text("Version 1.0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text("Call Auto-Response est une solution intelligente conçue pour optimiser la gestion de votre disponibilité au quotidien. Que vous soyez au volant, en réunion importante ou en salle d'opération, l'application veille sur votre tranquillité en gérant vos appels urgents de manière autonome et sélective par SMS.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text("Crédits", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Concepteur : Kiswendsida Komdaogo, Chef de Département Production chez OXY CONSEIL, une initiative de e-barka group", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text("Contact & Support", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("E-mail : kiskomda@gmail.com", style = MaterialTheme.typography.bodyMedium)
                            Text("Tel : +226 75978379 / 60123857", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { uriHandler.openUri("https://sites.google.com/view/kiswendsidakomdaogo/accueil") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Visiter le Portfolio en ligne")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Pour toute suggestion, support technique ou partenariat commercial, contactez-nous via notre support ou visitez notre e-portfolio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAbout = false }) {
                        Text("Fermer")
                    }
                }
            )
        }
    }
}

@Composable
fun ContactRow(contact: SelectedContact, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Retirer", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun StatusCard(
    status: ResponderStatus,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (status.id) {
        "driving" -> Icons.Default.Place
        "surgery" -> Icons.Default.Add
        "meeting" -> Icons.Default.Person
        "praying" -> Icons.Default.Favorite
        else -> Icons.Default.Edit
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() }
            .testTag("status_card_${status.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun WeeklyStatsChart(history: List<HistoryItem>) {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val millisInDay = 24 * 60 * 60 * 1000L
    
    val counts = LongArray(7) { 0L }
    for (item in history) {
        val daysAgo = if (item.timestamp >= todayStart) 0 else {
            ((todayStart - item.timestamp) / millisInDay).toInt() + 1
        }
        if (daysAgo in 0..6) {
            counts[6 - daysAgo]++
        }
    }
    val safeMax = counts.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text("Messages envoyés (7 derniers jours): ${counts.sum()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 0..6) {
                val fraction = (counts[i].toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f)) {
                    Text(counts[i].toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(fraction.coerceAtLeast(0.05f))
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                }
            }
        }
    }
}
