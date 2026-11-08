package com.dietoexpress.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dietoexpress.mobile.data.ExerciseAlternative
import com.dietoexpress.mobile.data.PortalService
import com.dietoexpress.mobile.data.SwapExerciseRequest
import java.io.IOException
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

/** Picture URL on OUR backend; the app never contacts third-party hosts for pictures. */
internal fun exerciseImageUrl(apiBaseUrl: String, exerciseId: String, slot: Int): String? {
    if (slot !in 0..3 || !Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,99}$").matches(exerciseId)) return null
    return "${apiBaseUrl.trimEnd('/')}/portal/training/exercise-images/$exerciseId/$slot"
}

/** Server error text (field "message") or a generic fallback; never crashes on odd bodies. */
internal fun swapErrorMessage(error: Throwable): String = when (error) {
    is HttpException -> runCatching {
        JSONObject(error.response()?.errorBody()?.string().orEmpty()).optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "No se pudo cambiar el ejercicio."
    is IOException -> "Sin conexión: el cambio de ejercicio necesita internet."
    else -> "No se pudo cambiar el ejercicio."
}

/**
 * Lets the patient replace one exercise of an upcoming session by an equivalent one (same movement
 * pattern, or at least the same primary muscles). The list comes from the server, which only offers
 * reviewed exercises and says explicitly when none exists. The change affects this session only.
 */
@Composable
internal fun ExerciseSwapDialog(
    sessionId: Int,
    exerciseId: String,
    exerciseName: String,
    onDismiss: () -> Unit,
    onSwapped: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var alternatives by remember { mutableStateOf<List<ExerciseAlternative>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId, exerciseId) {
        loading = true
        try {
            val response = PortalService.api.exerciseAlternatives(sessionId, exerciseId)
            alternatives = response.alternatives
            message = response.message
        } catch (error: Exception) {
            alternatives = emptyList()
            message = swapErrorMessage(error)
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Cambiar «$exerciseName»") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    loading -> CircularProgressIndicator()
                    alternatives.isEmpty() -> Text(message ?: "No hay alternativas disponibles.")
                    else -> {
                        Text(
                            "Mismas series, repeticiones y descanso. El cambio solo afecta a esta sesión.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        alternatives.forEach { option ->
                            OutlinedButton(
                                onClick = { selected = option.exerciseId },
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected == option.exerciseId)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                                )
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(option.name, fontWeight = FontWeight.SemiBold)
                                    val detail = buildList {
                                        if (option.inRotationPool) add("Recomendado")
                                        if (option.matchLevel == "muscles") add("Mismos músculos, otro movimiento")
                                        if (option.requiredEquipment.isNotEmpty()) add(option.requiredEquipment.joinToString(", "))
                                    }.joinToString(" · ")
                                    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        message?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && !saving,
                onClick = {
                    val replacement = selected ?: return@TextButton
                    saving = true
                    scope.launch {
                        try {
                            PortalService.api.swapExercise(sessionId, exerciseId, SwapExerciseRequest(replacement))
                            onSwapped()
                        } catch (error: Exception) {
                            message = swapErrorMessage(error)
                            saving = false
                        }
                    }
                }
            ) { Text(if (saving) "Cambiando…" else "Cambiar ejercicio") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } }
    )
}
