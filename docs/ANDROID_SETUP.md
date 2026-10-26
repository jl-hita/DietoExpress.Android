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

El proyecto utiliza Firebase Cloud Messaging (FCM). La configuración Android de Firebase debe corresponder al identificador exacto:

```text
com.dietoexpress.mobile
```

En Firebase Console:

1. Abre el proyecto Firebase de DietoExpress o crea uno específico para la app.
2. Añade una aplicación Android con el identificador exacto `com.dietoexpress.mobile`.
3. Obtén los valores de configuración del proyecto: API key, Firebase App ID, Project ID y sender ID.
4. Comprueba que Cloud Messaging está disponible. La configuración del servidor es independiente de la app cliente: sigue la guía del backend [docs/P1_PUSH_NOTIFICATIONS.md](https://github.com/jl-hita/DietoExpress/blob/main/docs/P1_PUSH_NOTIFICATIONS.md), que documenta la cuenta de servicio, las variables `DIETOEXPRESS_FCM_PROJECT_ID` y `DIETOEXPRESS_FCM_SERVICE_ACCOUNT_PATH`, los permisos del archivo y el reinicio de systemd.
5. Restringe la API key cliente a las APIs y aplicaciones necesarias cuando sea posible. Esta clave no sustituye a credenciales de servidor ni concede autorización clínica.

Configura los valores como propiedades locales de Gradle, no dentro de un archivo versionado. En Windows crea/edita `%USERPROFILE%\.gradle\gradle.properties`; en Linux/macOS, `~/.gradle/gradle.properties`.

Contenido (sustituye los marcadores por los valores de Firebase Console):

```properties
dietoexpressFirebaseApiKey=REEMPLAZAR
dietoexpressFirebaseApplicationId=REEMPLAZAR
dietoexpressFirebaseProjectId=REEMPLAZAR
dietoexpressFirebaseSenderId=REEMPLAZAR
dietoexpressApiBaseUrl=https://jlhitap.duckdns.org/api
```

No añadas comillas ni espacios alrededor de `=`. `dietoexpressApiBaseUrl` debe apuntar a la raíz de la API y terminar en `/api`, sin añadir rutas de controladores. Para desarrollo local, cambia esta URL explícitamente: el `localhost` del emulador no es el PC anfitrión. No desactives la validación TLS para solucionar errores de certificados.

La app compila estos valores en el cliente. Son configuración identificativa de una app cliente, no credenciales administrativas. No guardes service-account JSON, claves privadas ni tokens de servidor en el proyecto Android. No versionar valores de configuración locales aunque no sean secretos de servidor: evita divergencias entre equipos y deja la configuración de cada entorno explícita.

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

El build Gradle espera estas cuatro variables de entorno. Si se almacenan en GitHub Actions, el workflow debe mapear explícitamente los secretos a esas variables y provisionar el archivo keystore en el runner. Antes de crear o renombrar secretos, comprueba los nombres que realmente consume el workflow. Los nombres de variables de entorno esperados son:

- `ANDROID_SIGNING_KEYSTORE_PATH`
- `ANDROID_SIGNING_KEYSTORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

**Importante:** una variable de ruta no transfiere por sí sola el keystore a un runner efímero. Comprueba que el workflow provisiona el archivo de forma segura en el runner y lo elimina al finalizar. Los PRs desde forks no deben recibir secretos. No imprimas el entorno completo ni actives `set -x` en pasos que manejan credenciales.

La configuración local y la CI deben usar la misma clave y alias. GitHub no permite leer de vuelta los valores guardados; consérvalos en el almacén seguro original.

## 6. Compatibilidad con el backend

Antes de distribuir una actualización:

1. Comprueba que CI del backend y Android está verde.
2. Si un cambio del backend modifica endpoints, DTO, autenticación/cookies, códigos HTTP, permisos, fechas o reglas de negocio, revisa el impacto en Android y déjalo registrado según `docs/API_COMPATIBILITY.md`.
3. Despliega primero los cambios compatibles del backend y prueba la app contra el entorno HTTPS.
4. Comprueba acceso, dieta, citas, documentos, mensajes, push y, cuando proceda, videoconsulta.
5. Revisa `docs/ANDROID_GAP_AUDIT.md`: hay validaciones en dispositivo real pendientes. La app no está lista para publicación solo porque compile.

## 7. Lista de comprobación antes de distribuir

- [ ] JDK 17, Android SDK 36 y Gradle Wrapper funcionan.
- [ ] Los cuatro valores Firebase locales están configurados fuera del repositorio.
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
