# Auditoría de brechas funcionales de DietoExpress Android

Fecha: 2026-10-26. Revisión estática de la rama `main` y sus consumidores API. No sustituye la validación manual de videollamadas que hará el usuario ni las pruebas dinámicas de red/dispositivo.

## Resumen ejecutivo

La app ya no es un simple prototipo de login: implementa buena parte del portal del paciente, FCM, LiveKit y una primera arquitectura offline. **No faltan solo pruebas.** Hay flujos funcionales importantes que todavía no existen en la interfaz, y el modo offline necesita validación y completar algunos escenarios antes de considerarlo cerrado.

## Matriz de capacidades

| Área | Estado en código | Qué falta |
|---|---|---|
| Acceso y sesión | Implementado | Validar expiración, revocación, restauración tras reinicio y cambio de cuenta en dispositivo real. |
| Perfil | Consulta implementada | Edición de datos del paciente no incluida. |
| Dieta | Consulta implementada con caché cifrada | Revisar presentación de fecha/antigüedad y validar modo avión con datos reales de prueba. |
| Citas | Lista, disponibilidad y solicitud al nutricionista asignado | Cancelación de cita desde la app no implementada; reserva sigue siendo online por seguridad. |
| Directorio público | Búsqueda y flujo de reserva implementados | Validar reserva pública, errores de concurrencia/timeout y consentimiento en dispositivo real. |
| Documentos | Lista de documentos implementada | El endpoint `acceptDocument` existe en el cliente, pero no se expone en la UI. No cachear ni aceptar documentos como si fueran vigentes sin conexión. |
| Lista de la compra | Consulta implementada con caché | Confirmar avisos de alérgenos/datos desactualizados y comportamiento offline. |
| Mensajes | Lectura, borrador y envío implementados; outbox cifrada e idempotente | Validar en dispositivo pérdida de respuesta, muerte del proceso, reconexión y cambio de cuenta. |
| Notificaciones | FCM y navegación a mensajes/citas implementados | Verificar icono de barra de estado (PR #30), permiso denegado, duplicados y recepción en segundo plano en más de una versión Android. |
| Videoconsultas | Cliente LiveKit y política de ventana implementados | Pendiente de validación manual del usuario; no se considera bloqueada por el código, pero falta confirmación en dispositivo. |
| Entrenamiento | Consulta de sesiones implementada | Registrar/completar una sesión desde la app no implementado. |
| Seguimiento semanal | Consulta de elegibilidad, revisión actual e historial | No existe formulario Android para enviar una revisión semanal. |
| Evolución | Historial de revisiones disponible | No hay gráficas de peso/mediciones ni resumen de tendencia. |
| Funciones profesionales | Deliberadamente fuera de alcance | Mantener la web como interfaz principal del nutricionista; no priorizar agenda profesional nativa. |

## Offline-first: qué está hecho y qué no

### Implementado

- Room almacena caché y operaciones pendientes por ID de paciente.
- Caché, borradores, mensajes pendientes y cookie de sesión se cifran mediante AES-GCM con clave respaldada por Android Keystore.
- Las lecturas de perfil, dieta, citas, documentos, lista de la compra, mensajes, entrenamiento y seguimiento pueden recurrir a la última copia guardada cuando falla la conexión.
- El envío de mensajes persiste el payload cifrado y la clave idempotente antes de la petición; WorkManager puede reintentar la misma operación.
- El worker comprueba la cuenta activa antes de usar la cookie; los errores temporales se reintentan y los 409 se aparcan como conflicto.

### No está cerrado

- Las pruebas de persistencia de Room son instrumentadas: CI las compila, pero no arranca emulador para ejecutarlas. No se ha demostrado automáticamente el ciclo completo tras matar/reiniciar el proceso.
- Falta prueba dinámica de pérdida de respuesta después de que el backend haya guardado el mensaje, recuperación de red, expiración de sesión y cambio de cuenta.
- El check-in semanal no tiene formulario ni outbox. Aunque el endpoint admite idempotencia, el servidor asigna la semana al recibir la petición; no se debe encolar sin resolver qué hacer si la sincronización cruza el límite semanal.
- Reservas/cancelaciones, aceptación documental y acciones de entrenamiento no se deben encolar genéricamente: necesitan contratos idempotentes y reglas de conflicto por acción.
- La UI no ofrece una pantalla general para revisar, resolver o descartar operaciones fallidas/conflictivas.

Por tanto, la base offline existe, pero **P0 offline-first no debe darse por terminado solo porque compilen las pruebas**.

## Orden de trabajo recomendado

1. **Cerrar PR #30**: CI verde, después validar el icono en el teléfono. El cambio visual no requiere una prueba de emulador lenta.
2. **Cerrar P0 offline**: pruebas instrumentadas dirigidas y manuales en dispositivo para reinicio, pérdida de respuesta, reconexión y cambio de cuenta; mejorar la visibilidad/resolución de mensajes pendientes sin reintentar escrituras no idempotentes.
3. **Añadir revisión semanal nativa**: formulario breve con campos opcionales de detalle, validación y envío online. Diseñar aparte la sincronización offline para evitar asignar la revisión a una semana incorrecta.
4. **Añadir evolución visual**: gráficas de peso/mediciones basadas en datos reales del historial, con estados vacíos y fechas DD/MM/YYYY.
5. **Completar acciones del portal**: aceptación documental online con confirmación explícita, cancelación de citas con reglas del backend y registro de entrenamiento si existe endpoint de paciente autorizado.
6. **Cierre de publicación**: accesibilidad, privacidad, iconos/notificaciones, versión y requisitos de Google Play.

## Restricciones de seguridad

- No mostrar como confirmadas reservas, aceptaciones o envíos que solo estén guardados localmente.
- No ocultar 401/403 con caché clínica.
- No enviar una operación de una cuenta con la cookie de otra.
- No reintentar automáticamente una escritura hasta que la idempotencia y los conflictos estén definidos en backend.
- Cada cambio de endpoint del backend requiere revisar expresamente el impacto en `jl-hita/DietoExpress.Android`.
