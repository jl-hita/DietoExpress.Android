# Publicación de DietoExpress Android en Google Play

Fecha de preparación: 26/10/2026.

## Estado técnico del paquete

- Identificador fijo: `com.dietoexpress.mobile`.
- `minSdk = 26`, `targetSdk = 36` (cumple el requisito vigente de nuevas publicaciones desde el 31/08/2026).
- Versión inicial configurada: `versionCode = 1`, `versionName = 0.1.0`. Si ya se ha subido un AAB a Play Console, incrementar `versionCode` antes de la siguiente subida.
- La CI de `main` genera un APK debug para pruebas y un AAB release firmado con la clave estable configurada en GitHub Actions.
- El AAB debe subirse a Play Console como **clave de carga (upload key)**. Si se activa Play App Signing, Google gestiona la clave de firma de distribución y la clave local se conserva como clave de carga.
- La generación del artefacto no demuestra que la ficha, las declaraciones ni la revisión de Play estén completas.

## Antes de crear el lanzamiento

1. Confirmar que la CI de `main` está verde y que el artefacto `DietoExpress-release-aab-<sha>` contiene un `.aab`.
2. Descargar el AAB desde GitHub Actions. No subir el APK debug a la pista de Play.
3. Conservar el keystore de carga y sus contraseñas en un gestor seguro y en una copia cifrada independiente. No subirlos al repositorio ni adjuntarlos a incidencias.
4. Confirmar que el AAB apunta al backend HTTPS correcto y al proyecto Firebase esperado.
5. Preparar una cuenta de paciente de prueba para el revisor, con datos ficticios/no sensibles, y mantenerla operativa durante la revisión.
6. Confirmar una política de privacidad pública accesible por URL. Debe identificar al responsable, los datos tratados, fines, conservación, destinatarios/proveedores, derechos y contacto; también debe poder abrirse desde la app. No inventar una URL: verificar primero que la página existe y corresponde a la app.
7. Preparar una dirección de soporte que se supervise.

## Crear la aplicación y el canal de pruebas cerradas

Los nombres exactos de algunos botones pueden variar en Play Console.

1. Entrar en https://play.google.com/console/ y crear la aplicación **DietoExpress** si todavía no existe.
2. Seleccionar idioma predeterminado y aplicación de tipo app; indicar que es gratuita si no se va a cobrar por descargarla. La monetización de servicios fuera de la app debe declararse conforme a las políticas aplicables.
3. Completar las tareas obligatorias del panel de la app. El nombre de paquete quedará fijado al primer artefacto que se suba: debe ser `com.dietoexpress.mobile`.
4. Completar la ficha de Play: descripción breve y completa, icono de alta resolución, capturas reales de la app, categoría, email de contacto y política de privacidad. No mostrar información clínica real en capturas.
5. Completar **Contenido de la aplicación**:
   - Política de privacidad.
   - Seguridad de los datos, describiendo con exactitud el tratamiento en la app y los SDK/proveedores, incluido Firebase Cloud Messaging y el backend.
   - Declaración de aplicación de salud y funciones nutricionales/de seguimiento.
   - Clasificación por edades, acceso a la app y cualquier otra declaración que muestre Play Console.
   - Declarar únicamente permisos y usos que existan en el artefacto final.
6. En **Pruebas y lanzamiento → Pruebas → Pruebas cerradas**, abrir la pista inicial (o crear una pista con nombre claro, por ejemplo `closed-test-1`).
7. En la pestaña de testers, crear una lista de correo con las cuentas Google de los participantes, o usar un Google Group. Proporcionar un email o URL real para comentarios.
8. Crear una versión, subir el AAB firmado, revisar los avisos de Play y guardar/publicar el lanzamiento de prueba.
9. Una vez publicada la pista, copiar el enlace de participación. Cada tester debe abrirlo con su cuenta Google, aceptar participar y luego instalar desde Google Play. Instalar el APK manualmente no cuenta como inscripción en la prueba.
10. Verificar en Play Console que la pista está publicada y que los testers pueden acceder. El enlace puede tardar unas horas en estar disponible.

## Requisito para solicitar acceso a producción

La cuenta de desarrollador es personal y se creó en 2025, por lo que se aplica el requisito de Google para cuentas personales creadas después del 13/11/2023: al menos **12 testers inscritos de forma continua durante 14 días** en la prueba cerrada antes de solicitar acceso a producción.

- Mantener al menos 12 testers inscritos durante todo el periodo.
- Pedirles que instalen y usen la app de verdad y que envíen comentarios; no basta con recopilar correos.
- Evitar que abandonen la prueba o se den de baja. Si la inscripción cae por debajo del mínimo, puede retrasar el cumplimiento del requisito.
- Registrar incidencias, cambios realizados y decisiones tomadas durante la prueba.
- Tras cumplir los requisitos, solicitar acceso a producción desde el panel y contestar honestamente las preguntas sobre testers, uso, comentarios y preparación.

## Checklist previo a pulsar publicar

- [ ] CI verde y AAB release firmado descargado.
- [ ] Identificador y versionCode correctos.
- [ ] Play App Signing revisado y clave de carga respaldada.
- [ ] Política de privacidad pública y accesible desde la app.
- [ ] Ficha, capturas y contacto de soporte listos.
- [ ] Seguridad de los datos, declaración de salud, clasificación por edades y acceso a la app completados con datos reales.
- [ ] Cuenta de revisión funcional y con datos ficticios.
- [ ] Pista cerrada publicada y enlace de inscripción comprobado.
- [ ] 12 o más testers inscritos y periodo de 14 días monitorizado.

## Referencias oficiales

- Requisitos de pruebas para cuentas personales: https://support.google.com/googleplay/android-developer/answer/14151465
- Configurar pruebas internas, cerradas y abiertas: https://support.google.com/googleplay/android-developer/answer/9845334
- Preparar y desplegar una versión: https://support.google.com/googleplay/android-developer/answer/9859348
- Requisito de nivel de API: https://support.google.com/googleplay/android-developer/answer/11926878
