# DietoExpress Android

Aplicación Android nativa de DietoExpress con Kotlin y Jetpack Compose. Consume la API ASP.NET Core existente; no replica reglas de negocio ni autorización.

## Estado

Primera versión funcional en desarrollo: inicio de sesión del portal de paciente mediante email/teléfono y código de acceso, solicitud de enlace por email, consulta de perfil, visualización de la dieta activa y cierre de sesión. Aún no es una versión publicable. Citas, documentos, seguimiento, push FCM, enlaces profundos y videollamadas están pendientes.

## Requisitos

- Android Studio estable compatible con Android Gradle Plugin 9.4.
- JDK 17.
- Gradle 9.8.1 para compilar mediante la tarea de CI.
- SDK Android 36.

## Android objetivo

- `minSdk = 26` (Android 8.0).
- `targetSdk = 36` (Android 16) como objetivo inicial. Antes de publicar, comprobar el requisito vigente de Google Play y validar el comportamiento con la API objetivo más reciente exigida.
- `compileSdk = 36`.

El identificador `com.dietoexpress.mobile` es provisional hasta verificarlo y fijarlo en Google Play.

## Compilar

Desde esta carpeta, con Gradle instalado:

```bash
gradle --no-daemon :app:assembleDebug
```

En Android Studio, abrir esta carpeta como proyecto Gradle. El repositorio aún no incluye Gradle Wrapper; se añadirá cuando se valide su generación con la instalación local/CI.

Se puede cambiar la base de API sin editar el código:

```bash
gradle -PdietoexpressApiBaseUrl=https://servidor-ejemplo/api :app:assembleDebug
```

La configuración por defecto apunta al entorno HTTPS de pruebas. No incluir secretos, passcodes, cookies ni credenciales Firebase en Git. Producción debe usar HTTPS.

## Arquitectura y sesión

- Kotlin + Jetpack Compose; cliente Retrofit/OkHttp y estado de pantalla con ViewModel.
- Backend como autoridad para identidad, permisos, tenant y reglas.
- Sesión del portal basada en cookie HttpOnly mantenida únicamente en memoria. No guardar el passcode, copiar el JWT a la UI ni registrar credenciales.
- La sesión se pierde al finalizar el proceso; el usuario deberá volver a iniciar sesión.
- FCM y enlaces profundos se añadirán tras diseñar el registro/revocación de dispositivo y las comprobaciones de autorización.
- La web seguirá siendo la interfaz de configuración y edición avanzada.

## Regla obligatoria de compatibilidad con la API

Cada cambio en el backend que añada, modifique o elimine endpoints, DTO, claims, permisos, cookies, códigos de estado, formatos de respuesta o reglas de negocio debe incluir una revisión explícita de su impacto en esta app. Registrar esa revisión en el PR/issue del backend; actualizar el cliente Android, los contratos y las pruebas si corresponde. No asumir que un cambio es inocuo porque compile en el backend.

Ver [docs/API_COMPATIBILITY.md](docs/API_COMPATIBILITY.md).

## Seguridad y validación pendiente

La auditoría estática inicial está en [docs/ANDROID_AUDIT.md](docs/ANDROID_AUDIT.md). El flujo de dieta consume `GET /api/portal/diet` y trata `404` como ausencia de una dieta activa. Antes de usar datos reales habrá que probar en dispositivo/emulador la autenticación por cookie, expiración/revocación, aislamiento entre pacientes/tenants y ausencia de datos clínicos en logs/notificaciones. La compilación de CI no sustituye esas pruebas dinámicas.
