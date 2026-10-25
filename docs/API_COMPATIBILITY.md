# Revisión obligatoria de compatibilidad de la API con Android

## Regla

Cada cambio del backend de DietoExpress que toque un endpoint o su comportamiento debe evaluar explícitamente el impacto en la app nativa `jl-hita/DietoExpress.Android`.

Esto incluye, como mínimo:

- Añadir, cambiar rutas, verbos o eliminar endpoints.
- Cambiar request/response DTO, campos requeridos, nombres, tipos, nullabilidad o formatos de fecha.
- Cambiar autenticación, cookie de sesión, expiración, logout, claims o códigos HTTP.
- Cambiar autorización por rol/tenant, reglas de acceso a pacientes/citas/documentos/vídeo.
- Cambiar validaciones, paginación, orden, estados, límites, efectos secundarios o reglas de negocio.
- Cambiar configuración CORS/HTTPS/cookies, upload/download, enlaces profundos o notificaciones.

## Checklist para PRs del backend

- [ ] He identificado los endpoints y contratos afectados.
- [ ] He revisado el uso de esos contratos en `DietoExpress.Android` (búsqueda de ruta, DTO y flujo funcional).
- [ ] He marcado explícitamente una de estas opciones en la descripción del PR: **sin impacto en Android** (con motivo) o **requiere cambios en Android** (con enlace al PR/issue correspondiente).
- [ ] He actualizado pruebas de contrato/integración o explicado por qué no son necesarias.
- [ ] Si el cambio es incompatible, he coordinado el orden de despliegue y la compatibilidad con versiones publicadas.
- [ ] He comprobado que no se filtran datos sensibles en respuestas, logs ni notificaciones.

## Checklist para PRs Android

- [ ] El cliente usa el contrato real del backend; no se han inventado campos ni rutas.
- [ ] Se cubren estados de éxito, error, sesión expirada y respuestas incompletas.
- [ ] Los permisos se verifican en el servidor; la navegación/visibilidad de UI no se considera seguridad.
- [ ] Los cambios de contrato tienen pruebas relevantes.

## Proceso recomendado

1. Localizar el endpoint/controlador y DTO en el backend.
2. Localizar sus consumidores web y Android.
3. Actualizar el contrato y las pruebas en el mismo cambio cuando sea viable; si son repos separados, enlazar ambos PRs.
4. No desplegar un cambio incompatible antes de que la app publicada pueda tolerarlo, salvo que se haya acordado una migración coordinada.
5. Registrar explícitamente la revisión incluso cuando la conclusión sea “sin impacto”.

## Revisión de idempotencia (octubre de 2026)

- `PATCH /api/messages/patient/read` conserva ruta y cuerpo y admite `Idempotency-Key`.
- `POST /api/messages/patient` conserva ruta y cuerpo y ahora recibe una clave idempotente en Android. La operación se guarda cifrada en Room antes de llamar al servidor; el mismo UUID se reutiliza en el envío inmediato y en WorkManager tras timeout o pérdida de respuesta.
- El worker elimina una operación solo cuando el endpoint confirma éxito. Los errores temporales se reintentan; 401/403 dejan la operación pendiente de autenticación y los errores definitivos quedan marcados como fallidos.
- El worker verifica que el ID de la cuenta activa coincide con la cuenta propietaria de la operación antes de usar la cookie de sesión compartida. No envía una cola de una cuenta con la sesión de otra.
- El backend también protege `POST /api/portal/check-ins`, pero Android todavía no expone su formulario de envío. No se encola el check-in offline porque el servidor asigna la semana en el momento de recepción; antes de habilitarlo hay que definir una semántica segura para operaciones que crucen el límite semanal.
- La clave de lectura y la de mensaje solo son efectivas si la versión del backend con `[Idempotent]` está desplegada. El encabezado sigue siendo opcional y compatible con el contrato anterior.


### Pruebas automatizadas de idempotencia

- `PortalApiIdempotencyTest` verifica mediante MockWebServer que dos intentos de `POST /api/messages/patient` envían la misma cabecera `Idempotency-Key` y exactamente el mismo cuerpo.
- Esta prueba valida el contrato HTTP del cliente, no la persistencia de Room ni el comportamiento de WorkManager tras matar el proceso.
- En el backend, `[Idempotent]` activa `IdempotencyMiddleware`: la clave se bloquea transaccionalmente en PostgreSQL, se guarda la huella de la petición y la respuesta 2xx, se reproduce la respuesta en reintentos y se devuelve 409 si se reutiliza la clave con otro cuerpo.
- Sigue pendiente la prueba dinámica en emulador/dispositivo y una prueba de integración contra PostgreSQL para timeout tras commit, reinicio del proceso, concurrencia y cambio de cuenta. No se considera cerrada la prioridad P0 hasta validar esos escenarios.


### Aislamiento de sesión y cancelación de sincronización

- Los cambios de sesión (inicio y cierre) y cada envío de WorkManager comparten un bloqueo de sesión. El worker comprueba dentro de ese bloqueo que la cuenta activa sigue siendo la propietaria de la operación.
- Las escrituras de mensajes y el marcado de lectura se guardan cifrados en Room antes del envío; la misma clave persistida se reutiliza tras timeout o pérdida de respuesta.
- La cancelación de coroutines se propaga sin convertir operaciones pendientes en fallos definitivos. La operación sigue en Room para que WorkManager pueda reintentarla.
- La prueba unitaria de SessionOperationLock verifica la exclusión mutua; siguen pendientes pruebas instrumentadas de reinicio, cambio de cuenta y pérdida de respuesta contra el backend desplegado.


## Auditoría offline-first (octubre de 2026)

La clasificación de rutas, permisos, sensibilidad, retención y viabilidad offline está en [`OFFLINE_ENDPOINT_AUDIT.md`](OFFLINE_ENDPOINT_AUDIT.md). La regla central es no encolar escrituras cuyo contrato no permita reintentos seguros: mensajes del paciente reutilizan un UUID persistido como `Idempotency-Key`; las reservas públicas, cancelaciones, check-ins y aceptaciones documentales siguen siendo solo online.

La caché cifrada aplica TTL por familia, conserva snapshots vencidos durante un máximo de 30 días para consulta offline claramente señalada, y se invalida en logout o cuando una respuesta online confirma que ya no existe la dieta. Los 401/403 no se ocultan con caché: se limpia el estado de sesión local y se exige autenticación. Las operaciones con HTTP 409 quedan en estado persistente `CONFLICT`, fuera de la cola de reintentos automáticos.
