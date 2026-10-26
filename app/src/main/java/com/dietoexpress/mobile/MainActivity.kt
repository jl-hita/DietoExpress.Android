package com.dietoexpress.mobile

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import com.dietoexpress.mobile.data.DietDay
import com.dietoexpress.mobile.data.DietDetail
import com.dietoexpress.mobile.data.DietMeal
import com.dietoexpress.mobile.data.PatientAppointment
import com.dietoexpress.mobile.data.PatientCheckin
import com.dietoexpress.mobile.data.PatientCheckinDraft
import com.dietoexpress.mobile.data.PatientDocument
import com.dietoexpress.mobile.data.PatientMessage
import com.dietoexpress.mobile.data.ShoppingCategory
import com.dietoexpress.mobile.data.TrainingEnvelope

private val Forest = Color(0xFF176B52)
private val ForestDark = Color(0xFF104B3B)
private val Sage = Color(0xFFE7F2EC)
private val Canvas = Color(0xFFF5F8F6)
private val Ink = Color(0xFF172923)
private val Muted = Color(0xFF687970)
private val Line = Color(0xFFE2EAE5)
private val Amber = Color(0xFF94651B)

class MainActivity : ComponentActivity() {
    private val notificationTarget = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget.value = intent.getStringExtra("notification_target")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        notificationTarget.value = intent.getStringExtra("notification_target")
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Forest,
                    onPrimary = Color.White,
                    secondary = Color(0xFF6B927C),
                    background = Canvas,
                    surface = Color.White,
                    onSurface = Ink,
                    onSurfaceVariant = Muted,
                    outline = Line,
                    error = Color(0xFFB42318)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
                    val vm: PortalViewModel = viewModel()
                    val state by vm.state.collectAsStateWithLifecycle()
                    val notificationDestination by notificationTarget.collectAsStateWithLifecycle()
                    LaunchedEffect(notificationDestination, state.profile?.clientId) {
                        if (state.profile != null && notificationDestination != null) {
                            when (notificationDestination) {
                                "messages" -> vm.openSection("messages")
                                "appointments" -> vm.openSection("appointments")
                            }
                            notificationTarget.value = null
                        }
                    }
                    var publicRoute by remember { mutableStateOf("portal") }
                    var showingNotifications by remember { mutableStateOf(false) }
                    LaunchedEffect(state.profile?.clientId) {
                        if (state.profile?.clientId != null && PushNotifications.isConfigured(this@MainActivity) &&
                            Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2026)
                        }
                        while (state.profile?.clientId != null) {
                            vm.refreshNotifications()
                            delay(60_000)
                        }
                    }
                    LaunchedEffect(publicRoute, state.profile?.clientId) {
                        if (publicRoute == "portal" && state.profile != null) vm.loadNutritionistTransfer()
                    }
                    if (publicRoute != "portal") {
                        PublicDirectoryScreen(
                            initialBooking = publicRoute == "booking",
                            onBack = { publicRoute = "portal" },
                            onOpenBooking = { publicRoute = "booking" }
                        )
                    } else if (state.profile == null) {
                        LoginScreen(
                            emailOrPhone = state.emailOrPhone,
                            passcode = state.passcode,
                            linkEmail = state.linkEmail,
                            busy = state.busy,
                            message = state.message,
                            error = state.error,
                            onEmailChange = vm::updateEmailOrPhone,
                            onPasscodeChange = vm::updatePasscode,
                            onLinkEmailChange = vm::updateLinkEmail,
                            onLogin = vm::login,
                            onRequestLink = vm::requestAccessLink,
                            onOpenDirectory = { publicRoute = "directory" }
                        )
                    } else {
                        Scaffold(
                            containerColor = Canvas,
                            bottomBar = {
                                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                                    val items = listOf(
                                        Triple("home", "Inicio", "⌂"),
                                        Triple("diet", "Mi dieta", "≋"),
                                        Triple("appointments", "Citas", "◷"),
                                        Triple("progress", "Progreso", "↗"),
                                        Triple("profile", "Perfil", "○")
                                    )
                                    items.forEach { item ->
                                        NavigationBarItem(
                                            selected = state.section == item.first,
                                            onClick = { vm.openSection(item.first) },
                                            icon = { Text(item.third, fontSize = 21.sp, fontWeight = FontWeight.Medium) },
                                            label = { Text(item.second, fontSize = 11.sp) },
                                            alwaysShowLabel = true
                                        )
                                    }
                                }
                            }
                        ) { padding ->
                            Column(
                                modifier = Modifier.fillMaxSize().padding(padding)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_dietoexpress_mark),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                            Text("DIETOEXPRESS", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                        }
                                        Text(if (showingNotifications) "Notificaciones" else sectionTitle(state.section), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                            IconButton(onClick = { showingNotifications = true }, modifier = Modifier.fillMaxSize()) {
                                                Text("🔔", fontSize = 23.sp)
                                            }
                                            if (state.unreadMessagesCount > 0) {
                                                Box(
                                                    modifier = Modifier.align(Alignment.TopEnd).size(19.dp).background(Color(0xFFB42318), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(if (state.unreadMessagesCount > 9) "9+" else state.unreadMessagesCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Box(
                                            modifier = Modifier.size(44.dp).background(Sage, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(initials(state.profile?.fullName.orEmpty()), color = ForestDark, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                state.message?.let {
                                    InfoBanner(it, state.error)
                                }

                                state.nutritionistTransfer?.takeIf { it.pending }?.let { transfer ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("Cambio de nutricionista", fontWeight = FontWeight.Bold, color = Ink)
                                            Text(
                                                if (transfer.requiresPatientConfirmation) "La cita ha sido aceptada por ${transfer.nutritionistName ?: "el nuevo profesional"}."
                                                else "Solicitud enviada a ${transfer.nutritionistName ?: "un nuevo profesional"}. Tu relación e historial siguen intactos.",
                                                color = Muted
                                            )
                                            if (transfer.requiresPatientConfirmation) {
                                                Text("Confirma el cambio solo si quieres transferir tu historial al nuevo profesional. No se hará efectivo antes de este consentimiento.", color = Ink)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = state.transferConsentChecked,
                                                        onCheckedChange = vm::setTransferConsent
                                                    )
                                                    Text("Consiento expresamente la transferencia de mi historial", color = Ink)
                                                }
                                                Button(
                                                    onClick = vm::confirmNutritionistTransfer,
                                                    enabled = state.transferConsentChecked && !state.busy,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { Text("Confirmar cambio y transferir historial") }
                                            }
                                            OutlinedButton(
                                                onClick = vm::cancelNutritionistTransfer,
                                                enabled = !state.busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Cancelar cambio") }
                                        }
                                    }
                                }

                                if (showingNotifications) {
                                    NotificationCenterContent(
                                        messages = state.notificationMessages.filter { it.senderType.equals("professional", true) && it.readAt == null }
                                            .sortedByDescending { it.createdAt },
                                        onBack = { showingNotifications = false },
                                        onOpenMessages = { showingNotifications = false; vm.openSection("messages") }
                                    )
                                } else when (state.section) {
                                    "diet" -> DietContent(
                                        diet = state.diet,
                                        loaded = state.dietLoaded,
                                        busy = state.busy,
                                        onRetry = vm::loadDiet
                                    )
                                    "video" -> VideoConsultationScreen(
                                        appointmentId = state.videoAppointmentId,
                                        onExit = { vm.openSection("appointments") }
                                    )
                                    "appointments" -> AppointmentsContent(
                                        appointments = state.appointments,
                                        slots = state.appointmentSlots,
                                        slotsError = state.appointmentSlotsError,
                                        hasAssignedNutritionist = state.nutritionistRelationship?.hasAssignedNutritionist ?: !state.profile?.nutritionistName.isNullOrBlank(),
                                        loaded = state.appointmentsLoaded,
                                        busy = state.busy,
                                        onRetry = { vm.refreshAppointments() },
                                        onBookAppointment = { publicRoute = "directory" },
                                        onRequestAppointment = vm::requestAppointment,
                                        onJoinVideo = vm::openVideoAppointment,
                                        onCancelAppointment = vm::cancelAppointment
                                    )
                                    "documents" -> DocumentsContent(
                                        documents = state.documents,
                                        loaded = state.documentsLoaded,
                                        busy = state.busy,
                                        onRetry = { vm.openSection("documents") },
                                        onOpenDocument = vm::openDocument,
                                        onAcceptDocument = vm::acceptDocument
                                    )
                                    "shopping" -> ShoppingPortalContent(state.shoppingList, state.shoppingLoaded, state.busy) { vm.openSection("shopping") }
                                    "messages" -> PatientMessagesContent(
                                        messages = state.messages, loaded = state.messagesLoaded, draft = state.messageDraft,
                                        busy = state.busy, onDraftChange = vm::updateMessageDraft,
                                        onSend = vm::sendMessage, onRetry = { vm.openSection("messages") }
                                    )
                                    "training" -> TrainingPortalContent(state.training, state.trainingLoaded, state.busy, vm::recordTrainingSession) { vm.openSection("training") }
                                    "progress" -> ProgressContent(
                                        eligibilityMessage = state.checkinEligibility?.message,
                                        eligible = state.checkinEligibility?.eligible,
                                        current = state.currentCheckin,
                                        history = state.checkinHistory,
                                        weightHistory = state.profile?.weightHistory.orEmpty(),
                                        draft = state.checkinDraft,
                                        submissionPending = state.checkinSubmissionPending,
                                        loaded = state.checkinLoaded,
                                        busy = state.busy,
                                        onDraftChange = vm::updateCheckinDraft,
                                        onSubmit = vm::submitCheckin,
                                        onRetry = { vm.openSection("progress") }
                                    )
                                    "profile" -> ProfileContent(
                                        name = state.profile?.fullName.orEmpty(),
                                        age = state.profile?.age,
                                        weight = state.profile?.currentWeight,
                                        height = state.profile?.currentHeight,
                                        clinic = state.profile?.clinicName,
                                        nutritionist = state.profile?.nutritionistName,
                                        hasDiet = state.profile?.hasActiveDiet == true,
                                        busy = state.busy,
                                        offlineOperations = state.offlineOperations,
                                        onRefresh = vm::refreshProfile,
                                        onLogout = vm::logout,
                                        onRefreshOfflineOperations = vm::refreshOfflineOperations,
                                        onRetryPendingSync = vm::retryPendingSync,
                                        onDiscardOfflineOperation = vm::discardOfflineOperation
                                    )
                                    else -> HomeContent(
                                        name = state.profile?.fullName.orEmpty(),
                                        clinic = state.profile?.clinicName,
                                        hasDiet = state.profile?.hasActiveDiet == true,
                                        nutritionist = state.nutritionistRelationship?.nutritionistName ?: state.profile?.nutritionistName,
                                        isClinic = state.nutritionistRelationship?.isClinic == true,
                                        hasPendingClinicReassignment = state.nutritionistRelationship?.hasPendingClinicReassignment == true,
                                        busy = state.busy,
                                        onDiet = { vm.openSection("diet") },
                                        onAppointments = { vm.openSection("appointments") },
                                        onProgress = { vm.openSection("progress") },
                                        onDocuments = { vm.openSection("documents") },
                                        onShopping = { vm.openSection("shopping") },
                                        onMessages = { vm.openSection("messages") },
                                        onTraining = { vm.openSection("training") },
                                        onProfile = { vm.openSection("profile") },
                                        onBookAppointment = { if (state.nutritionistRelationship?.hasAssignedNutritionist ?: !state.profile?.nutritionistName.isNullOrBlank()) vm.openSection("appointments") else publicRoute = "directory" },
                                        onLeaveNutritionist = { vm.requestNutritionistChange("leave_and_find_new") },
                                        onRequestClinicReassignment = { vm.requestNutritionistChange("clinic_reassignment") }
                                    )
                                }

                                if (state.busy) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Actualizando información…", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    emailOrPhone: String,
    passcode: String,
    linkEmail: String,
    busy: Boolean,
    message: String?,
    error: Boolean,
    onEmailChange: (String) -> Unit,
    onPasscodeChange: (String) -> Unit,
    onLinkEmailChange: (String) -> Unit,
    onLogin: () -> Unit,
    onRequestLink: () -> Unit,
    onOpenDirectory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_dietoexpress_mark),
            contentDescription = "DietoExpress",
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(22.dp))
        )
        Spacer(Modifier.height(24.dp))
        Text("DIETOEXPRESS", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
        Text("Cuida de ti,\nun día a la vez.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 39.sp)
        Spacer(Modifier.height(10.dp))
        Text("Tu espacio personal para seguir tu plan nutricional y estar en contacto con tu profesional.", color = Muted, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bienvenido/a", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Accede a tu portal de paciente", color = Muted, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = emailOrPhone,
                    onValueChange = onEmailChange,
                    label = { Text("Email o teléfono") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = passcode,
                    onValueChange = onPasscodeChange,
                    label = { Text("Código de acceso") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = onLogin,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)
                ) {
                    Text(if (busy) "Conectando…" else "Entrar en mi cuenta", fontWeight = FontWeight.SemiBold)
                }
                message?.let { InfoBanner(it, error) }
                HorizontalDivider(color = Line)
                Text("¿No tienes tu código?", fontWeight = FontWeight.SemiBold)
                Text("Te enviaremos un enlace seguro a tu correo.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = linkEmail,
                    onValueChange = onLinkEmailChange,
                    label = { Text("Tu email") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                TextButton(onClick = onRequestLink, enabled = !busy) {
                    Text("Solicitar enlace de acceso")
                }
                OutlinedButton(onClick = onOpenDirectory, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Buscar nutricionista")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Tu información personal se consulta de forma segura.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NotificationCenterContent(
    messages: List<PatientMessage>,
    onBack: () -> Unit,
    onOpenMessages: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("Notificaciones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        Text("Aquí verás los mensajes nuevos de tu profesional y, más adelante, otros avisos importantes.", color = Muted)
        if (messages.isEmpty()) {
            EmptyCard("Todo al día", "No tienes mensajes nuevos sin leer.")
        } else {
            messages.forEach { item ->
                Card(
                    onClick = onOpenMessages,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).background(Forest, CircleShape))
                            Text("Nuevo mensaje de tu profesional", color = Ink, fontWeight = FontWeight.Bold)
                        }
                        Text(item.body, color = Muted, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(item.createdAt.replace("T", " ").take(16), color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text("Abrir conversación →", color = Forest, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = onOpenMessages, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Ver todos los mensajes")
            }
        }
    }
}

@Composable
private fun HomeContent(
    name: String,
    clinic: String?,
    hasDiet: Boolean,
    nutritionist: String?,
    isClinic: Boolean,
    hasPendingClinicReassignment: Boolean,
    busy: Boolean,
    onDiet: () -> Unit,
    onAppointments: () -> Unit,
    onProgress: () -> Unit,
    onDocuments: () -> Unit,
    onShopping: () -> Unit,
    onMessages: () -> Unit,
    onTraining: () -> Unit,
    onProfile: () -> Unit,
    onBookAppointment: () -> Unit,
    onLeaveNutritionist: () -> Unit,
    onRequestClinicReassignment: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Forest)
        ) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TU BIENESTAR, A TU RITMO", color = Color(0xFFC8E4D4), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("Hola, ${name.substringBefore(" ").ifBlank { "bienvenido/a" }}", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (hasDiet) "Tu plan nutricional está listo para acompañarte hoy." else "Aquí encontrarás tu plan y el seguimiento de tu proceso.",
                    color = Color(0xFFE2F0E8),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDiet,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Forest)
                ) { Text(if (hasDiet) "Consultar mi dieta  →" else "Consultar mi plan  →", fontWeight = FontWeight.SemiBold) }
            }
        }
        if (!nutritionist.isNullOrBlank()) {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) {
                        Text("＋", color = Forest, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Tu equipo de cuidado", fontWeight = FontWeight.SemiBold, color = Ink)
                        clinic?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                        nutritionist?.takeIf { it.isNotBlank() }?.let { Text("Profesional: $it", color = Muted, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onBookAppointment,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Reservar consulta") }
                        OutlinedButton(
                            onClick = onLeaveNutritionist,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !hasPendingClinicReassignment,
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Buscar otro nutricionista") }
                        if (isClinic) {
                            OutlinedButton(
                                onClick = onRequestClinicReassignment,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy && !hasPendingClinicReassignment,
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(if (hasPendingClinicReassignment) "Reasignación solicitada" else "Solicitar reasignación en la clínica") }
                        }
                    }
                }
            }
        } else {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tu equipo de cuidado", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("Encuentra un profesional y solicita tu primera cita.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onBookAppointment, shape = RoundedCornerShape(12.dp)) { Text("Buscar nutricionista y reservar") }
                    if (isClinic) {
                        OutlinedButton(
                            onClick = onRequestClinicReassignment,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !hasPendingClinicReassignment,
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (hasPendingClinicReassignment) "Reasignación solicitada" else "Solicitar reasignación en la clínica") }
                    }
                }
            }
        }
        Text("Accesos rápidos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard("Mi dieta", "Plan y comidas", "≋", Modifier.weight(1f), onDiet)
            ShortcutCard("Mis citas", "Agenda", "◷", Modifier.weight(1f), onAppointments)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard("Seguimiento", "Tu evolución", "↗", Modifier.weight(1f), onProgress)
            ShortcutCard("Documentos", "Archivos y consentimientos", "▤", Modifier.weight(1f), onDocuments)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard("Lista de compra", "Ingredientes del plan", "✓", Modifier.weight(1f), onShopping)
            ShortcutCard("Mensajes", "Habla con tu profesional", "✉", Modifier.weight(1f), onMessages)
        }
        ShortcutCard("Entrenamiento", "Tus sesiones deportivas", "⌁", Modifier.fillMaxWidth(), onTraining)
        OutlinedButton(onClick = onProfile, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), enabled = !busy) {
            Text("Ver mi perfil")
        }
    }
}

@Composable
private fun ShortcutCard(title: String, subtitle: String, symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(42.dp).background(Sage, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text(symbol, color = Forest, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            }
            Text(title, color = Ink, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun DietContent(diet: DietDetail?, loaded: Boolean, busy: Boolean, onRetry: () -> Unit) {
    if (busy && !loaded) LoadingCard("Estamos preparando tu dieta")
    else if (diet == null) EmptyCard(
        title = "Tu dieta estará aquí",
        body = "Cuando tu profesional te asigne un plan activo, podrás consultar aquí tus comidas y objetivos.",
        action = "Actualizar",
        onAction = onRetry
    )
    else {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Forest)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PLAN ACTIVO", color = Color(0xFFC8E4D4), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(diet.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                diet.dietType?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFFE2F0E8)) }
            }
        }
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Objetivos diarios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                MacroRow("Energía", diet.targetKcal, "kcal")
                MacroRow("Proteínas", diet.targetProtein, "g")
                MacroRow("Hidratos", diet.targetCarbs, "g")
                MacroRow("Grasas", diet.targetFat, "g")
            }
        }
        if (diet.days.isEmpty()) EmptyCard("Tu menú se está preparando", "Todavía no hay días de menú disponibles.")
        else diet.days.sortedBy { it.dayIndex }.forEach { day -> DietDayCard(day) }
    }
}

@Composable
private fun DietDayCard(day: DietDay) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).background(Sage, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text((day.dayIndex + 1).toString().padStart(2, '0'), color = Forest, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Día ${day.dayIndex + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${day.meals.size} comidas", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            if (day.meals.isEmpty()) Text("Sin comidas registradas.", color = Muted)
            else day.meals.sortedBy { it.mealIndex }.forEachIndexed { index, meal ->
                if (index > 0) HorizontalDivider(color = Line)
                MealBlock(meal)
            }
        }
    }
}

@Composable
private fun MealBlock(meal: DietMeal) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(meal.name, fontWeight = FontWeight.SemiBold, color = ForestDark)
        if (meal.items.isEmpty()) Text("Sin alimentos detallados.", color = Muted, style = MaterialTheme.typography.bodySmall)
        meal.items.forEach { item ->
            val name = item.foodName?.takeIf { it.isNotBlank() }
                ?: item.recipeName?.takeIf { it.isNotBlank() }
                ?: item.exchangeGroupName?.takeIf { it.isNotBlank() }
                ?: "Alimento"
            val amount = when {
                item.grams != null -> "${number(item.grams)} g"
                item.exchangeCount != null -> "${number(item.exchangeCount)} intercambios"
                item.recipeServings != null -> "${number(item.recipeServings)} raciones"
                else -> null
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, color = Ink)
                    amount?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                item.kcal?.let { Text("${number(it)} kcal", color = Muted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun AppointmentsContent(
    appointments: List<PatientAppointment>,
    slots: List<com.dietoexpress.mobile.data.AppointmentSlot>,
    slotsError: String?,
    hasAssignedNutritionist: Boolean,
    loaded: Boolean,
    busy: Boolean,
    onRetry: () -> Unit,
    onBookAppointment: () -> Unit,
    onRequestAppointment: (com.dietoexpress.mobile.data.AppointmentSlot, String) -> Unit,
    onJoinVideo: (Int) -> Unit,
    onCancelAppointment: (Int) -> Unit
) {
    var selectedDate by remember(slots) { mutableStateOf(slots.firstOrNull()?.startsAt?.take(10)) }
    var selectedSlotStart by remember(slots) { mutableStateOf<String?>(null) }
    var showingHistory by remember { mutableStateOf(false) }
    var historyLimit by remember { mutableStateOf(10) }
    val slotsByDate = slots.groupBy { it.startsAt.take(10) }.toSortedMap()
    val activeDate = selectedDate?.takeIf { slotsByDate.containsKey(it) } ?: slotsByDate.keys.firstOrNull()
    val daySlots = activeDate?.let { slotsByDate[it] }.orEmpty()
    val selectedSlot = daySlots.firstOrNull { it.startsAt == selectedSlotStart }
    val now = java.time.Instant.now()
    fun isUpcoming(appointment: PatientAppointment): Boolean =
        (appointment.status.equals("confirmed", true) || appointment.status.equals("requested", true)) &&
            runCatching { java.time.Instant.parse(appointment.startsAt).isAfter(now) }.getOrDefault(false)

    val upcomingAppointments = appointments.filter(::isUpcoming).sortedBy { it.startsAt }
    val historyAppointments = appointments.filterNot(::isUpcoming).sortedByDescending { it.startsAt }

    if (showingHistory) {
        AppointmentHistoryContent(
            appointments = historyAppointments,
            loaded = loaded,
            busy = busy,
            visibleLimit = historyLimit,
            onShowMore = { historyLimit += 10 },
            onBack = { showingHistory = false },
            onRetry = onRetry
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (upcomingAppointments.isNotEmpty()) {
            Text("Próximas citas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            upcomingAppointments.forEach { appointment -> AppointmentCard(appointment, onJoinVideo) }
        }

        if (!hasAssignedNutritionist) {
            EmptyCard("Sin nutricionista asignado", "Busca un profesional en el directorio para solicitar una primera consulta.", "Buscar nutricionista", onBookAppointment)
        } else {
            Text("Disponibilidad de tu nutricionista", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            Text("Elige primero el día y después una hora. Solo se muestran los horarios de ese día.", color = Muted)
            slotsError?.let { InfoBanner(it, true) }
            if (busy && !loaded) {
                LoadingCard("Cargando disponibilidad")
            } else if (slots.isEmpty() && slotsError == null) {
                EmptyCard("No hay horarios disponibles", "Tu nutricionista todavía no tiene huecos libres configurados.", "Actualizar horarios", onRetry)
            } else if (slots.isNotEmpty()) {
                Text("Día de la cita", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    slotsByDate.forEach { (date, dateSlots) ->
                        val isSelected = date == activeDate
                        if (isSelected) {
                            Button(
                                onClick = { selectedDate = date; selectedSlotStart = null },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(dateLabel(dateSlots.first().startsAt), fontWeight = FontWeight.Bold)
                                    Text("${dateSlots.size} horarios", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { selectedDate = date; selectedSlotStart = null },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(dateLabel(dateSlots.first().startsAt), fontWeight = FontWeight.SemiBold)
                                    Text("${dateSlots.size} horarios", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                if (daySlots.isNotEmpty()) {
                    Text("Horas disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                    daySlots.chunked(3).forEach { rowSlots ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSlots.forEach { slot ->
                                val isSelected = slot.startsAt == selectedSlotStart
                                Box(Modifier.weight(1f)) {
                                    if (isSelected) {
                                        Button(
                                            onClick = { selectedSlotStart = slot.startsAt },
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                        ) { Text(timeLabel(slot.startsAt), fontWeight = FontWeight.Bold) }
                                    } else {
                                        OutlinedButton(
                                            onClick = { selectedSlotStart = slot.startsAt },
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                        ) { Text(timeLabel(slot.startsAt), fontWeight = FontWeight.SemiBold) }
                                    }
                                }
                            }
                            repeat(3 - rowSlots.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                selectedSlot?.let { slot ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Sage)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Horario seleccionado", style = MaterialTheme.typography.labelLarge, color = ForestDark)
                            Text("${dateLabel(slot.startsAt)} · ${timeLabel(slot.startsAt)}", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
                            Text("¿Cómo prefieres la consulta?", color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRequestAppointment(slot, "in_person") },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Presencial") }
                                if (slot.onlineConsultationsAvailable) {
                                    OutlinedButton(
                                        onClick = { onRequestAppointment(slot, "online") },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Online") }
                                }
                            }
                        }
                    }
                } ?: run {
                    Text("Toca una hora para elegir la modalidad de la consulta.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Actualizar disponibilidad") }
        }

        if (loaded) {
            OutlinedButton(
                onClick = { showingHistory = true; historyLimit = 10 },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (historyAppointments.isEmpty()) "Historial de citas" else "Ver historial de citas (${historyAppointments.size})")
            }
        }
    }
}

@Composable
private fun AppointmentHistoryContent(
    appointments: List<PatientAppointment>,
    loaded: Boolean,
    busy: Boolean,
    visibleLimit: Int,
    onShowMore: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Volver a citas") }
        Text("Historial de citas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        Text("Aquí puedes consultar las citas anteriores y las solicitudes que ya no están pendientes.", color = Muted)
        when {
            busy && !loaded -> LoadingCard("Cargando tu historial")
            !loaded -> EmptyCard("Historial no disponible", "Carga tus citas para consultar el historial.", "Actualizar citas", onRetry)
            appointments.isEmpty() -> EmptyCard("Sin historial todavía", "Las citas pasadas, canceladas o finalizadas aparecerán aquí.")
            else -> {
                appointments.take(visibleLimit).forEach { appointment -> AppointmentCard(appointment) }
                if (appointments.size > visibleLimit) {
                    OutlinedButton(onClick = onShowMore, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("Mostrar más citas (${appointments.size - visibleLimit} restantes)")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(appointment: PatientAppointment, onJoinVideo: ((Int) -> Unit)? = null, onCancel: ((Int) -> Unit)? = null) {
    var showCancelConfirmation by remember(appointment.id) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dateLabel(appointment.startsAt), fontWeight = FontWeight.Bold, color = Ink)
                    Text(timeLabel(appointment.startsAt), color = Forest, fontWeight = FontWeight.SemiBold)
                }
                StatusPill(appointment.status, appointment.endsAt)
            }
            Text(appointment.nutritionistName?.takeIf { it.isNotBlank() } ?: "Tu profesional", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text(if (appointment.modality.equals("online", true)) "Videoconsulta" else "Presencial", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (onCancel != null && (appointment.status.equals("confirmed", true) || appointment.status.equals("requested", true)) &&
                runCatching { java.time.Instant.parse(appointment.startsAt).isAfter(java.time.Instant.now()) }.getOrDefault(false)) {
                OutlinedButton(onClick = { showCancelConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar cita")
                }
            }
            if (appointment.status.equals("confirmed", true) && appointment.modality.equals("online", true)) {
                val withinAccessWindow = isWithinVideoAccessWindow(appointment.startsAt, appointment.endsAt, java.time.Instant.now())
                if (withinAccessWindow && onJoinVideo != null) {
                    Button(
                        onClick = { onJoinVideo(appointment.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Entrar a la videoconsulta") }
                } else {
                    Text("El acceso a la videoconsulta estará disponible 15 minutos antes de la cita.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showCancelConfirmation && onCancel != null) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Cancelar cita") },
            text = { Text("¿Quieres cancelar esta cita? Esta acción no se puede deshacer.") },
            confirmButton = { Button(onClick = { showCancelConfirmation = false; onCancel(appointment.id) }) { Text("Sí, cancelar") } },
            dismissButton = { TextButton(onClick = { showCancelConfirmation = false }) { Text("Mantener cita") } }
        )
    }
}

@Composable
private fun DocumentsContent(
    documents: List<PatientDocument>,
    loaded: Boolean,
    busy: Boolean,
    onRetry: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onAcceptDocument: (Long) -> Unit
) {
    when {
        busy && !loaded -> LoadingCard("Cargando tus documentos")
        !loaded -> EmptyCard("Tus documentos", "Los documentos que comparta tu profesional aparecerán aquí.", "Cargar documentos", onRetry)
        documents.isEmpty() -> EmptyCard("No hay documentos todavía", "Cuando tu profesional comparta documentación contigo, la verás en esta sección.")
        else -> documents.forEach { document ->
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).background(Sage, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Text("▤", color = Forest, fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(document.name, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(document.documentType?.replaceFirstChar { it.uppercase() } ?: "Documento", color = Muted, style = MaterialTheme.typography.bodySmall)
                        if (document.requiresSignature) Text(if (document.status.equals("signed", true)) "Aceptado" else "Requiere revisión", color = if (document.status.equals("signed", true)) Forest else Amber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        else Text("Disponible en tu portal", color = Muted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { onOpenDocument(document.id) }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth()) { Text("Abrir documento PDF") }
                        if (document.requiresSignature && document.status.equals("pending", true)) {
                            if (document.viewedAt != null) {
                                Button(onClick = { onAcceptDocument(document.id) }, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()) { Text("He leído y acepto el documento") }
                            } else {
                                Text("Abre el PDF y revísalo antes de aceptar.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressContent(
    eligibilityMessage: String?,
    eligible: Boolean?,
    current: PatientCheckin?,
    history: List<PatientCheckin>,
    weightHistory: List<com.dietoexpress.mobile.data.WeightEntry>,
    draft: PatientCheckinDraft,
    submissionPending: Boolean,
    loaded: Boolean,
    busy: Boolean,
    onDraftChange: (PatientCheckinDraft) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit
) {
    when {
        busy && !loaded -> LoadingCard("Cargando tu seguimiento")
        !loaded -> EmptyCard("Tu evolución", "Aquí encontrarás las revisiones de seguimiento de tu proceso.", "Consultar", onRetry)
        eligible == false -> EmptyCard("El seguimiento se activará tras tu primera consulta", eligibilityMessage ?: "Cuando completes tu primera consulta, podrás consultar el seguimiento.")
        else -> {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Forest)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TU SEGUIMIENTO", color = Color(0xFFC8E4D4), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("Pequeños pasos, progreso real.", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Revisa tus hábitos y sigue la evolución de tu peso.", color = Color(0xFFE2F0E8))
                }
            }
            val biometricPoints = weightHistory.filter { it.weight > 0.0 }.sortedBy { it.date }.takeLast(12)
            val weightPoints: List<Pair<String, Double>> = if (biometricPoints.size >= 2) {
                biometricPoints.map { it.date to it.weight }
            } else {
                history.filter { it.weight != null }
                    .sortedBy { it.weekStart ?: it.submittedAt.orEmpty() }.takeLast(12)
                    .map { (it.weekStart ?: it.submittedAt?.take(10).orEmpty()) to it.weight!! }
            }
            if (weightPoints.size >= 2) {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Evolución del peso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
                        val weights = weightPoints.map { it.second }
                        val minWeight = (weights.minOrNull() ?: 0.0) - 1.0
                        val maxWeight = (weights.maxOrNull() ?: 1.0) + 1.0
                        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                            val range = (maxWeight - minWeight).coerceAtLeast(1.0)
                            val points = weights.mapIndexed { index, value ->
                                Offset(
                                    x = if (weights.size == 1) size.width / 2 else size.width * index / (weights.size - 1),
                                    y = size.height - ((value - minWeight) / range * size.height).toFloat()
                                )
                            }
                            for (index in 0 until points.lastIndex) {
                                drawLine(Forest, points[index], points[index + 1], strokeWidth = 5f, cap = StrokeCap.Round)
                            }
                            points.forEach { drawCircle(Forest, radius = 6f, center = it) }
                        }
                        val first = weightPoints.first()
                        val last = weightPoints.last()
                        Text("${number(first.second)} kg (${dateLabel(first.first)}) → ${number(last.second)} kg (${dateLabel(last.first)})",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                        if (biometricPoints.size >= 2) {
                            Text("Basado en mediciones biométricas del historial.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Basado en las revisiones semanales.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Text("La gráfica de peso aparecerá cuando haya al menos dos mediciones registradas.", color = Muted)
            }
            if (current != null) {
                Text("Revisión de esta semana", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                CheckinCard(current)
            } else {
                Text("Revisión semanal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Cuéntale a tu profesional cómo ha ido la semana. La adherencia es obligatoria; el resto de los datos son opcionales.", color = Muted)
                        if (submissionPending) {
                            Text("El envío anterior no se pudo confirmar. No edites la revisión: reintenta con la misma clave para evitar duplicados.", color = Amber, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedTextField(value = draft.adherence, onValueChange = { onDraftChange(draft.copy(adherence = it)) },
                            label = { Text("Adherencia a la dieta (0–100 %) *") }, enabled = !busy && !submissionPending,
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = draft.weight, onValueChange = { onDraftChange(draft.copy(weight = it)) },
                            label = { Text("Peso (kg, opcional)") }, enabled = !busy && !submissionPending,
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = draft.hunger, onValueChange = { onDraftChange(draft.copy(hunger = it)) },
                                label = { Text("Hambre (0–10)") }, enabled = !busy && !submissionPending, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = draft.energy, onValueChange = { onDraftChange(draft.copy(energy = it)) },
                                label = { Text("Energía (0–10)") }, enabled = !busy && !submissionPending, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(value = draft.sleepQuality, onValueChange = { onDraftChange(draft.copy(sleepQuality = it)) },
                                label = { Text("Sueño (0–10)") }, enabled = !busy && !submissionPending, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = draft.sleepHours, onValueChange = { onDraftChange(draft.copy(sleepHours = it)) },
                                label = { Text("Horas de sueño") }, enabled = !busy && !submissionPending, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        OutlinedTextField(value = draft.training, onValueChange = { onDraftChange(draft.copy(training = it)) },
                            label = { Text("Entrenamiento (0–10)") }, enabled = !busy && !submissionPending,
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("¿Qué dificultades has encontrado?", fontWeight = FontWeight.SemiBold, color = Ink)
                        val barrierOptions = listOf(
                            "time" to "Falta de tiempo", "hunger" to "Hambre", "social" to "Compromisos sociales",
                            "cooking" to "Cocinar", "budget" to "Presupuesto", "planning" to "Organización",
                            "cravings" to "Antojos", "emotions" to "Estado emocional", "travel" to "Viajes", "other" to "Otras"
                        )
                        barrierOptions.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                pair.forEach { (key, label) ->
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = key in draft.barriers, enabled = !busy && !submissionPending,
                                            onCheckedChange = { checked ->
                                                val next = if (checked) (draft.barriers + key).distinct() else draft.barriers - key
                                                onDraftChange(draft.copy(barriers = next))
                                            })
                                        Text(label, style = MaterialTheme.typography.bodySmall, color = Ink)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(value = draft.difficulties, onValueChange = { onDraftChange(draft.copy(difficulties = it)) },
                            label = { Text("Detalle opcional") }, enabled = !busy && !submissionPending,
                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                        OutlinedTextField(value = draft.notes, onValueChange = { onDraftChange(draft.copy(notes = it)) },
                            label = { Text("Comentario para tu profesional (opcional)") }, enabled = !busy && !submissionPending,
                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                        Button(onClick = onSubmit, enabled = !busy && draft.adherence.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text(if (submissionPending) "Reintentar envío seguro" else if (busy) "Enviando…" else "Enviar revisión semanal")
                        }
                    }
                }
            }
            if (history.isNotEmpty()) {
                Text("Historial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                history.forEach { CheckinCard(it) }
            }
        }
    }
}

@Composable
private fun CheckinCard(item: PatientCheckin) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(item.weekStart?.let(::dateLabel) ?: item.submittedAt?.take(10)?.let(::dateLabel) ?: "Revisión", color = Forest, fontWeight = FontWeight.Bold)
            item.weight?.let { ProfileRow("Peso", "${number(it)} kg") }
            item.adherence?.let { ProfileRow("Adherencia", "$it %") }
            item.hunger?.let { ProfileRow("Hambre", "$it / 10") }
            item.energy?.let { ProfileRow("Energía", "$it / 10") }
            item.sleepQuality?.let { ProfileRow("Calidad del sueño", "$it / 10") }
            item.difficulties?.takeIf { it.isNotBlank() }?.let { Text("Dificultades: $it", color = Muted) }
            item.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted) }
        }
    }
}

@Composable
private fun ProfileContent(
    name: String,
    age: Int?,
    weight: Double?,
    height: Double?,
    clinic: String?,
    nutritionist: String?,
    hasDiet: Boolean,
    busy: Boolean,
    offlineOperations: List<OfflineOperationSummary>,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onRefreshOfflineOperations: () -> Unit,
    onRetryPendingSync: () -> Unit,
    onDiscardOfflineOperation: (String) -> Unit
) {
    var discardTarget by remember { mutableStateOf<OfflineOperationSummary?>(null) }
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(76.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) {
                Text(initials(name), color = Forest, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Tu información personal", color = Muted, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = Line)
            ProfileRow("Edad", age?.let { "$it años" } ?: "Sin datos")
            ProfileRow("Peso actual", weight?.let { "${number(it)} kg" } ?: "Sin datos")
            ProfileRow("Altura", height?.let { "$it cm" } ?: "Sin datos")
            ProfileRow("Dieta activa", if (hasDiet) "Sí" else "Todavía no")
            clinic?.takeIf { it.isNotBlank() }?.let { ProfileRow("Centro", it) }
            nutritionist?.takeIf { it.isNotBlank() }?.let { ProfileRow("Profesional", it) }
            Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Actualizar información") }
            TextButton(onClick = onLogout, enabled = !busy) { Text("Cerrar sesión", color = Muted) }
        }
    }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sincronización sin conexión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            Text("Los mensajes pendientes se conservan cifrados en este dispositivo. Las operaciones en conflicto no se reenvían automáticamente.", color = Muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefreshOfflineOperations, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Actualizar estado")
                }
                if (offlineOperations.any { it.status == "PENDING" || it.status == "NEEDS_AUTH" }) {
                    Button(onClick = onRetryPendingSync, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("Sincronizar")
                    }
                }
            }
            if (offlineOperations.isEmpty()) {
                Text("No hay operaciones pendientes ni errores guardados.", color = Forest, fontWeight = FontWeight.SemiBold)
            } else {
                offlineOperations.sortedByDescending { it.createdAtEpochMs }.take(10).forEach { operation ->
                    HorizontalDivider(color = Line)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when (operation.operationType) {
                                "SEND_PATIENT_MESSAGE" -> "Enviar mensaje"
                                "MARK_MESSAGES_READ" -> "Actualizar estado de lectura"
                                else -> "Operación del portal"
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Text(
                            when (operation.status) {
                                "PENDING" -> "Pendiente de sincronización"
                                "NEEDS_AUTH" -> "Necesita una sesión válida"
                                "FAILED" -> "Fallida; no se reintentará automáticamente"
                                "CONFLICT" -> "En conflicto; requiere revisión"
                                else -> operation.status
                            },
                            color = if (operation.status == "CONFLICT" || operation.status == "FAILED") Amber else Muted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        operation.lastError?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (operation.status == "FAILED" || operation.status == "CONFLICT") {
                            TextButton(onClick = { discardTarget = operation }, enabled = !busy) {
                                Text("Descartar operación")
                            }
                        }
                    }
                }
            }
        }
    }
    discardTarget?.let { operation ->
        AlertDialog(
            onDismissRequest = { discardTarget = null },
            title = { Text("Descartar operación") },
            text = { Text("Se eliminará del dispositivo la operación pendiente. Si era un mensaje que no llegó al servidor, tendrás que redactarlo y enviarlo de nuevo. ¿Quieres continuar?") },
            confirmButton = {
                Button(onClick = { discardTarget = null; onDiscardOfflineOperation(operation.id) }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { discardTarget = null }) { Text("Volver") } }
        )
    }
}

@Composable
private fun MacroRow(label: String, value: Double?, unit: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted)
        Text(value?.let { "${number(it)} $unit" } ?: "Sin objetivo", fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoBanner(message: String, error: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (error) Color(0xFFFFF0EE) else Sage)
    ) {
        Text(message, modifier = Modifier.padding(14.dp), color = if (error) Color(0xFF9B2C23) else ForestDark, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadingCard(message: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(14.dp))
            Text(message, color = Muted)
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(44.dp).background(Sage, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("✓", color = Forest, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
            Text(body, color = Muted, style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) {
                OutlinedButton(onClick = onAction, shape = RoundedCornerShape(14.dp)) { Text(action) }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String, endsAt: String?) {
    // Una solicitud sin confirmar que ya quedó atrás caduca; no debe mostrarse eternamente como pendiente.
    // No inferimos una ausencia a partir de la fecha: "No asistió" requiere que el profesional lo registre.
    val expiredRequest = status.equals("requested", true) &&
        endsAt?.let { runCatching { java.time.Instant.parse(it).isBefore(java.time.Instant.now()) }.getOrDefault(false) } == true
    val displayStatus = if (expiredRequest) "expired" else status.lowercase()
    val label = when (displayStatus) {
        "requested" -> "Pendiente"
        "expired" -> "Caducada"
        "confirmed" -> "Confirmada"
        "cancelled" -> "Cancelada"
        "completed" -> "Completada"
        "no_show" -> "No asistida"
        else -> status.replaceFirstChar { it.uppercase() }
    }
    val bg = when (displayStatus) {
        "confirmed", "completed" -> Sage
        "requested", "expired" -> Color(0xFFFFF3D9)
        "cancelled", "no_show" -> Color(0xFFF0F1F0)
        else -> Sage
    }
    val fg = if (displayStatus == "requested" || displayStatus == "expired") Amber else ForestDark
    Surface(color = bg, shape = RoundedCornerShape(100.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun sectionTitle(section: String): String = when (section) {
    "diet" -> "Mi dieta"
    "appointments" -> "Mis citas"
    "documents" -> "Mis documentos"
    "shopping" -> "Lista de la compra"
    "messages" -> "Mensajes"
    "training" -> "Entrenamiento"
    "progress" -> "Mi seguimiento"
    "profile" -> "Mi perfil"
    else -> "Tu espacio"
}

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "D" }

private fun dateLabel(value: String): String = try {
    val date = value.take(10).split("-")
    if (date.size == 3) "${date[2]}/${date[1]}/${date[0]}" else value
} catch (_: Exception) { value }

private fun timeLabel(value: String): String = try {
    value.substringAfter("T").take(5).ifBlank { "Hora pendiente" }
} catch (_: Exception) { "Hora pendiente" }

private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
