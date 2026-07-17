# Photonne.Client.Native

Cliente nativo de Photonne para Android, iOS y Desktop, construido con
[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) y
[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/).

> Estado actual: **fuente principal de consumo** de Photonne. Cubre timeline,
> álbumes, carpetas, mapa, búsqueda, personas, favoritos, archivo, papelera,
> notificaciones, subida, copia de seguridad del dispositivo, compartir y
> detalle de asset, además de administración del servidor. El cliente web
> (`Photonne.Client.Web`) queda reservado como panel de administración.

## Layout

```
composeApp/
├── src/commonMain         # UI Compose + dominio + cliente Ktor
├── src/commonTest         # Tests con MockEngine de Ktor
├── src/androidMain        # MainActivity, EncryptedSharedPreferences, OkHttp engine
├── src/iosMain            # MainViewController, NSUserDefaults, Darwin engine
└── src/desktopMain        # Main.kt, java.util.prefs, CIO engine
iosApp/                    # Wrapper Xcode para empaquetar el framework iOS
```

La decisión arquitectónica está documentada en
[`docs/ADR-001-kotlin-multiplatform.md`](docs/ADR-001-kotlin-multiplatform.md).

## Cromo flotante (cápsulas de cristal esmerilado)

Todo el cromo que flota sobre el contenido —la navegación inferior, las barras de
selección, la píldora superior del timeline, los scrubbers, el botón de subir, las
barras del visor, los controles de slideshow y los toasts del mapa— comparte **un
único estilo**: una cápsula de **cristal esmerilado** que difumina en tiempo real
el contenido que tiene debajo (blur real) y lo tiñe de gris, en vez de un color
plano translúcido.

El blur lo aporta [Haze](https://github.com/chrisbanes/haze)
(`dev.chrisbanes.haze:haze`). **Está fijado en `1.5.4` a propósito**: es la última
serie que apunta a Compose Multiplatform 1.7.x (la de este proyecto); la 1.6+ ya
exige CMP 1.8. No la subas sin migrar CMP.

### Núcleo: `ui/main/ChromeBackdropTint.kt`

Es la fuente de verdad de todo el estilo:

- `LocalChromeHazeState` — publica el `HazeState` compartido (la fuente de blur).
  Las cápsulas lo consumen aunque vivan en slots distintos del árbol.
- `Modifier.chromeCapsuleBackdrop(baseColor, hazeState)` — pinta el cristal: si hay
  fuente, un `hazeEffect` (blur + tinte); si no, cae a un gris sólido de reserva.
- `chromeBaseGray()` / `ChromeBaseGrayDark` (`0xFF242428`) / `ChromeBaseGrayLight`
  (`0xFFE9E9EE`) — el gris base del tinte, por tema.
- `chromeActivePillColor()` — el velo del ítem activo de la nav.
- Perillas: `ChromeBlurRadius = 30.dp`, `ChromeTintAlpha = 0.6f`,
  `ChromeFallbackAlpha = 0.92f`.

### Patrón de cápsula (aplícalo a TODA cápsula nueva)

```kotlin
Surface(shape = <forma>, color = Color.Transparent, shadowElevation = <n>.dp) {
    Box {
        Box(Modifier.matchParentSize().chromeCapsuleBackdrop(/* baseColor/hazeState */))
        // contenido en onSurface (blanco en oscuro)
    }
}
```

La `Surface` transparente aporta **forma + sombra** y **recorta el blur** a la
cápsula; el `Box` de fondo pinta el cristal; el contenido va encima en `onSurface`.

### Regla crítica de Haze (fuente ≠ descendiente)

Un `hazeEffect` **no puede colgar de su propia fuente `hazeSource`** (leería una
capa a medio grabar y saldría transparente). Por eso:

- Cada pantalla con scroll propio marca **solo** lo que scrollea/dibuja por detrás
  con `Modifier.hazeSource(state)` (la rejilla, el pager de la foto, el mapa… no el
  contenedor entero) y pasa ese `state` a sus cápsulas **por parámetro**, de modo
  que estas queden **hermanas** de la fuente y no descendientes.
- `MainScaffold` es la excepción: publica un `HazeState` global vía
  `LocalChromeHazeState` y marca su `content` como fuente; la nav vive en el slot
  del `Scaffold` (hermana del contenido), así que lo hereda por el composition
  local sin romper la regla.
- Donde una cápsula es descendiente de la fuente (el badge de Live Photo, dentro
  del pager) o no tiene nada detrás (barras acopladas de admin), el mismo helper
  cae al gris sólido y solo unifica color.

### Tamaño y color estandarizados

- **Vertical**: todas las cápsulas miden lo mismo, `CompactNavBarContentHeight`
  (`64.dp`). Lo único que cambia es el **ancho**, según cuántos ítems tengan.
- **Ítems**: los de la nav y los de las barras de selección (asset, tarjeta de
  álbum, tarjeta de carpeta) comparten la misma pareja `widthIn(min =
  FloatingNavItemMinWidth)` + `padding(horizontal = FloatingNavItemContentPadding)`,
  y un `EqualWidthRow` a medida los **iguala entre sí** dentro de cada barra (mide
  el `maxIntrinsicWidth` y aplica el mayor a todos, encogiendo en proporción si no
  caben en vez de desbordar). El ítem activo de la nav se marca con el icono relleno
  y un velo concéntrico (`chromeActivePillColor()`), no con color de acento.
- **Color**: el gris base oscuro es único (`ChromeBaseGrayDark`). El cromo del
  visor lo reusa como su base **fija en ambos temas** (sus iconos son blancos sobre
  la foto, así que en claro no puede coger el gris claro del tema), de modo que la
  barra del visor destaca sobre una foto oscura igual que la nav principal.
- Sin bordes de otro color: la cápsula destaca solo por blur + tinte + los blancos.

Para afinar cuánto destaca sobre fondos oscuros hay dos diales: subir
`ChromeBaseGrayDark` (más claro) o `ChromeTintAlpha` (más sólido).

### Inventario de cápsulas

| Cápsula | Fichero | Fuente de blur |
|---------|---------|----------------|
| Nav inferior + barras de selección | `ui/main/MainScaffold.kt` | `content` de `MainScaffold` (via `LocalChromeHazeState`) |
| Píldora superior + scrubber + botón subir (timeline) | `ui/timeline/TimelineScreen.kt` · `TimelineScrubber.kt` | `GroupedAssetGrid` (`gridHazeState`, por parámetro) |
| Barra superior + acciones + slideshow (visor) | `ui/asset/AssetDetailScreen.kt` | el `HorizontalPager` de la foto (`viewerHazeState`, por parámetro) |
| Badge de Live Photo (visor) | `ui/asset/AssetDetailScreen.kt` | ninguna (descendiente del pager → gris sólido) |
| Scrubber del álbum | `ui/grid/AlbumGridScrubber.kt` · `ui/album/AlbumDetailScreen.kt` | el `AssetGrid` del álbum (`albumHazeState`) |
| Toasts del mapa (loading/empty) | `ui/map/MapScreen.kt` | `OsmMap` (`mapHazeState`) |

**Fuera de este estilo a propósito**: la `SelectionActionBar` de
`ui/admin/AdminSharedTrashScreen.kt` (barra acoplada de texto + botones), el toast
de error del mapa (`errorContainer`, el rojo pierde legibilidad sobre cristal) y
los FABs del mapa (son botones de acción, no cromo translúcido).

## Requisitos

| Target | Toolchain |
|--------|-----------|
| Todos | JDK 21, Kotlin 2.1, Gradle 8.10 (vendored vía wrapper) |
| Android | Android SDK 35, NDK opcional, AGP 8.7 |
| iOS | macOS + Xcode 16, CocoaPods opcional |
| Desktop | JDK 21 |

El JDK lo proporciona el wrapper o se puede instalar con `brew install --cask temurin@21`.

## Comandos habituales

Desde `Src/Photonne.Client.Native/`:

```sh
# Tests unitarios (commonTest sobre JVM)
./gradlew :composeApp:desktopTest

# Compilar sólo Desktop sin construir nada más
./gradlew :composeApp:compileKotlinDesktop

# Arrancar la app de escritorio apuntando al API local
./gradlew :composeApp:run -PApiBaseUrl=http://localhost:1107

# Empaquetar la app de escritorio como dmg/msi/deb
./gradlew :composeApp:packageDistributionForCurrentOS

# Compilar sólo el módulo Android (sin firmar)
./gradlew :composeApp:assembleDebug

# Generar el framework de iOS (necesita macOS)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

## Configuración del API

La URL del API se resuelve en este orden:

1. `-PApiBaseUrl=...` al ejecutar Gradle (Desktop y Android).
2. `PHOTONNE_API_BASE_URL` como variable de entorno (Desktop).
3. `BuildConfig.API_BASE_URL` (Android).
4. `PhotonneApiBaseUrl` en `Info.plist` (iOS, ver `iosApp/Configuration/Config.xcconfig`).
5. Por defecto: `http://localhost:1107` (puerto definido en `.env.example`).

El cliente añade automáticamente `Authorization: Bearer <jwt>` en cada
petición, y reintenta una sola vez tras un `401` llamando a
`/api/auth/refresh` (replica el patrón de
`Src/Photonne.Client.Web/Services/AuthRefreshHandler.cs`).

## iOS: cómo crear el proyecto Xcode

El `.xcodeproj` no está versionado todavía. La forma recomendada de generarlo:

1. Abre `Src/Photonne.Client.Native/` con **Android Studio + KMP plugin** o
   **Fleet**, que detecta el proyecto KMP y ofrece "Add iOS App".
2. Alternativamente, en macOS:
   ```sh
   ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
   open iosApp/   # arrastra ContentView.swift e iOSApp.swift a un nuevo proyecto Xcode
   ```
3. Enlaza el framework `ComposeApp.framework` generado en
   `composeApp/build/bin/iosSimulatorArm64/debugFramework/`.

Una vez generado el `iosApp.xcodeproj`, comítelo dentro de `iosApp/` para
fijar la configuración compartida.

## Versionado

No edites las versiones a mano. `versionName`/`versionCode` (Android) y el
`packageVersion` (Desktop) se leen en configuración de Gradle desde
`../Directory.Build.props` (`<Version>`); iOS (`MARKETING_VERSION` /
`CURRENT_PROJECT_VERSION` en `iosApp.xcodeproj/project.pbxproj`) lo sincroniza
el hook `post-commit` del repo. Ese hook incrementa la versión en cada commit
según el tipo del Conventional Commit — ver [Versionado en el README
raíz](../../README.md#versionado). `CURRENT_PROJECT_VERSION` usa el mismo
esquema que `versionCode`: `major*10000 + minor*100 + patch`.

## Generación del cliente del API desde OpenAPI

Hoy las DTOs están escritas a mano (ver
`composeApp/src/commonMain/kotlin/com/photonne/app/data/models/`). Cuando
el contrato se estabilice, se puede migrar al plugin
[`org.openapitools.generator`](https://openapi-generator.tech/) apuntando a
`http://localhost:1107/openapi/v1.json`.

## Verificación end-to-end

```sh
docker compose up -d photonne-api
cd Src/Photonne.Client.Native
./gradlew :composeApp:desktopTest
./gradlew :composeApp:run -PApiBaseUrl=http://localhost:1107
```

Loguea con `admin` / `${ADMIN_PASSWORD}` y deberías llegar a
`HomeScreen` con el saludo.
