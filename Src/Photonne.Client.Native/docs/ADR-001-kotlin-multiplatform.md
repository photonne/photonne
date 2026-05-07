# ADR-001: Kotlin Multiplatform + Compose para las apps nativas

- **Estado**: Aceptado
- **Fecha**: 2026-05-07
- **Decisores**: Marc Caralps Fontrubí

## Contexto

Photonne tiene hoy:

- `Photonne.Server.Api` — backend ASP.NET Core 10.
- `Photonne.Client.Web` — PWA Blazor WebAssembly.
- `Photonne.MlService` — servicio de ML en Python.

Queremos arrancar el desarrollo de **apps nativas** para Android, iOS y, a
medio plazo, Desktop, manteniendo paridad funcional con la PWA pero
aprovechando las capacidades nativas (background sync, notificaciones,
acceso a la galería, sensores, biometría, etc.).

## Alternativas evaluadas

| Tecnología | Pros | Contras |
|---|---|---|
| **.NET MAUI** | Reutilización masiva de modelos C# / contratos del API; un solo lenguaje en todo el stack; encaja con la convención `Photonne.*`. MAUI Blazor Hybrid permitiría reusar componentes Razor existentes. | Stack móvil de Microsoft con menor inercia comunitaria; tooling iOS menos pulido; el render de Blazor Hybrid no es 100% nativo. |
| **Kotlin Multiplatform + Compose** | UI compartida real, look & feel nativo en Android, comunidad muy activa, herramientas de JetBrains (IDE, profiler, debugger). Compose Multiplatform 1.7 ya soporta iOS y Desktop establemente. | Introduce un tercer lenguaje (Kotlin) y Gradle. iOS sigue requiriendo macOS para release y aún no es 1.0 en algunos sub-targets. Se duplican modelos C#→Kotlin (mitigable con OpenAPI generator). |
| **Flutter** | Excelente UX cross-platform, hot reload, comunidad enorme. | Cuarto lenguaje (Dart), no reutiliza nada de lo existente, look & feel propio (no exactamente nativo). |
| **React Native** | Ecosistema JS/TS conocido, Expo facilita el bootstrap. | Bridge JS ↔ nativo añade overhead, look & feel inconsistente, gestión de dependencias frágil. |
| **Nativo puro** (Swift + Kotlin) | Máxima calidad por plataforma. | Doble desarrollo, doble esfuerzo de mantenimiento, no escalable para el equipo actual. |

## Decisión

Se elige **Kotlin Multiplatform con Compose Multiplatform**.

Razones:

1. La decisión es deliberadamente la del usuario tras evaluación: prefiere la
   calidad UI/UX que ofrece Compose en Android y la posibilidad de extender
   a Desktop con un único módulo.
2. Los contratos del API son sencillos (REST + JSON) y se pueden replicar
   en Kotlin sin demasiado esfuerzo, o autogenerar desde OpenAPI cuando se
   estabilicen.
3. JDK 21 ya es parte del entorno de desarrollo, y Gradle no entra en
   conflicto con la solución `.sln` existente al vivir en su propio
   subdirectorio.

## Consecuencias

### Positivas

- Una sola UI compartida para Android, iOS y Desktop.
- Posibilidad futura de objetivos adicionales (Web vía Wasm, watchOS,
  tvOS) con relativamente poco esfuerzo.
- Tipado fuerte y null-safety en toda la app, alineado con la cultura de
  C# del backend.

### Negativas / mitigaciones

| Riesgo | Mitigación |
|---|---|
| Duplicación de DTOs respecto al backend C#. | Documentar el camino de migración a `org.openapitools.generator` cuando el spec se estabilice. |
| iOS requiere macOS y Xcode para release. | CI inicial sólo en Linux (compila Desktop + Android). Se añadirá un job en macOS runner en cuanto haya un primer release de iOS. |
| Tres ecosistemas de dependencias (.NET, Python, Gradle). | Aislar el proyecto en `Src/Photonne.Client.Native/` con su propio `.gitignore` y workflow de CI segregado por `paths:`. |
| Compose Multiplatform en iOS aún tiene rough edges (text input, scroll). | Aceptable para v1; revisión de la decisión cuando lleguemos a feature parity con la PWA. |

## Plan de revisión

Esta decisión se revisará cuando ocurra cualquiera de:

- La app móvil supere en MAU al cliente web.
- Compose Multiplatform en iOS publique 1.0 estable o, al contrario, sufra
  regresiones que bloqueen el release.
- El equipo decida unificar lenguaje (por ejemplo migrando todo el
  backend a Kotlin/Ktor).
