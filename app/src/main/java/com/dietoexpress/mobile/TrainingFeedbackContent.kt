package com.dietoexpress.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dietoexpress.mobile.data.PatientTrainingRecordRequest
import com.dietoexpress.mobile.data.TrainingEnvelope
import com.dietoexpress.mobile.data.TrainingSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TrainingForest = Color(0xFF176B52)
private val TrainingMuted = Color(0xFF687970)

@Composable
internal fun TrainingFeedbackContent(
    envelope: TrainingEnvelope?,
    loaded: Boolean,
    busy: Boolean,
    onRecord: (TrainingSession, PatientTrainingRecordRequest) -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Entrenamiento", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Registra cómo te ha ido para que tu profesional pueda revisar la planificación.", color = TrainingMuted)

        if (!loaded && busy) {
            Box(modifier = Modifier.fillMaxWidth().padding(28.dp)) {
                CircularProgressIndicator()
            }
        } else if (envelope?.enabled != true) {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("El seguimiento deportivo no está activado", fontWeight = FontWeight.SemiBold)
                    Text("Si esperabas ver tus sesiones, consulta con tu profesional.", color = TrainingMuted)
                    OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Actualizar") }
                }
            }
        } else {
            envelope.adaptationRecommendation?.let { recommendation ->
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F2EC))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Orientación según tus registros", fontWeight = FontWeight.Bold, color = TrainingForest)
                        Text(adaptationTitle(recommendation.action), fontWeight = FontWeight.SemiBold)
                        Text(recommendation.explanation)
                        Text(
                            "Esta orientación no modifica tu plan. Consulta con tu profesional antes de cambiar el entrenamiento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrainingMuted
                        )
                    }
                }
            }

            if (envelope.sessions.isEmpty()) {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No hay sesiones programadas", fontWeight = FontWeight.SemiBold)
                        Text("Cuando tu profesional añada sesiones, aparecerán aquí.", color = TrainingMuted)
                        OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Actualizar") }
                    }
                }
            }

            envelope.sessions.forEach { session ->
                TrainingSessionCard(session = session, busy = busy, onRecord = onRecord)
            }
        }
    }
}

@Composable
private fun TrainingSessionCard(
    session: TrainingSession,
    busy: Boolean,
    onRecord: (TrainingSession, PatientTrainingRecordRequest) -> Unit
) {
    var outcome by remember(session.id, session.outcome) { mutableStateOf(session.outcome ?: "") }
    var reason by remember(session.id, session.nonCompletionReason) { mutableStateOf(session.nonCompletionReason ?: "none") }
    var recovery by remember(session.id, session.recoveryRating) { mutableStateOf(session.recoveryRating ?: "unknown") }
    var pain by remember(session.id, session.painSignal) { mutableStateOf(session.painSignal ?: "none") }
    var progression by remember(session.id, session.progressionTargetAchieved) {
        mutableStateOf(session.progressionTargetAchieved?.toString() ?: "unknown")
    }
    var duration by remember(session.id, session.actualDurationMinutes, session.durationMinutes) {
        mutableStateOf(session.actualDurationMinutes?.toString() ?: session.durationMinutes?.toString().orEmpty())
    }
    var rpe by remember(session.id, session.sessionRpe) { mutableStateOf(session.sessionRpe?.toString().orEmpty()) }
    var notes by remember(session.id, session.recordNotes) { mutableStateOf(session.recordNotes.orEmpty()) }

    val missed = outcome == "not_completed"
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(dateLabel(session.scheduledDate), style = MaterialTheme.typography.labelMedium, color = TrainingMuted)
            Text(session.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            session.durationMinutes?.let { Text("Duración prevista: $it min", color = TrainingMuted) }
            session.intensityTarget?.takeIf { it.isNotBlank() }?.let { Text(it, color = TrainingMuted) }
            session.instructions?.takeIf { it.isNotBlank() }?.let { Text(it) }
            session.exercisePrescription?.takeIf { it.isNotBlank() }?.let {
                Text("Ejercicios, series y descansos", fontWeight = FontWeight.SemiBold)
                Text(it)
            }
            if (session.completedAt != null) {
                Text(
                    "Último registro: ${session.actualDurationMinutes ?: "—"} min · RPE ${session.sessionRpe ?: "—"}",
                    color = TrainingMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TrainingChoiceField(
                label = "¿Cómo fue la sesión?",
                value = outcome,
                options = listOf("" to "Selecciona una opción", "completed" to "Completada", "partial" to "Parcial", "not_completed" to "No realizada"),
                onSelect = { outcome = it; if (it == "completed") reason = "none" }
            )
            if (outcome.isNotBlank() && outcome != "completed") {
                TrainingChoiceField(
                    label = "Motivo",
                    value = reason,
                    options = listOf(
                        "none" to "Selecciona un motivo",
                        "time" to "Falta de tiempo",
                        "fatigue" to "Fatiga o esfuerzo excesivo",
                        "equipment" to "Falta de material",
                        "motivation" to "Falta de motivación",
                        "illness" to "Enfermedad",
                        "other" to "Otro"
                    ),
                    onSelect = { reason = it }
                )
            }
            TrainingChoiceField(
                label = "¿Cómo te has recuperado?",
                value = recovery,
                options = listOf("unknown" to "No lo sé / no lo indico", "good" to "Bien", "partial" to "Parcialmente", "insufficient" to "Insuficientemente"),
                onSelect = { recovery = it }
            )
            TrainingChoiceField(
                label = "¿Has tenido molestias nuevas o que empeoran?",
                value = pain,
                options = listOf("none" to "No", "new_or_worsening" to "Sí"),
                onSelect = { pain = it }
            )
            TrainingChoiceField(
                label = "¿Has alcanzado el objetivo de repeticiones?",
                value = progression,
                options = listOf("unknown" to "No indicado", "true" to "Sí", "false" to "No"),
                onSelect = { progression = it }
            )

            if (!missed) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Duración realizada (minutos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !busy
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { rpe = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Esfuerzo percibido (0–10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !busy
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas opcionales") },
                minLines = 2,
                maxLines = 4,
                enabled = !busy
            )
            Button(
                onClick = {
                    onRecord(session, PatientTrainingRecordRequest(
                        completedAt = if (missed) null else java.time.Instant.now().toString(),
                        actualDurationMinutes = if (missed) null else duration.toIntOrNull(),
                        sessionRpe = if (missed) null else rpe.toDoubleOrNull(),
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                        outcome = outcome,
                        nonCompletionReason = if (outcome == "completed") "none" else reason,
                        recoveryRating = recovery,
                        painSignal = pain,
                        progressionTargetAchieved = progression.toBooleanStrictOrNull()
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text(if (busy) "Guardando…" else "Guardar check-in") }
        }
    }
}

@Composable
private fun TrainingChoiceField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember(label, value) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second ?: "Selecciona una opción"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(selectedLabel)
                    Text("⌄")
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (optionValue, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = { onSelect(optionValue); expanded = false }
                    )
                }
            }
        }
    }
}

private fun adaptationTitle(action: String): String = when (action) {
    "insufficient_data" -> "Aún faltan registros para orientar cambios"
    "invalid_feedback" -> "Hay registros que necesitan revisión"
    "no_change" -> "Mantén la planificación actual"
    "reorganize_future_sessions" -> "Reorganiza las próximas sesiones"
    "hold_progression_and_review" -> "Pausa la progresión y revisa cómo te encuentras"
    "review_and_reduce_training_stress" -> "Revisa la carga de entrenamiento con tu profesional"
    "progress_according_to_prescription" -> "Se cumplen los criterios de la progresión prescrita"
    "pause_affected_exercise_and_review" -> "Detén el ejercicio implicado y consulta con tu profesional"
    else -> "Revisa la recomendación con tu profesional"
}

private fun dateLabel(value: String): String = try {
    LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
} catch (_: Exception) { value }
