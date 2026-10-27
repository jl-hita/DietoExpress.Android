# Auditoría de endpoints y política offline

Fecha: 2026-10-25. Revisión estática de los controladores ASP.NET Core de `jl-hita/DietoExpress` y de los consumidores Android. La auditoría describe los contratos encontrados en el repositorio; no afirma que el entorno desplegado esté disponible ni sustituye las pruebas dinámicas autenticadas.

## Principios

- PostgreSQL/backend sigue siendo la autoridad de permisos, estados, licencias, disponibilidad, asignaciones y reglas de negocio.
- El ID de paciente solo sirve para particionar caché local; nunca se envía para decidir autorización. El servidor deriva el paciente desde la cookie/claims.
- Solo se reintentan automáticamente escrituras con contrato de idempotencia documentado y una clave UUID persistida antes del primer envío.
- No se encolan reservas, cancelaciones, aceptación documental, check-ins ni acciones clínicas genéricas: pueden depender de estado actual, caducar o tener efectos secundarios no cubiertos por una clave idempotente.
- Los datos de salud cacheados se cifran con AES-GCM y clave Android Keystore. No se registran cuerpos, cookies, nombres, email, dieta ni notas clínicas.
- La UI debe marcar el contenido cacheado como potencialmente desactualizado. No debe presentar caché como confirmación de una cita, firma, lectura o sincronización.
- Si el servidor devuelve 401/403, se requiere autenticación; no se usa una cookie para una cola perteneciente a otra cuenta.

## Matriz de endpoints

| Endpoint | Uso Android / permisos backend | Sensibilidad | Offline / política |
|---|---|---|---|
| `POST /api/portal/auth` | Inicio de sesión de paciente; limita intentos y establece cookie de sesión HttpOnly | Alta: credenciales/cookie | Solo online. No cachear petición ni passcode/token. |
| `POST /api/portal/request-access-link` | Solicita enlace; respuesta no revela si el email existe | Alta: email | Solo online; nunca en cola automática. |
| `POST /api/portal/logout` | Revoca la sesión de paciente y limpia cookie | Alta: sesión | Online best-effort; limpiar sesión local siempre. |
| `GET /api/portal/profile` | Perfil del paciente autenticado | Alta: datos personales y antropometría | Caché cifrada por paciente; TTL 24 h; mostrar fecha/aviso de antigüedad. |
| `GET /api/portal/diet` | Dieta activa del paciente autenticado | Muy alta: información nutricional/salud | Caché cifrada por paciente; TTL 24 h. Nunca interpretar caché como garantía de vigencia; el backend puede haber sustituido la dieta. Un 404 online elimina la caché de dieta. |
| `GET /api/appointments/mine` | Citas del paciente autenticado | Alta: actividad sanitaria y horarios | Caché cifrada; TTL 15 min. Solo lectura offline; no inferir estados ni crear/cancelar citas sin servidor. |
| `GET /api/portal/documents` | Documentos asignados al paciente | Muy alta: documentos y consentimientos | Caché cifrada de metadatos, TTL 24 h. No guardar archivos descargados en esta caché genérica. |
| `GET /api/portal/shopping-list` | Lista derivada del plan alimentario | Alta: preferencias y dieta | Caché cifrada; TTL 24 h. Etiquetar como última versión guardada. |
| `GET /api/messages/patient` | Mensajes de la sesión de paciente | Muy alta: comunicaciones privadas | Caché cifrada; TTL 5 min. Nunca incluir cuerpo en logs/notificaciones. |
| `POST /api/messages/patient` | Enviar mensaje del paciente | Muy alta: contenido libre | Permitido en outbox cifrada. UUID persistido como `Idempotency-Key`; mismo cuerpo/clave en todos los reintentos. Retener tras timeout; no crear otra operación al reintentar. |
| `PATCH /api/messages/patient/read` | Marca mensajes como leídos | Alta: metadatos de comunicación | No programar automáticamente desde una lectura offline ni desde una vista parcial: el endpoint actual puede marcar como leídos mensajes que el paciente aún no ha visto. Solo invocar online cuando la semántica de lectura esté verificada. |
| `GET /api/portal/training/sessions` | Sesiones de entrenamiento del paciente | Muy alta: actividad/salud | Caché cifrada; TTL 24 h. Offline solo consulta, no se inventa progreso completado. |
| `POST /api/portal/training/sessions/{sessionId}/record` | Registro online de resultado, duración, RPE, recuperación, molestias y notas por sesión | Muy alta: actividad/salud y posibles señales de dolor | Solo online. No se encola en outbox: antes hay que validar idempotencia, reintentos tras timeout y resolución de conflictos. El servidor actualiza el registro único de sesión/paciente. |
| `GET /api/portal/check-ins/eligibility` | Determina si el paciente puede enviar revisión | Alta: progreso sanitario | Caché cifrada; TTL 1 h, solo informativa. No usarla para autorizar un envío. |
| `GET /api/portal/check-ins/current` | Revisión actual | Muy alta: peso, adherencia, sueño, energía, notas | Caché cifrada; TTL 1 h. |
| `GET /api/portal/check-ins` | Historial de revisiones | Muy alta: histórico sanitario | Caché cifrada; TTL 1 h. |
| `POST /api/portal/check-ins` | Envío de revisión; el backend calcula la semana al recibirla | Muy alta | No encolar offline aún. Antes hay que fijar la semana pretendida y resolver envíos que cruzan el límite semanal, aunque el backend admita Idempotency-Key. |
| `POST /api/portal/documents/{id}/accept` | Aceptación de documento/consentimiento | Muy alta: acción legal | Solo online; exige contenido vigente y confirmación del usuario. No reintentar automáticamente. |
| `GET /api/directory/professionals` | Directorio público; AllowAnonymous; filtros city/province/online/speciality/goal/availableOnly | Baja/moderada: perfiles profesionales públicos | Puede cachearse en una fase posterior con TTL corto (p. ej. 15 min); en esta implementación la búsqueda requiere red. Nunca guardar datos de pacientes. |
| `GET /api/directory/professionals/{slug}` | Perfil público del profesional | Baja: datos profesionales publicados | Lectura offline viable con TTL corto; disponibilidad de licencia puede cambiar. |
| `GET /api/directory/professionals/{slug}/availability?days=N` | Horarios libres calculados con agenda y calendario externo | Moderada; información operativa | No usar caché para reservar ni mostrarla como disponibilidad actual. Solo referencia offline, con aviso claro. |
| `POST /api/directory/professionals/{slug}/appointments` | Reserva pública anónima; el servidor valida licencia, horario, solapamientos, modalidad y límites | Alta: identidad/contacto y cita | Solo online. No encolar ni reintentar automáticamente sin contrato idempotente: un timeout puede ocultar una reserva creada y un slot puede ocuparse. Ante timeout, consultar al usuario y refrescar disponibilidad. |
| `POST /api/directory/professionals/{slug}/interest` | Solicitud pública de interés cuando no se puede reservar; exige consentimiento explícito | Alta: nombre/email/teléfono y consentimiento | Solo online; no encolar sin idempotencia y consentimiento persistido inequívoco. |
| `GET /api/directory/professionals/{slug}/reviews` | Reseñas públicas | Baja/moderada | Lectura cacheable en una fase posterior; no es necesaria para el flujo offline clínico. |
| `POST /api/portal/reviews` | Valoración del profesional por paciente autenticado y cita completada; idempotencia backend | Moderada: opinión vinculada a consulta | No se ofrece offline aún: comprobar elegibilidad/duplicado y conservar confirmación del usuario. Puede añadirse tras una operación de outbox dedicada y pruebas de contrato. |

## Otros candidatos detectados en el backend

- `GET /api/appointments/professional`, slots profesionales, actualización de estado, cancelaciones y reenvío de correo: requieren rol profesional y validación de tenant. No forman parte de la sesión de paciente ni se deben reutilizar en su cola.
- Vídeo (`/api/appointments/{id}/video-access`, eventos y finalización): requiere cita, sesión correcta y ventana temporal del servidor. No cachear credenciales ni crear accesos offline.
- Endpoints de dietas, perfil clínico, documentos firmados, recetas y entrenamiento profesional: escritura clínica con concurrencia y permisos específicos; no se admite cola genérica hasta documentar idempotencia, resolución de conflicto y auditoría por endpoint.

## Retención, actualización e invalidación

- Perfil: 24 h; dieta: 24 h; citas: 15 min; documentos: 24 h; lista de compra: 24 h; mensajes: 5 min; sesiones de entrenamiento: 24 h; revisiones: 1 h.
- Una respuesta exitosa reemplaza atómicamente el payload cacheado y su marca temporal. Los datos vencidos no se presentan como frescos; pueden mostrarse como última copia solo con aviso explícito.
- Un 404 autoritativo para la dieta elimina la dieta cacheada; logout elimina la caché del paciente activo. La outbox pendiente se conserva aislada por cuenta para no perder mensajes aceptados localmente, pero no se envía sin sesión de esa misma cuenta.
- Un 401/403 aparca la operación como `NEEDS_AUTH`; un 409 queda en `CONFLICT` para intervención explícita, nunca en bucle de reintentos. Errores 429/5xx y fallos de red usan backoff exponencial limitado; errores de validación son definitivos.
- La limpieza de caché vencida se ejecuta antes de lecturas/escrituras de caché y durante la sincronización. La retención local no sustituye las obligaciones de privacidad ni el borrado de datos del servidor.

## Validación pendiente

La CI compila y ejecuta tests unitarios; no arranca emulador por defecto para evitar esperas de arranque. Las pruebas instrumentadas de Room deben comprobar reapertura tras cierre, aislamiento entre cuentas, migración 1→2 y outbox durable. La prueba de worker usa MockWebServer para pérdida de respuesta, 401/403, 409, 429/5xx, timeout y reintentos. Una prueba contra PostgreSQL es necesaria para demostrar idempotencia del servidor bajo concurrencia; no se afirma que la prueba HTTP Android la sustituya.
