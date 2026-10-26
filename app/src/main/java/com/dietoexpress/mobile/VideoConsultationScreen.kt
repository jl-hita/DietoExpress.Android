package com.dietoexpress.mobile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dietoexpress.mobile.data.PortalService
import com.dietoexpress.mobile.data.VideoEventRequest
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Composable
fun VideoConsultationScreen(appointmentId: Int?, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val room: Room = remember { LiveKit.create(context.applicationContext) }
    var microphoneGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var microphoneEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf<com.dietoexpress.mobile.data.VideoAccessResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var connectionStatus by remember { mutableStateOf("Preparando la sala…") }
    var retryNonce by remember { mutableStateOf(0) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        microphoneGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        cameraGranted = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!microphoneGranted) error = "Necesitamos permiso para usar el micrófono. Puedes activarlo en los ajustes de la app."
        if (!cameraGranted) cameraEnabled = false
        else if (connected && !cameraEnabled) {
            scope.launch {
                if (runCatching { room.localParticipant.setCameraEnabled(true) }.getOrDefault(false)) {
                    cameraEnabled = true
                    localVideoTrack = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                }
            }
        }
    }

    LaunchedEffect(appointmentId) {
        if (!microphoneGranted || !cameraGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                    .filter { permission ->
                        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                    }.toTypedArray()
            )
        }
    }

    LaunchedEffect(room) {
        room.events.collect { event ->
            when (event) {
                is RoomEvent.TrackSubscribed -> {
                    val track = event.track as? VideoTrack
                    if (track != null) {
                        remoteVideoTrack = track
                        renderer?.let { track.addRenderer(it) }
                    }
                }
                is RoomEvent.TrackUnsubscribed -> {
                    val track = event.track as? VideoTrack
                    if (track != null) {
                        renderer?.let { track.removeRenderer(it) }
                        if (remoteVideoTrack == track) remoteVideoTrack = null
                    }
                }
                is RoomEvent.Reconnecting -> {
                    connectionStatus = "Se ha interrumpido la conexión. Reconectando…"
                    connected = false
                    appointmentId?.let { id ->
                        withContext(Dispatchers.IO) {
                            runCatching { PortalService.api.recordVideoEvent(id, VideoEventRequest("reconnecting")) }
                        }
                    }
                }
                is RoomEvent.Reconnected -> {
                    connectionStatus = "Conectado"
                    connected = true
                    appointmentId?.let { id ->
                        withContext(Dispatchers.IO) {
                            runCatching { PortalService.api.recordVideoEvent(id, VideoEventRequest("reconnected")) }
                        }
                    }
                }
                is RoomEvent.Disconnected -> {
                    connectionStatus = "Desconectado"
                    connected = false
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(appointmentId, microphoneGranted, retryNonce) {
        val id = appointmentId
        if (id == null) {
            error = "No se ha seleccionado ninguna cita."
            return@LaunchedEffect
        }
        if (!microphoneGranted || connected || connecting || access != null) return@LaunchedEffect
        connecting = true
        error = null
        connectionStatus = "Conectando…"
        try {
            val meeting = withContext(Dispatchers.IO) { PortalService.api.videoAccess(id) }
            if (!meeting.provider.equals("livekit", true)) {
                error = "Esta cita utiliza un proveedor de videollamadas no compatible con la app."
                return@LaunchedEffect
            }
            access = meeting
            room.connect(meeting.roomUrl, meeting.token)
            room.localParticipant.setMicrophoneEnabled(true)
            if (cameraGranted && room.localParticipant.setCameraEnabled(true)) {
                cameraEnabled = true
                localVideoTrack = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
            } else {
                cameraEnabled = false
            }
            connected = true
            connectionStatus = "Conectado"
            withContext(Dispatchers.IO) {
                runCatching { PortalService.api.recordVideoEvent(id, VideoEventRequest("connected")) }
            }
        } catch (ex: HttpException) {
            runCatching { room.disconnect() }
            access = null
            connectionStatus = "No conectado"
            error = when (ex.code()) {
                401, 403 -> "Tu sesión ha caducado o no tienes acceso a esta cita. Vuelve a iniciar sesión."
                409 -> "La sala todavía no está disponible o el acceso ha expirado. Comprueba la fecha y hora de la cita."
                429 -> "Se ha alcanzado temporalmente la cuota de videollamadas. Inténtalo más tarde."
                else -> "No se ha podido autorizar la videoconsulta (HTTP ${ex.code()})."
            }
            withContext(Dispatchers.IO) { runCatching { PortalService.api.recordVideoEvent(id, VideoEventRequest("failed")) } }
        } catch (_: Exception) {
            runCatching { room.disconnect() }
            access = null
            connectionStatus = "No conectado"
            error = "No se ha podido conectar a la videoconsulta. Comprueba tu conexión y vuelve a intentarlo."
            withContext(Dispatchers.IO) { runCatching { PortalService.api.recordVideoEvent(id, VideoEventRequest("failed")) } }
        } finally {
            connecting = false
        }
    }

    DisposableEffect(room) {
        onDispose {
            scope.launch {
                runCatching { room.disconnect() }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Videoconsulta", style = MaterialTheme.typography.headlineSmall)
        Text("Cita #${appointmentId ?: "—"} · Tu conexión es privada y está autorizada por DietoExpress.")
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF10251F))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        SurfaceViewRenderer(viewContext).also { view ->
                            renderer = view
                            room.initVideoRenderer(view)
                            remoteVideoTrack?.addRenderer(view)
                        }
                    },
                    update = { view ->
                        renderer = view
                        remoteVideoTrack?.addRenderer(view)
                    }
                )
                if (remoteVideoTrack == null) {
                    Text(
                        if (connected) "Conectado. Esperando el vídeo de la otra persona…" else connectionStatus,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                if (cameraGranted) {
                    AndroidView(
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).width(96.dp).height(136.dp),
                        factory = { viewContext ->
                            SurfaceViewRenderer(viewContext).also { view ->
                                localRenderer = view
                                room.initVideoRenderer(view)
                                localVideoTrack?.addRenderer(view)
                            }
                        },
                        update = { view ->
                            localRenderer = view
                            localVideoTrack?.addRenderer(view)
                        }
                    )
                }
                if (connecting) CircularProgressIndicator()
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (error != null && microphoneGranted) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { room.disconnect() }
                        access = null
                        connected = false
                        retryNonce += 1
                    }
                },
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reintentar conexión") }
        }
        if (!microphoneGranted || !cameraGranted) {
            OutlinedButton(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA).filter { permission ->
                            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                        }.toTypedArray()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (!microphoneGranted) "Permitir micrófono y cámara" else "Activar cámara") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    if (!connected) return@OutlinedButton
                    scope.launch {
                        val next = !microphoneEnabled
                        if (runCatching { room.localParticipant.setMicrophoneEnabled(next) }.isSuccess) microphoneEnabled = next
                    }
                },
                enabled = connected,
                modifier = Modifier.weight(1f)
            ) { Text(if (microphoneEnabled) "Silenciar micro" else "Activar micro") }
            OutlinedButton(
                onClick = {
                    if (!connected || !cameraGranted) return@OutlinedButton
                    scope.launch {
                        val next = !cameraEnabled
                        if (runCatching { room.localParticipant.setCameraEnabled(next) }.getOrDefault(false)) {
                            cameraEnabled = next
                            localVideoTrack = room.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                        }
                    }
                },
                enabled = connected && cameraGranted,
                modifier = Modifier.weight(1f)
            ) { Text(if (cameraEnabled) "Apagar cámara" else "Activar cámara") }
        }
        Button(
            onClick = {
                scope.launch {
                    appointmentId?.let { id ->
                        withContext(Dispatchers.IO) {
                            runCatching { PortalService.api.recordVideoEvent(id, VideoEventRequest("disconnected")) }
                        }
                    }
                    runCatching { room.disconnect() }
                    onExit()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Finalizar y volver a citas") }
    }
}
