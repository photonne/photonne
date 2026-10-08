# Roadmap — Photonne.Client.SPA

Plan de construcción del nuevo cliente web decidido en [ADR-004](ADR-004-client-spa-sveltekit.md): SvelteKit en modo SPA, que sustituye a `Photonne.Client.Web` (Blazor). El alcance es el del cliente nativo sin el backup del dispositivo, más el workspace de gestión y la administración de Client.Web, adaptado a escritorio: selección cómoda, arrastrar y soltar, atajos de teclado y lotes.

Cada fase termina en un commit (o varios) que deja la rama compilando, con lint, comprobación de tipos y tests en verde.

## Fases

- [x] **1. Base.** Proyecto, i18n (es/en), cliente generado del contrato, sesión (login, refresh en cookie, logout, cookie de media), shell con navegación, tokens de diseño claro/oscuro, CI.
- [x] **2. Timeline.** Rejilla justificada sobre el modelo de buckets, virtualizada; scrubber por meses; miniaturas versionadas; selección con teclado y ratón (Shift, Ctrl/Cmd, arrastre).
- [ ] **3. Visor.** Overlay con URL propia, anterior/siguiente, zoom, vídeo y Live Photo, panel de información editable, descarga, atajos.
- [ ] **4. Acciones en lote.** Favorito, archivar, papelera con deshacer, añadir a álbum, mover a carpeta, descargar zip, compartir; arrastrar a álbumes y carpetas.
- [ ] **5. Álbumes y carpetas.** CRUD, álbumes inteligentes con editor de reglas, compartición y permisos; árbol de carpetas, permisos y movimientos.
- [ ] **6. Colecciones y descubrimiento.** Favoritos, archivados, papelera, recuerdos, explorar (etiquetas, objetos, escenas), personas, mapa.
- [ ] **7. Búsqueda y gestión.** Búsqueda con filtros y semántica, organizar (bandeja y reglas), duplicados, utilidades.
- [ ] **8. Subida, notificaciones, ajustes y enlaces públicos.** Subida por arrastre, notificaciones, perfil, seguridad, apariencia e idioma, página pública de enlaces compartidos.
- [ ] **9. Administración.** Usuarios, bibliotecas externas, estadísticas, tareas y colas, mantenimiento, copia de seguridad, ajustes del servidor y de ML, sistema.
- [ ] **10. Sustitución.** El SPA se sirve desde el contenedor del servidor en `/`, PWA, se retira Client.Web y se actualiza la documentación.
