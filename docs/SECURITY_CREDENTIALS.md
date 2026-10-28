# Proteger las credenciales Android

Esta guía evita publicar secretos en Git y explica cómo actuar si ya se han publicado.

## Qué va en cada sitio

- `gradle.properties` del repositorio: solo valores no secretos necesarios para compilar.
- `%USERPROFILE%\.gradle\gradle.properties` en Windows (o `~/.gradle/gradle.properties` en Linux): configuración privada local.
- GitHub Actions: secretos de repositorio/entorno solo cuando un workflow necesite una credencial para realizar una operación que requiera firma.
- El archivo `.jks`/`.keystore`: fuera del repositorio, con una copia de seguridad cifrada y separada.

No pegues contraseñas, claves privadas ni tokens en incidencias, pull requests, logs, capturas o conversaciones. No incluyas nunca un keystore en Git.

## 1. Comprueba el estado antes de cambiar nada

1. En GitHub, abre el repositorio y busca el historial de `gradle.properties` y los cambios del PR #45.
2. Comprueba que el archivo `.jks` o `.keystore` no aparece en ningún commit ni en la lista de archivos del repositorio.
3. Localiza el keystore original y haz una copia de seguridad segura antes de tocarlo. No lo borres ni generes otro por accidente.
4. No publiques aquí las contraseñas ni el contenido de tu archivo local.

Eliminar una contraseña del archivo actual no la elimina de los commits antiguos. La rotación de la credencial es lo que invalida el valor anterior cuando sea posible; limpiar el historial por sí solo no lo hace.

## 2. Contraseñas de firma

Hazlo en el ordenador que contiene el keystore, usando una terminal local y el `keytool` del JDK. No envíes contraseñas como argumentos del comando; deja que `keytool` las solicite interactivamente.

- Para un almacén JKS, `keytool -storepasswd -keystore "RUTA_AL_KEYSTORE"` permite cambiar la contraseña del almacén.
- Para cambiar la contraseña de una clave dentro de un JKS, usa `keytool -keypasswd -alias "ALIAS_DE_LA_CLAVE" -keystore "RUTA_AL_KEYSTORE"`.
- En almacenes PKCS12, Java puede exigir que la contraseña de la clave y la del almacén sean iguales; sigue el comportamiento de `keytool` para el tipo real de almacén y no conviertas ni regeneres la clave sin verificar antes la compatibilidad con Google Play.

Después actualiza las propiedades `androidSigningKeystorePassword` y `androidSigningKeyPassword` en el archivo local `%USERPROFILE%\.gradle\gradle.properties`. Mantén la misma clave privada si solo se filtraron las contraseñas y el archivo de clave sigue privado. Prueba una compilación release firmada local antes de publicar.

Si el propio archivo del keystore se publicó o sospechas que alguien copió la clave privada, cambiar solo las contraseñas no basta. No elimines la clave antigua ni crees otra sin revisar primero Play Console: si usas Play App Signing, el procedimiento de restablecimiento de la upload key depende de la configuración de la aplicación.

## 3. Firebase

Los identificadores de proyecto, App ID y Sender ID suelen ser configuración pública de cliente; no hace falta rotarlos por aparecer en Git. La API key de Firebase también suele estar incluida en aplicaciones cliente, pero debe restringirse según las API y servicios que usa.

1. Abre Google Cloud Console > APIs y servicios > Credenciales.
2. Selecciona el proyecto correcto y revisa la API key usada por Android.
3. Aplica restricciones de API y, cuando proceda, restricciones para Android con el paquete `com.dietoexpress.mobile` y las huellas de certificado correctas.
4. Revisa cuotas y actividad antes de revocar o sustituir una clave. No la elimines a ciegas: podrías romper notificaciones u otros servicios.
5. Comprueba las reglas de seguridad de Firebase y App Check; la API key no sustituye a esas protecciones.

## 4. Evitar futuras exposiciones

- Revisa los cambios de `.gitignore` antes de añadir archivos privados. El archivo ya ignora `*.jks`, `*.keystore`, `google-services.json` y archivos de cuenta de servicio.
- No guardes credenciales en el `gradle.properties` versionado.
- Activa en GitHub Settings > Code security la detección de secretos y Push protection si están disponibles para este repositorio.
- El workflow de análisis de secretos ayuda a detectar nuevas exposiciones; no sustituye a la rotación de credenciales antiguas ni garantiza que el historial previo esté limpio.
- Si alguna vez necesitas firmar desde CI, usa secretos de GitHub y evita imprimirlos en logs.

## Referencias oficiales

- GitHub secret scanning: https://docs.github.com/en/code-security/secret-scanning/introduction/about-secret-scanning
- GitHub push protection: https://docs.github.com/en/code-security/secret-scanning/push-protection-for-repositories-and-organizations
- Firebase API keys: https://firebase.google.com/docs/projects/api-keys
- Android app signing: https://developer.android.com/studio/publish/app-signing
