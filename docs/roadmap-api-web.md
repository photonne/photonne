# Roadmap API — preparación para el cliente web

Revisión del 2026-10-08 sobre `Src/Photonne.Server.Api`, hecha al decidir el nuevo cliente web ([ADR-004](ADR-004-client-spa-sveltekit.md)). Las rutas son relativas a esa carpeta.

No se reescribe la API. ASP.NET Core 10 con minimal APIs por feature sigue siendo la base, y el modelo de buckets del timeline ya es el diseño correcto para una rejilla virtualizada. Lo que sigue son las adaptaciones que pide un navegador, que tiene restricciones que el cliente nativo no tiene.

Esfuerzo: S pequeño, M medio, L grande.

## Fase 0 — Seguridad (independiente de la web, hacer ya)

Ya figuraba como deuda consciente en [ADR-003](../Src/Photonne.Client.Web/docs/ADR-003-user-workspace.md) ("endpoints de media anónimos protegidos solo por GUID"). Se amplía con lo encontrado en esta revisión.

- [x] **1. Media sin autenticación ni control de propiedad** (M). Cerrado con `AssetVisibilityService.CanReadAsync` (dueño, carpeta legible aunque esté oculta del timeline, biblioteca, álbum compartido; admin lo lee todo) y un test que exige `RequireAuthorization` o `AllowAnonymous` en todo `/api`. Un extraño recibe 404, igual que si el asset no existiera. `Features/AssetDetail/AssetContentEndpoint.cs` (`/api/assets/{id}/content`) y `Features/Thumbnails/ThumbnailEndpoint.cs` (`/api/assets/{id}/thumbnail`) no tienen `RequireAuthorization()`. `Program.cs` no define ninguna política por defecto, y ningún handler comprueba que el asset sea visible para quien lo pide. Revisar también `/motion` y `/api/assets/pending/content`. Toca servidor, KMP y la web a la vez.
- [x] **2. `/thumbnails` como ficheros estáticos anónimos** (S). Eliminado: ningún cliente lo usaba. `Program.cs` monta el directorio completo de miniaturas con `UseStaticFiles` y un `PhysicalFileProvider`.
- [x] **3. Stack traces en las respuestas 500** (S). Fuera de Development solo se envían el título y el `traceId`; la excepción va al log. El middleware de excepciones de `Program.cs` añade `ex.ToString()` y `ex.Message` en producción. Se deben incluir solo en Development.
- [x] **4. `Cache-Control: public` en contenido privado** (S). `ThumbnailEndpoint` permite que proxies y CDN compartidos guarden fotos privadas. Debe ser `private`.
- [x] **5. Rutas físicas en los mensajes de error** (S). Cerrado en los endpoints de media. Quedan unos 35 endpoints autenticados (casi todos de admin) que devuelven `ex.Message`; se revisarán con el punto 9, al tipar las respuestas. Varios 404 devuelven la ruta del fichero en disco (`File not found at: ...`).

## Fase 1 — Base para la web (antes de construir pantallas)

- [ ] **6. Autenticación por cookie para el navegador** (L). `<img>` y `<video>` no pueden enviar `Authorization`, y sin cookie habría que bajar cada imagen con `fetch` y convertirla en blob, sin caché HTTP. Se añade un esquema de cookie HttpOnly (`SameSite=Strict`, `Secure`, protección antiforgery en las mutaciones) junto al JWT Bearer, con una política que acepte cualquiera de los dos. El refresh token web viaja en cookie HttpOnly y deja de estar en `localStorage`, expuesto a XSS. El nativo sigue con Bearer y el `access_token` por query para libvlc.
- [ ] **7. Caché HTTP correcta para la media** (M). URLs versionadas (`?v=<checksum o fecha de generación>`), `Cache-Control: private, max-age=31536000, immutable` en las versionadas y `ETag`/`Last-Modified` en el resto. Así una miniatura regenerada (por ejemplo, tras rotar) se refresca sola.
- [ ] **8. Servir los ficheros en streaming** (S). `ThumbnailEndpoint` carga el fichero entero con `File.ReadAllBytesAsync`. Pasar a `Results.File(path, ..., enableRangeProcessing: true)` o `PhysicalFile`, con Range y sin copiar el fichero en memoria.
- [ ] **9. Contrato OpenAPI tipado** (L, incremental). Hay unos 333 `Results.Ok/NotFound(...)` sin tipo y ningún `TypedResults` ni `Results<...>`, así que el OpenAPI no describe las respuestas. Hay que migrar a `TypedResults` y `Results<Ok<T>, NotFound, ...>` por feature, usar ProblemDetails homogéneo con un `code` estable por error y emitir el documento OpenAPI también en Release (solo el JSON, sin Scalar). Con eso se genera el cliente TypeScript del SPA.
- [ ] **10. Política de compatibilidad** (M). Con apps en las tiendas que no se pueden forzar a actualizar, la API es un contrato público. Habrá cambios solo aditivos, o `/api/v2` para lo incompatible. Se añade la cabecera `X-Photonne-Client: <plataforma>/<versión>`, con una respuesta "actualiza la app" por debajo de una versión mínima, y un job de CI que compare el OpenAPI generado con el de `main` y falle si hay cambios incompatibles.

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
