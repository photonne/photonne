# Roadmap — Photonne.Client.SPA

Plan de construcción del nuevo cliente web decidido en [ADR-004](ADR-004-client-spa-sveltekit.md): SvelteKit en modo SPA, que sustituye a `Photonne.Client.Web` (Blazor). El alcance es el del cliente nativo sin el backup del dispositivo, más el workspace de gestión y la administración de Client.Web, adaptado a escritorio: selección cómoda, arrastrar y soltar, atajos de teclado y lotes.

Cada fase termina en un commit (o varios) que deja la rama compilando, con lint, comprobación de tipos y tests en verde.

## Fases

- [x] **1. Base.** Proyecto, i18n (es/en), cliente generado del contrato, sesión (login, refresh en cookie, logout, cookie de media), shell con navegación, tokens de diseño claro/oscuro, CI.
- [x] **2. Timeline.** Rejilla justificada sobre el modelo de buckets, virtualizada; scrubber por meses; miniaturas versionadas; selección con teclado y ratón (Shift, Ctrl/Cmd, arrastre).
- [x] **3. Visor.** Overlay con URL propia, anterior/siguiente, zoom, vídeo y Live Photo, panel de información editable, descarga, atajos.
- [x] **4. Acciones en lote.** Favorito, archivar, papelera con deshacer, añadir a álbum, mover a carpeta, descargar zip, compartir; arrastrar a álbumes y carpetas.
- [x] **5. Álbumes y carpetas.** CRUD, álbumes inteligentes con editor de reglas, compartición y permisos; árbol de carpetas, permisos y movimientos.
- [x] **6. Colecciones y descubrimiento.** Favoritos, archivados, papelera, recuerdos, explorar (etiquetas, objetos, escenas), personas, mapa.
- [x] **7. Búsqueda y gestión.** Búsqueda con filtros y semántica, organizar (bandeja y reglas), duplicados, utilidades.
- [x] **8. Subida, notificaciones, ajustes y enlaces públicos.** Subida por arrastre, notificaciones, perfil, seguridad, apariencia e idioma, página pública de enlaces compartidos.
- [x] **9. Administración.** Usuarios, bibliotecas externas, estadísticas, tareas y colas, mantenimiento, copia de seguridad, ajustes del servidor y de ML, sistema.
- [x] **10. Sustitución.** El SPA se sirve desde el contenedor del servidor en `/`, PWA, se retira Client.Web y se actualiza la documentación.

## Huecos de la API detectados al construir el SPA

El SPA los rodea en el cliente; resolverlos en la API simplificaría el cliente o mejoraría el resultado.

- **Visor**
  - `FaceDto` no trae el nombre de la persona (una petición por persona).
  - No hay endpoint de "fotos con las mismas personas".
  - El detalle no indica el origen de la fecha (`capturedAtSource`) ni si se puede escribir la fecha en el archivo.
  - Las fechas de captura llegan sin zona.
  - `PersonAssetDto` no trae versión de miniatura.
  - Guardar un fotograma de Live Photo solo devuelve el id.
  - Los endpoints de caras no tienen `operationId`.
- **Análisis**: `enrichment/retry-all` solo reintenta los fallidos de una foto; no hay "reintentar todos mis fallidos".
- **Lotes**: no hay borrado o salida en lote de álbumes, ni borrado o movimiento en lote de carpetas (el cliente los hace uno a uno).
- **Compartir**
  - Crear álbum y enlace son tres peticiones, sin atomicidad.
  - `GET /api/share/sent` omite los enlaces caducados o agotados, así que no se pueden limpiar.
  - El parámetro `format` de descarga es un string libre.
- **Línea de tiempo**
  - Los buckets son mensuales, sin recuentos por día.
  - `timeline/years` muestrea un máximo de 100 fotos repartidas por el año.
- **Papelera**
  - La lista no trae `deletedAt`.
  - `download-zip` ignora los elementos en la papelera.
- **Personas**: el endpoint de fotos de una persona no devuelve `TimelineResponse`.
- **Mapa**: `MapPointResponse` no trae tipo ni proporción.
