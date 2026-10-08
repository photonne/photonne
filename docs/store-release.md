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
| Apple: bundle ID `com.photonne.app`, igual que Android (fijo tras el primer build) | `project.pbxproj` |
| Apple: número de build = versión semver (`1.162.2`), sube con cada release de release-please | `.github/workflows/release.yml` |
| Ambas: el asistente de login no trae ninguna URL prerrellenada en release (`Config.xcconfig` no está enlazado al proyecto, así que `PhotonneApiBaseUrl` sale vacío) | `build.gradle.kts`, `Info.plist` |

## Decisiones tomadas (2026-10-02)

- **Subidas a mano** de momento: AAB firmado a Play Console y archivo de
  Xcode a TestFlight. El workflow de CI queda para más adelante (ver el
  último apartado).
- **Política de privacidad en GitHub Pages** del repo (`photonne/photonne`,
  público), para que la URL no dependa de tener un servidor encendido.
- **Cuenta de Play personal creada en septiembre de 2026**: Google exige una
  **prueba cerrada con al menos 12 testers apuntados durante 14 días
  seguidos** antes de dejar solicitar el acceso a producción.
- **Sin borrado de cuenta en la app**: Apple 5.1.1(v) y la política de Play
  solo lo exigen si la app permite *crear* cuentas, y aquí las crea el
  administrador del servidor. Si un revisor lo pide, la respuesta es esa; el
  plan B sería `DELETE /api/users/me` + opción en Ajustes → Cuenta.

- **iOS solo para iPhone** (`TARGETED_DEVICE_FAMILY = 1`): en iPad se
  instala en modo compatibilidad. Añadir iPad más adelante está permitido;
  quitarlo de una versión publicada, no.

## Siguientes pasos, por orden

### 1. Antes del primer build

- [ ] **Política de privacidad**: escrita en `site/privacy/index.html`
  (español e inglés) y publicada por `.github/workflows/pages.yml` en
  `https://photonne.github.io/photonne/privacy/`. Falta activar una vez
  Settings → Pages → Source: "GitHub Actions". Si la app empieza a hablar
  con un servicio nuevo (otro proveedor de mapas, informes de fallos…), hay
  que actualizarla junto con `PrivacyInfo.xcprivacy` y los formularios de
  las tiendas.
- [ ] **Instancia demo para revisores** (Apple 2.1, Play "App access"):
  servidor accesible por HTTPS con fotos de ejemplo, en modo demo
  (`DemoModeGuardMiddleware`) y con un usuario y contraseña que no caduquen.
  Los ajustes globales no se pueden tocar desde la demo; los que necesite
  (la key del mapa…) se fijan en la configuración, p. ej.
  `DemoMode__Settings__ServerSettings__MapTileApiKey` (con `__` en vez del
  punto: el `sh` del entrypoint descarta las variables con punto), y se
  reaplican tras cada reinicio.
  También les sirve a los testers de la prueba cerrada que no tengan servidor.
- [ ] **Probar en dispositivo real el build de release** (Android con R8, iOS
  archivado): login, timeline, visor, copia en segundo plano, liberar
  espacio, compartir/guardar.

### 2. Google Play: arrancar la prueba cerrada cuanto antes

Es el camino más largo (14 días como mínimo), así que va primero.

1. **Clave de subida** (una sola vez). Guárdala fuera del repo y con copia de
   seguridad; con Play App Signing, si se pierde, Google permite cambiarla,
   pero es un trámite.
   ```sh
   keytool -genkeypair -v -keystore ~/photonne-upload.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **Credenciales de firma** en `~/.gradle/gradle.properties`, fuera del
   repo:
   ```properties
   PHOTONNE_KEYSTORE_FILE=/Users/<tú>/photonne-upload.jks
   PHOTONNE_KEYSTORE_PASSWORD=...
   PHOTONNE_KEY_ALIAS=upload
   PHOTONNE_KEY_PASSWORD=...
   ```
3. **Generar el AAB** desde un commit con versión nueva:
   ```sh
   cd Src/Photonne.Client.Native
   ./gradlew :composeApp:bundleRelease
   # → composeApp/build/outputs/bundle/release/composeApp-release.aab
   ```
   El mapping de R8 va dentro del AAB
   (`BUNDLE-METADATA/.../proguard.map`) y Play lo usa solo para desofuscar
   los fallos: no hay que subirlo aparte. El aviso de Play "contiene código
   nativo pero no has subido símbolos de depuración" se ignora: el único
   `.so` es `libandroidx.graphics.path.so` (de Compose, precompilado y sin
   símbolos publicados); la app no tiene código nativo propio. Comprobar la firma (debe salir la
   clave de subida, no `CN=Android Debug`):
   ```sh
   keytool -printcert -jarfile composeApp/build/outputs/bundle/release/composeApp-release.aab
   ```
4. **Play Console → Crear app**: nombre "Photonne", app gratuita, idioma
   predeterminado español.
5. **Prueba → Prueba cerrada → crear pista**: lista de testers (correos de
   Google o un Grupo de Google), países, y subir el AAB. Al subirlo por
   primera vez se activa **Play App Signing**: acepta que Google gestione la
   clave de firma; la tuya queda como clave de subida.
6. **Contenido de la app** (Panel → "Configura tu app"); la prueba cerrada
   no se revisa sin esto:
   - Política de privacidad (la URL de Pages).
   - Acceso a la app: URL de la instancia demo + usuario + contraseña.
   - Anuncios: no.
   - Clasificación de contenido (cuestionario IARC).
   - Público objetivo: mayores de 18 (evita las reglas de apps para niños).
   - **Seguridad de los datos**: el desarrollador no recoge ni comparte
     datos; los datos viajan cifrados (HTTPS) solo al servidor que elige el
     usuario. Si un revisor discrepa, declarar fotos/vídeos, ubicación
     aproximada (EXIF) y credenciales como "no compartidos, necesarios para
     la funcionalidad".
   - **Permisos de fotos y vídeos** (`READ_MEDIA_IMAGES/VIDEO`): la copia de
     seguridad de la galería es la función principal; el selector del sistema
     no sirve porque hay que leer toda la galería en segundo plano.
   - **Servicio en primer plano**: tipo "Sincronización de datos", caso de
     uso subida/transferencia de archivos. Declarar los dos usos reales de
     `BackupWorker`: "Subir ahora" y la copia programada cuando hay archivos
     pendientes. Vídeo (Drive o YouTube, accesible con el enlace sin iniciar
     sesión): pulsar "Subir ahora", salir y bajar la persiana con la
     notificación de progreso. No borrar ni mover el vídeo: Play puede
     revisarlo en cualquier actualización.
   - **Identificador de publicidad**: no. El manifiesto final no lleva
     `AD_ID` ni hay SDK de anuncios o analítica; si alguna dependencia nueva
     lo añade, esta respuesta cambia.
   - La **prueba interna** sirve para instalar el build de release en el
     móvil sin revisión, pero **no cuenta** para el requisito de los 14 días:
     solo cuenta la cerrada. Promocionar la versión interna a la cerrada
     evita volver a subir el AAB. Un build de debug de Android Studio choca
     con el de Play (misma `applicationId`, otra firma): hay que
     desinstalarlo antes de instalar desde Play.
7. **Reclutar 12 testers** y comprobar que *aceptan* la invitación (Play
   cuenta los apuntados, no los invitados). Deben seguir apuntados los 14
   días; mejor buscar 15 o más por si alguien se borra.
8. **Durante los 14 días**: sube builds nuevos a la misma pista cuando
   arregles cosas (Google valora que la prueba sea real) y prepara la ficha
   de la tienda (paso 4).
9. **Panel → Solicitar acceso a producción**: cuestionario sobre la prueba
   (cómo reclutaste a los testers, qué feedback hubo, qué cambiaste). La
   respuesta tarda unos días; después, crear la versión de producción con el
   mismo AAB o uno nuevo.

### 3. App Store: TestFlight y revisión

Ya hecho: App ID explícito `com.photonne.app` registrado y ficha de App Store
Connect apuntando a él.

1. **Archivar** desde un commit con versión nueva (dos builds con la misma
   versión chocan):
   - Abre `Src/Photonne.Client.Native/iosApp/iosApp.xcodeproj`.
   - Destino: **Any iOS Device (arm64)**.
   - **Product → Archive**. El enlace de Kotlin/Native en Release es lento
     (varios minutos en local).
2. **Firmar con el certificado Apple Distribution local**, no con el
   gestionado en la nube. Con la firma en la nube, Xcode 27 escribe el
   nombre del certificado en el *designated requirement* con la "í" de
   "Fontrubí" descompuesta (`i` + U+0301) mientras el certificado la lleva
   compuesta (U+00ED): la firma no cumple su propio requisito y App Store
   Connect la rechaza con **ITMS-90035 "Invalid Signature"**. El certificado
   local está en el Llavero (Xcode → Settings → Accounts → Manage
   Certificates); la primera vez macOS pide permiso para usar su clave:
   "Permitir siempre". Exportar y comprobar antes de subir:
   ```sh
   xcodebuild -exportArchive -archivePath <archivo>.xcarchive \
     -exportPath <dir> -exportOptionsPlist <ExportOptions.plist> \
     -allowProvisioningUpdates
   # ExportOptions: method app-store-connect, destination export,
   # signingStyle automatic, teamID ZUSGZTSQRK,
   # signingCertificate "Apple Distribution"
   unzip -q <dir>/iosApp.ipa -d <dir>
   codesign --verify --deep --strict --verbose=2 <dir>/Payload/iosApp.app
   # debe decir "satisfies its Designated Requirement"
   ```
   Con el certificado local en el Llavero, **Organizer → Distribute App →
   App Store Connect → Upload** ya firma con él y la subida pasa (1.185.3,
   2026-10-03). Alternativa: subir el `.ipa` verificado con **Transporter**.
3. **TestFlight**: el build aparece en 10–30 min tras el procesado. Pruébalo
   con testers internos (hasta 100 usuarios de tu equipo, sin revisión).
   Los testers externos requieren una revisión ligera de Beta App Review.
4. **Ficha de la versión**: capturas de iPhone 6,9" (solo iPhone:
   `TARGETED_DEVICE_FAMILY = 1`, así que no hacen falta las de iPad),
   descripción, palabras
   clave, URL de soporte, URL de la política de privacidad, categoría
   (Fotografía) y clasificación por edades.
5. **App Privacy**: "No se recopilan datos", coherente con
   `PrivacyInfo.xcprivacy` (los datos van al servidor del usuario, no al
   desarrollador).
6. **Declaración de comerciante (DSA)** en Business, obligatoria para
   distribuir en la UE. Si eres comerciante, Apple publica tu dirección y
   teléfono en la ficha; para una app gratuita sin ánimo comercial se puede
   declarar "no comerciante".
7. **Notas para el revisor** (App Review Information): explicar que es el
   cliente de un servidor autoalojado, dar la URL de la instancia demo, el
   usuario y la contraseña, y los pasos (abrir la app → escribir la URL →
   iniciar sesión). Es el motivo de rechazo más habitual (2.1) en este tipo
   de apps.
8. **Añadir para revisión** con el build de TestFlight.

### 4. Ficha de la tienda (ambas)

- Textos en español e inglés: en `docs/store-listing/` (estructura de
  fastlane, un archivo por campo y por idioma, con sus límites y reglas en
  el README de esa carpeta).
- Capturas desde un dispositivo o emulador con la instancia demo: Play pide
  2–8 de teléfono y un gráfico destacado de 1024×500; Apple, las de 6,9".
- Icono: Play pide 512×512 PNG; Apple lo toma del build
  (`AppIcon.appiconset/icon-1024.png`).
- Mejora opcional: icono adaptativo de Android (`mipmap-anydpi-v26`, con capa
  monocroma para los iconos temáticos de Android 13+); hoy solo hay PNG
  planos.

## Versión nueva en Google Play

El flujo de cada versión, una vez montadas las pistas: primero a la prueba
interna para probarla en el móvil sin esperar a nadie, y de ahí a la cerrada.

1. **Generar el AAB** desde la etiqueta de una release nueva (`git checkout
   vX.Y.Z`: la versión solo sube al publicar una release y `versionCode` sale
   de ella), como en el paso 3 de la prueba cerrada:
   ```sh
   cd Src/Photonne.Client.Native
   ./gradlew :composeApp:bundleRelease
   ```
2. **Prueba → Prueba interna → Crear nueva versión**, subir el AAB y las
   notas de la versión (`<es-ES>…</es-ES>` y `<en-US>…</en-US>`, máximo 500
   caracteres por idioma). **Siguiente → Guardar → Publicar**. La interna
   **no pasa revisión**: llega a sus testers en minutos. Si el AAB ya se subió
   a otra pista, **Añadir desde biblioteca** evita subirlo otra vez.
3. **En el teléfono**, con la cuenta de Google que está en la lista de
   testers de la interna (el enlace de apuntarse está en la pista →
   Testers; basta con aceptarlo una vez): Play Store → Photonne →
   **Actualizar**. Si no aparece, borrar la caché de Google Play Store
   (Ajustes → Aplicaciones → Google Play Store → Almacenamiento) y volver a
   mirar. Estando en varias pistas, Play instala el `versionCode` más alto de
   todas, así que la interna y la cerrada no chocan.
4. **Probada, a la prueba cerrada**: en la versión de la interna,
   **Promocionar versión → Prueba cerrada**, sin volver a subir el AAB. Esa sí
   pasa revisión (de unas horas a un par de días; el estado está en
   **Resumen de la publicación**), y solo entonces les llega a los testers.
   Más adelante, el mismo paso hacia **Producción**.

Si la versión trae funciones que necesitan endpoints nuevos, hay que
desplegar el servidor antes de probarla en el móvil.

## Más adelante: subidas desde CI

Cuando las subidas sean frecuentes, un workflow `workflow_dispatch` que
firme y suba:

- **Android**: `bundleRelease` con el keystore en secrets (en base64) y
  subida a la pista de Play con una cuenta de servicio de Google Cloud
  invitada en Play Console con permiso de publicar en pistas de prueba. La
  API de Play no puede crear la primera versión de una app: el workflow solo
  sirve a partir de la primera subida manual.
- **iOS**: `xcodebuild archive` + `-exportArchive` con
  `-allowProvisioningUpdates` y una **clave de la API de App Store Connect**
  (`.p8` + Key ID + Issuer ID, rol App Manager) para la firma gestionada en
  la nube, sin exportar certificados.
- El enlace de Release de Kotlin/Native tarda más de media hora en el runner
  de macOS de GitHub (ver `native-build.yml`). El repo es público, así que
  esos minutos no consumen cuota.
