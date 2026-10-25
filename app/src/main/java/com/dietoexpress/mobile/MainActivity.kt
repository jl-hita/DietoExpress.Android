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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

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
                        verticalArrangement = Arrangement.Center
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = vm::refreshProfile, enabled = !state.busy) { Text("Actualizar") }
                                TextButton(onClick = vm::logout, enabled = !state.busy) { Text("Cerrar sesión") }
                            }
                        }
                        if (state.busy) {
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
