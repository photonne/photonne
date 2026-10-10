# ADR-004 — Nuevo cliente web: Photonne.Client.SPA (SvelteKit)

- **Estado**: Aceptado
- **Fecha**: 2026-10-08
- **Sustituye a**: [ADR-002](ADR-002-pwa-admin-console.md) y [ADR-003](ADR-003-user-workspace.md)

## Contexto

Con `Photonne.Client.Native` llegando a Google Play y App Store, se replantea la web. ADR-003 recuperó una superficie de usuario en Blazor (el workspace de gestión) y descartó un stack web nuevo por el coste permanente de un tercer stack para un único mantenedor.

Ese criterio cambia. El tiempo de desarrollo deja de ser la restricción y el objetivo pasa a ser **la mayor calidad posible** de la web. En una web de fotos, calidad significa:

- rejilla virtualizada fluida con más de 100k assets;
- imágenes servidas por el pipeline del navegador (`<img>`, `srcset`, AVIF/WebP, caché HTTP);
- comportamiento de web real: URLs profundas, atrás/adelante, selección de texto, Ctrl+F, abrir en otra pestaña;
- accesibilidad;
- carga inicial rápida;
- previews de los enlaces compartidos.

Opciones evaluadas:

| Opción | Motivo de descarte |
|---|---|
| Compose Multiplatform Web (Wasm) | Renderiza en `<canvas>`: pierde la selección de texto, Ctrl+F, la accesibilidad y el scroll nativo, y el bundle ocupa varios MB. |
| KMP solo en la lógica + UI en el DOM | La interoperabilidad Kotlin→JS añade fricción, y el cliente generado desde OpenAPI ya cubre lo reutilizable. |
| Seguir con Blazor WASM | Payload y arranque pesados. Rejillas enormes e imágenes dependen de la interoperabilidad JS. Techo de calidad más bajo. |
| React + Vite + TanStack | Alternativa sólida y la más cercana. Más código de infraestructura y bundle algo mayor que Svelte. |
| SSR completo (Next/Nuxt/SvelteKit node) | Obliga a un proceso Node en el contenedor self-hosted, y en una app detrás de login el SSR apenas aporta. |
| Flutter Web | Los mismos problemas de canvas, y otro lenguaje más. |

## Decisión

1. **Nuevo proyecto `src/Client.SPA`**: SvelteKit (Svelte 5) en modo SPA (`adapter-static`, `ssr = false`) y TypeScript estricto.
2. **Reemplaza a `Photonne.Client.Web`** (Blazor). Cuando alcance la paridad, Client.Web y `tests/Client.Web.Tests` se retiran, y ADR-002 y ADR-003 se mueven a `docs/` como histórico.
3. **Alcance**: todo lo que ofrece el cliente nativo **excepto el backup del dispositivo** (`ui/devicebackup`, que es propio del dispositivo), más lo que hoy cubre Client.Web: el workspace de gestión de ADR-003 y toda la administración del servidor. No sustituye a la landing (`site/`).
4. **Versión adaptada a escritorio**, no un port de la UI táctil: selección con Shift/Ctrl y por arrastre, drag & drop (subir, mover a álbum o carpeta), atajos de teclado en todas las vistas, menús contextuales, operaciones en lote, y paneles que aprovechan pantallas anchas.
5. **Despliegue**: el build estático se copia en el `wwwroot` de `Photonne.Server.Api` (etapa Node en el `Dockerfile`), con fallback a `index.html`. Sigue siendo un solo contenedor y sin Node en tiempo de ejecución.
6. **Contrato**: cliente TypeScript generado desde el OpenAPI del servidor y regenerado en CI. Se usan los requisitos de la API recogidos en [roadmap-api-web.md](roadmap-api-web.md), Fases 0 y 1.

Piezas técnicas previstas, a concretar al montar el proyecto: TanStack Query (Svelte) para la caché de datos, rejilla justificada propia sobre el modelo de buckets (se porta la lógica de `JustifiedLayout` y `SelectionState` de Client.Web), MapLibre GL para el mapa, `<video>` nativo (con hls.js si se adopta HLS), Playwright para e2e y Vitest para unit.

## Consecuencias

- **El repo incorpora un tercer stack** (Node/TypeScript como herramienta de build). Es el coste que ADR-003 quiso evitar, asumido de forma consciente por el objetivo de calidad.
- **Convivencia durante la migración**: Client.Web sigue sirviéndose en `/` hasta la paridad. El SPA se desarrolla con su servidor de Vite y un proxy a la API. El cambio de uno a otro se hace en un único commit cuando el SPA cubra el alcance.
- **La API evoluciona antes** que las pantallas: autenticación por cookie para `<img>`/`<video>`, caché HTTP, OpenAPI tipado y endurecimiento de la media anónima (ver roadmap).
- **Las apps nativas no cambian.** La API mantiene la compatibilidad con las versiones publicadas en las tiendas.
