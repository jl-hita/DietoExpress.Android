package com.dietoexpress.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dietoexpress.mobile.data.PortalService
import com.dietoexpress.mobile.data.TrainingReviewPlan
import com.dietoexpress.mobile.data.TrainingReviewRequestBody
import java.io.IOException
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

internal const val TRAINING_REVIEW_NOTE_MAX = 1000

/** Estado visible de la última solicitud de una rutina (null si nunca se ha solicitado). */
internal fun trainingReviewStatusLabel(status: String?): String? = when (status) {
    "pending" -> "Solicitud enviada · pendiente de revisión"
    "attended" -> "Revisión atendida por tu entrenador"
    "rejected" -> "Tu entrenador ha respondido sin cambios"
    else -> null
}

/** El botón solo se ofrece si no hay ya una solicitud pendiente para ese plan. */
internal fun canRequestTrainingReview(plan: TrainingReviewPlan): Boolean = plan.reviewRequest?.status != "pending"

internal fun trainingReviewErrorMessage(error: Throwable): String = when (error) {
    is HttpException -> runCatching {
        JSONObject(error.response()?.errorBody()?.string().orEmpty()).optString("message")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "No se pudo enviar la solicitud. Inténtalo de nuevo en unos minutos."
    is IOException -> "Sin conexión: la solicitud de revisión necesita internet."
    else -> "No se pudo enviar la solicitud. Inténtalo de nuevo en unos minutos."
}

/**
 * Permite pedir a tu entrenador una revisión manual de la rutina (original o revisión posterior).
 * La solicitud no cambia el plan por sí sola; el servidor evita duplicados pendientes por rutina.
 * Es una escritura online: no se encola sin conexión.
 */
@Composable
internal fun TrainingReviewRequestSection() {
    var plans by remember { mutableStateOf<List<TrainingReviewPlan>>(emptyList()) }
    var openPlanId by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        plans = try {
            PortalService.api.trainingReviewRequests().plans
        } catch (_: Exception) {
            plans // sin conexión o sin acceso: se conserva lo último visto y no se bloquea la pantalla
        }
    }

    plans.forEach { plan ->
        val statusLabel = trainingReviewStatusLabel(plan.reviewRequest?.status)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (plan.version > 1) "Rutina · revisión ${plan.version}" else "Rutina",
                    style = MaterialTheme.typography.labelMedium, color = TrainingPalette.Muted
                )
                Text(plan.name, fontWeight = FontWeight.SemiBold)
                if (statusLabel != null) {
                    Text(
                        statusLabel, color = TrainingPalette.Muted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                plan.reviewRequest?.resolutionNotes?.takeIf { it.isNotBlank() }?.let {
                    Text("Nota de tu entrenador: $it", style = MaterialTheme.typography.bodySmall, color = TrainingPalette.Muted)
                }
                if (openPlanId == plan.planId) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= TRAINING_REVIEW_NOTE_MAX) note = it },
                        label = { Text("¿Qué te gustaría que revisara? (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = !sending
                    )
                    Text(
                        "Tu entrenador recibirá un aviso. Tu rutina no cambia hasta que la revise y las sesiones ya realizadas no se modifican.",
                        style = MaterialTheme.typography.bodySmall, color = TrainingPalette.Muted
                    )
                    error?.let {
                        Text(
                            it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                        )
                    }
                    Button(
                        enabled = !sending,
                        onClick = {
                            sending = true
                            error = null
                            scope.launch {
                                try {
                                    val result = PortalService.api.requestTrainingReview(
                                        plan.planId, TrainingReviewRequestBody(note.trim().ifBlank { null })
                                    )
                                    confirmation = result["message"] as? String ?: "Solicitud enviada a tu entrenador."
                                    openPlanId = null
                                    note = ""
                                    reloadKey++
                                } catch (e: Exception) {
                                    error = trainingReviewErrorMessage(e)
                                    if (e is HttpException && e.code() == 409) {
                                        openPlanId = null
                                        reloadKey++
                                    }
                                }
                                sending = false
                            }
                        }
                    ) { Text(if (sending) "Enviando…" else "Enviar solicitud") }
                    TextButton(onClick = { openPlanId = null; note = ""; error = null }, enabled = !sending) { Text("Cancelar") }
                } else if (canRequestTrainingReview(plan)) {
                    OutlinedButton(
                        onClick = { openPlanId = plan.planId; note = ""; error = null; confirmation = null },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "Solicitar revisión manual de ${plan.name} a mi entrenador"
                        }
                    ) { Text("Solicitar revisión manual") }
                }
            }
        }
    }
    confirmation?.let {
        Text(it, color = TrainingPalette.Muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
