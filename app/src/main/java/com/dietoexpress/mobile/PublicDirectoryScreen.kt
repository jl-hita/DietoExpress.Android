package com.dietoexpress.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietoexpress.mobile.data.PortalService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private val directoryHttp by lazy { PortalService.authenticatedClient() }
private val directoryJson = "application/json; charset=utf-8".toMediaType()
private val directoryDateTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale("es", "ES"))

private data class PublicNutritionist(
    val slug: String,
    val name: String,
    val clinic: String,
    val city: String,
    val province: String,
    val specialties: String,
    val bio: String,
    val online: Boolean,
    val verified: Boolean,
    val canBook: Boolean
)

private data class PublicSlot(val startsAt: String, val endsAt: String) {
    fun label(): String = runCatching {
        directoryDateTime.withZone(ZoneId.systemDefault()).format(Instant.parse(startsAt))
    }.getOrDefault(startsAt)
    fun dayKey(): String = runCatching {
        DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(Instant.parse(startsAt))
    }.getOrDefault(startsAt.take(10))
}

private suspend fun directoryRequest(path: String, body: String? = null): String = withContext(Dispatchers.IO) {
    val url = BuildConfig.API_BASE_URL.trimEnd('/') + path
    val builder = Request.Builder().url(url).header("Accept", "application/json")
    if (body != null) builder.post(body.toRequestBody(directoryJson))
    directoryHttp.newCall(builder.build()).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val message = runCatching { JsonParser.parseString(text).asJsonObject.get("message")?.asString }.getOrNull()
            throw IllegalStateException(message ?: when (response.code) {
                409 -> "Ese horario acaba de ocuparse. Actualiza la disponibilidad y elige otro."
                429 -> "Has realizado demasiadas solicitudes. Espera un momento y vuelve a intentarlo."
                else -> "No se ha podido completar la solicitud (HTTP ${response.code})."
            })
        }
        text
    }
}

private fun JsonObject.string(name: String): String = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
private fun JsonObject.bool(name: String): Boolean = runCatching { get(name)?.asBoolean == true }.getOrDefault(false)

private suspend fun searchNutritionists(query: String, province: String, speciality: String, goal: String, onlineOnly: Boolean, availableOnly: Boolean): List<PublicNutritionist> {
    val params = buildList {
        if (query.isNotBlank()) add("city=" + URLEncoder.encode(query.trim(), "UTF-8"))
        if (province.isNotBlank()) add("province=" + URLEncoder.encode(province.trim(), "UTF-8"))
        if (speciality.isNotBlank()) add("speciality=" + URLEncoder.encode(speciality.trim(), "UTF-8"))
        if (goal.isNotBlank()) add("goal=" + URLEncoder.encode(goal.trim(), "UTF-8"))
        if (onlineOnly) add("online=true")
        if (availableOnly) add("availableOnly=true")
    }.joinToString("&").let { if (it.isBlank()) "" else "?$it" }
    val json = JsonParser.parseString(directoryRequest("/directory/professionals$params")).asJsonArray
    return json.mapNotNull { element ->
        runCatching {
            val o = element.asJsonObject
            PublicNutritionist(
                slug = o.string("slug"),
                name = o.string("fullName").ifBlank { "Nutricionista" },
                clinic = o.string("clinicName"),
                city = o.string("city"),
                province = o.string("province"),
                specialties = o.string("specialties"),
                bio = o.string("publicBio"),
                online = o.bool("onlineConsultations"),
                verified = o.bool("isVerified"),
                canBook = o.bool("canAcceptAppointments")
            )
        }.getOrNull()?.takeIf { it.slug.isNotBlank() }
    }
}

private suspend fun loadPublicSlots(slug: String): List<PublicSlot> {
    val json = JsonParser.parseString(directoryRequest("/directory/professionals/${URLEncoder.encode(slug, "UTF-8")}/availability?days=60")).asJsonArray
    val now = Instant.now()
    return json.mapNotNull { element ->
        runCatching {
            val o = element.asJsonObject
            PublicSlot(o.string("startsAt"), o.string("endsAt"))
        }.getOrNull()
    }.filter { slot -> runCatching { Instant.parse(slot.startsAt).isAfter(now) }.getOrDefault(false) }
        .sortedBy { it.startsAt }
}

@Composable
fun PublicDirectoryScreen(
    initialBooking: Boolean,
    onBack: () -> Unit,
    onOpenBooking: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var speciality by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var availableOnly by remember { mutableStateOf(false) }
    var onlineOnly by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var slotsLoading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var profiles by remember { mutableStateOf<List<PublicNutritionist>>(emptyList()) }
    var selected by remember { mutableStateOf<PublicNutritionist?>(null) }
    var slots by remember { mutableStateOf<List<PublicSlot>>(emptyList()) }
    var selectedSlot by remember { mutableStateOf<PublicSlot?>(null) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var modality by remember { mutableStateOf("in_person") }
    var hasSearched by remember { mutableStateOf(false) }

    fun search() {
        scope.launch {
            loading = true; error = null; notice = null; selected = null; selectedSlot = null; slots = emptyList()
            try { profiles = searchNutritionists(query, province, speciality, goal, onlineOnly, availableOnly); hasSearched = true }
            catch (ex: Exception) { error = ex.message ?: "No se ha podido cargar el directorio." }
            finally { loading = false }
        }
    }
    fun selectNutritionist(profile: PublicNutritionist) {
        selected = profile; selectedSlot = null; slots = emptyList(); error = null; notice = null
        if (!profile.canBook) {
            error = "Este profesional no puede aceptar reservas en este momento."
            return
        }
        scope.launch {
            slotsLoading = true
            try {
                slots = loadPublicSlots(profile.slug)
                if (slots.isEmpty()) notice = "No hay horarios disponibles durante los próximos 60 días. Puedes elegir otro profesional."
            } catch (ex: Exception) { error = ex.message ?: "No se ha podido consultar la disponibilidad." }
            finally { slotsLoading = false }
        }
    }
    fun submitBooking() {
        val professional = selected ?: return
        val slot = selectedSlot ?: return
        if (fullName.isBlank() || email.isBlank() || !email.contains("@")) {
            error = "Introduce tu nombre y un correo electrónico válido."
            return
        }
        scope.launch {
            submitting = true; error = null; notice = null
            try {
                val payload = JsonObject().apply {
                    addProperty("startsAt", slot.startsAt)
                    addProperty("durationMinutes", ((Instant.parse(slot.endsAt).toEpochMilli() - Instant.parse(slot.startsAt).toEpochMilli()) / 60000).toInt())
                    addProperty("fullName", fullName.trim())
                    addProperty("email", email.trim())
                    addProperty("phone", phone.trim())
                    addProperty("patientNotes", notes.trim())
                    addProperty("modality", if (modality == "online") "online" else "in_person")
                }.toString()
                val response = JsonParser.parseString(directoryRequest("/directory/professionals/${URLEncoder.encode(professional.slug, "UTF-8")}/appointments", payload)).asJsonObject
                val status = response.string("status")
                notice = if (status == "confirmed") "¡Cita confirmada! Recibirás los detalles por correo." else "Solicitud enviada. El profesional debe confirmar la cita."
                selectedSlot = null
                // Refrescar horarios del servidor tras reservar para no dejar visible un hueco ocupado.
                slots = loadPublicSlots(professional.slug)
            } catch (ex: Exception) {
                error = ex.message ?: "No se ha podido enviar la solicitud. Actualiza los horarios e inténtalo de nuevo."
                // Un conflicto puede significar que otro paciente reservó el hueco simultáneamente.
                runCatching { slots = loadPublicSlots(professional.slug) }
            } finally { submitting = false }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = { if (selected != null) { selected = null; selectedSlot = null; slots = emptyList(); error = null; notice = null } else onBack() }) {
            Text(if (selected != null) "← Resultados" else "← Volver")
        }
        Text("Nutricionistas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DirectoryForestDark)
        Text("Encuentra un profesional y consulta sus próximos horarios disponibles.", color = DirectoryMuted)
        if (selected == null) {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ciudad o localidad") }, singleLine = true)
            OutlinedTextField(value = province, onValueChange = { province = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Provincia (opcional)") }, singleLine = true)
            OutlinedTextField(value = speciality, onValueChange = { speciality = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Especialidad (opcional)") }, singleLine = true)
            OutlinedTextField(value = goal, onValueChange = { goal = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Objetivo (opcional)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onlineOnly = !onlineOnly }) { Text(if (onlineOnly) "✓ Solo online" else "Filtrar online") }
                OutlinedButton(onClick = { availableOnly = !availableOnly }) { Text(if (availableOnly) "✓ Con horarios" else "Con horarios") }
            }
            Button(onClick = { search() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Buscar nutricionistas") }
            if (!hasSearched) {
                Text("Puedes buscar por ciudad o filtrar profesionales que atienden online.")
                Button(onClick = { search() }, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text("Ver nutricionistas") }
            }
            if (loading) CircularProgressIndicator()
            if (hasSearched && !loading && profiles.isEmpty() && error == null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("No hay resultados", fontWeight = FontWeight.Bold)
                        Text("Prueba otra localidad o ajusta los filtros de provincia, especialidad y modalidad.", color = DirectoryMuted)
                    }
                }
            }
            profiles.forEach { profile ->
                Card(
                    onClick = { selectNutritionist(profile) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(profile.name, fontWeight = FontWeight.Bold, color = DirectoryInk, modifier = Modifier.weight(1f))
                            if (profile.verified) Text("Verificado", color = DirectoryForest, fontSize = 11.sp)
                        }
                        val location = listOf(profile.city, profile.province).filter { it.isNotBlank() }.distinct().joinToString(", ")
                        if (location.isNotBlank()) Text(location, color = DirectoryMuted, style = MaterialTheme.typography.bodySmall)
                        if (profile.clinic.isNotBlank()) Text(profile.clinic, color = DirectoryMuted, style = MaterialTheme.typography.bodySmall)
                        if (profile.specialties.isNotBlank()) Text(profile.specialties, color = DirectoryForestDark, style = MaterialTheme.typography.bodyMedium)
                        if (profile.bio.isNotBlank()) Text(profile.bio.take(220), color = DirectoryMuted, style = MaterialTheme.typography.bodySmall)
                        Text(if (profile.canBook) "Consultar horarios →" else "Reservas no disponibles", color = if (profile.canBook) DirectoryForest else DirectoryMuted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            val profile = selected!!
            Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(listOf(profile.city, profile.province).filter { it.isNotBlank() }.distinct().joinToString(", "), color = DirectoryMuted)
            if (slotsLoading) CircularProgressIndicator()
            if (!slotsLoading && slots.isNotEmpty()) {
                Text("Elige fecha y hora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                slots.groupBy { it.dayKey() }.toSortedMap().forEach { (day, daySlots) ->
                    Text(runCatching { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("es", "ES")).format(java.time.LocalDate.parse(day)) }.getOrDefault(day).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, color = DirectoryInk)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        daySlots.forEach { slot ->
                            val chosen = selectedSlot == slot
                            OutlinedButton(onClick = { selectedSlot = slot; error = null }, enabled = !submitting) {
                                Text(if (chosen) "✓ ${slot.label().substringAfter(", ")}" else slot.label().substringAfter(", "))
                            }
                        }
                    }
                }
                Text("Datos para la reserva", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nombre completo") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Correo electrónico") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Teléfono (opcional)") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Motivo o notas (opcional)") }, minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { modality = "in_person" }, enabled = !submitting) { Text(if (modality == "in_person") "✓ Presencial" else "Presencial") }
                    OutlinedButton(onClick = { modality = "online" }, enabled = !submitting && profile.online) { Text(if (modality == "online") "✓ Online" else "Online") }
                }
                Button(onClick = { submitBooking() }, modifier = Modifier.fillMaxWidth(), enabled = !submitting && selectedSlot != null, shape = RoundedCornerShape(14.dp)) {
                    Text(if (submitting) "Enviando solicitud…" else "Solicitar cita")
                }
            }
            if (!slotsLoading && slots.isEmpty() && error == null && notice == null) {
                Text("No hay horarios disponibles. Vuelve a consultar más tarde o elige otro profesional.", color = DirectoryMuted)
            }
        }
        error?.let { DirectoryMessage(it, true) }
        notice?.let { DirectoryMessage(it, false) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DirectoryMessage(message: String, isError: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (isError) Color(0xFFFFF0EE) else Color(0xFFE7F2EC))) {
        Text(message, modifier = Modifier.padding(14.dp), color = if (isError) Color(0xFF9B2C23) else DirectoryForestDark)
    }
}
