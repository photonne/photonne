# Publicación en Google Play y App Store

Estado del cliente nativo (`Src/Photonne.Client.Native`) frente a los requisitos
de las tiendas, y lo que queda fuera del código.

## Resuelto en el código

| Requisito | Dónde |
| --- | --- |
| Play: `targetSdk` 36 (obligatorio desde el 31-08-2026) | `composeApp/build.gradle.kts` |
| Play: `versionCode` siempre creciente (`MMMmmmppp`, 1.153.2 → 1153002) | `toVersionCode()` en `build.gradle.kts` |
| Play: App Bundle (`bundleRelease`), R8 y alineación de 16 KB comprobados en CI | `.github/workflows/native-build.yml` |
| Play: firma de subida por variables `PHOTONNE_KEYSTORE_*` | `README.md` del cliente |
| Android: sin copia de seguridad automática de tokens cifrados | `AndroidManifest.xml`, `data_extraction_rules.xml` |
| Apple: manifiesto de privacidad (ITMS-91053) | `iosApp/iosApp/PrivacyInfo.xcprivacy` |
| Apple: orientaciones de iPad (ITMS-90474) | `Info.plist` (`UISupportedInterfaceOrientations~ipad`) |
| Apple: `NSPhotoLibraryAddUsageDescription` (guardar desde la hoja de compartir) | `Info.plist` |
| Apple: textos de permisos en español e inglés | `iosApp/iosApp/{es,en}.lproj/InfoPlist.strings` |
| Apple: exención de cifrado (`ITSAppUsesNonExemptEncryption`) | `Info.plist` |
| Apple: tokens en el Keychain | `PlatformModule.ios.kt` |

## Pendiente fuera del código

### Ambas tiendas
- **Probar en dispositivo real** el build de release (Android con R8, iOS
  archivado) antes de enviar: login, timeline, visor, copia en segundo plano,
  liberar espacio, compartir/guardar.
- **Servidor y cuenta de demostración** para los revisores (Apple 2.1, Play
  "App access"): una instancia accesible por HTTPS con fotos de ejemplo.
- **Política de privacidad** pública (URL obligatoria en ambas fichas).
- **Borrado de cuenta** (Apple 5.1.1(v), Play "Account deletion"): hoy las
  cuentas las crea un administrador y el usuario no puede borrar la suya desde
  la app. Si las tiendas lo consideran "creación de cuenta en la app", hará
  falta un endpoint `DELETE /api/users/me` en el servidor y la opción en
  Ajustes → Cuenta, o al menos una URL web de solicitud de borrado para Play.

### Google Play Console
- Declaración de **permisos de fotos y vídeos** (`READ_MEDIA_IMAGES/VIDEO`):
  justificar que la copia de seguridad de la galería es la función principal.
- Declaración de **servicio en primer plano `dataSync`** con vídeo de la
  subida manual ("Subir ahora").
- Formulario de **seguridad de los datos**: las fotos y los datos de cuenta
  solo viajan al servidor que configura el usuario.
- Activar **Play App Signing** y usar el keystore de `PHOTONNE_KEYSTORE_*`
  como clave de subida.

### App Store Connect
- **App Privacy** coherente con `PrivacyInfo.xcprivacy` (sin seguimiento ni
  datos recogidos por el desarrollador).
- Versión y build: `MARKETING_VERSION` y `CURRENT_PROJECT_VERSION` están fijos
  en `project.pbxproj`; súbelos a mano en cada envío (el build debe crecer).
- El identificador actual es `com.photonne.app.iosApp`; debe coincidir con el
  App ID registrado.
