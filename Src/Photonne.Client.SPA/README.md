# Photonne.Client.SPA

Cliente web de Photonne: SvelteKit (Svelte 5) en modo SPA, TypeScript estricto. Sustituirá a `Photonne.Client.Web` (Blazor); la decisión está en [ADR-004](../../docs/ADR-004-client-spa-sveltekit.md) y el avance, en [roadmap-spa.md](../../docs/roadmap-spa.md).

## Desarrollo

Necesita Node 22 y la API en marcha (por defecto en `http://localhost:5030`).

```sh
npm install        # instala y genera el cliente de la API y los mensajes i18n
npm run dev        # http://localhost:5173, con /api redirigido a la API
```

Otra URL para la API: `PHOTONNE_API_URL=http://192.168.1.10:8080 npm run dev`.

El servidor de Vite hace de proxy de `/api`, así que el navegador ve un único origen. Las cookies de sesión (`SameSite=Strict`, `Path=/api`) funcionan igual que en producción, donde el propio servidor sirve el build.

| Comando                | Qué hace                                                                              |
| ---------------------- | ------------------------------------------------------------------------------------- |
| `npm run check`        | Comprobación de tipos (svelte-check)                                                  |
| `npm run lint`         | Prettier y ESLint                                                                     |
| `npm run format`       | Formatea con Prettier                                                                 |
| `npm run test:unit`    | Vitest: tests de Node (`*.spec.ts`) y de componentes en Chromium (`*.svelte.spec.ts`) |
| `npm run test:e2e`     | Playwright contra el build de producción, con la API simulada (`e2e/fake-api.ts`)     |
| `npm run build`        | Build estático en `build/`                                                            |
| `npm run api:generate` | Regenera el cliente de la API                                                         |

Si ya hay un Chromium instalado y no quieres descargar el de Playwright: `CHROMIUM_PATH=/ruta/a/chrome npm run test:e2e`.

## Cómo está montado

- **Cliente de la API**: se genera con [`@hey-api/openapi-ts`](https://heyapi.dev) a partir del contrato versionado del servidor (`../Photonne.Server.Api/openapi/v1.json`) en `src/lib/api/generated`. No se sube al repo: lo crean `npm install` y `npm run api:generate`. Incluye tipos, una función por operación y opciones para TanStack Query.
- **Sesión** (`src/lib/auth/session.svelte.ts`):
  - El access token vive solo en memoria.
  - El refresh token va en una cookie HttpOnly (`refreshTokenInCookie`) que JavaScript no puede leer.
  - Al cargar la página, la sesión se restaura con un refresh.
  - Un 401 provoca un único refresh compartido y repite la petición (`src/lib/api/auth-fetch.ts`).
  - Las fotos y los vídeos cargan con la cookie de media que ponen login y refresh.
- **Datos**: [TanStack Query](https://tanstack.com/query) para caché, reintentos e invalidación.
- **i18n**: [Paraglide](https://inlang.com/m/gerre34r/library-inlang-paraglideJs) con `messages/es.json` (base) y `messages/en.json`. El idioma sale de la preferencia del usuario, después del navegador y por último del español. Los plugins de inlang se cargan desde `node_modules`, no desde una CDN, para que el build funcione sin red.
- **Estilos**: CSS con ámbito de Svelte sobre los tokens de `src/app.css` (modo claro y oscuro con `prefers-color-scheme`).
- **Rutas**: `src/routes/login` es pública; todo lo de `src/routes/(app)` exige sesión y se pinta dentro de `AppShell`.
