# Auditoría técnica inicial de la app Android

Fecha: 2026-10-25. Revisión estática del backend de DietoExpress; no sustituye a compilación, pruebas de API ni auditoría dinámica.

## Resultado

El backend existente permite construir una app nativa sin duplicar reglas de negocio. La primera fase debe centrarse en el portal del cliente; la agenda profesional vendrá después.

## Endpoints y capacidades localizados

### Portal de paciente

- `POST /api/portal/auth`: acepta token o email/teléfono + passcode.
- `POST /api/portal/request-access-link`: solicita un enlace de acceso.
- `POST /api/portal/logout`: revoca sesiones anteriores incrementando `portal_token_version` y borra la cookie.
- `GET /api/portal/profile`, `/diet` y `/shopping-list`.
- `GET /api/appointments/mine`, `GET /api/appointments/slots`.
- `POST /api/appointments` y `POST /api/appointments/{id}/cancel`.
- `GET /api/appointments/{id}/video-access`, eventos de vídeo y finalización.
- El servicio Angular también referencia documentos, seguimiento, tareas, mensajería y otras funciones; verificar cada contrato antes de integrarlo en Android.

### Agenda profesional

El servicio web referencia `GET /api/appointments/professional`, disponibilidad, actualización de estado, reenvío de email y endpoints de acciones profesionales. Verificar autorización por rol/tenant y reglas de concurrencia antes de reutilizarlos.

## Hallazgos y decisiones

### Sesión del portal basada en cookie

El login no devuelve el JWT en el cuerpo: devuelve datos mínimos del paciente y establece la cookie `dietoexpress_patient_session`, configurada HttpOnly, Secure, SameSite=Strict y Path=/, con caducidad.

**Decisión:** usar un cliente HTTP nativo con gestión de cookies; no copiar el JWT a almacenamiento accesible por UI ni almacenar el passcode. Si se persisten cookies, protegerlas con cifrado respaldado por Android Keystore y no registrarlas en logs.

### Notificaciones

La revisión estática encontró Web Push, pero no integración FCM nativa ni registro de tokens de dispositivo.

**Decisión:** FCM es una integración nueva. Diseñar registro/actualización/revocación del token por sesión y dispositivo. No incluir información clínica en notificaciones visibles.

### Autorización y aislamiento

Los endpoints del portal usan el rol `patient` y derivan el ID del paciente de los claims. Los endpoints profesionales deben conservar las políticas existentes. La UI móvil nunca es un límite de seguridad.

**Pruebas obligatorias:** paciente accediendo a datos ajenos; profesional accediendo a otro tenant; llamadas sin sesión; sesión caducada; logout seguido de reutilización de cookie; citas no autorizadas y vídeo fuera de ventana.

### Vídeo

El backend expone acceso a vídeo, eventos y finalización. La app debe respetar autorización y ventana del servidor, sin crear credenciales de reunión por su cuenta.

### Datos de salud y publicación

Antes de producción: política de privacidad pública y accesible, declaraciones de Google Play aplicables, clasificación de contenido, soporte y pruebas de revisión con cuentas no productivas. No incluir datos clínicos en analytics, logs o notificaciones.

## Arquitectura propuesta

- Kotlin + Jetpack Compose.
- UI/ViewModel, repositorios y cliente HTTP separados.
- Base URL configurable por build; producción solo HTTPS.
- Sesión de cookie gestionada por cliente HTTP y cierre remoto.
- Backend ASP.NET Core como autoridad de permisos, tenant y reglas.
- FCM tras implementar registro backend.
- Deep links validados después de comprobar sesión y autorización.
- Angular conserva administración avanzada y edición compleja.

## Orden de implementación

1. Base Android compilable y navegación inicial.
2. Cliente HTTP y sesión, con pruebas de cookie y logout.
3. Login/enlace mágico y perfil.
4. Dieta y citas del cliente.
5. Pruebas de autorización y errores de red.
6. Agenda profesional.
7. FCM, enlaces profundos y videollamada.
8. Pruebas de dispositivo, accesibilidad, privacidad y publicación.

## Límites

No se han ejecutado tests, build Android ni pruebas de API o seguridad dinámica. Verificar los endpoints contra el servidor de pruebas antes de dar por cerrada la auditoría.


## P0 offline-first — primera implementación

- Room almacena caché por ID de paciente y una outbox persistente.
- Los payloads de caché, borradores y mensajes pendientes se cifran con AES-GCM y una clave no exportable de Android Keystore.
- Perfil, dieta, citas, documentos, lista de la compra, mensajes, entrenamiento y revisiones pueden mostrarse desde caché cuando falla la conexión; la UI advierte de que son datos posiblemente desactualizados.
- WorkManager y la outbox persistente proporcionan la base para reintentos de operaciones idempotentes; no se encolan recibos de lectura de mensajes porque el endpoint actual puede marcar como leídos mensajes que el paciente aún no ha visto.
- Los borradores de mensajes sobreviven a la navegación y a la muerte del proceso. El envío se persiste primero en la outbox cifrada de Room con un UUID estable, reutilizado como `Idempotency-Key` en el envío inmediato y en todos los reintentos de WorkManager. Si se pierde la respuesta, el mismo mensaje no se duplica.
- La cookie de sesión se persiste cifrada con AES-GCM y Android Keystore; no se guardan contraseñas ni códigos de acceso. El ID de cuenta activa permite restaurar el perfil cacheado al reabrir la app. El backend puede invalidar la sesión y un 401 obliga a autenticarse de nuevo.
- Al cerrar sesión se elimina la caché de ese paciente y se borra el ID de cuenta activa. El worker comprueba que la cuenta activa coincide con la propietaria de la operación antes de usar la cookie compartida; si no coincide, no la envía.

### Validación pendiente
CI de compilación y pruebas en dispositivo/emulador para modo avión, cierre forzado, reinicio, sesión caducada, recuperación de conectividad y errores de sincronización. Esta implementación inicial no equivale aún a una cola offline para todas las escrituras clínicas futuras ni incluye resolución interactiva de conflictos.


## Idempotencia Android — mensajes (octubre de 2026)

- `POST /api/messages/patient` conserva ruta y cuerpo `{ body }`; Android añade `Idempotency-Key` sin cambiar el contrato funcional.
- El mensaje se guarda primero en Room con el cuerpo cifrado y un UUID durable. La operación solo se elimina tras recibir éxito; los timeouts conservan la misma clave para el reintento.
- Al volver a abrir la bandeja se muestran los mensajes pendientes o fallidos. Los errores temporales se reintentan; 401/403 requieren autenticación.
- `POST /api/portal/check-ins` ya admite idempotencia en backend, pero la app aún no implementa el formulario de escritura. No se añade una cola offline para ese endpoint porque el servidor calcula la semana de revisión al recibir la petición y una cola que cruce el límite semanal podría guardar los datos en la semana equivocada. La integración segura requiere definir primero cómo fijar la semana pretendida.
