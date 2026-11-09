package com.dietoexpress.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dietoexpress.mobile.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val WorkoutSurface = TrainingPalette.Surface
private val WorkoutInput = TrainingPalette.Input
private val WorkoutAccent = TrainingPalette.Accent
private val WorkoutText = TrainingPalette.Text
private val WorkoutMuted = TrainingPalette.Muted
private val WorkoutBorder = TrainingPalette.Border

@Composable
internal fun TrainingWorkoutScreen(
    session: TrainingSession,
    initialDraft: TrainingWorkoutDraft?,
    busy: Boolean,
    onDraftChanged: (TrainingWorkoutDraft) -> Unit,
    onFinish: (TrainingSession, PatientTrainingRecordRequest) -> Unit,
    onBack: () -> Unit,
    history: List<TrainingSession> = emptyList()
) {
    val initial = remember(session.id) { initialDraft?.takeIf { it.sessionId == session.id } ?: newWorkoutDraft(session, history) }
    var draft by remember(session.id) { mutableStateOf(initial) }
    // Save in the input event itself; never wait for a later Compose effect.
    fun updateDraft(next: TrainingWorkoutDraft) {
        draft = next
        onDraftChanged(next)
    }
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
    val elapsedMinutes = ((System.currentTimeMillis() - draft.startedAtEpochMs) / 60_000L).toInt().coerceAtLeast(1)
    val planned = draft.exercises.sumOf { it.plannedSets }
    val done = draft.exercises.sumOf { e -> e.sets.count { it.kind == "working" && it.completed } }
    val validationMessages = workoutValidationMessages(draft)
    val liveContext = androidx.compose.ui.platform.LocalContext.current
    // Pictures come from OUR backend with the patient's session cookie.
    val imageLoader = remember(liveContext) {
        coil.ImageLoader.Builder(liveContext).okHttpClient(PortalService.authenticatedClient()).build()
    }
    val imageSlots by produceState<Map<String, List<Int>>>(emptyMap(), session.id) {
        value = runCatching { PortalService.api.sessionExerciseImages(session.id) }
            .getOrNull()?.exercises?.associate { it.exerciseId to it.slots }.orEmpty()
    }
    var answersTarget by remember(session.id) { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onDraftChanged(draft); onBack() }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutAccent)) { Text("← Volver") }
            Text("ENTRENAMIENTO", color = WorkoutAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(session.title, color = WorkoutText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(runCatching { LocalDate.parse(session.scheduledDate.take(10)).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }.getOrDefault("Sesión programada"),
            color = WorkoutMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("$done/$planned", "series completadas", Modifier.weight(1f))
            Metric("$elapsedMinutes min", "tiempo transcurrido", Modifier.weight(1f))
        }
        val runningRest = activeRestExerciseIds.firstOrNull()?.let { restRemainingByExercise[it] }?.takeIf { it > 0 }
        if (runningRest != null) {
            Card(colors = CardDefaults.cardColors(containerColor = WorkoutAccent), shape = RoundedCornerShape(16.dp)) {
                Text("Descanso · ${runningRest / 60}:${(runningRest % 60).toString().padStart(2, '0')}", Modifier.fillMaxWidth().padding(14.dp),
                    color = TrainingPalette.OnAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
        draft.exercises.forEachIndexed { exerciseIndex, exercise ->
            key(exercise.exerciseId ?: "exercise-$exerciseIndex") {
            val timerKey = exercise.exerciseId ?: "exercise-$exerciseIndex"
            val restRemaining = restRemainingByExercise[timerKey] ?: 0
            val restActive = timerKey in activeRestExerciseIds
            Card(colors = CardDefaults.cardColors(containerColor = WorkoutSurface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${exerciseIndex + 1}. ${exercise.exerciseName}", color = WorkoutText, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            lastWorkingSet(history, session.id, exercise.exerciseId)?.let { last ->
                                Text("Última vez: ${formatWorkoutNumber(last.weightKg ?: 0.0)} kg × ${last.repetitions}" + (last.rir?.let { " · RIR ${formatWorkoutNumber(it)}" } ?: ""),
                                    color = WorkoutAccent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            Text("${exercise.plannedSets} series · ${exercise.minRepetitions ?: "—"}–${exercise.maxRepetitions ?: "—"} reps · RIR ${exercise.targetRir ?: "—"}",
                                color = WorkoutMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${exercise.sets.count { it.kind == "working" && it.completed }}/${exercise.plannedSets}", color = WorkoutAccent, fontWeight = FontWeight.Bold)
                    }
                    val liveSlots = exercise.exerciseId?.let { imageSlots[it] }.orEmpty()
                    if (liveSlots.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            liveSlots.forEachIndexed { imageIndex, slot ->
                                coil.compose.AsyncImage(
                                    model = exerciseImageUrl(BuildConfig.API_BASE_URL, exercise.exerciseId.orEmpty(), slot),
                                    imageLoader = imageLoader,
                                    contentDescription = if (liveSlots.size == 1) "Técnica de ${exercise.exerciseName}"
                                        else if (imageIndex == 0) "Posición inicial de ${exercise.exerciseName}" else "Posición final de ${exercise.exerciseName}",
                                    modifier = Modifier.weight(1f).height(96.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
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
                            Text(if (set.kind == "warmup") "Cal ${set.setNumber + 1}" else set.setNumber.toString(),
                                Modifier.weight(0.8f), color = if (set.kind == "warmup") WorkoutMuted else WorkoutAccent, fontWeight = FontWeight.Bold)
                            WorkoutSetField(
                                value = set.weightKg?.let(::formatWorkoutNumber).orEmpty(),
                                onValueChange = { value -> updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(weightKg = value.replace(',', '.').toDoubleOrNull()), ::updateDraft) },
                                modifier = Modifier.weight(1.05f), placeholder = "0", keyboardType = KeyboardType.Decimal, enabled = !busy
                            )
                            WorkoutSetField(
                                value = set.repetitions?.toString().orEmpty(),
                                onValueChange = { value -> updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(repetitions = value.toIntOrNull()), ::updateDraft) },
                                modifier = Modifier.weight(0.95f), placeholder = "0", keyboardType = KeyboardType.Number, enabled = !busy
                            )
                            WorkoutSetField(
                                value = set.rir?.let(::formatWorkoutNumber).orEmpty(),
                                onValueChange = { value -> updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(rir = value.replace(',', '.').toDoubleOrNull()), ::updateDraft) },
                                modifier = Modifier.weight(0.72f), placeholder = "—", keyboardType = KeyboardType.Decimal, enabled = !busy
                            )
                            Checkbox(set.completed, colors = CheckboxDefaults.colors(checkedColor = TrainingPalette.Accent, uncheckedColor = WorkoutMuted, checkmarkColor = TrainingPalette.OnAccent), onCheckedChange = { checked ->
                                updateWorkoutSet(draft, exerciseIndex, setIndex, set.copy(completed = checked), ::updateDraft)
                                // Completing a working set starts that exercise's rest timer automatically.
                                val restSeconds = exercise.restSeconds
                                if (checked && set.kind == "working" && restSeconds != null) {
                                    restRemainingByExercise = restRemainingByExercise + (timerKey to restSeconds)
                                    activeRestExerciseIds = activeRestExerciseIds + timerKey
                                }
                            }, enabled = !busy)
                        }
                    }
                    val workingDone = exercise.sets.count { it.kind == "working" && it.completed }
                    var menuOpen by remember(timerKey) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            updateDraft(draft.copy(exercises = draft.exercises.mapIndexed { i, item ->
                                if (i == exerciseIndex) item.copy(plannedSets = item.plannedSets + 1,
                                    sets = item.sets + TrainingSetRecord(item.sets.count { it.kind == "working" } + 1,
                                        weightKg = item.sets.lastOrNull { it.kind == "working" }?.weightKg,
                                        repetitions = item.minRepetitions, rir = item.targetRir)) else item
                            }))
                        }, enabled = !busy && exercise.plannedSets < 50, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutAccent)) { Text("+ Serie") }
                        Box {
                            TextButton(onClick = { menuOpen = true }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = WorkoutMuted)) { Text("Más ⋯") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("+ Calentamiento") },
                                    enabled = exercise.sets.count { it.kind == "warmup" } < 50,
                                    onClick = {
                                        menuOpen = false
                                        updateDraft(draft.copy(exercises = draft.exercises.mapIndexed { i, item ->
                                            if (i == exerciseIndex) item.copy(sets = listOf(TrainingSetRecord(item.sets.count { it.kind == "warmup" }, "warmup")) + item.sets) else item
                                        }))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("− Quitar última serie") },
                                    enabled = exercise.plannedSets > 1,
                                    onClick = {
                                        menuOpen = false
                                        updateDraft(draft.copy(exercises = draft.exercises.mapIndexed { i, item ->
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
                                        }))
                                    }
                                )
                            }
                        }
                    }
                    // One compact footer: rate the exercise (after at least one set) and, if sets were skipped, why.
                    if (workingDone > 0 || workingDone < exercise.plannedSets) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (workingDone > 0) {
                                OutlinedButton(onClick = { answersTarget = exerciseIndex }, enabled = !busy, modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WorkoutAccent)) {
                                    Text(if (exercise.stimulus != null || exercise.discomfort != null || exercise.preFatigue != null) "Valoración ✓" else "Valorar ejercicio")
                                }
                            }
                            if (workingDone < exercise.plannedSets) {
                                var reasonMenu by remember(timerKey) { mutableStateOf(false) }
                                Box(Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { reasonMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WorkoutText)) {
                                        Text(workoutSkipReasons.toMap()[draft.exerciseSkipReasons[timerKey]] ?: "¿Por qué no lo completaste? ▾", maxLines = 1)
                                    }
                                    DropdownMenu(expanded = reasonMenu, onDismissRequest = { reasonMenu = false }) {
                                        workoutSkipReasons.forEach { (value, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                                reasonMenu = false
                                                updateDraft(draft.copy(exerciseSkipReasons = draft.exerciseSkipReasons + (timerKey to value)))
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    exercise.targetLoadKg?.let { load ->
                        Text("Carga prescrita: ${formatWorkoutNumber(load)} kg", color = WorkoutMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    exercise.warmupNote?.let { note ->
                        Text(note, color = WorkoutMuted, style = MaterialTheme.typography.labelSmall)
                    }
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
        answersTarget?.let { index ->
            val target = draft.exercises.getOrNull(index)
            if (target != null) {
                AlertDialog(
                    onDismissRequest = { answersTarget = null },
                    containerColor = TrainingPalette.Surface,
                    titleContentColor = WorkoutText,
                    textContentColor = WorkoutText,
                    title = { Text("¿Cómo fue ${target.exerciseName}?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnswerRow("Estímulo en el músculo", listOf("low" to "Poco", "adequate" to "Adecuado", "high" to "Mucho"), target.stimulus) { value ->
                                updateDraft(draft.copy(exercises = draft.exercises.mapIndexed { i, item -> if (i == index) item.copy(stimulus = value) else item }))
                            }
                            AnswerRow("Molestias en articulaciones o tendones", listOf("none" to "Ninguna", "mild" to "Leves", "strong" to "Fuertes"), target.discomfort) { value ->
                                updateDraft(draft.copy(exercises = draft.exercises.mapIndexed { i, item -> if (i == index) item.copy(discomfort = value) else item }))
                            }
                            AnswerRow("Al empezar, el músculo estaba", listOf("recovered" to "Recuperado", "loaded" to "Aún cargado"), target.preFatigue) { value ->
                                updateDraft(draft.copy(exercises = draft.exercises.mapIndexed { i, item -> if (i == index) item.copy(preFatigue = value) else item }))
                            }
                            Text("Opcional. Con estas respuestas la app ajusta tus próximas sesiones.", color = WorkoutMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = { answersTarget = null }) { Text("Listo", color = WorkoutAccent) } }
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = WorkoutSurface), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cierre del entrenamiento", color = WorkoutText, fontWeight = FontWeight.Bold)
                Text("Esfuerzo global (RPE 0–10)", color = WorkoutMuted)
                OutlinedTextField(draft.sessionRpe?.toString().orEmpty(), { v ->
                    if (v.isBlank()) updateDraft(draft.copy(sessionRpe = null))
                    else v.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..10.0 }?.let { updateDraft(draft.copy(sessionRpe = it)) }
                },
                    Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = workoutFieldColors())
                if (draft.exercises.any { ex -> ex.sets.count { it.kind == "working" && it.completed } < ex.plannedSets }) {
                    Text("El motivo de las series no completadas se indica en cada ejercicio.", color = WorkoutMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text("Recuperación", color = WorkoutMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Choice("No indicar", draft.recoveryRating == "unknown") { updateDraft(draft.copy(recoveryRating = "unknown")) }
                    Choice("Buena", draft.recoveryRating == "good") { updateDraft(draft.copy(recoveryRating = "good")) }
                    Choice("Parcial", draft.recoveryRating == "partial") { updateDraft(draft.copy(recoveryRating = "partial")) }
                    Choice("Baja", draft.recoveryRating == "insufficient") { updateDraft(draft.copy(recoveryRating = "insufficient")) }
                }
                Text("¿Molestias nuevas o que empeoran?", color = WorkoutMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("No", draft.painSignal == "none") { updateDraft(draft.copy(painSignal = "none")) }
                    Choice("Sí", draft.painSignal == "new_or_worsening") { updateDraft(draft.copy(painSignal = "new_or_worsening")) }
                }
                if (draft.painSignal.isBlank() || draft.sessionRpe == null) {
                    Text("Para guardar, indica el RPE y confirma si has tenido molestias.", color = WorkoutMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (draft.painSignal == "new_or_worsening") {
                    OutlinedTextField(draft.painDetails, { updateDraft(draft.copy(painDetails = it.take(1000))) },
                        Modifier.fillMaxWidth(), label = { Text("Describe las molestias (opcional)") },
                        enabled = !busy, minLines = 2, colors = workoutFieldColors())
                }
                OutlinedTextField(draft.notes, { updateDraft(draft.copy(notes = it.take(4000))) }, Modifier.fillMaxWidth(),
                    label = { Text("Notas generales (opcional)") }, enabled = !busy, minLines = 2, colors = workoutFieldColors())
            }
        }
        Button(onClick = {
            val feedback = draft.exercises.mapIndexed { exerciseIndex, exercise ->
                val completed = exercise.sets.count { it.kind == "working" && it.completed }
                TrainingExerciseFeedback(exerciseId = exercise.exerciseId, exerciseName = exercise.exerciseName,
                    plannedSets = exercise.plannedSets, completedSets = completed,
                    skippedSetReason = if (completed < exercise.plannedSets) draft.exerciseSkipReasons[exercise.exerciseId ?: "exercise-$exerciseIndex"] ?: draft.nonCompletionReason else null, sets = exercise.sets,
                    stimulus = exercise.stimulus, discomfort = exercise.discomfort, preFatigue = exercise.preFatigue)
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
                // The session-level column does not know "load_too_heavy": it is sent as "other" while each exercise keeps the precise reason.
                nonCompletionReason = if (allComplete) "none" else sessionReasonFrom(feedback.mapNotNull { it.skippedSetReason }, "other"), recoveryRating = draft.recoveryRating,
                painSignal = draft.painSignal, exerciseFeedback = feedback
            ))
            onBack()
        }, Modifier.fillMaxWidth(), enabled = !busy && validationMessages.isEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = WorkoutAccent, contentColor = TrainingPalette.OnAccent)) {
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
private fun WorkoutSetField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    keyboardType: KeyboardType,
    enabled: Boolean
) {
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(WorkoutInput, shape)
            .border(1.dp, WorkoutBorder, shape)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = if (enabled) WorkoutText else WorkoutMuted,
            fontWeight = FontWeight.SemiBold
        ),
        cursorBrush = SolidColor(WorkoutAccent),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = WorkoutMuted, style = MaterialTheme.typography.bodyMedium)
                innerTextField()
            }
        }
    )
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
        if (exercise.sets.count { it.kind == "working" && it.completed } < exercise.plannedSets &&
            draft.exerciseSkipReasons[exercise.exerciseId ?: "exercise-$index"] == null)
            add("$name: indica por qué no completaste todas las series.")
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

private fun newWorkoutDraft(session: TrainingSession, history: List<TrainingSession>): TrainingWorkoutDraft {
    val prescribed = session.exercisePrescriptionData?.exercises.orEmpty()
    // Plans generated before warm-up guidance existed carry none: derive it with the same rule.
    val derivedWarmups = if (prescribed.none { it.warmupSets != null }) deriveWarmupSets(prescribed) else emptyMap()
    val exercises = prescribed.mapIndexed { exerciseIndex, exercise ->
        val warmupCount = exercise.warmupSets ?: derivedWarmups[exerciseIndex]
        val warmupNote = exercise.warmupNote ?: warmupCount?.let { "Haz $it serie(s) de aproximación muy fáciles y lejos del fallo antes de las series de trabajo. No cuentan como series de trabajo." }
        val count = (exercise.sets ?: 0).coerceIn(0, 50)
        // Warm-up rows are created from the prescription (very easy approach sets, not counted as working sets).
        val warmups = (0 until (warmupCount ?: 0).coerceIn(0, 3)).map { TrainingSetRecord(it, "warmup", repetitions = exercise.maxRepetitions) }
        val sets = warmups + (1..count).map { n -> TrainingSetRecord(n, weightKg = exercise.targetLoadKg ?: lastWorkingSet(history, session.id, exercise.exerciseId)?.weightKg, repetitions = exercise.minRepetitions, rir = exercise.targetRepetitionsInReserve) }
        TrainingExerciseDraft(exercise.exerciseId, exercise.exerciseNameSnapshot ?: "Ejercicio", count,
            exercise.minRepetitions, exercise.maxRepetitions, exercise.targetRepetitionsInReserve, exercise.restSeconds, sets,
            targetLoadKg = exercise.targetLoadKg, warmupSets = warmupCount, warmupNote = warmupNote, loadBasis = exercise.loadBasis)
    }
    return TrainingWorkoutDraft(session.id, System.currentTimeMillis(), exercises)
}

private fun updateWorkoutSet(draft: TrainingWorkoutDraft, exerciseIndex: Int, setIndex: Int, set: TrainingSetRecord, update: (TrainingWorkoutDraft) -> Unit) {
    update(draft.copy(exercises = draft.exercises.mapIndexed { i, exercise ->
        if (i == exerciseIndex) exercise.copy(sets = exercise.sets.mapIndexed { j, old -> if (j == setIndex) set else old }) else exercise
    }))
}

private fun formatWorkoutNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/**
 * Heaviest completed working set of the most recent earlier session that recorded this exercise.
 * Used to show "last time" and to pre-fill the weight when the prescription carries no load.
 */
internal fun lastWorkingSet(history: List<TrainingSession>, currentSessionId: Int, exerciseId: String?): TrainingSetRecord? {
    if (exerciseId == null) return null
    return history.asSequence()
        .filter { it.id != currentSessionId && it.completedAt != null }
        .sortedByDescending { it.scheduledDate }
        .mapNotNull { session ->
            session.exerciseFeedback.firstOrNull { it.exerciseId == exerciseId }?.sets.orEmpty()
                .filter { it.kind == "working" && it.completed && (it.weightKg ?: 0.0) > 0.0 && (it.repetitions ?: 0) > 0 }
                .maxByOrNull { it.weightKg ?: 0.0 }
        }
        .firstOrNull()
}

/** Session-level reason (the server only accepts a fixed set) derived from the per-exercise reasons. */
internal fun sessionReasonFrom(reasons: Collection<String>, fallback: String): String {
    val mapped = reasons.map { if (it == "load_too_heavy" || it == "pain") "other" else it }
        .filter { it in setOf("time", "fatigue", "equipment", "motivation", "illness", "other") }
    return mapped.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: fallback
}

private val workoutSkipReasons = listOf(
    "load_too_heavy" to "Peso demasiado alto", "fatigue" to "Fatiga", "time" to "Falta de tiempo",
    "equipment" to "Falta de material", "pain" to "Dolor"
)

@Composable
private fun AnswerRow(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = WorkoutMuted, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            options.forEach { (value, text) -> Choice(text, selected == value) { onSelect(value) } }
        }
    }
}

/**
 * Warm-up rule used when the prescription does not carry warm-up sets (plans generated before they
 * existed): an exercise gets one or two very easy approach sets (two for the session's first) only if
 * one of its primary muscles was not yet a primary muscle of an earlier exercise of the session.
 * Core-only exercises are skipped. Same rule as the server's generator.
 */
internal fun deriveWarmupSets(exercises: List<StructuredTrainingExercise>): Map<Int, Int> {
    val warmed = mutableSetOf<String>()
    var first = true
    val result = mutableMapOf<Int, Int>()
    exercises.forEachIndexed { index, exercise ->
        val coreOnly = exercise.primaryMuscleGroups.all { it == "Abdominals" || it == "Obliques" }
        val cold = exercise.primaryMuscleGroups.filter { it !in warmed }
        warmed += exercise.primaryMuscleGroups
        if (!coreOnly && cold.isNotEmpty()) {
            result[index] = if (first) 2 else 1
            first = false
        }
    }
    return result
}
