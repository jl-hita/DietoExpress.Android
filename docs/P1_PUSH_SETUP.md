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

## 2. Backend

El backend de DietoExpress implementa `POST /api/portal/push-tokens` y `POST /api/portal/push-tokens/revoke`. Para el envío real, configura en el servidor `DIETOEXPRESS_FCM_PROJECT_ID` y `DIETOEXPRESS_FCM_SERVICE_ACCOUNT_PATH` según `docs/P1_PUSH_NOTIFICATIONS.md` del repositorio backend. Hasta entonces, la app sigue funcionando y el backend no afirma que haya enviado avisos. Nunca guardes credenciales de cuenta de servicio en Android ni en Git.

## 3. Permisos

Android 13 o posterior exige el permiso de notificaciones. Si el usuario lo deniega, las videoconsultas y el resto del portal siguen funcionando.
