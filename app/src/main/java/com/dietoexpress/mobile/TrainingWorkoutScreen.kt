package com.dietoexpress.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dietoexpress.mobile.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val WorkoutBackground = Color(0xFF171B20)
private val WorkoutSurface = Color(0xFF242B31)
private val WorkoutLime = Color(0xFFC4F15B)
private val WorkoutMuted = Color(0xFFB2BDC4)

@Composable
internal fun TrainingWorkoutScreen(
    session: TrainingSession,
    initialDraft: TrainingWorkoutDraft?,
    busy: Boolean,
    onDraftChanged: (TrainingWorkoutDraft) -> Unit,
    onFinish: (TrainingSession, PatientTrainingRecordRequest) -> Unit,
    onBack: () -> Unit
) {
    val initial = remember(session.id) { initialDraft?.takeIf { it.sessionId == session.id } ?: newWorkoutDraft(session) }
    var draft by remember(session.id) { mutableStateOf(initial) }
    var restRemaining by remember(session.id) { mutableStateOf(0) }
    var restActive by remember(session.id) { mutableStateOf(false) }
    LaunchedEffect(restActive, restRemaining) {
        if (restActive && restRemaining > 0) { delay(1000); restRemaining -= 1 }
        else if (restRemaining == 0) restActive = false
    }
    LaunchedEffect(draft) { onDraftChanged(draft) }
    val elapsedMinutes = ((System.currentTimeMillis() - draft.startedAtEpochMs) / 60_000L).toInt().coerceAtLeast(1)
    val planned = draft.exercises.sumOf { it.plannedSets }
    val done = draft.exercises.sumOf { e -> e.sets.count { it.kind == "working" && it.completed } }

    Column(Modifier.fillMaxWidth().background(WorkoutBackground, RoundedCornerShape(26.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutLime)) { Text("← Volver") }
            Text("ENTRENAMIENTO", color = WorkoutLime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(session.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(runCatching { LocalDate.parse(session.scheduledDate.take(10)).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }.getOrDefault("Sesión programada"),
            color = WorkoutMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("$done/$planned", "series completadas", Modifier.weight(1f))
            Metric("$elapsedMinutes min", "tiempo transcurrido", Modifier.weight(1f))
        }
        draft.exercises.forEachIndexed { exerciseIndex, exercise ->
            Card(colors = CardDefaults.cardColors(containerColor = WorkoutSurface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${exerciseIndex + 1}. ${exercise.exerciseName}", color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("${exercise.plannedSets} series · ${exercise.minRepetitions ?: "—"}–${exercise.maxRepetitions ?: "—"} reps · RIR ${exercise.targetRir ?: "—"}",
                                color = WorkoutMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${exercise.sets.count { it.kind == "working" && it.completed }}/${exercise.plannedSets}", color = WorkoutLime, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SERIE", Modifier.weight(0.8f), color = WorkoutMuted)
                        Text("KG", Modifier.weight(1f), color = WorkoutMuted)
                        Text("REPS", Modifier.weight(1f), color = WorkoutMuted)
                        Text("RIR", Modifier.weight(0.8f), color = WorkoutMuted)
                        Text("OK", Modifier.weight(0.55f), color = WorkoutMuted)
                    }
                    exercise.sets.forEachIndexed { setIndex, set ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (set.kind == "warmup") "Cal." else set.setNumber.toString(),
                                Modifier.weight(0.8f), color = if (set.kind == "warmup") WorkoutMuted else WorkoutLime, fontWeight = FontWeight.Bold)
                            OutlinedTextField(set.weightKg?.let(::formatWorkoutNumber).orEmpty(), { value ->
                                updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(weightKg = value.replace(',', '.').toDoubleOrNull())) { draft = it }
                            }, Modifier.weight(1f), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                placeholder = { Text("0") })
                            OutlinedTextField(set.repetitions?.toString().orEmpty(), { value ->
                                updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(repetitions = value.toIntOrNull())) { draft = it }
                            }, Modifier.weight(1f), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder = { Text("0") })
                            OutlinedTextField(set.rir?.let(::formatWorkoutNumber).orEmpty(), { value ->
                                updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(rir = value.replace(',', '.').toDoubleOrNull())) { draft = it }
                            }, Modifier.weight(0.8f), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                placeholder = { Text("—") })
                            Checkbox(set.completed, { checked ->
                                updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(completed = checked)) { draft = it }
                            }, enabled = !busy)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            draft = draft.copy(exercises = draft.exercises.mapIndexed { i, item ->
                                if (i == exerciseIndex) item.copy(sets = listOf(TrainingSetRecord(item.sets.count { it.kind == "warmup" }, "warmup")) + item.sets) else item
                            })
                        }, enabled = !busy && exercise.sets.count { it.kind == "warmup" } < 50, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutMuted)) { Text("+ Calentamiento") }
                        TextButton(onClick = {
                            draft = draft.copy(exercises = draft.exercises.mapIndexed { i, item ->
                                if (i == exerciseIndex) item.copy(plannedSets = item.plannedSets + 1,
                                    sets = item.sets + TrainingSetRecord(item.sets.count { it.kind == "working" } + 1,
                                        repetitions = item.minRepetitions, rir = item.targetRir)) else item
                            })
                        }, enabled = !busy && exercise.plannedSets < 50, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutLime)) { Text("+ Añadir serie") }
                    }
                    TextButton(onClick = {
                        draft = draft.copy(exercises = draft.exercises.mapIndexed { i, item ->
                            if (i == exerciseIndex) {
                                val lastWorking = item.sets.indexOfLast { it.kind == "working" }
                                if (lastWorking < 0) item else item.copy(
                                    plannedSets = (item.plannedSets - 1).coerceAtLeast(1),
                                    sets = item.sets.filterIndexed { index, _ -> index != lastWorking }.let { remaining ->
                                        var workingNumber = 0
                                        var warmupNumber = 0
                                        remaining.map { set ->
                                            if (set.kind == "working") set.copy(setNumber = ++workingNumber)
                                            else set.copy(setNumber = warmupNumber++)
                                        }
                                    }
                                )
                            } else item
                        })
                    }, enabled = !busy && exercise.plannedSets > 1,
                        colors = ButtonDefaults.textButtonColors(contentColor = WorkoutMuted)) { Text("− Quitar última serie") }
                    exercise.restSeconds?.let { seconds ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Descanso recomendado: $seconds s", color = WorkoutMuted, style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { restRemaining = seconds; restActive = true }, enabled = !busy,
                                colors = ButtonDefaults.textButtonColors(contentColor = WorkoutLime)) {
                                Text(if (restActive) "Descanso: ${restRemaining}s" else "Iniciar descanso")
                            }
                            if (restActive) TextButton(onClick = { restActive = false; restRemaining = 0 }) { Text("Parar") }
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = WorkoutSurface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Cierre del entrenamiento", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Esfuerzo global (RPE 0–10)", color = WorkoutMuted)
                OutlinedTextField(draft.sessionRpe?.toString().orEmpty(), { v ->
                    if (v.isBlank()) draft = draft.copy(sessionRpe = null)
                    else v.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..10.0 }?.let { draft = draft.copy(sessionRpe = it) }
                },
                    Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (draft.exercises.any { ex -> ex.sets.count { it.kind == "working" && it.completed } < ex.plannedSets }) {
                    Text("Motivo principal de las series no completadas", color = WorkoutMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Choice("Fatiga", draft.nonCompletionReason == "fatigue") { draft = draft.copy(nonCompletionReason = "fatigue") }
                        Choice("Tiempo", draft.nonCompletionReason == "time") { draft = draft.copy(nonCompletionReason = "time") }
                        Choice("Material", draft.nonCompletionReason == "equipment") { draft = draft.copy(nonCompletionReason = "equipment") }
                    }
                }
                Text("Recuperación", color = WorkoutMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Choice("No indicar", draft.recoveryRating == "unknown") { draft = draft.copy(recoveryRating = "unknown") }
                    Choice("Buena", draft.recoveryRating == "good") { draft = draft.copy(recoveryRating = "good") }
                    Choice("Parcial", draft.recoveryRating == "partial") { draft = draft.copy(recoveryRating = "partial") }
                    Choice("Baja", draft.recoveryRating == "insufficient") { draft = draft.copy(recoveryRating = "insufficient") }
                }
                Text("¿Molestias nuevas o que empeoran?", color = WorkoutMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("No", draft.painSignal == "none") { draft = draft.copy(painSignal = "none") }
                    Choice("Sí", draft.painSignal == "new_or_worsening") { draft = draft.copy(painSignal = "new_or_worsening") }
                }
                if (draft.painSignal.isBlank() || draft.sessionRpe == null) {
                    Text("Para guardar, indica el RPE y confirma si has tenido molestias.", color = WorkoutMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(draft.notes, { draft = draft.copy(notes = it.take(4000)) }, Modifier.fillMaxWidth(),
                    label = { Text("Notas (opcional)") }, enabled = !busy, minLines = 2)
            }
        }
        Button(onClick = {
            val feedback = draft.exercises.map { exercise ->
                val completed = exercise.sets.count { it.kind == "working" && it.completed }
                TrainingExerciseFeedback(exerciseId = exercise.exerciseId, exerciseName = exercise.exerciseName,
                    plannedSets = exercise.plannedSets, completedSets = completed,
                    skippedSetReason = if (completed < exercise.plannedSets) "fatigue" else null, sets = exercise.sets)
            }
            val allComplete = feedback.all { it.completedSets == it.plannedSets }
            onFinish(session, PatientTrainingRecordRequest(
                completedAt = Instant.now().toString(), actualDurationMinutes = elapsedMinutes, sessionRpe = draft.sessionRpe,
                notes = draft.notes, outcome = if (allComplete) "completed" else "partial",
                nonCompletionReason = if (allComplete) "none" else draft.nonCompletionReason, recoveryRating = draft.recoveryRating,
                painSignal = draft.painSignal, exerciseFeedback = feedback
            ))
            onBack()
        }, Modifier.fillMaxWidth(), enabled = !busy && draft.exercises.isNotEmpty() &&
            draft.sessionRpe?.let { it in 0.0..10.0 } == true && draft.painSignal in setOf("none", "new_or_worsening") &&
            draft.exercises.all { exercise ->
            exercise.plannedSets in 1..50 && exercise.sets.count { it.kind == "warmup" } <= 50 &&
                exercise.sets.all { set ->
                    (set.weightKg == null || set.weightKg in 0.0..1000.0) &&
                    (set.repetitions == null || set.repetitions in 0..1000) &&
                    (set.rir == null || set.rir in 0.0..10.0) &&
                    (!set.completed || (set.repetitions != null && set.repetitions in 1..1000))
                }
        }, colors = ButtonDefaults.buttonColors(containerColor = WorkoutLime, contentColor = WorkoutBackground)) {
            Text(if (busy) "Guardando…" else "Finalizar y guardar entrenamiento", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = WorkoutSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = WorkoutLime, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(label, color = WorkoutMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, colors = ButtonDefaults.outlinedButtonColors(
        containerColor = if (selected) WorkoutLime.copy(alpha = 0.15f) else Color.Transparent,
        contentColor = if (selected) WorkoutLime else WorkoutMuted)) { Text(label) }
}

private fun newWorkoutDraft(session: TrainingSession): TrainingWorkoutDraft {
    val exercises = session.exercisePrescriptionData?.exercises.orEmpty().map { exercise ->
        val count = (exercise.sets ?: 0).coerceIn(1, 30)
        val sets = (1..count).map { n -> TrainingSetRecord(n, repetitions = exercise.minRepetitions, rir = exercise.targetRepetitionsInReserve) }
        TrainingExerciseDraft(exercise.exerciseId, exercise.exerciseNameSnapshot ?: "Ejercicio", count,
            exercise.minRepetitions, exercise.maxRepetitions, exercise.targetRepetitionsInReserve, exercise.restSeconds, sets)
    }
    return TrainingWorkoutDraft(session.id, System.currentTimeMillis(), exercises)
}

private fun updateWorkoutSet(draft: TrainingWorkoutDraft, exerciseIndex: Int, setIndex: Int, set: TrainingSetRecord, update: (TrainingWorkoutDraft) -> Unit) {
    update(draft.copy(exercises = draft.exercises.mapIndexed { i, exercise ->
        if (i == exerciseIndex) exercise.copy(sets = exercise.sets.mapIndexed { j, old -> if (j == setIndex) set else old }) else exercise
    }))
}

private fun formatWorkoutNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
