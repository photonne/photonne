# Roadmap API — preparación para el cliente web

Revisión del 2026-10-08 sobre `Src/Photonne.Server.Api`, hecha al decidir el nuevo cliente web ([ADR-004](ADR-004-client-spa-sveltekit.md)). Las rutas son relativas a esa carpeta.

No se reescribe la API. ASP.NET Core 10 con minimal APIs por feature sigue siendo la base, y el modelo de buckets del timeline ya es el diseño correcto para una rejilla virtualizada. Lo que sigue son las adaptaciones que pide un navegador, que tiene restricciones que el cliente nativo no tiene.

Esfuerzo: S pequeño, M medio, L grande.

## Fase 0 — Seguridad (independiente de la web, hacer ya)

Ya figuraba como deuda consciente en [ADR-003](ADR-003-user-workspace.md) ("endpoints de media anónimos protegidos solo por GUID"). Se amplía con lo encontrado en esta revisión.

- [x] **1. Media sin autenticación ni control de propiedad** (M). Cerrado con `AssetVisibilityService.CanReadAsync` (dueño, carpeta legible aunque esté oculta del timeline, biblioteca, álbum compartido; admin lo lee todo) y un test que exige `RequireAuthorization` o `AllowAnonymous` en todo `/api`. Un extraño recibe 404, igual que si el asset no existiera. `Features/AssetDetail/AssetContentEndpoint.cs` (`/api/assets/{id}/content`) y `Features/Thumbnails/ThumbnailEndpoint.cs` (`/api/assets/{id}/thumbnail`) no tienen `RequireAuthorization()`. `Program.cs` no define ninguna política por defecto, y ningún handler comprueba que el asset sea visible para quien lo pide. Revisar también `/motion` y `/api/assets/pending/content`. Toca servidor, KMP y la web a la vez.
- [x] **2. `/thumbnails` como ficheros estáticos anónimos** (S). Eliminado: ningún cliente lo usaba. `Program.cs` monta el directorio completo de miniaturas con `UseStaticFiles` y un `PhysicalFileProvider`.
- [x] **3. Stack traces en las respuestas 500** (S). Fuera de Development solo se envían el título y el `traceId`; la excepción va al log. El middleware de excepciones de `Program.cs` añade `ex.ToString()` y `ex.Message` en producción. Se deben incluir solo en Development.
- [x] **4. `Cache-Control: public` en contenido privado** (S). `ThumbnailEndpoint` permite que proxies y CDN compartidos guarden fotos privadas. Debe ser `private`.
- [x] **5. Rutas físicas en los mensajes de error** (S). Cerrado en los endpoints de media. Con el punto 9 ya ningún endpoint devuelve `ex.Message` como error: solo quedan, recortados, en las notificaciones y el progreso de las tareas de admin.

## Fase 1 — Base para la web (antes de construir pantallas)

- [x] **6. Autenticación por cookie para el navegador** (L). Dos cookies HttpOnly, `SameSite=Strict`:
  - `photonne_media` (`Path=/api`) lleva el access token y **solo** se acepta en GET/HEAD de media (miniatura, original, clip, cara). La ponen login y refresh; `POST /api/auth/media-session` la crea desde el Bearer de una sesión anterior.
  - `photonne_refresh` (`Path=/api/auth`) es opcional: con `refreshTokenInCookie: true` en el login, el refresh token nunca llega a JavaScript. Refresh lo lee de la cookie y la rota, y `POST /api/auth/logout` lo revoca y borra ambas cookies.

  Las mutaciones siguen exigiendo `Authorization: Bearer` (el access token vive en memoria de la página), así que la cookie no abre superficie CSRF y no hace falta antiforgery. El nativo no cambia.

- [x] **7. Caché HTTP correcta para la media** (M). `MediaCaching`: ETag (tamaño + mtime del fichero) y Last-Modified con 304 en miniatura, original, clip y miniaturas de enlaces compartidos; `?v=` → inmutable un año, sin versión → `private, max-age=86400`. El timeline expone `thumbnailsGeneratedAt` como versión de las miniaturas; para el original sirve el `checksum`. URLs versionadas (`?v=<checksum o fecha de generación>`), `Cache-Control: private, max-age=31536000, immutable` en las versionadas y `ETag`/`Last-Modified` en el resto. Así una miniatura regenerada (por ejemplo, tras rotar) se refresca sola.
- [x] **8. Servir los ficheros en streaming** (S). Miniaturas (también las de enlaces compartidos) y caras se sirven desde disco con `Results.File(path)`. Queda en memoria el clip incrustado en una foto en movimiento, que es una porción del fichero. `ThumbnailEndpoint` carga el fichero entero con `File.ReadAllBytesAsync`. Pasar a `Results.File(path, ..., enableRangeProcessing: true)` o `PhysicalFile`, con Range y sin copiar el fichero en memoria.
- [x] **9. Contrato OpenAPI tipado** (L). Las 233 operaciones responden con `TypedResults` y el documento describe todas sus respuestas de éxito. Los errores 4xx son `ApiError { error, code }`: `error` mantiene el texto que leen las apps publicadas y `code` es estable. Las 500 son ProblemDetails del middleware, con `traceId` y sin detalles internos. El documento se sirve en todos los entornos (`/openapi/v1.json`), declara el esquema Bearer y se versiona en `Src/Photonne.Server.Api/openapi/v1.json`, del que se generará el cliente del SPA. Dos tests lo protegen: la instantánea (`UPDATE_OPENAPI_SNAPSHOT=1` para regenerarla) y un contador de operaciones sin tipar que debe seguir en 0.

- [x] **10. Política de compatibilidad** (M). La versión mínima ya existía: `PhotonneMinClientVersion`/`PhotonneMinServerVersion` en `Src/Directory.Build.props`, que se publican en `GET /api/version` y hacen que la app pida actualizar. No se añade la cabecera `X-Photonne-Client`, porque ese mecanismo ya cubre el caso. Lo nuevo:
  - la política escrita en [api-compatibilidad.md](api-compatibilidad.md): solo cambios que añaden; cuando algo no puede serlo, se sube la versión mínima de cliente o se usa `/api/v2`;
  - el workflow `api-contract.yml`, que en cada PR del servidor comprueba la instantánea del contrato y compara con oasdiff contra la rama base. Un cambio incompatible solo pasa si la PR sube `PhotonneMinClientVersion`.

## Fase 2 — Calidad de la experiencia web (según lo pida el SPA)

- [ ] **11. Formatos de imagen modernos** (M). Negociar AVIF o WebP según `Accept` (el generador ya soporta WebP) y añadir tamaños pensados para `srcset`.
- [ ] **12. Placeholders en la respuesta del timeline** (M). Un thumbhash por asset (unos 25 bytes) en los items del bucket, para que la rejilla pinte al instante.
- [ ] **13. Subida reanudable** (L). Protocolo tus (tusdotnet) o chunks propios. La subida actual es de una sola petición, y un vídeo grande desde el navegador necesita poder reanudarse.
- [ ] **14. Tiempo real con SSE** (M). Ahora no hay canal de tiempo real. Usar Server-Sent Events para el progreso de indexación y de tareas, las notificaciones y los assets nuevos.
- [ ] **15. Vídeo reproducible en navegador** (L). Chrome y Firefox no reproducen bien HEVC/MOV. Transcodificar a una versión H.264 (o AV1) para web, o servir HLS.
- [ ] **16. Previews de enlaces compartidos** (S). Para `/s/{token}`, el servidor inyecta las meta Open Graph (título, miniatura del álbum) en el `index.html` del SPA. Así hay previews en mensajería sin necesidad de SSR.

## Orden recomendado

1. Fase 0 completa, sin esperar a la web.
2. Puntos 6, 7, 8 y 9, que son la base del SPA. El 9 puede avanzar por feature, al ritmo que el SPA vaya consumiendo cada una.
3. Punto 10 antes del siguiente release de las apps.
4. Fase 2 bajo demanda.
