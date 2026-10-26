# Configuración y publicación de DietoExpress Android

Fecha de revisión: 26/10/2026.

Esta guía describe cómo preparar el entorno local, configurar Firebase, compilar la app y generar el AAB firmado para Google Play desde Android Studio. La app nativa es el portal del paciente; el backend ASP.NET Core continúa siendo la autoridad para identidad, permisos y reglas de negocio.

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

Alternativamente, copia el APK a un dispositivo e instálalo; Android puede pedir autorización para instalar desde esa fuente. En Linux/macOS usa `./gradlew` en lugar de `./gradlew.bat`.

## 4. Configurar la firma estable en tu PC

El AAB de Google Play se genera **localmente desde Android Studio**. GitHub Actions no genera el AAB ni necesita acceso a la clave de carga.

Guarda la configuración de firma en el archivo personal de Gradle:

- Windows: `%USERPROFILE%\.gradle\gradle.properties`
- Linux/macOS: `~/.gradle/gradle.properties`

Añade las siguientes propiedades con los datos de la clave estable que ya creaste:

```properties
androidSigningKeystorePath=C:/ruta/privada/dietoexpress-release.jks
androidSigningKeystorePassword=[REDACTED]
androidSigningKeyAlias=ALIAS_DE_LA_CLAVE
androidSigningKeyPassword=[REDACTED]
```

Sustituye los valores de ejemplo por los tuyos. En Windows puedes usar barras `/` en la ruta. No guardes estos valores en el repositorio ni los compartas. Asegúrate de que la ruta existe y apunta al keystore correcto. Cierra y vuelve a abrir Android Studio si estaba abierto mientras modificabas el archivo.

Gradle solo configura la firma release estable si las cuatro propiedades existen y la ruta apunta a un archivo. Si no están disponibles, no subas el AAB a Google Play: podría no estar firmado con la clave de carga esperada.

### Generar el AAB desde Android Studio

1. Abre el proyecto y espera a que termine la sincronización de Gradle.
2. Ejecuta las pruebas y comprueba que el backend HTTPS y Firebase configurados son los correctos.
3. Si ya subiste una versión a Play Console, incrementa `versionCode` en `app/build.gradle.kts`. Cada lanzamiento debe tener un código superior al anterior.
4. Abre **Build → Generate Signed Bundle / APK…** (el texto puede variar ligeramente entre versiones de Android Studio).
5. Selecciona **Android App Bundle**, el módulo `app` y la variante `release`.
6. Sigue el asistente. Si pide el keystore, selecciona el archivo y alias existentes; no generes una clave nueva.
7. Finaliza la generación y localiza el archivo `.aab` indicado por Android Studio. Normalmente estará en `app/build/outputs/bundle/release/`.
8. Verifica que el paquete corresponde a `com.dietoexpress.mobile`, que el AAB está firmado con la clave de carga esperada y que el `versionCode` es correcto. Sube ese AAB a Play Console; no subas el APK debug.

También puedes generar el AAB desde la terminal de Android Studio:

```powershell
.\gradlew.bat --no-daemon :app:bundleRelease
```

### Copia de seguridad de la clave

Guarda una copia del keystore y de sus credenciales en un gestor o almacén cifrado independiente del PC de desarrollo. Limita el acceso y comprueba que la copia se puede leer. No la subas a GitHub, almacenamiento sin cifrar, tickets ni chats. Perder la clave puede impedir actualizar la app si necesitas usar la misma clave para futuras cargas.

Si activas Play App Signing, Google gestiona la clave con la que firma la app distribuida; tu keystore local actúa como clave de carga. Conserva la copia de seguridad.

## 5. GitHub Actions

GitHub Actions ejecuta las pruebas unitarias, compila las pruebas instrumentadas y compila el APK debug. En pushes a `main` puede conservar un APK debug como artefacto de diagnóstico. **No genera AAB, no firma lanzamientos y no necesita los secretos de firma.** El AAB publicable se genera en tu propio Android Studio siguiendo el apartado anterior.

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
- [ ] El AAB release usa la clave estable esperada y se ha verificado su firma.
- [ ] El keystore y las credenciales tienen copia de seguridad cifrada.
- [ ] Se han revisado privacidad, accesibilidad, soporte y requisitos de Google Play.

El estado funcional y las pruebas pendientes se mantienen en `README.md` y `docs/ANDROID_GAP_AUDIT.md`. No se debe anunciar una publicación lista hasta cerrar los puntos aplicables y completar las pruebas manuales.
