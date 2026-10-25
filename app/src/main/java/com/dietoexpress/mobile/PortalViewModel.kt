package com.dietoexpress.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dietoexpress.mobile.data.AccessLinkRequest
import com.dietoexpress.mobile.data.CheckinEligibility
import com.dietoexpress.mobile.data.DietDetail
import com.dietoexpress.mobile.data.PatientAppointment
import com.dietoexpress.mobile.data.AppointmentSlot
import com.dietoexpress.mobile.data.CreatePatientAppointmentRequest
import com.dietoexpress.mobile.data.NutritionistRelationship
import com.dietoexpress.mobile.data.NutritionistTransfer
import com.dietoexpress.mobile.data.ConfirmNutritionistTransferRequest
import com.dietoexpress.mobile.data.NutritionistChangeRequest
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
import com.dietoexpress.mobile.data.SessionOperationLock
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class PortalUiState(
    val emailOrPhone: String = "",
    val passcode: String = "",
    val linkEmail: String = "",
    val profile: PatientProfile? = null,
    val nutritionistRelationship: NutritionistRelationship? = null,
    val nutritionistTransfer: NutritionistTransfer? = null,
    val transferConsentChecked: Boolean = false,
    val diet: DietDetail? = null,
    val dietLoaded: Boolean = false,
    val appointments: List<PatientAppointment> = emptyList(),
    val appointmentsLoaded: Boolean = false,
    val appointmentSlots: List<AppointmentSlot> = emptyList(),
    val appointmentSlotsError: String? = null,
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
            // Serializa el cambio de sesión con cualquier envío offline en curso: una cookie recién
            // autenticada nunca puede utilizarse accidentalmente para una operación de otra cuenta.
            SessionOperationLock.withLock {
                PortalService.api.authenticate(
                    PatientAuthRequest(emailOrPhone = current.emailOrPhone.trim(), passcode = current.passcode)
                )
                val profile = PortalService.api.profile()
                val relationship = runCatching { PortalService.api.nutritionistRelationship() }.getOrNull()
                val transfer = runCatching { PortalService.api.nutritionistTransfer() }.getOrNull()
                offline.cacheValue(profile.clientId, "profile", profile)
                offline.setActiveAccountId(profile.clientId)
                offline.scheduleSync(profile.clientId)
                _state.value = _state.value.copy(
                    profile = profile, nutritionistRelationship = relationship, nutritionistTransfer = transfer?.takeIf { it.pending },
                    transferConsentChecked = false, passcode = "", diet = null, dietLoaded = false,
                    appointmentsLoaded = false, documentsLoaded = false, shoppingLoaded = false,
                    messagesLoaded = false, trainingLoaded = false, checkinLoaded = false,
                    section = "home", message = null, error = false
                )
            }
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
        val relationship = runCatching { PortalService.api.nutritionistRelationship() }.getOrNull()
        _state.value = _state.value.copy(profile = result.value, nutritionistRelationship = relationship,
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
                    offline.invalidate(_state.value.profile?.clientId ?: return@runRequest, "diet")
                    _state.value = _state.value.copy(
                        diet = null, dietLoaded = true,
                        message = "Tu nutricionista todavía no te ha asignado una dieta activa.",
                        error = false
                    )
                } else throw ex
            }
        }
    }

    fun refreshAppointments() {
        _state.value = _state.value.copy(appointmentsLoaded = false, appointmentSlotsError = null)
        loadAppointments()
    }

    fun loadAppointments() {
        if (_state.value.busy || _state.value.appointmentsLoaded) return
        runRequest {
            val accountId = _state.value.profile?.clientId ?: throw IOException("No hay una sesión activa.")
            val type = TypeToken.getParameterized(List::class.java, PatientAppointment::class.java).type
            val result = offline.readThrough(accountId, "appointments", type) { PortalService.api.appointments() }
            var slots = emptyList<AppointmentSlot>()
            var slotsError: String? = null
            val hasAssignedNutritionist = _state.value.nutritionistRelationship?.hasAssignedNutritionist
                ?: !_state.value.profile?.nutritionistName.isNullOrBlank()
            if (hasAssignedNutritionist) {
                try {
                    slots = PortalService.api.appointmentSlots(30)
                } catch (ex: Exception) {
                    slotsError = "No hemos podido cargar la disponibilidad de tu nutricionista. Comprueba la conexión e inténtalo de nuevo."
                }
            }
            _state.value = _state.value.copy(appointments = result.value, appointmentsLoaded = true,
                appointmentSlots = slots, appointmentSlotsError = slotsError, section = "appointments",
                message = if (result.fromCache) "Sin conexión: se muestran las últimas citas guardadas." else null, error = false)
        }
    }

    fun loadNutritionistTransfer() {
        viewModelScope.launch {
            val transfer = runCatching { PortalService.api.nutritionistTransfer() }.getOrNull()
            _state.value = _state.value.copy(nutritionistTransfer = transfer?.takeIf { it.pending })
        }
    }

    fun setTransferConsent(checked: Boolean) {
        _state.value = _state.value.copy(transferConsentChecked = checked)
    }

    fun confirmNutritionistTransfer() {
        val current = _state.value
        val transfer = current.nutritionistTransfer ?: return
        val id = transfer.id ?: return
        if (!transfer.requiresPatientConfirmation || !current.transferConsentChecked || current.busy) return
        runRequest {
            val result = PortalService.api.confirmNutritionistTransfer(id, ConfirmNutritionistTransferRequest(true))
            val relationship = runCatching { PortalService.api.nutritionistRelationship() }.getOrNull()
            _state.value = _state.value.copy(
                nutritionistTransfer = null, transferConsentChecked = false,
                nutritionistRelationship = relationship,
                message = result.message ?: "Cambio y transferencia confirmados.",
                error = false
            )
            refreshProfile()
            loadAppointments()
        }
    }

    fun cancelNutritionistTransfer() {
        val transfer = _state.value.nutritionistTransfer ?: return
        val id = transfer.id ?: return
        if (_state.value.busy) return
        runRequest {
            val result = PortalService.api.cancelNutritionistTransfer(id)
            _state.value = _state.value.copy(
                nutritionistTransfer = null, transferConsentChecked = false,
                message = result.message ?: "Cambio cancelado. Mantienes tu nutricionista actual.",
                error = false
            )
            loadAppointments()
        }
    }

    fun requestNutritionistChange(type: String) {
        if (_state.value.busy) return
        runRequest {
            val response = PortalService.api.requestNutritionistChange(NutritionistChangeRequest(type))
            val current = _state.value
            // Buscar otro profesional no equivale a confirmar el cambio. No borrar la
            // relación ni la disponibilidad: se mantienen hasta la reserva y confirmación final.
            val relationship = if (type == "clinic_reassignment") {
                current.nutritionistRelationship?.copy(hasPendingClinicReassignment = true)
            } else {
                current.nutritionistRelationship
            }
            _state.value = current.copy(
                nutritionistRelationship = relationship,
                message = response.message ?: "Solicitud procesada.",
                error = false
            )
        }
    }

    fun requestAppointment(slot: AppointmentSlot, modality: String) {
        if (_state.value.busy) return
        runRequest {
            val response = PortalService.api.requestAppointment(
                CreatePatientAppointmentRequest(startsAt = slot.startsAt, durationMinutes = 30, modality = modality)
            )
            val current = _state.value
            _state.value = current.copy(
                appointments = listOf(response) + current.appointments,
                appointmentSlots = current.appointmentSlots.filterNot { it.startsAt == slot.startsAt },
                appointmentSlotsError = null,
                message = "Solicitud enviada a tu nutricionista. Queda pendiente de confirmación.",
                error = false,
                section = "appointments"
            )
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
            if (!result.fromCache) {
                // La operación de lectura se persiste antes del envío. Si se pierde la respuesta,
                // WorkManager reintenta con el mismo ID en lugar de generar una clave nueva.
                val operationId = offline.enqueueMarkMessagesRead(accountId)
                try {
                    val sent = SessionOperationLock.withLock {
                        if (!offline.activeAccountMatches(accountId)) false
                        else {
                            PortalService.api.markMessagesRead(operationId, emptyMap())
                            true
                        }
                    }
                    if (sent) offline.completeOperation(operationId)
                    else offline.needsAuthentication(operationId)
                } catch (_: IOException) {
                    // La operación queda en Room y WorkManager la reintentará con la misma clave.
                } catch (ex: HttpException) {
                    when {
                        ex.code() == 401 || ex.code() == 403 ->
                            offline.needsAuthentication(operationId)
                        ex.code() == 409 ->
                            offline.conflictOperation(operationId, "Conflicto al marcar la conversación como leída.", ex.response()?.errorBody()?.string())
                        ex.code() >= 500 || ex.code() == 429 ->
                            offline.retryOperation(operationId, "El servidor rechazó temporalmente la lectura.")
                        else ->
                            offline.failOperation(operationId, "No se pudo marcar la conversación como leída (HTTP ${ex.code()}).")
                    }
                }
            }
            val draft = offline.messageDraft(accountId)
            val queued = offline.allOperations(accountId).filter {
                it.operationType == OfflineRepository.OP_SEND_PATIENT_MESSAGE
            }
            val pendingCount = queued.count { it.status == "PENDING" || it.status == "NEEDS_AUTH" }
            val failedCount = queued.count { it.status == "FAILED" }
            val syncMessage = when {
                failedCount > 0 -> "Hay $failedCount mensaje(s) que no se pudieron enviar. Comprueba el contenido y vuelve a intentarlo."
                pendingCount > 0 -> "Tienes $pendingCount mensaje(s) guardados y pendientes de confirmación."
                result.fromCache -> "Sin conexión: se muestran los últimos mensajes guardados."
                else -> null
            }
            _state.value = _state.value.copy(messages = result.value, messagesLoaded = true, messageDraft = draft, section = "messages",
                message = syncMessage, error = failedCount > 0)
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
            // Primero se persiste el payload cifrado y la clave UUID; solo después se intenta la red.
            // Si la respuesta se pierde, WorkManager repetirá exactamente la misma operación lógica.
            val operationId = offline.enqueueSendPatientMessage(accountId, body)
            val request = SendPatientMessageRequest(body)
            try {
                val sent = SessionOperationLock.withLock {
                    if (!offline.activeAccountMatches(accountId)) false
                    else {
                        PortalService.api.sendMessage(operationId, request)
                        true
                    }
                }
                if (!sent) {
                    offline.needsAuthentication(operationId)
                    offline.saveMessageDraft(accountId, "")
                    _state.value = _state.value.copy(
                        messageDraft = "",
                        message = "El mensaje está guardado en el dispositivo, pero necesita una sesión válida para enviarse.",
                        error = false
                    )
                    return@runRequest
                }
                offline.completeOperation(operationId)
                offline.saveMessageDraft(accountId, "")
                _state.value = _state.value.copy(messageDraft = "", message = "Mensaje enviado.", error = false)
                try {
                    val type = TypeToken.getParameterized(List::class.java, PatientMessage::class.java).type
                    val result = offline.readThrough(accountId, "messages", type) { PortalService.api.messages() }
                    _state.value = _state.value.copy(
                        messages = result.value, messagesLoaded = true, section = "messages",
                        message = if (result.fromCache) "Mensaje enviado. El historial guardado puede estar desactualizado." else null,
                        error = false
                    )
                } catch (_: IOException) {
                    _state.value = _state.value.copy(
                        message = "Mensaje enviado. No se pudo actualizar el historial; se actualizará cuando haya conexión.",
                        error = false
                    )
                }
            } catch (_: IOException) {
                // La petición puede haber llegado al servidor. No se borra la operación ni se genera otra clave.
                offline.saveMessageDraft(accountId, "")
                _state.value = _state.value.copy(
                    messageDraft = "",
                    message = "Mensaje guardado de forma segura y pendiente de confirmación. Se reintentará automáticamente; no lo vuelvas a enviar.",
                    error = false
                )
            } catch (ex: HttpException) {
                when {
                    ex.code() == 401 || ex.code() == 403 -> {
                        offline.needsAuthentication(operationId)
                        offline.saveMessageDraft(accountId, "")
                        _state.value = _state.value.copy(
                            messageDraft = "",
                            message = "El mensaje está guardado en el dispositivo, pero necesita una sesión válida para enviarse.",
                            error = false
                        )
                    }
                    ex.code() == 409 -> {
                        offline.conflictOperation(operationId, "El servidor detectó un conflicto al enviar el mensaje; no se repetirá automáticamente.", ex.response()?.errorBody()?.string())
                        _state.value = _state.value.copy(
                            message = "El servidor ha detectado un conflicto. El mensaje no se repetirá automáticamente; revisa el estado antes de volver a enviarlo.",
                            error = true
                        )
                    }
                    ex.code() >= 500 || ex.code() == 429 -> {
                        offline.retryOperation(operationId, "El servidor rechazó temporalmente el envío.")
                        offline.saveMessageDraft(accountId, "")
                        _state.value = _state.value.copy(
                            messageDraft = "",
                            message = "Mensaje guardado de forma segura y pendiente de envío. Se reintentará automáticamente.",
                            error = false
                        )
                    }
                    else -> {
                        offline.failOperation(operationId, "El servidor rechazó el mensaje (HTTP ${ex.code()}).")
                        _state.value = _state.value.copy(
                            message = "No se ha enviado el mensaje (HTTP ${ex.code()}). El borrador se conserva para corregirlo.",
                            error = true
                        )
                    }
                }
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
        SessionOperationLock.withLock {
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
    }

    private fun runRequest(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null, error = false)
        viewModelScope.launch {
            try {
                block()
            } catch (ex: CancellationException) {
                // No convertir la cancelación del ViewModel en un error normal: la cola persistida
                // debe quedar pendiente para que WorkManager pueda retomarla de forma segura.
                throw ex
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
                    _state.value.profile?.clientId?.let(offline::clearActiveAccountId)
                    _state.value = _state.value.copy(
                        profile = null, diet = null, dietLoaded = false,
                        appointments = emptyList(), appointmentsLoaded = false,
                        documents = emptyList(), documentsLoaded = false,
                        shoppingList = emptyList(), shoppingLoaded = false,
                        messages = emptyList(), messagesLoaded = false,
                        training = null, trainingLoaded = false,
                        checkinEligibility = null, currentCheckin = null, checkinHistory = emptyList(), checkinLoaded = false,
                        section = "home"
                    )
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
