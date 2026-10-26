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
| Citas | Lista, disponibilidad, reserva y cancelación de citas futuras | Reprogramación no implementada; validar reserva/cancelación en dispositivo. |
| Directorio público | Búsqueda y flujo de reserva implementados | Validar reserva pública, errores de concurrencia/timeout y consentimiento en dispositivo real. |
| Documentos | Lista, descarga PDF, apertura externa y aceptación tras visualización | UX de visor integrado no implementada; validar descarga y aceptación en dispositivo. |
| Lista de la compra | Consulta implementada con caché | Confirmar avisos de alérgenos/datos desactualizados y comportamiento offline. |
| Mensajes | Lectura, borrador y envío implementados; outbox cifrada e idempotente | Validar en dispositivo pérdida de respuesta, muerte del proceso, reconexión y cambio de cuenta. |
| Notificaciones | FCM y navegación a mensajes/citas implementados | Verificar icono de barra de estado (PR #30), permiso denegado, duplicados y recepción en segundo plano en más de una versión Android. |
| Videoconsultas | Cliente LiveKit y política de ventana implementados | Pendiente de validación manual del usuario; no se considera bloqueada por el código, pero falta confirmación en dispositivo. |
| Entrenamiento | Consulta y registro de sesión completada | Validar en dispositivo real el registro de duración real, RPE y notas. |
| Seguimiento semanal | Formulario, validación, borrador cifrado y clave idempotente persistida | Validar envío, pérdida de respuesta y límites semanales en dispositivo. |
| Evolución | Gráfica de peso basada en revisiones semanales | Faltan gráficas de biometrías fuera del check-in. |
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
- El formulario semanal tiene borrador cifrado y clave idempotente persistida. No se encola para envío automático: el backend asigna la semana al recibir la petición y un borrador podría cruzar el límite semanal.
- Reservas/cancelaciones, aceptación documental y acciones de entrenamiento no se deben encolar genéricamente: necesitan contratos idempotentes y reglas de conflicto por acción.
- El perfil permite revisar operaciones pendientes/fallidas/en conflicto, programar la sincronización y descartar operaciones terminales con confirmación.

Por tanto, la base offline existe, pero **P0 offline-first no debe darse por terminado solo porque compilen las pruebas**.

## Orden de trabajo recomendado

1. Validar manualmente iconos y notificaciones en el teléfono; el acabado visual ya está integrado.
2. **Cerrar P0 offline**: ejecutar el checklist manual de reinicio, pérdida de respuesta, reconexión y cambio de cuenta; la CI compila las pruebas instrumentadas, pero no ejecuta emulador.
3. Validar el formulario de revisión semanal y el reintento seguro con pérdida de respuesta.
4. Añadir evolución de biometrías distintas del peso registrado en el check-in.
5. Valorar edición de perfil y reprogramación de citas si el backend lo soporta; el registro de entrenamiento ya admite duración real/RPE/notas.
6. Cierre de publicación: accesibilidad, privacidad, soporte y requisitos de Google Play.

## Checklist manual para cerrar P0 offline

1. Inicia sesión con una cuenta de prueba y carga dieta, citas, documentos, mensajes y seguimiento.
2. Activa modo avión y confirma que las lecturas muestran caché con advertencias; guarda un borrador semanal y cierra/abre la app para comprobar que persiste.
3. Crea un mensaje pendiente, fuerza el cierre de la app, recupera la conexión y comprueba que llega una sola vez.
4. Simula una respuesta perdida tras guardar el mensaje en el servidor y comprueba que el reintento usa la misma clave idempotente.
5. Cambia a otra cuenta en el mismo dispositivo y confirma que ninguna operación anterior se envía con la sesión nueva.
6. Comprueba que un conflicto queda visible y no se reintenta automáticamente; descártalo solo tras leer la advertencia.
7. Prueba la revisión semanal cerca del cambio de semana UTC: el borrador no debe enviarse automáticamente en una semana posterior.

## Restricciones de seguridad

- No mostrar como confirmadas reservas, aceptaciones o envíos que solo estén guardados localmente.
- No ocultar 401/403 con caché clínica.
- No enviar una operación de una cuenta con la cookie de otra.
- No reintentar automáticamente una escritura hasta que la idempotencia y los conflictos estén definidos en backend.
- Cada cambio de endpoint del backend requiere revisar expresamente el impacto en `jl-hita/DietoExpress.Android`.
