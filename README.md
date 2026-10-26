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

### Funciones pendientes o incompletas
- Edición del perfil y visor PDF integrado (la app usa el visor PDF externo).
- Reprogramación de citas.
- Validación dinámica del ciclo offline: recuperación tras reinicio, pérdida de respuesta después del commit, cambio de cuenta y errores de sincronización.
- Revisar el despliegue del endpoint backend para las gráficas biométricas y comprobarlo con mediciones suficientes.
- Revisión final de privacidad, accesibilidad, soporte y requisitos de Google Play.

Las escrituras sin contrato seguro siguen siendo online. Ver [la auditoría de brechas](docs/ANDROID_GAP_AUDIT.md) y la política offline en [docs/OFFLINE_ENDPOINT_AUDIT.md](docs/OFFLINE_ENDPOINT_AUDIT.md).

## Documentación de configuración y publicación
- [Configuración, Firebase y firma](docs/ANDROID_SETUP.md).
- [Guía para publicar en Google Play y crear la prueba cerrada](docs/GOOGLE_PLAY_CLOSED_TEST.md).

## Requisitos y compilación
- Android Studio compatible con Android Gradle Plugin 9.2.1.
- JDK 17.
- SDK Android 36.
- Gradle Wrapper incluido.

Firebase ya está configurado en el `gradle.properties` versionado del proyecto. Para una compilación normal no necesitas copiar esos cuatro valores ni crear secretos Firebase en GitHub. Puedes clonar el repositorio y compilar directamente.

Windows (PowerShell):
```powershell
.\\gradlew.bat --no-daemon :app:testDebugUnitTest
.\\gradlew.bat --no-daemon :app:assembleDebug
```

Linux/macOS:
```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Configuración local opcional
No es necesario configurar Firebase en el equipo: los valores de cliente están en el `gradle.properties` versionado. Si necesitas apuntar a otra instalación del backend o probar otro proyecto Firebase, puedes sobrescribir propiedades desde el archivo personal de Gradle: `%USERPROFILE%\\.gradle\\gradle.properties` (Windows) o `~/.gradle/gradle.properties` (Linux/macOS). Las claves son `dietoexpressApiBaseUrl`, `dietoexpressFirebaseApiKey`, `dietoexpressFirebaseApplicationId`, `dietoexpressFirebaseProjectId` y `dietoexpressFirebaseSenderId`.

Los cuatro valores Firebase identifican la aplicación cliente; no son credenciales administrativas. Aun así, restringe la API key a las APIs y aplicaciones necesarias cuando sea posible. **Nunca** guardes en el repositorio credenciales de servidor, service-account JSON, contraseñas, tokens, cookies ni el keystore de firma.

Para generar el AAB de Google Play, configura las cuatro propiedades `androidSigning*` en tu archivo personal de Gradle siguiendo [la guía de firma y publicación](docs/ANDROID_SETUP.md). El AAB se genera desde Android Studio en tu PC; GitHub Actions no recibe la clave ni genera paquetes de publicación.

## Compatibilidad obligatoria con el backend
Cada PR que cambie endpoints, DTO, cookies, permisos, estados HTTP, formatos de fecha o reglas de negocio debe revisar el impacto en Android y registrar la conclusión. Ver [docs/API_COMPATIBILITY.md](docs/API_COMPATIBILITY.md).
