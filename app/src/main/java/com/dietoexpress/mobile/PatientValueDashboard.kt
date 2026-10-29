package com.dietoexpress.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietoexpress.mobile.data.PatientAppointment
import com.dietoexpress.mobile.data.WeightEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DashboardForest = Color(0xFF176B52)
private val DashboardSage = Color(0xFFE7F2EC)
private val DashboardInk = Color(0xFF172923)
private val DashboardMuted = Color(0xFF687970)
private val DashboardLine = Color(0xFFE2EAE5)

private data class WeightTrend(
    val first: WeightEntry,
    val latest: WeightEntry,
    val changeKg: Double
)

private fun weightTrend(entries: List<WeightEntry>): WeightTrend? {
    val dated = entries.mapNotNull { entry ->
        val date = runCatching { LocalDate.parse(entry.date.take(10)) }.getOrNull()
            ?: return@mapNotNull null
        date to entry
    }.sortedBy { it.first }
    if (dated.size < 2) return null
    val first = dated.first().second
    val latest = dated.last().second
    return WeightTrend(first, latest, latest.weight - first.weight)
}

private fun dashboardDate(value: String): String {
    val date = runCatching { LocalDate.parse(value.take(10)) }.getOrNull() ?: return value.take(10)
    return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "ES")))
}

private fun dashboardNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale("es", "ES"), "%.1f", value)

@Composable
internal fun PatientValueDashboard(
    currentWeight: Double?,
    weightHistory: List<WeightEntry>,
    nextAppointment: PatientAppointment?,
    onProgress: () -> Unit,
    onAppointments: () -> Unit
) {
    val trend = remember(weightHistory) { weightTrend(weightHistory) }
    if (currentWeight == null && trend == null && nextAppointment == null) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Tu seguimiento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DashboardInk)
                Text("Pequeños avances, decisiones mejor informadas.", style = MaterialTheme.typography.bodySmall, color = DashboardMuted)
            }
            Box(
                modifier = Modifier.size(42.dp).background(DashboardSage, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("↗", color = DashboardForest, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (currentWeight != null || trend != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Peso registrado", color = DashboardMuted, style = MaterialTheme.typography.labelMedium)
                            Text(
                                currentWeight?.let { "${dashboardNumber(it)} kg" } ?: "Aún sin dato actual",
                                color = DashboardInk,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(Modifier.size(44.dp).background(DashboardSage, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Text("kg", color = DashboardForest, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (trend != null) {
                        androidx.compose.material3.HorizontalDivider(color = DashboardLine)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val change = trend.changeKg
                            Box(
                                modifier = Modifier.size(38.dp).background(
                                    if (change == 0.0) Color(0xFFF0F3F1) else DashboardSage,
                                    CircleShape
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when { change < 0 -> "↓"; change > 0 -> "↑"; else -> "→" },
                                    color = DashboardForest,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Cambio en el historial: ${if (change > 0) "+" else ""}${dashboardNumber(change)} kg",
                                    color = DashboardInk,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${dashboardDate(trend.first.date)} – ${dashboardDate(trend.latest.date)}",
                                    color = DashboardMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text(
                            "La variación compara el primer y el último registro disponible; no es una valoración clínica por sí sola.",
                            color = DashboardMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Registra nuevas medidas con tu profesional para ver cómo evoluciona tu historial.", color = DashboardMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onProgress, modifier = Modifier.align(Alignment.End)) {
                        Text("Ver evolución completa →", color = DashboardForest, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        nextAppointment?.let { appointment ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DashboardForest)
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).background(Color(0xFF2C8066), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("◷", color = Color.White, fontSize = 23.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Tu próxima consulta", color = Color(0xFFC8E4D4), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(dashboardDate(appointment.startsAt), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${appointment.startsAt.substringAfter("T", "").take(5).ifBlank { "Hora por confirmar" }} · ${appointment.modality?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Consulta"}",
                            color = Color(0xFFE2F0E8),
                            style = MaterialTheme.typography.bodySmall
                        )
                        appointment.nutritionistName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = onAppointments) {
                        Text("Ver", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
