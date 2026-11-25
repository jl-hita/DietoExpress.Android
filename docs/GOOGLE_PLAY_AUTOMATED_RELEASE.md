# Publicación automática en Google Play

El workflow `.github/workflows/play-release.yml` se ejecuta con cada merge a `main` que cambie `app/`, `gradle/` o los archivos Gradle de la raíz. También se puede lanzar a mano sobre `main` desde **Actions → Google Play release → Run workflow**.

Pasos: pruebas unitarias, cálculo de versión, generación del AAB firmado, verificación de la firma y subida a la pista de pruebas cerradas. El AAB y `mapping.txt` se conservan 30 días como artefacto de la ejecución.

## Versionado

- `versionCode = 10 + número de ejecución` del workflow.
- `versionName = 0.1.(versionCode - 2)`. La primera ejecución publica `0.1.9` (`versionCode` 11).
- Los valores de `app/build.gradle.kts` solo se usan en compilaciones locales; el workflow no hace commits.
- Una ejecución fallida también consume un número. Los huecos son válidos para Google Play.
- Antes de la primera ejecución, comprueba en Play Console el `versionCode` más alto ya subido. Si es 11 o mayor, sube `VERSION_CODE_BASE` en el workflow. Lo mismo si subes un AAB a mano con un código superior.

## Configuración necesaria (una sola vez)

### 1. Cuenta de servicio de Google Play

1. En [Google Cloud Console](https://console.cloud.google.com/), elige o crea un proyecto y activa **Google Play Android Developer API**.
2. En **IAM y administración → Cuentas de servicio**, crea una cuenta de servicio y genera una clave JSON.
3. En [Play Console](https://play.google.com/console/), **Usuarios y permisos → Invitar usuarios**, añade el email de la cuenta de servicio. Dale acceso solo a DietoExpress, con permiso para ver la información de la app y publicar en las pistas de prueba.
4. Guarda el JSON completo como secreto del repositorio y borra la copia local:

```powershell
Get-Content -Raw "C:\ruta\privada\service-account.json" | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON --repo jl-hita/DietoExpress.Android
```

La API solo funciona si la aplicación ya existe en Play Console y se ha subido a mano al menos un AAB.

### 2. Firma

Secretos del repositorio: `ANDROID_SIGNING_KEYSTORE_BASE64`, `ANDROID_SIGNING_KEYSTORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS` y `ANDROID_SIGNING_KEY_PASSWORD`. Deben corresponder a la clave de carga registrada en Play Console. Si has cambiado las contraseñas, actualiza los secretos.

### 3. Firebase

Variables del repositorio (**Settings → Secrets and variables → Actions → Variables**): `DIETOEXPRESS_FIREBASE_API_KEY`, `DIETOEXPRESS_FIREBASE_APPLICATION_ID`, `DIETOEXPRESS_FIREBASE_PROJECT_ID` y `DIETOEXPRESS_FIREBASE_SENDER_ID`. Sin ellas la publicación continúa, pero la app no tendrá notificaciones push. Ver [la guía de push](P1_PUSH_SETUP.md).

### 4. Opcional

| Variable | Valor por defecto | Uso |
|---|---|---|
| `PLAY_TRACK` | `alpha` | Identificador de la pista. Una pista cerrada con nombre propio usa ese nombre. |
| `PLAY_RELEASE_STATUS` | `completed` | Usa `draft` para revisar y publicar a mano, o si la app sigue en borrador en Play Console. |

## Si falla

- `Version code ... has already been used`: sube `VERSION_CODE_BASE`.
- `The caller does not have permission`: revisa los permisos de la cuenta de servicio; pueden tardar en aplicarse.
- `Only releases with status draft may be created on draft app`: define `PLAY_RELEASE_STATUS=draft`.
- Fallo en **Restore upload keystore**: el keystore, la contraseña o el alias de los secretos no coinciden.
