package com.dietoexpress.mobile

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

private val WorkoutSurface = Color(0xFFFFFFFF)
private val WorkoutInput = Color(0xFFF1F5F6)
private val WorkoutAccent = Color(0xFF176B67)
private val WorkoutText = Color(0xFF18282D)
private val WorkoutMuted = Color(0xFF52636B)
private val WorkoutBorder = Color(0xFFB9C9C8)

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
    var restRemainingByExercise by remember(session.id) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var activeRestExerciseIds by remember(session.id) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(activeRestExerciseIds) {
        while (activeRestExerciseIds.isNotEmpty()) {
            delay(1000)
            val active = activeRestExerciseIds
            restRemainingByExercise = restRemainingByExercise.mapValues { (id, seconds) ->
                if (id in active) (seconds - 1).coerceAtLeast(0) else seconds
            }
            activeRestExerciseIds = active.filterTo(mutableSetOf()) { (restRemainingByExercise[it] ?: 0) > 0 }
        }
    }
    val lastPersistedDraft = remember(session.id) { mutableStateOf<TrainingWorkoutDraft?>(null) }
    SideEffect {
        if (lastPersistedDraft.value != draft) {
            onDraftChanged(draft)
            lastPersistedDraft.value = draft
        }
    }
    val elapsedMinutes = ((System.currentTimeMillis() - draft.startedAtEpochMs) / 60_000L).toInt().coerceAtLeast(1)
    val planned = draft.exercises.sumOf { it.plannedSets }
    val done = draft.exercises.sumOf { e -> e.sets.count { it.kind == "working" && it.completed } }
    val validationMessages = workoutValidationMessages(draft)

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutAccent)) { Text("← Volver") }
            Text("ENTRENAMIENTO", color = WorkoutAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(session.title, color = WorkoutText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(runCatching { LocalDate.parse(session.scheduledDate.take(10)).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }.getOrDefault("Sesión programada"),
            color = WorkoutMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("$done/$planned", "series completadas", Modifier.weight(1f))
            Metric("$elapsedMinutes min", "tiempo transcurrido", Modifier.weight(1f))
        }
        draft.exercises.forEachIndexed { exerciseIndex, exercise ->
            key(exercise.exerciseId ?: "exercise-$exerciseIndex") {
            val timerKey = exercise.exerciseId ?: "exercise-$exerciseIndex"
            val restRemaining = restRemainingByExercise[timerKey] ?: 0
            val restActive = timerKey in activeRestExerciseIds
            Card(colors = CardDefaults.cardColors(containerColor = WorkoutSurface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${exerciseIndex + 1}. ${exercise.exerciseName}", color = WorkoutText, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("${exercise.plannedSets} series · ${exercise.minRepetitions ?: "—"}–${exercise.maxRepetitions ?: "—"} reps · RIR ${exercise.targetRir ?: "—"}",
                                color = WorkoutMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${exercise.sets.count { it.kind == "working" && it.completed }}/${exercise.plannedSets}", color = WorkoutAccent, fontWeight = FontWeight.Bold)
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
                                Modifier.weight(0.8f), color = if (set.kind == "warmup") WorkoutMuted else WorkoutAccent, fontWeight = FontWeight.Bold)
                            OutlinedTextField(set.weightKg?.let(::formatWorkoutNumber).orEmpty(), { value ->
                                updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(weightKg = value.replace(',', '.').toDoubleOrNull())) { draft = it }
                            }, Modifier.weight(1f), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = workoutFieldColors(), placeholder = { Text("0", color = WorkoutMuted) })
                            OutlinedTextField(set.repetitions?.toString().orEmpty(), { value ->
                                updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(repetitions = value.toIntOrNull())) { draft = it }
                            }, Modifier.weight(1f), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = workoutFieldColors(), placeholder = { Text("0", color = WorkoutMuted) })
                            OutlinedTextField(set.rir?.let(::formatWorkoutNumber).orEmpty(), { value ->
                                updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(rir = value.replace(',', '.').toDoubleOrNull())) { draft = it }
                            }, Modifier.weight(0.8f), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = workoutFieldColors(), placeholder = { Text("—", color = WorkoutMuted) })
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
                        }, enabled = !busy && exercise.plannedSets < 50, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutAccent)) { Text("+ Añadir serie") }
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
                            TextButton(onClick = {
                                if (restActive) activeRestExerciseIds = activeRestExerciseIds - timerKey
                                else {
                                    if (restRemaining == 0) restRemainingByExercise = restRemainingByExercise + (timerKey to seconds)
                                    activeRestExerciseIds = activeRestExerciseIds + timerKey
                                }
                            }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutAccent)) {
                                Text(when {
                                    restActive -> "Pausar · ${restRemaining}s"
                                    restRemaining > 0 -> "Reanudar · ${restRemaining}s"
                                    else -> "Iniciar · ${seconds}s"
                                })
                            }
                            if (restRemaining > 0) TextButton(onClick = {
                                activeRestExerciseIds = activeRestExerciseIds - timerKey
                                restRemainingByExercise = restRemainingByExercise + (timerKey to 0)
                            }, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutMuted)) { Text("Detener") }
                        }
                    }
                }
            }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = WorkoutSurface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Cierre del entrenamiento", color = WorkoutText, fontWeight = FontWeight.Bold)
                Text("Esfuerzo global (RPE 0–10)", color = WorkoutMuted)
                OutlinedTextField(draft.sessionRpe?.toString().orEmpty(), { v ->
                    if (v.isBlank()) draft = draft.copy(sessionRpe = null)
                    else v.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..10.0 }?.let { draft = draft.copy(sessionRpe = it) }
                },
                    Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = workoutFieldColors())
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
                if (draft.painSignal == "new_or_worsening") {
                    OutlinedTextField(draft.painDetails, { draft = draft.copy(painDetails = it.take(1000)) },
                        Modifier.fillMaxWidth(), label = { Text("Describe las molestias (opcional)") },
                        enabled = !busy, minLines = 2, colors = workoutFieldColors())
                }
                OutlinedTextField(draft.notes, { draft = draft.copy(notes = it.take(4000)) }, Modifier.fillMaxWidth(),
                    label = { Text("Notas generales (opcional)") }, enabled = !busy, minLines = 2, colors = workoutFieldColors())
            }
        }
        Button(onClick = {
            val feedback = draft.exercises.map { exercise ->
                val completed = exercise.sets.count { it.kind == "working" && it.completed }
                TrainingExerciseFeedback(exerciseId = exercise.exerciseId, exerciseName = exercise.exerciseName,
                    plannedSets = exercise.plannedSets, completedSets = completed,
                    skippedSetReason = if (completed < exercise.plannedSets) draft.nonCompletionReason else null, sets = exercise.sets)
            }
            val allComplete = feedback.all { it.completedSets == it.plannedSets }
            onFinish(session, PatientTrainingRecordRequest(
                completedAt = Instant.now().toString(), actualDurationMinutes = elapsedMinutes, sessionRpe = draft.sessionRpe,
                notes = buildList {
                    draft.notes.trim().takeIf { it.isNotEmpty() }?.let(::add)
                    if (draft.painSignal == "new_or_worsening") {
                        draft.painDetails.trim().takeIf { it.isNotEmpty() }?.let { add("Molestias: $it") }
                    }
                }.joinToString("\n\n").take(4000).takeIf { it.isNotBlank() },
                outcome = if (allComplete) "completed" else "partial",
                nonCompletionReason = if (allComplete) "none" else draft.nonCompletionReason, recoveryRating = draft.recoveryRating,
                painSignal = draft.painSignal, exerciseFeedback = feedback
            ))
            onBack()
        }, Modifier.fillMaxWidth(), enabled = !busy && validationMessages.isEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = WorkoutAccent, contentColor = Color.White)) {
            Text(if (busy) "Guardando…" else "Finalizar y guardar entrenamiento", fontWeight = FontWeight.Bold)
        }
        if (!busy && validationMessages.isNotEmpty()) {
            Text("Para finalizar, revisa:", color = Color(0xFF9B3B32), fontWeight = FontWeight.SemiBold)
            validationMessages.forEach { message ->
                Text("• $message", color = Color(0xFF9B3B32), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun workoutFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WorkoutText,
    unfocusedTextColor = WorkoutText,
    disabledTextColor = WorkoutMuted,
    focusedContainerColor = WorkoutInput,
    unfocusedContainerColor = WorkoutInput,
    disabledContainerColor = WorkoutInput,
    cursorColor = WorkoutAccent,
    focusedBorderColor = WorkoutAccent,
    unfocusedBorderColor = WorkoutBorder,
    focusedLabelColor = WorkoutAccent,
    unfocusedLabelColor = WorkoutMuted,
    focusedPlaceholderColor = WorkoutMuted,
    unfocusedPlaceholderColor = WorkoutMuted
)

private fun workoutValidationMessages(draft: TrainingWorkoutDraft): List<String> = buildList {
    if (draft.exercises.isEmpty()) add("La sesión no contiene ejercicios.")
    if (draft.sessionRpe == null || draft.sessionRpe !in 0.0..10.0) add("Indica el esfuerzo global (RPE de 0 a 10).")
    if (draft.painSignal !in setOf("none", "new_or_worsening")) add("Indica si has tenido molestias: Sí o No.")
    draft.exercises.forEachIndexed { index, exercise ->
        val name = exercise.exerciseName.ifBlank { "Ejercicio ${index + 1}" }
        if (exercise.plannedSets !in 1..50) add("$name: el número de series debe estar entre 1 y 50.")
        if (exercise.sets.size > 100) add("$name: no puede superar las 100 series registradas.")
        if (exercise.sets.count { it.kind == "warmup" } > 50) add("$name: no puede superar las 50 series de calentamiento.")
        exercise.sets.forEachIndexed { setIndex, set ->
            val label = if (set.kind == "warmup") "calentamiento ${setIndex + 1}" else "serie ${set.setNumber}"
            if (set.weightKg != null && set.weightKg !in 0.0..1000.0) add("$name, $label: revisa el peso (0–1000 kg).")
            if (set.repetitions != null && set.repetitions !in 0..1000) add("$name, $label: revisa las repeticiones.")
            if (set.rir != null && set.rir !in 0.0..10.0) add("$name, $label: el RIR debe estar entre 0 y 10.")
            if (set.completed && (set.repetitions == null || set.repetitions !in 1..1000)) {
                add("$name, $label: introduce al menos 1 repetición o desmarca la serie.")
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = WorkoutSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = WorkoutAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(label, color = WorkoutMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, colors = ButtonDefaults.outlinedButtonColors(
        containerColor = if (selected) WorkoutAccent.copy(alpha = 0.15f) else Color.Transparent,
        contentColor = if (selected) WorkoutAccent else WorkoutMuted)) { Text(label) }
}

private fun newWorkoutDraft(session: TrainingSession): TrainingWorkoutDraft {
    val exercises = session.exercisePrescriptionData?.exercises.orEmpty().map { exercise ->
        val count = (exercise.sets ?: 0).coerceIn(0, 50)
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
