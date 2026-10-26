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

La app registra el token autenticado en `POST /api/portal/push-tokens`. El backend debe guardar el token asociado a la cuenta paciente, permitir su revocación al cerrar sesión y usar FCM HTTP v1 con credenciales de servidor protegidas para enviar avisos. Nunca guardes credenciales de cuenta de servicio en Android ni en Git.

## 3. Permisos

Android 13 o posterior exige el permiso de notificaciones. Si el usuario lo deniega, las videoconsultas y el resto del portal siguen funcionando.
