package com.dietoexpress.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dietoexpress.mobile.data.DietDay
import com.dietoexpress.mobile.data.DietDetail
import com.dietoexpress.mobile.data.DietMeal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: PortalViewModel = viewModel()
                    val state by vm.state.collectAsStateWithLifecycle()
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text("DietoExpress", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Tu portal de paciente", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(24.dp))

                        state.message?.let {
                            Text(it, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                        }

                        if (state.profile == null) {
                            Text("Accede a tu cuenta", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = state.emailOrPhone,
                                onValueChange = vm::updateEmailOrPhone,
                                label = { Text("Email o teléfono") },
                                singleLine = true,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.passcode,
                                onValueChange = vm::updatePasscode,
                                label = { Text("Código de acceso") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = vm::login, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Entrar")
                            }
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(16.dp))
                            Text("¿No tienes el código?", style = MaterialTheme.typography.titleMedium)
                            Text("Solicita un enlace de acceso en tu email.")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.linkEmail,
                                onValueChange = vm::updateLinkEmail,
                                label = { Text("Email") },
                                singleLine = true,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = vm::requestAccessLink, enabled = !state.busy) {
                                Text("Enviar enlace de acceso")
                            }
                        } else if (state.section == "diet") {
                            TextButton(onClick = vm::showProfile, enabled = !state.busy) { Text("← Mi perfil") }
                            Spacer(Modifier.height(8.dp))
                            if (state.busy && !state.dietLoaded) {
                                CircularProgressIndicator()
                            } else if (state.dietLoaded && state.diet == null) {
                                Text("Mi dieta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(state.message ?: "No tienes una dieta activa.")
                                TextButton(onClick = vm::loadDiet, enabled = !state.busy) { Text("Volver a intentar") }
                            } else {
                                state.diet?.let { DietScreen(it) }
                            }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = vm::logout, enabled = !state.busy) { Text("Cerrar sesión") }
                        } else {
                            val profile = state.profile!!
                            Text("Hola, ${profile.fullName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            profile.clinicName?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(20.dp))
                            Text("Mi perfil", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            ProfileRow("Edad", profile.age?.let { "$it años" } ?: "Sin datos")
                            ProfileRow("Peso actual", profile.currentWeight?.let { "$it kg" } ?: "Sin datos")
                            ProfileRow("Altura", profile.currentHeight?.let { "$it cm" } ?: "Sin datos")
                            ProfileRow("Dieta activa", if (profile.hasActiveDiet) "Sí" else "No")
                            profile.nutritionistName?.takeIf { it.isNotBlank() }?.let { ProfileRow("Profesional", it) }
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = vm::loadDiet, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Text("Ver mi dieta")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = vm::refreshProfile, enabled = !state.busy) { Text("Actualizar perfil") }
                                TextButton(onClick = vm::logout, enabled = !state.busy) { Text("Cerrar sesión") }
                            }
                        }
                        if (state.busy && state.section != "diet") {
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DietScreen(diet: DietDetail) {
    Text(diet.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    diet.dietType?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Objetivos diarios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            MacroRow("Energía", diet.targetKcal, "kcal")
            MacroRow("Proteínas", diet.targetProtein, "g")
            MacroRow("Hidratos", diet.targetCarbs, "g")
            MacroRow("Grasas", diet.targetFat, "g")
        }
    }
    Spacer(Modifier.height(16.dp))
    if (diet.days.isEmpty()) {
        Text("La dieta todavía no contiene días.")
    } else {
        diet.days.sortedBy { it.dayIndex }.forEach { day ->
            DietDayCard(day)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DietDayCard(day: DietDay) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Día ${day.dayIndex}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (day.meals.isEmpty()) {
                Text("Sin comidas registradas.")
            } else {
                day.meals.sortedBy { it.mealIndex }.forEachIndexed { index, meal ->
                    if (index > 0) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                    }
                    MealBlock(meal)
                }
            }
        }
    }
}

@Composable
private fun MealBlock(meal: DietMeal) {
    Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    meal.items.forEach { item ->
        val name = item.foodName?.takeIf { it.isNotBlank() }
            ?: item.recipeName?.takeIf { it.isNotBlank() }
            ?: item.exchangeGroupName?.takeIf { it.isNotBlank() }
            ?: "Alimento"
        val amount = when {
            item.grams != null -> "${number(item.grams)} g"
            item.exchangeCount != null -> "${number(item.exchangeCount)} intercambio(s)"
            item.recipeServings != null -> "${number(item.recipeServings)} ración(es)"
            else -> null
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name)
                amount?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item.kcal?.let { Text("${number(it)} kcal", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun MacroRow(label: String, value: Double?, unit: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value?.let { "${number(it)} $unit" } ?: "Sin objetivo", fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
