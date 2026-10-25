package com.dietoexpress.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietoexpress.mobile.data.AccessLinkRequest
import com.dietoexpress.mobile.data.CheckinEligibility
import com.dietoexpress.mobile.data.DietDetail
import com.dietoexpress.mobile.data.PatientAppointment
import com.dietoexpress.mobile.data.PatientAuthRequest
import com.dietoexpress.mobile.data.PatientCheckin
import com.dietoexpress.mobile.data.PatientDocument
import com.dietoexpress.mobile.data.PatientMessage
import com.dietoexpress.mobile.data.ShoppingCategory
import com.dietoexpress.mobile.data.TrainingEnvelope
import com.dietoexpress.mobile.data.SendPatientMessageRequest
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
    val appointments: List<PatientAppointment> = emptyList(),
    val appointmentsLoaded: Boolean = false,
    val documents: List<PatientDocument> = emptyList(),
    val documentsLoaded: Boolean = false,
    val shoppingList: List<ShoppingCategory> = emptyList(),
    val shoppingLoaded: Boolean = false,
    val messages: List<PatientMessage> = emptyList(),
    val messagesLoaded: Boolean = false,
    val messageDraft: String = "",
    val training: TrainingEnvelope? = null,
    val trainingLoaded: Boolean = false,
    val checkinEligibility: CheckinEligibility? = null,
    val currentCheckin: PatientCheckin? = null,
    val checkinHistory: List<PatientCheckin> = emptyList(),
    val checkinLoaded: Boolean = false,
    val section: String = "home",
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
                appointmentsLoaded = false, documentsLoaded = false, shoppingLoaded = false,
                messagesLoaded = false, trainingLoaded = false, checkinLoaded = false,
                section = "home", message = null, error = false
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

    fun openSection(section: String) {
        _state.value = _state.value.copy(section = section, message = null, error = false)
        when (section) {
            "diet" -> loadDiet()
            "appointments" -> loadAppointments()
            "documents" -> loadDocuments()
            "shopping" -> loadShoppingList()
            "messages" -> loadMessages()
            "training" -> loadTraining()
            "progress" -> loadCheckin()
            "profile" -> refreshProfile()
        }
    }

    fun showProfile() = openSection("home")

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
                } else throw ex
            }
        }
    }

    fun loadAppointments() {
        if (_state.value.busy || _state.value.appointmentsLoaded) return
        runRequest {
            val appointments = PortalService.api.appointments()
            _state.value = _state.value.copy(appointments = appointments, appointmentsLoaded = true, section = "appointments")
        }
    }

    fun loadDocuments() {
        if (_state.value.busy || _state.value.documentsLoaded) return
        runRequest {
            val documents = PortalService.api.documents()
            _state.value = _state.value.copy(documents = documents, documentsLoaded = true, section = "documents")
        }
    }


    fun loadShoppingList() {
        if (_state.value.busy || _state.value.shoppingLoaded) return
        runRequest {
            val items = PortalService.api.shoppingList()
            _state.value = _state.value.copy(shoppingList = items, shoppingLoaded = true, section = "shopping")
        }
    }

    fun loadMessages() {
        if (_state.value.busy) return
        runRequest {
            val items = PortalService.api.messages()
            PortalService.api.markMessagesRead(emptyMap())
            _state.value = _state.value.copy(messages = items, messagesLoaded = true, section = "messages")
        }
    }

    fun updateMessageDraft(value: String) {
        _state.value = _state.value.copy(messageDraft = value)
    }

    fun sendMessage() {
        val body = _state.value.messageDraft.trim()
        if (body.isBlank() || body.length > 5000 || _state.value.busy) return
        runRequest {
            PortalService.api.sendMessage(SendPatientMessageRequest(body))
            val messages = PortalService.api.messages()
            PortalService.api.markMessagesRead(emptyMap())
            _state.value = _state.value.copy(messages = messages, messagesLoaded = true, messageDraft = "", section = "messages")
        }
    }

    fun loadTraining() {
        if (_state.value.busy || _state.value.trainingLoaded) return
        runRequest {
            val training = PortalService.api.trainingSessions()
            _state.value = _state.value.copy(training = training, trainingLoaded = true, section = "training")
        }
    }

    fun loadCheckin() {
        if (_state.value.busy || _state.value.checkinLoaded) return
        runRequest {
            val eligibility = PortalService.api.checkinEligibility()
            val current = if (eligibility.eligible) PortalService.api.currentCheckin() else null
            val history = if (eligibility.eligible) PortalService.api.checkinHistory() else emptyList()
            _state.value = _state.value.copy(
                checkinEligibility = eligibility, currentCheckin = current,
                checkinHistory = history, checkinLoaded = true, section = "progress"
            )
        }
    }

    fun logout() = runRequest {
        try {
            PortalService.api.logout()
        } finally {
            PortalService.clearSessionCookies()
            _state.value = PortalUiState(message = "Sesión cerrada.", error = false)
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
                    404 -> "Todavía no hay información disponible para esta sección."
                    409 -> "Esta operación no está disponible en este momento."
                    429 -> "Demasiados intentos. Espera un momento y vuelve a probar."
                    in 500..599 -> "El servicio no está disponible ahora. Inténtalo más tarde."
                    else -> "No se ha podido completar la operación (HTTP ${ex.code()})."
                }
                _state.value = _state.value.copy(message = message, error = true)
                if (ex.code() == 401) {
                    PortalService.clearSessionCookies()
                    _state.value = _state.value.copy(profile = null, diet = null, dietLoaded = false, section = "home")
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
