# DietoExpress Android

Aplicación nativa para el **portal del paciente** de DietoExpress, desarrollada con Kotlin y Jetpack Compose. El backend ASP.NET Core sigue siendo la autoridad para identidad, permisos, citas y reglas clínicas; la interfaz profesional avanzada continúa en la web.

## Estado actual

La app está en desarrollo y **todavía no es una versión publicable**.

### Funciones ya implementadas en código

- Acceso de paciente por email/teléfono y código, solicitud de enlace de acceso y cierre de sesión.
- Sesión por cookie protegida; la cookie se conserva cifrada con Android Keystore para restaurar sesión entre reinicios.
- Perfil de paciente y dieta activa, con caché cifrada para consulta sin conexión.
- Consulta de citas y disponibilidad, solicitud de cita con el nutricionista asignado y búsqueda pública de profesionales con flujo de reserva.
- Consulta de documentos asignados y lista de la compra; descarga/lectura de PDF, aceptación documental explícita, registro de entrenamientos y revisiones semanales.
- Mensajería con borradores cifrados, outbox persistente y reintentos con clave `Idempotency-Key`.
- Notificaciones push FCM con navegación a mensajes/citas y videoconsultas nativas con LiveKit.
- Caché Room cifrada por cuenta, reintentos con WorkManager y aislamiento de las operaciones pendientes entre cuentas.

La lista anterior describe funcionalidad presente en el código; no significa que cada flujo se haya validado en dispositivos reales.

## Funciones pendientes o incompletas

- Formulario de revisión semanal con validación, borrador local cifrado y reintento idempotente seguro si se pierde la respuesta.
- Gráfica de evolución de peso a partir de las revisiones semanales.
- Edición del perfil y visor PDF integrado (la app usa el visor PDF externo).
- Reprogramación de citas. Validar en dispositivo real el registro de entrenamiento con duración real, esfuerzo percibido y notas.
- Validación dinámica del ciclo offline: recuperación tras reinicio, pérdida de respuesta después del commit, cambio de cuenta y errores de sincronización. El perfil ya ofrece un panel para revisar operaciones pendientes y conflictos.
- Validación manual en dispositivo real de iconos, notificaciones en segundo plano, videoconsultas, permisos y accesibilidad.
- Revisión de privacidad, requisitos de Google Play, enlaces de soporte y preparación de publicación.

La revisión semanal se guarda como borrador offline, pero no se envía automáticamente: el backend asigna la semana al recibir la petición. Las escrituras sin contrato seguro siguen siendo online. Ver [la auditoría de brechas](docs/ANDROID_GAP_AUDIT.md) y la política offline en [docs/OFFLINE_ENDPOINT_AUDIT.md](docs/OFFLINE_ENDPOINT_AUDIT.md).

## Requisitos y compilación

- Android Studio compatible con Android Gradle Plugin 9.2.1.
- JDK 17.
- SDK Android 36.
- Gradle Wrapper incluido.

Windows (PowerShell):

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest
.\gradlew.bat --no-daemon :app:assembleDebug
```

Linux/macOS:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Configuración local

Las propiedades Firebase se guardan fuera del repositorio, en `%USERPROFILE%\.gradle\gradle.properties` (Windows) o `~/.gradle/gradle.properties`:

```properties
dietoexpressFirebaseApiKey=...
dietoexpressFirebaseApplicationId=...
dietoexpressFirebaseProjectId=...
dietoexpressFirebaseSenderId=...
```

Para el servidor API se puede usar `dietoexpressApiBaseUrl`; por defecto se apunta al entorno HTTPS configurado en el proyecto. No subir credenciales, contraseñas, passcodes, cookies, keystores ni archivos de configuración secretos al repositorio.

La firma estable de APK se configura mediante las variables de entorno `ANDROID_SIGNING_KEYSTORE_PATH`, `ANDROID_SIGNING_KEYSTORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS` y `ANDROID_SIGNING_KEY_PASSWORD`. Para CI, los secretos correspondientes se configuran en GitHub Actions. No generar builds de actualización con una clave diferente de la usada para la instalación existente.

## Compatibilidad obligatoria con el backend

Cada PR que cambie endpoints, DTO, cookies, permisos, estados HTTP, formatos de fecha o reglas de negocio debe revisar el impacto en Android y registrar la conclusión. Ver [docs/API_COMPATIBILITY.md](docs/API_COMPATIBILITY.md).
