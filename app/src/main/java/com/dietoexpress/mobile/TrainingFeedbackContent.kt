package com.dietoexpress.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dietoexpress.mobile.data.PatientTrainingRecordRequest
import com.dietoexpress.mobile.data.TrainingEnvelope
import com.dietoexpress.mobile.data.TrainingExerciseFeedback
import com.dietoexpress.mobile.data.TrainingSession
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter

private val TrainingForest = Color(0xFF176B52)
private val TrainingMuted = Color(0xFF687970)

private data class TrainingWeekSummary(
    val weekStart: LocalDate, val sessions: Int, val totalSets: Int, val averageRir: Double?,
    val loadIndex: Int, val volumePct: Float, val frequencyPct: Float, val intensityPct: Float
)

internal fun upcomingTrainingSessions(
    sessions: List<TrainingSession>,
    today: LocalDate = LocalDate.now(),
    pageOffset: Int = 0
): List<TrainingSession> {
    val periodStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(pageOffset * 2L)
    val periodEnd = periodStart.plusWeeks(2).minusDays(1)
    return sessions.filter { session ->
        val date = runCatching { LocalDate.parse(session.scheduledDate.take(10)) }.getOrNull()
        date != null && date in periodStart..periodEnd
    }.sortedBy { it.scheduledDate }
}

internal fun hasTrainingSessionsBefore(sessions: List<TrainingSession>, today: LocalDate, pageOffset: Int): Boolean {
    val periodStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(pageOffset * 2L)
    return sessions.any { session ->
        runCatching { LocalDate.parse(session.scheduledDate.take(10)) }.getOrNull()?.isBefore(periodStart) == true
    }
}

internal fun hasTrainingSessionsAfter(sessions: List<TrainingSession>, today: LocalDate, pageOffset: Int): Boolean {
    val periodEnd = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(pageOffset * 2L + 2).minusDays(1)
    return sessions.any { session ->
        runCatching { LocalDate.parse(session.scheduledDate.take(10)) }.getOrNull()?.isAfter(periodEnd) == true
    }
}

private fun buildTrainingWeekSummaries(sessions: List<TrainingSession>): List<TrainingWeekSummary> {
    val grouped = sessions.mapNotNull { session ->
        val date = runCatching { LocalDate.parse(session.scheduledDate.take(10)) }.getOrNull() ?: return@mapNotNull null
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to session
    }.groupBy({ it.first }, { it.second }).toSortedMap()
    val raw = grouped.map { (start, weekSessions) ->
        val exercises = weekSessions.flatMap { it.exercisePrescriptionData?.exercises.orEmpty() }
        val sets = exercises.sumOf { (it.sets ?: 0).coerceAtLeast(0) }
        val rirs = exercises.mapNotNull { it.targetRepetitionsInReserve?.takeIf { rir -> rir in 0.0..10.0 } }
        val averageRir = rirs.takeIf { it.isNotEmpty() }?.average()
        val intensity = averageRir?.let { (10.0 - it).coerceIn(0.0, 10.0) }
        val load = sets * (intensity?.let { 0.7 + it / 10.0 * 0.6 } ?: 1.0) * (1.0 + (weekSessions.size - 2).coerceAtLeast(0) * 0.05)
        Triple(start, Triple(weekSessions.size, sets, averageRir), load)
    }
    val maxSets = (raw.maxOfOrNull { it.second.second } ?: 1).coerceAtLeast(1)
    val maxFrequency = (raw.maxOfOrNull { it.second.first } ?: 1).coerceAtLeast(1)
    val maxLoad = (raw.maxOfOrNull { it.third } ?: 1.0).coerceAtLeast(1.0)
    return raw.map { (start, metrics, load) ->
        val intensity = metrics.third?.let { ((10.0 - it).coerceIn(0.0, 10.0) / 10.0).toFloat() } ?: 0f
        TrainingWeekSummary(start, metrics.first, metrics.second, metrics.third, (load / maxLoad * 100).toInt(),
            metrics.second.toFloat() / maxSets, metrics.first.toFloat() / maxFrequency, intensity)
    }.takeLast(6)
}

@Composable
private fun LoadLegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.padding(top = 4.dp).width(8.dp).height(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TrainingMuted)
    }
}

@Composable
private fun TrainingLoadBar(label: String, value: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TrainingMuted)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF263D32))
        }
        Box(modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE9F0EC))) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(7.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
internal fun TrainingFeedbackContent(
    envelope: TrainingEnvelope?,
    loaded: Boolean,
    busy: Boolean,
    onRecord: (TrainingSession, PatientTrainingRecordRequest) -> Unit,
    onRetry: () -> Unit
) {
    val weekSummaries = remember(envelope?.sessions) { buildTrainingWeekSummaries(envelope?.sessions.orEmpty()) }
    var trainingPageOffset by remember { mutableStateOf(0) }
    val allSessions = envelope?.sessions.orEmpty()
    val visibleSessions = remember(allSessions, trainingPageOffset) {
        upcomingTrainingSessions(allSessions, pageOffset = trainingPageOffset)
    }
    val canGoBack = remember(allSessions, trainingPageOffset) {
        hasTrainingSessionsBefore(allSessions, LocalDate.now(), trainingPageOffset)
    }
    val canGoForward = remember(allSessions, trainingPageOffset) {
        hasTrainingSessionsAfter(allSessions, LocalDate.now(), trainingPageOffset)
    }
    val pageStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(trainingPageOffset * 2L)
    val pageEnd = pageStart.plusWeeks(2).minusDays(1)
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM")
    var selectedSessionId by remember { mutableStateOf<Int?>(null) }
    val selectedSession = visibleSessions.firstOrNull { it.id == selectedSessionId }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TrainingForest)) {
            Text(
                "Tu plan, paso a paso",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (!loaded && busy) {
            Box(modifier = Modifier.fillMaxWidth().padding(28.dp)) { CircularProgressIndicator() }
        } else if (envelope?.enabled != true) {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("El seguimiento deportivo no está activado", fontWeight = FontWeight.SemiBold)
                    Text("Si esperabas ver tus sesiones, consulta con tu profesional.", color = TrainingMuted)
                    OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Actualizar") }
                }
            }
        } else if (selectedSession != null) {
            OutlinedButton(onClick = { selectedSessionId = null }, enabled = !busy) {
                Text("← Volver a mis entrenamientos")
            }
            TrainingSessionCard(session = selectedSession, busy = busy, onRecord = onRecord)
        } else {
            envelope.adaptationRecommendation?.let { recommendation ->
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F2EC))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Orientación según tus registros", fontWeight = FontWeight.Bold, color = TrainingForest)
                        Text(adaptationTitle(recommendation.action), fontWeight = FontWeight.SemiBold)
                        Text(recommendation.explanation)
                        Text("Esta orientación no modifica tu plan. Consulta con tu profesional antes de cambiar el entrenamiento.",
                            style = MaterialTheme.typography.bodySmall, color = TrainingMuted)
                    }
                }
            }
            if (weekSummaries.isNotEmpty()) {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("La carga de tu plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TrainingForest)
                        Text("Resumen semanal de volumen, frecuencia e intensidad prescrita.", color = TrainingMuted, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LoadLegendDot(Color(0xFF25845F), "Volumen")
                            LoadLegendDot(Color(0xFF438FCA), "Frecuencia")
                            LoadLegendDot(Color(0xFFDF9D43), "Intensidad")
                        }
                        weekSummaries.forEach { week ->
                            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Semana del ${week.weekStart.format(DateTimeFormatter.ofPattern("dd/MM"))}", fontWeight = FontWeight.SemiBold)
                                    Text("${week.loadIndex}/100", color = TrainingForest, fontWeight = FontWeight.Bold)
                                }
                                TrainingLoadBar("Volumen", "${week.totalSets} series", week.volumePct, Color(0xFF25845F))
                                TrainingLoadBar("Frecuencia", "${week.sessions} sesiones", week.frequencyPct, Color(0xFF438FCA))
                                TrainingLoadBar("Intensidad · RIR", week.averageRir?.let { "RIR ${"%.1f".format(java.util.Locale.ROOT, it)}" } ?: "No indicada", week.intensityPct, Color(0xFFDF9D43))
                            }
                        }
                        Text("El índice compara las semanas de tu plan. La intensidad se muestra como RIR (repeticiones en reserva); no representa kilos levantados.", style = MaterialTheme.typography.bodySmall, color = TrainingMuted)
                    }
                }
            }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (trainingPageOffset == 0) "Esta semana y la siguiente" else "Entrenamientos · ${pageStart.format(dateFormatter)}–${pageEnd.format(dateFormatter)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TrainingForest
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { trainingPageOffset -= 1 }, enabled = canGoBack && !busy) {
                            Text("← Anteriores")
                        }
                        Text("${trainingPageOffset + 1}", color = TrainingMuted, style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = { trainingPageOffset += 1 }, enabled = canGoForward && !busy) {
                            Text("Siguientes →")
                        }
                    }
                }
                if (visibleSessions.isEmpty()) {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No hay sesiones próximas", fontWeight = FontWeight.SemiBold)
                            Text("No hay sesiones programadas en este periodo de dos semanas.", color = TrainingMuted)
                            OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Actualizar") }
                        }
                    }
                } else {
                    visibleSessions.forEach { session ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { selectedSessionId = session.id },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(session.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${dateLabel(session.scheduledDate)} · ${session.sessionType.replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TrainingMuted
                                    )
                                    session.durationMinutes?.let { Text("$it min", style = MaterialTheme.typography.labelSmall, color = TrainingMuted) }
                                }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        if (session.completedAt != null) "Completada" else "Pendiente",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TrainingForest,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("Ver sesión  ›", color = TrainingForest, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

private fun trainingMuscleLabel(value: String): String = when (value) {
    // Numeric values are used by existing JSONB snapshots serialized before string-enum conversion.
    "0" -> "Pecho"
    "1" -> "Dorsales"
    "2" -> "Espalda alta"
    "3" -> "Deltoides"
    "4" -> "Bíceps"
    "5" -> "Tríceps"
    "6" -> "Antebrazos"
    "7" -> "Abdominales"
    "8" -> "Oblicuos"
    "9" -> "Erectores espinales"
    "10" -> "Glúteos"
    "11" -> "Cuádriceps"
    "12" -> "Isquiotibiales"
    "13" -> "Gemelos"
    "Chest" -> "Pecho"
    "Lats" -> "Dorsales"
    "UpperBack" -> "Espalda alta"
    "Deltoids" -> "Deltoides"
    "Biceps" -> "Bíceps"
    "Triceps" -> "Tríceps"
    "Forearms" -> "Antebrazos"
    "Abdominals" -> "Abdominales"
    "Obliques" -> "Oblicuos"
    "ErectorSpinae" -> "Erectores espinales"
    "Glutes" -> "Glúteos"
    "Quadriceps" -> "Cuádriceps"
    "Hamstrings" -> "Isquiotibiales"
    "Calves" -> "Gemelos"
    else -> value
}

private fun trainingProgressionLabel(rule: Int?): String = when (rule) {
    1 -> "Doble progresión"
    2 -> "Aumentar carga dentro de límites"
    else -> "Sin progresión automática definida"
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
    val completedSets = remember(session.id, session.exerciseFeedback) {
        mutableStateMapOf<String, String>().apply {
            session.exerciseFeedback.forEach { feedback ->
                val exercise = session.exercisePrescriptionData?.exercises?.firstOrNull {
                    it.exerciseId == feedback.exerciseId || it.exerciseNameSnapshot == feedback.exerciseName
                }
                val index = session.exercisePrescriptionData?.exercises?.indexOfFirst {
                    it.exerciseId == feedback.exerciseId || it.exerciseNameSnapshot == feedback.exerciseName
                } ?: -1
                val key = exercise?.exerciseId ?: "order-${exercise?.order ?: index.coerceAtLeast(0)}"
                put(key, feedback.completedSets.toString())
            }
        }
    }
    val skippedReasons = remember(session.id, session.exerciseFeedback) {
        mutableStateMapOf<String, String>().apply {
            session.exerciseFeedback.forEach { feedback ->
                if (feedback.skippedSetReason != null) {
                    val exercise = session.exercisePrescriptionData?.exercises?.firstOrNull {
                        it.exerciseId == feedback.exerciseId || it.exerciseNameSnapshot == feedback.exerciseName
                    }
                    val index = session.exercisePrescriptionData?.exercises?.indexOfFirst {
                        it.exerciseId == feedback.exerciseId || it.exerciseNameSnapshot == feedback.exerciseName
                    } ?: -1
                    val key = exercise?.exerciseId ?: "order-${exercise?.order ?: index.coerceAtLeast(0)}"
                    put(key, feedback.skippedSetReason)
                }
            }
        }
    }

    val missed = outcome == "not_completed"
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(dateLabel(session.scheduledDate), style = MaterialTheme.typography.labelMedium, color = TrainingMuted)
            Text(session.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            session.durationMinutes?.let { Text("Duración prevista: $it min", color = TrainingMuted) }
            session.intensityTarget?.takeIf { it.isNotBlank() }?.let { Text(it, color = TrainingMuted) }
            session.instructions?.takeIf { it.isNotBlank() }?.let { Text(it) }
            val structuredPrescription = session.exercisePrescriptionData?.takeIf { it.exercises.isNotEmpty() }
            if (structuredPrescription != null) {
                Text("Ejercicios, series y descansos", fontWeight = FontWeight.SemiBold)
                structuredPrescription.weekNumber?.let { week ->
                    Text("Semana $week · ${structuredPrescription.estimatedDurationMinutes ?: session.durationMinutes ?: "—"} min",
                        color = TrainingMuted, style = MaterialTheme.typography.bodySmall)
                }
                structuredPrescription.exercises.forEachIndexed { index, exercise ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F8F6))
                    ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("${exercise.order ?: index + 1}. ${exercise.exerciseNameSnapshot ?: "Ejercicio"}",
                            fontWeight = FontWeight.SemiBold)
                        Text("${exercise.sets ?: "—"} series × ${exercise.minRepetitions ?: "—"}–${exercise.maxRepetitions ?: "—"} repeticiones")
                        Text("RIR ${exercise.targetRepetitionsInReserve?.toString() ?: "—"} · Descanso ${exercise.restSeconds ?: "—"} s",
                            color = TrainingMuted, style = MaterialTheme.typography.bodySmall)
                        if (exercise.primaryMuscleGroups.isNotEmpty()) {
                            Text("Principal: ${exercise.primaryMuscleGroups.joinToString(", ") { trainingMuscleLabel(it) }}",
                                color = TrainingMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (exercise.secondaryMuscleGroups.isNotEmpty()) {
                            Text("Secundario: ${exercise.secondaryMuscleGroups.joinToString(", ") { trainingMuscleLabel(it) }}",
                                color = TrainingMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Progresión: ${trainingProgressionLabel(exercise.progressionRule)}",
                            color = TrainingMuted, style = MaterialTheme.typography.bodySmall)
                        val key = exercise.exerciseId ?: "order-${exercise.order ?: index}"
                        val planned = exercise.sets ?: 0
                        if (planned > 0) {
                            OutlinedTextField(
                                value = completedSets[key].orEmpty(),
                                onValueChange = { value -> if (value.isEmpty() || value.all(Char::isDigit)) completedSets[key] = value },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Series realizadas (de $planned)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                enabled = !busy
                            )
                        } else {
                            Text("No hay un número de series prescritas con el que comparar el volumen real.", color = TrainingMuted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        val done = completedSets[key]?.toIntOrNull()
                        if (done != null && done in 0 until planned) {
                            TrainingChoiceField("Motivo de las series omitidas", skippedReasons[key].orEmpty(), listOf(
                                "" to "Selecciona un motivo",
                                "fatigue" to "Fatiga o esfuerzo excesivo",
                                "time" to "Falta de tiempo",
                                "equipment" to "Falta de material",
                                "motivation" to "Falta de motivación",
                                "illness" to "Enfermedad",
                                "pain" to "Dolor o molestias",
                                "other" to "Otro"
                            )) { skippedReasons[key] = it }
                        }
                    }
                    }
                }
            } else {
                session.exercisePrescription?.takeIf { it.isNotBlank() }?.let {
                    Text("Ejercicios, series y descansos", fontWeight = FontWeight.SemiBold)
                    Text(it)
                }
            }
            if (session.completedAt != null) {
                Text("Último registro: ${session.actualDurationMinutes ?: "—"} min · RPE ${session.sessionRpe ?: "—"}",
                    color = TrainingMuted, style = MaterialTheme.typography.bodySmall)
            }
            TrainingChoiceField("¿Cómo fue la sesión?", outcome,
                listOf("" to "Selecciona una opción", "completed" to "Completada", "partial" to "Parcial", "not_completed" to "No realizada")) {
                outcome = it
                if (it == "completed") reason = "none"
            }
            if (outcome.isNotBlank() && outcome != "completed") {
                TrainingChoiceField("Motivo", reason, listOf(
                    "none" to "Selecciona un motivo", "time" to "Falta de tiempo", "fatigue" to "Fatiga o esfuerzo excesivo",
                    "equipment" to "Falta de material", "motivation" to "Falta de motivación", "illness" to "Enfermedad", "other" to "Otro"
                )) { reason = it }
            }
            TrainingChoiceField("¿Cómo te has recuperado?", recovery, listOf(
                "unknown" to "No lo sé / no lo indico", "good" to "Bien", "partial" to "Parcialmente", "insufficient" to "Insuficientemente"
            )) { recovery = it }
            TrainingChoiceField("¿Has tenido molestias nuevas o que empeoran?", pain,
                listOf("none" to "No", "new_or_worsening" to "Sí")) { pain = it }
            TrainingChoiceField("¿Has alcanzado el objetivo de repeticiones?", progression,
                listOf("unknown" to "No indicado", "true" to "Sí", "false" to "No")) { progression = it }

            if (!missed) {
                OutlinedTextField(value = duration, onValueChange = { duration = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Duración realizada (minutos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy)
                OutlinedTextField(value = rpe, onValueChange = { rpe = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Esfuerzo percibido (0–10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy)
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas opcionales") }, minLines = 2, maxLines = 4, enabled = !busy)
            val exerciseFeedbackReady = structuredPrescription == null || structuredPrescription.exercises.filter { (it.sets ?: 0) > 0 }.all { exercise ->
                val key = exercise.exerciseId ?: "order-${exercise.order ?: structuredPrescription.exercises.indexOf(exercise)}"
                val planned = exercise.sets ?: 0
                val done = completedSets[key]?.toIntOrNull()
                done != null && planned > 0 && done in 0..planned &&
                    (done == planned || skippedReasons[key] in setOf("fatigue", "time", "equipment", "motivation", "illness", "pain", "other"))
            }
            Button(onClick = {
                onRecord(session, PatientTrainingRecordRequest(
                    completedAt = if (missed) null else java.time.Instant.now().toString(),
                    actualDurationMinutes = if (missed) null else duration.toIntOrNull(),
                    sessionRpe = if (missed) null else rpe.toDoubleOrNull(),
                    notes = notes.trim().takeIf { it.isNotEmpty() },
                    outcome = outcome,
                    nonCompletionReason = if (outcome == "completed") "none" else reason,
                    recoveryRating = recovery,
                    painSignal = pain,
                    progressionTargetAchieved = progression.toBooleanStrictOrNull(),
                    exerciseFeedback = structuredPrescription?.exercises.orEmpty().filter { (it.sets ?: 0) > 0 }.mapIndexed { index, exercise ->
                        val key = exercise.exerciseId ?: "order-${exercise.order ?: index}"
                        TrainingExerciseFeedback(
                            exerciseId = exercise.exerciseId,
                            exerciseName = exercise.exerciseNameSnapshot ?: "Ejercicio ${index + 1}",
                            plannedSets = exercise.sets ?: 0,
                            completedSets = completedSets[key]?.toIntOrNull() ?: -1,
                            skippedSetReason = if ((completedSets[key]?.toIntOrNull() ?: -1) < (exercise.sets ?: 0))
                                skippedReasons[key] else null
                        )
                    }
                ))
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text(if (busy) "Guardando…" else "Guardar check-in")
            }
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
                    DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onSelect(optionValue); expanded = false })
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
