# Configuración y publicación de DietoExpress Android

Fecha de revisión: 26/10/2026.

Esta guía describe cómo preparar el entorno local, configurar Firebase, compilar la app y firmar APKs de forma reproducible. La app nativa es el portal del paciente; el backend ASP.NET Core continúa siendo la autoridad para identidad, permisos y reglas de negocio.

## 1. Requisitos

- Windows 10/11 (los comandos principales usan PowerShell), o Linux/macOS.
- Android Studio compatible con Android Gradle Plugin 9.2.1.
- JDK 17.
- Android SDK 36 y Platform Tools.
- Git y acceso al repositorio `jl-hita/DietoExpress.Android`.
- Conexión HTTPS al backend de DietoExpress.

Abre el proyecto desde la raíz del repositorio, donde está el Gradle Wrapper. No sustituyas el wrapper por una instalación global de Gradle. Acepta las licencias del SDK desde Android Studio/SDK Manager.

Comprueba Java desde la terminal que utilizará Gradle:

```powershell
java -version
.\gradlew.bat --version
```

Debe utilizarse Java 17. Si Android Studio y la terminal usan JDK distintos, configura **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.

## 2. Firebase y notificaciones push

El proyecto utiliza Firebase Cloud Messaging (FCM). El repositorio ya contiene los cuatro valores de configuración cliente en el `gradle.properties` de la raíz. Por tanto, **no necesitas crear un archivo local de propiedades ni configurar secretos Firebase en GitHub Actions para compilar**. El identificador Android configurado es:

```text
com.dietoexpress.mobile
```

Si en el futuro se cambia de proyecto Firebase o se necesita probar otra configuración, puedes sobrescribir los valores en el archivo personal de Gradle, sin modificar el repositorio: en Windows `%USERPROFILE%\.gradle\gradle.properties`; en Linux/macOS `~/.gradle/gradle.properties`. Las propiedades personales de Gradle permiten mantener una configuración local distinta. Las claves son:

```properties
dietoexpressFirebaseApiKey=REEMPLAZAR
dietoexpressFirebaseApplicationId=REEMPLAZAR
dietoexpressFirebaseProjectId=REEMPLAZAR
dietoexpressFirebaseSenderId=REEMPLAZAR
dietoexpressApiBaseUrl=https://jlhitap.duckdns.org/api
```

Solo necesitas definir la URL de API en las propiedades personales si quieres sobrescribir la URL predeterminada o apuntar a otro entorno. La URL debe apuntar a la raíz de la API y terminar en `/api`, sin añadir rutas de controladores. Para desarrollo local, recuerda que `localhost` del emulador no es el PC anfitrión. No desactives la validación TLS para solucionar errores de certificados.

Estos cuatro valores son configuración identificativa de una aplicación cliente Android, no credenciales administrativas. La API key cliente debe restringirse a las APIs y aplicaciones necesarias cuando sea posible. **No guardes en el repositorio credenciales de servidor, service-account JSON, claves privadas, tokens ni contraseñas.** La cuenta de servicio FCM del backend es independiente de la app cliente: sigue la guía [docs/P1_PUSH_NOTIFICATIONS.md](https://github.com/jl-hita/DietoExpress/blob/main/docs/P1_PUSH_NOTIFICATIONS.md), que documenta `DIETOEXPRESS_FCM_PROJECT_ID`, `DIETOEXPRESS_FCM_SERVICE_ACCOUNT_PATH`, los permisos del archivo y el reinicio de systemd. El JSON de esa cuenta de servicio debe permanecer únicamente en el servidor.

### Comprobación

Desde la raíz del repositorio:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest
.\gradlew.bat --no-daemon :app:assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`. Instálalo en un dispositivo/emulador y verifica inicio de sesión y recepción de push en primer y segundo plano. Compilar no demuestra que FCM funcione de extremo a extremo.

## 3. Instalar en un dispositivo

Habilita las opciones de desarrollador y depuración USB, conecta el teléfono y acepta la huella del ordenador:

```powershell
adb devices
.\gradlew.bat --no-daemon :app:installDebug
```

Alternativamente, copia el APK a un dispositivo e instálalo; Android puede pedir autorización para instalar desde esa fuente. En Linux/macOS usa `./gradlew` en lugar de `.\gradlew.bat`.

## 4. Firma estable de APK

La firma estable es obligatoria para actualizar una instalación existente sin desinstalarla. El proyecto lee estas variables de entorno:

- `ANDROID_SIGNING_KEYSTORE_PATH`: ruta al archivo keystore.
- `ANDROID_SIGNING_KEYSTORE_PASSWORD`: contraseña del keystore.
- `ANDROID_SIGNING_KEY_ALIAS`: alias de la clave privada.
- `ANDROID_SIGNING_KEY_PASSWORD`: contraseña de la clave privada.

Los cuatro valores deben estar presentes y la ruta debe apuntar a un archivo legible. Configúralos solo en el entorno local protegido o en el almacén de secretos de CI; nunca los escribas en el repositorio, comandos guardados o logs.

En PowerShell puedes pedir las contraseñas de forma interactiva para no escribirlas en el comando:

```powershell
$env:ANDROID_SIGNING_KEYSTORE_PATH = "C:\ruta\privada\dietoexpress-release.jks"
$env:ANDROID_SIGNING_KEYSTORE_PASSWORD = Read-Host "Contraseña del keystore"
$env:ANDROID_SIGNING_KEY_ALIAS = Read-Host "Alias de la clave"
$env:ANDROID_SIGNING_KEY_PASSWORD = Read-Host "Contraseña de la clave"
```

Ten en cuenta que `Read-Host` muestra lo que escribes. Para mayor protección usa un gestor de secretos y evita copiar valores reales a la guía o al historial. No uses `setx` para guardar contraseñas permanentemente en el perfil de Windows.

Con la firma estable configurada, compila:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

La configuración actual aplica la clave estable al tipo `debug` si las cuatro variables están disponibles. **No asumas que `assembleRelease` produce un APK publicable firmado**: antes de distribuir, revisa la configuración Gradle de release y verifica el APK generado.

### Copia de seguridad de la clave

Guarda una copia del keystore y de sus credenciales en un gestor o almacén cifrado independiente del PC de desarrollo. Limita el acceso y comprueba que la copia se puede leer. No la subas a GitHub, a almacenamiento sin cifrar, tickets ni chats. Perder la clave puede impedir actualizar la app instalada con una APK firmada con una identidad diferente.

No generes una clave nueva para cada compilación. Una actualización debe usar el mismo certificado que la instalación anterior; Android rechazará una APK con firma distinta. Desinstalar primero puede perder los datos locales.

## 5. GitHub Actions y secretos de CI

La configuración Firebase cliente se obtiene del `gradle.properties` versionado, por lo que no hacen falta secretos Firebase adicionales en GitHub Actions. La firma estable sí usa secretos. El workflow espera estos cuatro secretos:

- `ANDROID_SIGNING_KEYSTORE_BASE64`: contenido del archivo keystore codificado en Base64.
- `ANDROID_SIGNING_KEYSTORE_PASSWORD`: contraseña del keystore.
- `ANDROID_SIGNING_KEY_ALIAS`: alias de la clave privada.
- `ANDROID_SIGNING_KEY_PASSWORD`: contraseña de la clave privada.

En el job de `main`, el workflow decodifica el keystore en un archivo temporal del runner, expone su ruta a Gradle mediante `ANDROID_SIGNING_KEYSTORE_PATH` y elimina el archivo al finalizar. Verifica que los cuatro secretos estén configurados si quieres que la compilación de `main` produzca una APK firmada con tu clave estable. Los PR no deben recibir esos secretos. No imprimas el entorno completo ni actives `set -x` en pasos que manejan credenciales.

La compilación local sin las cuatro variables de firma sigue funcionando, pero Android Studio generará una APK debug con la firma de depuración predeterminada. Esa APK no sirve para actualizar una instalación que use tu clave estable.

## 6. Compatibilidad con el backend

Antes de distribuir una actualización:

1. Comprueba que CI del backend y Android está verde.
2. Si un cambio del backend modifica endpoints, DTO, autenticación/cookies, códigos HTTP, permisos, fechas o reglas de negocio, revisa el impacto en Android y déjalo registrado según `docs/API_COMPATIBILITY.md`.
3. Despliega primero los cambios compatibles del backend y prueba la app contra el entorno HTTPS.
4. Comprueba acceso, dieta, citas, documentos, mensajes, push y, cuando proceda, videoconsulta.
5. Revisa `docs/ANDROID_GAP_AUDIT.md`: hay validaciones en dispositivo real pendientes. La app no está lista para publicación solo porque compile.

## 7. Lista de comprobación antes de distribuir

- [ ] JDK 17, Android SDK 36 y Gradle Wrapper funcionan.
- [ ] La configuración Firebase incluida en el repositorio corresponde al proyecto Firebase esperado.
- [ ] La URL base HTTPS apunta al backend correcto.
- [ ] Compilan las pruebas unitarias y el APK.
- [ ] Se ha probado el acceso con una cuenta de paciente de pruebas.
- [ ] Se han probado notificaciones en primer/segundo plano, permisos y navegación.
- [ ] Se ha probado modo sin conexión, reinicio del proceso, reintentos y cambio de cuenta.
- [ ] Se ha probado la videoconsulta en dispositivo real.
- [ ] La APK usa la clave estable esperada y se ha verificado su firma.
- [ ] El keystore y las credenciales tienen copia de seguridad cifrada.
- [ ] Se han revisado privacidad, accesibilidad, soporte y requisitos de Google Play.

El estado funcional y las pruebas pendientes se mantienen en `README.md` y `docs/ANDROID_GAP_AUDIT.md`. No se debe anunciar una publicación lista hasta cerrar los puntos aplicables y completar las pruebas manuales.
