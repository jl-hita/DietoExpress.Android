package com.dietoexpress.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietoexpress.mobile.data.AccessLinkRequest
import com.dietoexpress.mobile.data.DietDetail
import com.dietoexpress.mobile.data.PatientAuthRequest
import com.dietoexpress.mobile.data.PatientProfile
import com.dietoexpress.mobile.data.PortalService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class PortalUiState(
    val emailOrPhone: String = "",
    val passcode: String = "",
    val linkEmail: String = "",
    val profile: PatientProfile? = null,
    val diet: DietDetail? = null,
    val dietLoaded: Boolean = false,
    val section: String = "profile",
    val busy: Boolean = false,
    val message: String? = null,
    val error: Boolean = false
)

class PortalViewModel : ViewModel() {
    private val _state = MutableStateFlow(PortalUiState())
    val state: StateFlow<PortalUiState> = _state.asStateFlow()

    fun updateEmailOrPhone(value: String) { _state.value = _state.value.copy(emailOrPhone = value) }
    fun updatePasscode(value: String) { _state.value = _state.value.copy(passcode = value) }
    fun updateLinkEmail(value: String) { _state.value = _state.value.copy(linkEmail = value) }

    fun login() {
        val current = _state.value
        if (current.emailOrPhone.isBlank() || current.passcode.isBlank()) {
            showMessage("Introduce tu email o teléfono y tu código de acceso.", true)
            return
        }
        runRequest {
            PortalService.api.authenticate(
                PatientAuthRequest(emailOrPhone = current.emailOrPhone.trim(), passcode = current.passcode)
            )
            val profile = PortalService.api.profile()
            _state.value = _state.value.copy(
                profile = profile, passcode = "", diet = null, dietLoaded = false,
                section = "profile", message = "Sesión iniciada.", error = false
            )
        }
    }

    fun requestAccessLink() {
        val email = _state.value.linkEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            showMessage("Introduce un email válido.", true)
            return
        }
        runRequest {
            val response = PortalService.api.requestAccessLink(AccessLinkRequest(email))
            showMessage(response.message ?: "Si el email corresponde a un paciente, recibirás un enlace.", false)
        }
    }

    fun refreshProfile() = runRequest {
        _state.value = _state.value.copy(profile = PortalService.api.profile(), message = null)
    }

    fun showProfile() {
        _state.value = _state.value.copy(section = "profile", message = null, error = false)
    }

    fun loadDiet() {
        if (_state.value.busy) return
        runRequest {
            _state.value = _state.value.copy(section = "diet")
            try {
                val diet = PortalService.api.activeDiet()
                _state.value = _state.value.copy(diet = diet, dietLoaded = true, message = null, error = false)
            } catch (ex: HttpException) {
                if (ex.code() == 404) {
                    _state.value = _state.value.copy(
                        diet = null, dietLoaded = true,
                        message = "Tu nutricionista todavía no te ha asignado una dieta activa.",
                        error = false
                    )
                } else {
                    throw ex
                }
            }
        }
    }

    fun logout() = runRequest {
        try {
            PortalService.api.logout()
        } finally {
            PortalService.clearSessionCookies()
            _state.value = _state.value.copy(
                profile = null, diet = null, dietLoaded = false, section = "profile",
                passcode = "", message = "Sesión cerrada.", error = false
            )
        }
    }

    private fun runRequest(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null, error = false)
        viewModelScope.launch {
            try {
                block()
            } catch (ex: HttpException) {
                val message = when (ex.code()) {
                    400 -> "Revisa los datos introducidos."
                    401 -> "Los datos no son válidos o la sesión ha caducado."
                    403 -> "El portal no está disponible para esta cuenta."
                    429 -> "Demasiados intentos. Espera un momento y vuelve a probar."
                    in 500..599 -> "El servicio no está disponible ahora. Inténtalo más tarde."
                    else -> "No se ha podido completar la operación (HTTP ${ex.code()})."
                }
                _state.value = _state.value.copy(message = message, error = true)
                if (ex.code() == 401) {
                    PortalService.clearSessionCookies()
                    _state.value = _state.value.copy(profile = null, diet = null, dietLoaded = false, section = "profile")
                }
            } catch (_: IOException) {
                _state.value = _state.value.copy(message = "No se ha podido conectar. Comprueba tu conexión.", error = true)
            } catch (_: Exception) {
                _state.value = _state.value.copy(message = "Ha ocurrido un error inesperado.", error = true)
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    private fun showMessage(message: String, error: Boolean) {
        _state.value = _state.value.copy(message = message, error = error)
    }
}
