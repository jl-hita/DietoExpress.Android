package com.dietoexpress.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import com.dietoexpress.mobile.data.OfflineRepository
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

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

class PortalViewModel(application: Application) : AndroidViewModel(application) {
    private val offline = OfflineRepository(application)

    init {
        PortalService.initialize(application)
        viewModelScope.launch {
            val cachedProfile = offline.restoreLastProfile()
            if (cachedProfile != null && _state.value.profile == null && !_state.value.busy) {
                _state.value = _state.value.copy(
                    profile = cachedProfile,
                    message = "Perfil recuperado del dispositivo; puede estar desactualizado hasta sincronizarse.",
                    error = false
                )
            }
        }
    }
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
            offline.cacheValue(profile.clientId, "profile", profile)
            offline.setActiveAccountId(profile.clientId)
            offline.scheduleSync(profile.clientId)
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
        val accountId = _state.value.profile?.clientId
        val result = if (accountId != null) {
            offline.readThrough(accountId, "profile", PatientProfile::class.java) { PortalService.api.profile() }
        } else {
            val profile = PortalService.api.profile()
            offline.cacheValue(profile.clientId, "profile", profile)
            com.dietoexpress.mobile.data.OfflineRead(profile, false)
        }
        _state.value = _state.value.copy(profile = result.value,
            message = if (result.fromCache) "Sin conexión: se muestran los últimos datos guardados." else null, error = false)
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
                val accountId = _state.value.profile?.clientId ?: throw IOException("No hay una sesión activa.")
                val result = offline.readThrough(accountId, "diet", DietDetail::class.java) { PortalService.api.activeDiet() }
                _state.value = _state.value.copy(diet = result.value, dietLoaded = true,
                    message = if (result.fromCache) "Sin conexión: se muestra la última dieta guardada. Comprueba su vigencia antes de seguirla." else null, error = false)
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
            val accountId = _state.value.profile?.clientId ?: throw IOException("No hay una sesión activa.")
            val type = TypeToken.getParameterized(List::class.java, PatientAppointment::class.java).type
            val result = offline.readThrough(accountId, "appointments", type) { PortalService.api.appointments() }
            _state.value = _state.value.copy(appointments = result.value, appointmentsLoaded = true, section = "appointments",
                message = if (result.fromCache) "Sin conexión: se muestran las últimas citas guardadas." else null, error = false)
        }
    }

    fun loadDocuments() {
        if (_state.value.busy || _state.value.documentsLoaded) return
        runRequest {
            val accountId = _state.value.profile?.clientId ?: throw IOException("No hay una sesión activa.")
            val type = TypeToken.getParameterized(List::class.java, PatientDocument::class.java).type
            val result = offline.readThrough(accountId, "documents", type) { PortalService.api.documents() }
            _state.value = _state.value.copy(documents = result.value, documentsLoaded = true, section = "documents",
                message = if (result.fromCache) "Sin conexión: se muestran los últimos documentos guardados." else null, error = false)
        }
    }


    fun loadShoppingList() {
        if (_state.value.busy || _state.value.shoppingLoaded) return
        runRequest {
            val accountId = _state.value.profile?.clientId ?: throw IOException("No hay una sesión activa.")
            val type = TypeToken.getParameterized(List::class.java, ShoppingCategory::class.java).type
            val result = offline.readThrough(accountId, "shopping-list", type) { PortalService.api.shoppingList() }
            _state.value = _state.value.copy(shoppingList = result.value, shoppingLoaded = true, section = "shopping",
                message = if (result.fromCache) "Sin conexión: se muestra la última lista guardada. Comprueba los alérgenos antes de comprar." else null, error = false)
        }
    }

    fun loadMessages() {
        if (_state.value.busy) return
        runRequest {
            val accountId = _state.value.profile?.clientId ?: throw IOException("No hay una sesión activa.")
            val type = TypeToken.getParameterized(List::class.java, PatientMessage::class.java).type
            val result = offline.readThrough(accountId, "messages", type) { PortalService.api.messages() }
            try { PortalService.api.markMessagesRead(UUID.randomUUID().toString(), emptyMap()) } catch (_: IOException) { /* Cached reads remain available; do not mark future unseen messages as read. */ }
            val draft = offline.messageDraft(accountId)
            _state.value = _state.value.copy(messages = result.value, messagesLoaded = true, messageDraft = draft, section = "messages",
                message = if (result.fromCache) "Sin conexión: se muestran los últimos mensajes guardados. El envío requiere conexión." else null, error = false)
        }
    }

    fun updateMessageDraft(value: String) {
        _state.value = _state.value.copy(messageDraft = value)
        _state.value.profile?.clientId?.let { accountId -> viewModelScope.launch { offline.saveMessageDraft(accountId, value) } }
    }

    fun sendMessage() {
        val body = _state.value.messageDraft.trim()
        val accountId = _state.value.profile?.clientId
        if (body.isBlank() || body.length > 5000 || _state.value.busy || accountId == null) return
        runRequest {
            try {
                PortalService.api.sendMessage(SendPatientMessageRequest(body))
            } catch (_: IOException) {
                _state.value = _state.value.copy(
                    message = "No se ha podido confirmar el envío. El borrador se conserva; comprueba los mensajes antes de volver a intentarlo.",
                    error = true
                )
                return@runRequest
            }

            offline.saveMessageDraft(accountId, "")
            _state.value = _state.value.copy(messageDraft = "", message = "Mensaje enviado.", error = false)
            try {
                val type = TypeToken.getParameterized(List::class.java, PatientMessage::class.java).type
                val result = offline.readThrough(accountId, "messages", type) { PortalService.api.messages() }
                try { PortalService.api.markMessagesRead(UUID.randomUUID().toString(), emptyMap()) } catch (_: IOException) { /* Do not defer a broad read receipt. */ }
                _state.value = _state.value.copy(
                    messages = result.value, messagesLoaded = true, section = "messages",
                    message = if (result.fromCache) "Mensaje enviado. El historial mostrado está guardado localmente y puede estar desactualizado." else null,
                    error = false
                )
            } catch (_: IOException) {
                _state.value = _state.value.copy(
                    message = "Mensaje enviado. No se pudo actualizar el historial; se actualizará cuando haya conexión.",
                    error = false
                )
            }
        }
    }

    fun loadTraining() {
        if (_state.value.busy || _state.value.trainingLoaded) return
        runRequest {
            val accountId = _state.value.profile?.clientId ?: throw IOException("No hay una sesión activa.")
            val result = offline.readThrough(accountId, "training", com.dietoexpress.mobile.data.TrainingEnvelope::class.java) { PortalService.api.trainingSessions() }
            _state.value = _state.value.copy(training = result.value, trainingLoaded = true, section = "training",
                message = if (result.fromCache) "Sin conexión: se muestran las últimas sesiones guardadas." else null, error = false)
        }
    }

    fun loadCheckin() {
        if (_state.value.busy || _state.value.checkinLoaded) return
        runRequest {
            val accountId = _state.value.profile?.clientId ?: throw IOException("No hay una sesión activa.")
            val eligibilityResult = offline.readThrough(accountId, "checkin-eligibility", CheckinEligibility::class.java) { PortalService.api.checkinEligibility() }
            val eligibility = eligibilityResult.value
            val currentResult = if (eligibility.eligible) offline.readThrough<PatientCheckin?>(accountId, "checkin-current", PatientCheckin::class.java) { PortalService.api.currentCheckin() } else null
            val historyResult = if (eligibility.eligible) {
                val type = TypeToken.getParameterized(List::class.java, PatientCheckin::class.java).type
                offline.readThrough(accountId, "checkin-history", type) { PortalService.api.checkinHistory() }
            } else null
            val stale = eligibilityResult.fromCache || currentResult?.fromCache == true || historyResult?.fromCache == true
            _state.value = _state.value.copy(checkinEligibility = eligibility, currentCheckin = currentResult?.value,
                checkinHistory = historyResult?.value ?: emptyList(), checkinLoaded = true, section = "progress",
                message = if (stale) "Sin conexión: se muestran los últimos datos de seguimiento guardados." else null, error = false)
        }
    }

    fun logout() = runRequest {
        val accountId = _state.value.profile?.clientId
        try {
            PortalService.api.logout()
        } finally {
            PortalService.clearSessionCookies()
            if (accountId != null) {
                offline.clearActiveAccountId(accountId)
                offline.clearAccountCache(accountId)
            }
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
