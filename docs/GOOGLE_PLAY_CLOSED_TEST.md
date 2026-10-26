# Publicación de DietoExpress Android en Google Play

Fecha de preparación: 26/10/2026.

## Estado técnico del paquete

- Identificador fijo: `com.dietoexpress.mobile`.
- `minSdk = 26`, `targetSdk = 36`.
- Versión inicial configurada: `versionCode = 1`, `versionName = 0.1.0`. Si ya se ha subido un AAB a Play Console, incrementar `versionCode` antes de la siguiente subida.
- **El AAB firmado se genera en Android Studio en tu propio PC.** GitHub Actions no genera AAB ni necesita secretos de firma; valida el código y conserva un APK debug de diagnóstico desde `main`.
- El AAB debe firmarse con tu clave de carga (upload key). Si activas Play App Signing, Google gestiona la clave de firma de distribución y tú conservas la clave de carga.
- La generación del artefacto no demuestra que la ficha, las declaraciones ni la revisión de Play estén completas.

## 1. Preparar la firma local

La configuración de firma se guarda fuera del repositorio, en el archivo personal de Gradle:

- Windows: `%USERPROFILE%\\.gradle\\gradle.properties`
- Linux/macOS: `~/.gradle/gradle.properties`

Añade estas propiedades con los valores reales de tu clave ya creada:

```properties
androidSigningKeystorePath=C:/ruta/privada/dietoexpress-release.jks
androidSigningKeystorePassword=[REDACTED]
androidSigningKeyAlias=TU_ALIAS
androidSigningKeyPassword=[REDACTED]
```

En Windows usa barras `/` en la ruta o escapa las barras invertidas. No compartas el archivo ni guardes estos valores en el repositorio. No crees un keystore nuevo si ya has configurado la clave estable.

Cierra y vuelve a abrir Android Studio si lo tenías abierto mientras modificabas el archivo de propiedades. Si las cuatro propiedades están correctas y la ruta existe, Gradle firmará el tipo `release` con esa clave. Si falta alguna, **no subas el AAB**: la configuración release no estará firmada con tu clave de carga.

## 2. Generar el AAB desde Android Studio

1. Abre el proyecto `DietoExpress.Android` en Android Studio y espera a que termine la sincronización de Gradle.
2. Antes de publicar, ejecuta las pruebas y confirma que el backend HTTPS y Firebase configurados son los correctos.
3. Actualiza `versionCode` en `app/build.gradle.kts` si ya subiste una versión anterior. Cada subida debe tener un `versionCode` mayor que el anterior.
4. En el menú **Build → Generate Signed Bundle / APK…** (en algunas versiones puede aparecer como **Generate Signed App Bundle or APK**), selecciona **Android App Bundle**.
5. Selecciona el módulo `app`, elige la variante `release` y sigue el asistente. Si Android Studio solicita el keystore, selecciona el mismo archivo y alias de carga y no generes una clave nueva.
6. Finaliza el asistente y localiza el archivo `.aab` que Android Studio indica. Normalmente queda bajo `app/build/outputs/bundle/release/`.
7. Comprueba que el AAB está firmado y que corresponde al identificador `com.dietoexpress.mobile`. No subas el APK debug a Play Console.

También puedes compilar desde la terminal de Android Studio, siempre que las cuatro propiedades personales de Gradle estén configuradas:

```powershell
.\\gradlew.bat --no-daemon :app:bundleRelease
```

## Antes de crear el lanzamiento

1. Conservar el keystore de carga y sus contraseñas en un gestor seguro y en una copia cifrada independiente. No subirlos al repositorio ni adjuntarlos a incidencias.
2. Confirmar que el AAB apunta al backend HTTPS correcto y al proyecto Firebase esperado.
3. Preparar una cuenta de paciente de prueba para el revisor, con datos ficticios/no sensibles, y mantenerla operativa durante la revisión.
4. Confirmar una política de privacidad pública accesible por URL. Debe identificar al responsable, los datos tratados, fines, conservación, destinatarios/proveedores, derechos y contacto; también debe poder abrirse desde la app. No inventar una URL: verificar primero que la página existe y corresponde a la app.
5. Preparar una dirección de soporte que se supervise.

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

- [ ] Pruebas locales/CI correctas.
- [ ] AAB release generado desde Android Studio y firmado con la clave de carga estable.
- [ ] Identificador y `versionCode` correctos.
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
