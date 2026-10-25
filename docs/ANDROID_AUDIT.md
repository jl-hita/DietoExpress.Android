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
