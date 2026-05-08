# Photonne.Client.Native

Cliente nativo de Photonne para Android, iOS y Desktop, construido con
[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) y
[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/).

> Estado actual: **scaffold inicial**. Sólo cubre login + pantalla de "Hola"
> y el patrón de refresh-on-401 que comparte con `Photonne.Client.Web`.
> El resto de pantallas (Timeline, Albums, Folders, Map, Search, AssetDetail,
> Notifications) se irá añadiendo en PRs sucesivos.

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
