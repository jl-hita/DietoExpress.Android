package com.dietoexpress.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dietoexpress.mobile.data.PortalService
import com.dietoexpress.mobile.data.PushTokenRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PushNotifications {
    private const val CHANNEL_ID = "dietoexpress_updates"

    fun registerCurrentToken(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        runCatching { FirebaseMessaging.getInstance().token }
            .getOrNull()
            ?.addOnSuccessListener { token ->
                if (token.isBlank()) return@addOnSuccessListener
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        PortalService.initialize(context.applicationContext)
                        PortalService.api.registerPushToken(PushTokenRequest(token))
                    } catch (_: Exception) {
                        // The login flow or the next token refresh retries registration.
                        // Never block the portal when push is not configured on the server yet.
                    }
                }
            }
    }

    fun show(context: Context, title: String, body: String, target: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Avisos de DietoExpress", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_target", target)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, target?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.ifBlank { "DietoExpress" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (_: SecurityException) {
            // Android 13+: the user may have denied notification permission.
        }
    }
}

class DietoExpressMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushNotifications.registerCurrentToken(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["title"] ?: message.notification?.title ?: "DietoExpress"
        val body = message.data["body"] ?: message.notification?.body ?: "Tienes una nueva notificación."
        PushNotifications.show(applicationContext, title, body, message.data["target"])
    }
}
