package com.dietoexpress.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietoexpress.mobile.data.PatientMessage
import com.dietoexpress.mobile.data.ShoppingCategory
import com.dietoexpress.mobile.data.TrainingEnvelope

@Composable
internal fun ShoppingPortalContent(categories: List<ShoppingCategory>, loaded: Boolean, busy: Boolean, onRetry: () -> Unit) {
    when {
        busy && !loaded -> Text("Preparando tu lista de la compra…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        !loaded -> Button(onClick = onRetry) { Text("Cargar lista de la compra") }
        categories.isEmpty() -> Text("No hay lista disponible. Necesitas un plan nutricional activo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> categories.forEach { category ->
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(category.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    category.items.forEach { item ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.foodName, fontWeight = FontWeight.SemiBold)
                                item.commercialDescription?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                                if (!item.allergenDataAvailable || item.requiresAllergenCheck) {
                                    Text("Comprueba los alérgenos en el envase", color = Color(0xFF94651B), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                } else if (item.allergenTags.isNotEmpty()) {
                                    Text("Alérgenos: ${item.allergenTags.joinToString()}", color = Color(0xFF94651B), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${item.roundedGrams.toInt()} g", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PatientMessagesContent(
    messages: List<PatientMessage>,
    loaded: Boolean,
    draft: String,
    busy: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit
) {
    if (!loaded) {
        Button(onClick = onRetry, enabled = !busy) { Text("Abrir conversación") }
        return
    }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tu conversación", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (messages.isEmpty()) Text("Todavía no hay mensajes. Puedes iniciar la conversación.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            messages.forEach { message ->
                val mine = message.senderType.equals("patient", true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.fillMaxWidth(0.88f)
                            .background(if (mine) Color(0xFFE7F2EC) else Color(0xFFF5F8F6), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(if (mine) "Tú" else "Tu profesional", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(message.body)
                        Text(message.createdAt.take(16).replace('T', ' '), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider()
            OutlinedTextField(
                value = draft, onValueChange = onDraftChange, enabled = !busy,
                modifier = Modifier.fillMaxWidth(), label = { Text("Escribe un mensaje") },
                minLines = 2, maxLines = 5, shape = RoundedCornerShape(14.dp)
            )
            Button(onClick = onSend, enabled = !busy && draft.isNotBlank() && draft.length <= 5000, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Enviando…" else "Enviar mensaje")
            }
        }
    }
}

@Composable
internal fun TrainingPortalContent(training: TrainingEnvelope?, loaded: Boolean, busy: Boolean, onRecordSession: (com.dietoexpress.mobile.data.TrainingSession, Int?, Double?, String?) -> Unit, onRetry: () -> Unit) {
    when {
        busy && !loaded -> Text("Cargando tu planificación…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        !loaded -> Button(onClick = onRetry) { Text("Cargar entrenamientos") }
        training?.enabled == false -> Text("El entrenamiento no está activado para tu expediente. Puedes comentarlo con tu profesional.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        training == null || training.sessions.isEmpty() -> Text("Todavía no hay sesiones programadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TU PLAN DE ENTRENAMIENTO", color = Color(0xFFC8E4D4), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("${training.sessions.size} sesiones programadas", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            training.sessions.forEach { session ->
                var actualDuration by remember(session.id) { mutableStateOf(session.actualDurationMinutes?.toString().orEmpty()) }
                var sessionRpe by remember(session.id) { mutableStateOf(session.sessionRpe?.toString().orEmpty()) }
                var recordNotes by remember(session.id) { mutableStateOf(session.recordNotes.orEmpty()) }
                var recordError by remember(session.id) { mutableStateOf<String?>(null) }
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(session.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(session.scheduledDate, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                            Text(if (session.completedAt != null) "Completada" else "Pendiente", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(session.sessionType.replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        session.durationMinutes?.let { Text("Duración prevista: $it min", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        session.intensityTarget?.takeIf { it.isNotBlank() }?.let { Text("Intensidad: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        session.instructions?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        session.exercisePrescription?.takeIf { it.isNotBlank() }?.let {
                            HorizontalDivider()
                            Text("Ejercicios", fontWeight = FontWeight.SemiBold)
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (session.completedAt == null) {
                            OutlinedTextField(
                                value = actualDuration,
                                onValueChange = { actualDuration = it; recordError = null },
                                label = { Text("Duración real (min, opcional)") },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sessionRpe,
                                onValueChange = { sessionRpe = it; recordError = null },
                                label = { Text("Esfuerzo percibido (RPE 0–10, opcional)") },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = recordNotes,
                                onValueChange = { recordNotes = it; recordError = null },
                                label = { Text("Notas (opcional)") },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                            recordError?.let { Text(it, color = Color(0xFF94651B), style = MaterialTheme.typography.bodySmall) }
                            Button(onClick = {
                                val duration = actualDuration.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                                val rpe = sessionRpe.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                                recordError = when {
                                    actualDuration.isNotBlank() && (duration == null || duration !in 1..1440) -> "La duración debe estar entre 1 y 1440 minutos."
                                    sessionRpe.isNotBlank() && (rpe == null || rpe !in 0.0..10.0) -> "El RPE debe estar entre 0 y 10."
                                    recordNotes.length > 2000 -> "Las notas no pueden superar 2000 caracteres."
                                    else -> null
                                }
                                if (recordError == null) onRecordSession(session, duration, rpe, recordNotes.trim().takeIf { it.isNotEmpty() })
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Registrar sesión realizada")
                            }
                        }
                    }
                }
            }
        }
    }
}
