# Materiales para la ficha de Google Play — DietoExpress

Borrador de trabajo para la primera prueba cerrada. Confirmar las funciones visibles en el AAB y completar los datos reales antes de enviar a revisión.

## 1. Nombre de la aplicación
DietoExpress

## 2. Descripción breve (máximo 80 caracteres)
Tu dieta, citas y seguimiento nutricional, en contacto con tu profesional.

## 3. Descripción completa
DietoExpress te permite consultar desde tu móvil la información de seguimiento nutricional que tu profesional pone a tu disposición.

Con la aplicación puedes:
- Consultar tu dieta asignada y las comidas del plan.
- Revisar tus citas y solicitar una cita con un profesional.
- Consultar documentos y listas de la compra vinculados a tu plan.
- Comunicarte con tu profesional mediante mensajes.
- Registrar información de seguimiento y entrenamientos cuando estas funciones estén disponibles en tu cuenta.
- Recibir notificaciones sobre mensajes y citas si las activas.
- Acceder a videoconsultas cuando tu profesional las habilite.

Las funciones disponibles dependen de tu cuenta y de los servicios activados por tu profesional.

DietoExpress es una herramienta de comunicación y seguimiento nutricional. No sustituye la valoración individual de un profesional sanitario ni debe utilizarse para emergencias médicas.

## 4. Icono
Usar el icono de lanzamiento existente en el proyecto Android, revisándolo sobre fondo claro y oscuro. Para Google Play se necesita un icono de tienda de 512 × 512 px en PNG de 32 bits, sin esquinas redondeadas añadidas manualmente ni etiquetas promocionales. No usar el icono adaptativo de Android sin exportarlo al formato requerido por Play Console.

## 5. Capturas de pantalla
Preparar al menos 4 capturas reales de la app, sin datos de pacientes reales:
1. Inicio / resumen del portal.
2. Mi dieta y comidas.
3. Citas.
4. Perfil o seguimiento.
Opcionales si están estables: mensajes y videoconsulta. Utilizar una cuenta de prueba con nombre, correo, medidas y dieta ficticios; revisar que no aparezcan notificaciones ni datos privados en la barra de estado. No crear capturas que representen funciones que no están disponibles en el AAB.

## 6. Categoría y contacto
- Categoría sugerida: Salud y bienestar (confirmar las opciones disponibles en Play Console).
- Email de soporte: [INTRODUCIR EMAIL REAL Y SUPERVISADO].
- Política de privacidad: publicar `docs/privacy-policy.html` en GitHub Pages solo después de sustituir todos los campos legales entre corchetes y validar el contenido. URL prevista: `https://jl-hita.github.io/DietoExpress.Android/privacy-policy.html` (confirmar que Pages está habilitado y la URL abre públicamente).

## 7. Acceso para el equipo de revisión
Seleccionar que algunas funciones están restringidas por inicio de sesión. Proporcionar una cuenta de paciente de prueba creada expresamente para la revisión, con datos ficticios, acceso estable y permisos suficientes para probar dieta, citas, documentos y mensajería. No enviar credenciales de administrador, de un paciente real ni secretos del keystore.

En instrucciones, indicar:
1. Abrir la app.
2. Introducir el email o teléfono de la cuenta de prueba.
3. Completar el acceso mediante el código/enlace que se haya preparado para el revisor.
4. Para probar una videoconsulta, explicar cómo acceder a una cita de demostración si se incluye en esta versión.

Antes de enviar, comprobar que el método de acceso del revisor no depende de recibir un SMS/email inaccesible para Google. Si el flujo exige un código de un solo uso, prepara un método de prueba compatible con las instrucciones de Play Console y mantenlo activo durante la revisión.

## 8. Seguridad de los datos: preparación del formulario
Esta lista es una guía preliminar basada en el código y las dependencias identificadas. No marcar las respuestas definitivas hasta verificar el comportamiento real del AAB, las configuraciones del backend y los proveedores.

Tipos que probablemente deban declararse:
- Información personal: nombre, correo y teléfono, si se transmiten/almacenan en el servicio.
- Información de salud y actividad física: dieta, preferencias/restricciones, medidas y seguimiento nutricional, entrenamientos.
- Mensajes y otra información generada por el usuario: comunicaciones, revisiones, solicitudes de cita.
- Actividad de la app y datos técnicos necesarios: uso de funciones, identificadores de cuenta, token de notificaciones push.
- Audio y vídeo: solo si la app o el SDK procesa/transmite esos datos durante videoconsultas; declarar conforme a la definición exacta del formulario.

Finalidades previsibles: funcionalidad de la app, gestión de cuenta, comunicaciones, citas, soporte y seguridad. La cámara y el micrófono se utilizan para videoconsultas. Revisar por separado qué recopilan Firebase Cloud Messaging, LiveKit y el backend. No declarar que los datos no se comparten: hay proveedores que procesan datos para prestar funciones.

Seguridad:
- Tráfico de la app al backend mediante HTTPS.
- Determinados datos de sesión y caché se almacenan cifrados en el dispositivo.
No afirmar que todos los datos están cifrados de extremo a extremo ni que se eliminan automáticamente si no se ha verificado.

## 9. Público objetivo y clasificación de contenido
- No seleccionar grupos de edad infantiles por defecto. Seleccionar 18+ solo si el producto y el servicio realmente están dirigidos exclusivamente a adultos; si profesionales usan DietoExpress con pacientes menores, revisar la selección con cuidado antes de declarar.
- Completar la clasificación por edades contestando el cuestionario según las funciones reales. La app no es un juego y no debería incluir contenido sexual, violencia gráfica ni apuestas, pero responde según el contenido real y no por suposiciones.
- En la declaración de salud, marcar las funciones relacionadas con nutrición y gestión del peso y cualquier actividad/seguimiento físico que esté disponible. No presentarla como diagnóstico o tratamiento médico si no lo es.

## 10. Bloqueadores antes de enviar a revisión
- Sustituir los marcadores legales de la política de privacidad y publicar la página en una URL pública estable.
- Confirmar que existe una vía efectiva de solicitud y tramitación de eliminación de cuenta/datos y completar el enlace correspondiente en Play Console. La política no sustituye una implementación efectiva.
- Añadir un acceso a la política de privacidad dentro de la app; Google exige enlace en Play Console y acceso desde la propia aplicación.
- Crear y verificar una cuenta de revisión de prueba.
- Confirmar que las capturas son reales, legibles y no contienen datos personales.
