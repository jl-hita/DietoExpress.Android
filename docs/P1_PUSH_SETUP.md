# Configuración de notificaciones push (P1)

La app admite FCM de forma opcional para que los builds locales y la CI sigan funcionando sin credenciales Firebase.

## 1. Configurar Firebase

1. En Firebase Console, registra una aplicación Android con el ID `com.dietoexpress.mobile`.
2. En la configuración del proyecto copia el API key, el App ID, el Project ID y el Sender ID (número del proyecto).
3. Añade estas propiedades a tu `~/.gradle/gradle.properties` local (no las publiques en el repositorio):

```properties
dietoexpressFirebaseApiKey=...
dietoexpressFirebaseApplicationId=1:...:android:...
dietoexpressFirebaseProjectId=...
dietoexpressFirebaseSenderId=...
```

No hace falta añadir `google-services.json` ni el plugin de Google Services: la app inicializa Firebase con esas opciones cuando están presentes. Sin ellas, FCM queda desactivado y la app funciona normalmente.

## 1.1. Firma de la versión de publicación

Guarda también las propiedades de firma **solo** en `~/.gradle/gradle.properties` (en Windows, normalmente `%USERPROFILE%\\.gradle\\gradle.properties`), nunca en el `gradle.properties` del repositorio:

```properties
androidSigningKeystorePath=C:/ruta/privada/upload-key.jks
androidSigningKeystorePassword=[REDACTED]
androidSigningKeyAlias=...
androidSigningKeyPassword=[REDACTED]
```

El archivo de claves debe estar fuera del repositorio y tener una copia de seguridad segura. No compartas contraseñas ni incluyas el keystore en Git. La CI compila `release` sin firmar; la firma estable solo se aplica cuando están configuradas las cuatro propiedades y el keystore existe.

## 2. Backend

El backend de DietoExpress implementa `POST /api/portal/push-tokens` y `POST /api/portal/push-tokens/revoke`. Para el envío real, configura en el servidor `DIETOEXPRESS_FCM_PROJECT_ID` y `DIETOEXPRESS_FCM_SERVICE_ACCOUNT_PATH` según `docs/P1_PUSH_NOTIFICATIONS.md` del repositorio backend. Hasta entonces, la app sigue funcionando y el backend no afirma que haya enviado avisos. Nunca guardes credenciales de cuenta de servicio en Android ni en Git.

## 3. Permisos

Android 13 o posterior exige el permiso de notificaciones. Si el usuario lo deniega, las videoconsultas y el resto del portal siguen funcionando.
