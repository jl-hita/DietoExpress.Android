package com.dietoexpress.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietoexpress.mobile.data.DietDay
import com.dietoexpress.mobile.data.DietDetail
import com.dietoexpress.mobile.data.DietMeal
import com.dietoexpress.mobile.data.PatientAppointment
import com.dietoexpress.mobile.data.PatientCheckin
import com.dietoexpress.mobile.data.PatientDocument

private val Forest = Color(0xFF176B52)
private val ForestDark = Color(0xFF104B3B)
private val Sage = Color(0xFFE7F2EC)
private val Canvas = Color(0xFFF5F8F6)
private val Ink = Color(0xFF172923)
private val Muted = Color(0xFF687970)
private val Line = Color(0xFFE2EAE5)
private val Amber = Color(0xFF94651B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    if (state.profile == null) {
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
                            onRequestLink = vm::requestAccessLink
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
                                        Text("DIETOEXPRESS", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                        Text(sectionTitle(state.section), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                                    }
                                    Box(
                                        modifier = Modifier.size(44.dp).background(Sage, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(initials(state.profile?.fullName.orEmpty()), color = ForestDark, fontWeight = FontWeight.Bold)
                                    }
                                }

                                state.message?.let {
                                    InfoBanner(it, state.error)
                                }

                                when (state.section) {
                                    "diet" -> DietContent(
                                        diet = state.diet,
                                        loaded = state.dietLoaded,
                                        busy = state.busy,
                                        onRetry = vm::loadDiet
                                    )
                                    "appointments" -> AppointmentsContent(
                                        appointments = state.appointments,
                                        loaded = state.appointmentsLoaded,
                                        busy = state.busy,
                                        onRetry = { vm.openSection("appointments") }
                                    )
                                    "documents" -> DocumentsContent(
                                        documents = state.documents,
                                        loaded = state.documentsLoaded,
                                        busy = state.busy,
                                        onRetry = { vm.openSection("documents") }
                                    )
                                    "progress" -> ProgressContent(
                                        eligibilityMessage = state.checkinEligibility?.message,
                                        eligible = state.checkinEligibility?.eligible,
                                        current = state.currentCheckin,
                                        history = state.checkinHistory,
                                        loaded = state.checkinLoaded,
                                        busy = state.busy,
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
                                        onRefresh = vm::refreshProfile,
                                        onLogout = vm::logout
                                    )
                                    else -> HomeContent(
                                        name = state.profile?.fullName.orEmpty(),
                                        clinic = state.profile?.clinicName,
                                        hasDiet = state.profile?.hasActiveDiet == true,
                                        nutritionist = state.profile?.nutritionistName,
                                        busy = state.busy,
                                        onDiet = { vm.openSection("diet") },
                                        onAppointments = { vm.openSection("appointments") },
                                        onProgress = { vm.openSection("progress") },
                                        onDocuments = { vm.openSection("documents") },
                                        onProfile = { vm.openSection("profile") }
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
    onRequestLink: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(64.dp).background(Forest, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("D", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
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
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Tu información personal se consulta de forma segura.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HomeContent(
    name: String,
    clinic: String?,
    hasDiet: Boolean,
    nutritionist: String?,
    busy: Boolean,
    onDiet: () -> Unit,
    onAppointments: () -> Unit,
    onProgress: () -> Unit,
    onDocuments: () -> Unit,
    onProfile: () -> Unit
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
        if (!clinic.isNullOrBlank() || !nutritionist.isNullOrBlank()) {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) {
                        Text("＋", color = Forest, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Tu equipo de cuidado", fontWeight = FontWeight.SemiBold, color = Ink)
                        clinic?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                        nutritionist?.takeIf { it.isNotBlank() }?.let { Text("Profesional: $it", color = Muted, style = MaterialTheme.typography.bodySmall) }
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
private fun AppointmentsContent(appointments: List<PatientAppointment>, loaded: Boolean, busy: Boolean, onRetry: () -> Unit) {
    when {
        busy && !loaded -> LoadingCard("Cargando tus citas")
        !loaded -> EmptyCard("Tu agenda", "Consulta aquí tus citas y su estado.", "Cargar citas", onRetry)
        appointments.isEmpty() -> EmptyCard("Todavía no tienes citas", "Cuando tengas una cita con tu profesional, aparecerá aquí.")
        else -> appointments.sortedByDescending { it.startsAt }.forEach { appointment ->
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dateLabel(appointment.startsAt), fontWeight = FontWeight.Bold, color = Ink)
                            Text(timeLabel(appointment.startsAt), color = Forest, fontWeight = FontWeight.SemiBold)
                        }
                        StatusPill(appointment.status)
                    }
                    Text(appointment.nutritionistName?.takeIf { it.isNotBlank() } ?: "Tu profesional", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(if (appointment.modality.equals("online", true)) "Videoconsulta" else "Presencial", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (appointment.status.equals("confirmed", true) && appointment.modality.equals("online", true)) {
                        Text("El acceso a la videoconsulta estará disponible según el horario de tu cita.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentsContent(documents: List<PatientDocument>, loaded: Boolean, busy: Boolean, onRetry: () -> Unit) {
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
    loaded: Boolean,
    busy: Boolean,
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
                    Text("Revisa los datos compartidos durante tus revisiones semanales.", color = Color(0xFFE2F0E8))
                }
            }
            current?.let {
                Text("Revisión de esta semana", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                CheckinCard(it)
            } ?: EmptyCard("Aún no hay revisión esta semana", "Cuando envíes una revisión semanal desde el portal, aparecerá aquí.")
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
            Text(item.weekStart ?: item.submittedAt?.take(10) ?: "Revisión", color = Forest, fontWeight = FontWeight.Bold)
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
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
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
private fun StatusPill(status: String) {
    val label = when (status.lowercase()) {
        "requested" -> "Pendiente"
        "confirmed" -> "Confirmada"
        "cancelled" -> "Cancelada"
        "completed" -> "Completada"
        "no_show" -> "No asistida"
        else -> status.replaceFirstChar { it.uppercase() }
    }
    val bg = when (status.lowercase()) {
        "confirmed", "completed" -> Sage
        "requested" -> Color(0xFFFFF3D9)
        "cancelled", "no_show" -> Color(0xFFF0F1F0)
        else -> Sage
    }
    val fg = if (status.lowercase() == "requested") Amber else ForestDark
    Surface(color = bg, shape = RoundedCornerShape(100.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun sectionTitle(section: String): String = when (section) {
    "diet" -> "Mi dieta"
    "appointments" -> "Mis citas"
    "documents" -> "Mis documentos"
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
