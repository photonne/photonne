# Timeline por buckets — plan de implementación

Refactor del timeline (servidor + cliente nativo) del modelo actual de
*stream plano paginado por cursor* a un modelo de **buckets por mes** estilo
Immich, donde el cliente conoce el esqueleto completo de la biblioteca desde
el primer request y carga el contenido de cada mes bajo demanda.

## Motivación

Hoy la vista anual no carga menos datos que la mensual: es el mismo timeline
paginado (80 items/request), solo cambia el tamaño de celda y la granularidad
de los headers. El problema real no es el render (ya hay virtualización con
`LazyColumn`) sino el **acceso aleatorio**: para llegar a 2018 hay que paginar
a través de todos los assets de 2019–2026. Con años visualmente comprimidos
(celdas de 35dp) el usuario cruza años muy rápido y la paginación no da
abasto.

## Idea central

1. Al abrir el timeline, el cliente descarga **solo el esqueleto**:
   `[(año-mes, count)]` — unos bytes por mes, toda la biblioteca en 1 request.
2. Con counts + ancho de celda, la altura de cada mes es **determinista** →
   el scroll completo existe desde el segundo cero, con skeletons.
3. Cada bucket carga su contenido **cuando entra en viewport** (± prefetch),
   con cache LRU y evicción.
4. Salto a cualquier fecha = scroll instantáneo a un header que ya existe.
   Esto hace trivial la UX objetivo: click en vista anual → zoom a mensual
   anclado en ese mes (en vez de abrir el detalle).

`/api/assets/timeline` (cursor) se mantiene intacto — lo usa el cliente web.

---

## Fase 0 — Unificar el predicado de visibilidad (server, prerequisito)

Los cuatro endpoints de timeline duplican el `WHERE` de visibilidad y ya han
divergido: `TimelineEndpoint` excluye la mitad `.mov` de los Live Photos
(`MotionPhotoPart`), pero `TimelineGridEndpoint`, `TimelineIndexEndpoint` y
`TimelineNeighborsEndpoint` no. Para buckets esto deja de ser cosmético: si
el count del bucket no coincide exactamente con su contenido, la reserva de
altura se rompe.

- Extraer a `Features/Timeline/TimelineQuery.cs` un helper compartido:
  - `VisibleAssets(db, allowedIds)` → el `WHERE` canónico:
    `DeletedAt == null && !IsArchived && !IsFileMissing && FolderId ∈ allowed
    && !MotionPhotoPart`.
  - Mover ahí también `HydrateTagsAsync` (hoy privado en `TimelineEndpoint`).
- Reescribir los 4 endpoints sobre el helper. Esto arregla de paso que
  grid/index/neighbors hoy muestran las mitades `.mov` de Live Photos.
- Test de integración: mismo dataset → mismo total en los 4 endpoints.

## Fase 1 — Endpoints de buckets (server)

**`GET /api/assets/timeline/buckets`**

```json
[ { "key": "2026-06", "count": 412 }, { "key": "2026-05", "count": 1893 } ]
```

- `GroupBy(a => new { a.CapturedAt.Year, a.CapturedAt.Month })`, orden DESC.
  Verificar que el índice de `20260526220813_AddTimelineAssetIndex` cubre el
  aggregate.
- Granularidad **mes** fija. La vista anual agrega meses en cliente; la
  diaria subdivide el bucket cargado en cliente. Sin parámetro.

**`GET /api/assets/timeline/buckets/{yearMonth}`**

- Todos los `TimelineResponse` de ese mes,
  `OrderByDescending(CapturedAt).ThenByDescending(FileModifiedAt)`, con
  `HydrateTagsAsync`. Sin paginación interna (un mes está acotado en la
  práctica; si algún día un mes supera ~5k, se añade cursor interno —
  límite conocido, no se construye ahora).
- Crítico: ambos endpoints usan **exactamente** el predicado de Fase 0 y la
  **misma expresión de agrupación**, para que `count == items.Count` siempre.

**Tests** (integración): suma de counts == total del timeline; contenido de
bucket == slice equivalente de `/timeline`; exclusiones (archived,
file-missing, MotionPhotoPart); permisos de carpetas.

## Fase 2 — Capa de datos nativa

- `TimelineModels.kt`: `TimelineBucket(key: String, count: Int)` + DTOs.
- `PhotonneApi`: `getTimelineBuckets()`, `getTimelineBucketItems(key)`.
- Nuevo `TimelineBucketStore` en `data/timeline/`:
  - `StateFlow<List<BucketState>>` donde
    `BucketState = { key, count, items: List<TimelineItem>?, isLoading }`.
  - `ensureLoaded(keys)`: dedup de requests en vuelo, prefetch, cache.
  - Evicción LRU de `items` (no de counts) más allá de ~12 buckets cargados.
  - `refresh()`: re-fetch de buckets, invalida cache, recarga los visibles.
- Tests en `TimelineRepositoryTest.kt`: cache/dedup/evicción/refresh.

## Fase 3 — ViewModel + UI

**`TimelineViewModel`**: `items/nextCursor/hasMore/loadMore()` →
`buckets: List<BucketState>` + `ensureVisible(keys)`. Las mutaciones locales
(`removeItemLocal`, `setFavorite`, bulk ops) operan sobre el bucket que
contiene el asset **y ajustan su `count`** (si no, la altura reservada
deriva).

**`GroupedAssetGrid` / `TimelineScreen`**: emisión por bucket:

- Bucket cargado → `groupTimelineEntries` + `packJustifiedRows` por bucket
  (el packer ya hace flush en headers).
- Bucket sin cargar → `stickyHeader` + placeholder de altura determinista:
  `ceil(count / floor(anchoContenedor / celda)) * altoFila`, con skeleton
  tiles cuadrados.
- Detección de visibilidad: `snapshotFlow` sobre `layoutInfo.visibleItemsInfo`
  → keys de bucket visibles → `ensureVisible(visibles ± 1)`. Sustituye al
  `PREFETCH_THRESHOLD`/`onLoadMore` actual.
- Keys estables (`h:2026-05`, `ph:2026-05`, filas por asset-id) para que
  `LazyColumn` preserve el scroll al hidratar un placeholder. Si hay salto
  (altura estimada ≠ real), compensar con `scrollBy(delta)` — ajuste fino.
- Zoom `Year`: headers solo de año (suprimir los de mes), placeholders
  siguen siendo por mes. `findRowIndexForDate` funciona sin cambios y ahora
  siempre encuentra el header — saltos de fecha y re-anclajes instantáneos.

**Merge con fotos del dispositivo** (`mergeTimelineWithLocal`): por bucket —
agrupar los locales por mes, mezclar en buckets cargados; meses solo-locales
→ bucket sintético. Limitación asumida: el dedup por checksum solo aplica en
buckets cargados.

**Pager de detalle**: v1 pasa la concatenación de la **racha contigua de
buckets cargados** alrededor del click — el pager queda acotado a esa racha.
Mejora posterior: extender el pager con `/api/assets/{id}/timeline-neighbors`
(ya existe en el server; el cliente nativo no lo usa).

## Fase 4 — La feature objetivo: click anual → mensual

- `onItemClick` con `zoomLevel.grouping == Year` → `zoomStore.update(Month)`
  + `pendingJumpDate = item.fileCreatedAt` (reutiliza el flujo de
  jump-to-date) en vez de `onOpenAsset`.
- Desactivar long-press/selección en vista anual.
- Ya no hace falta cap de N filas: la vista anual es barata porque solo
  carga lo visible.

## Fase 5 — Vista anual comprimida (muestreo en servidor)

Los buckets resuelven el coste de *datos* de la vista anual, pero no su
*longitud*: cada asset sigue ocupando celda (real o skeleton), así que un año
de 8.000 fotos mide ~50 pantallas. La compresión:

**`GET /api/assets/timeline/years?sample=N`** — por año (descendente):
`{ year, count, items[≤N] }`, donde `items` es una muestra distribuida
uniformemente sobre el timeline del año (cada ⌈count/N⌉-ésimo item, más
recientes primero). Dos fases: pasada ligera `(Id, CapturedAt)` sobre toda la
biblioteca visible (misma clase de coste que el grid endpoint) + hidratación
ID-bounded vía la proyección compartida.

**Cliente**: `TimelineBucketStore.ensureYearSummaries(sample)` (cache por
tamaño de muestra, dedup en vuelo, se invalida en `refresh()`); el nivel de
zoom `Year` renderiza `buildYearSummaryEntries` + `truncateRowsPerGroup`
(máx. 5 filas por año) con el **total del año en el header** ("8 214 fotos")
para que la muestra nunca parezca "todas mis fotos". `sample` = columnas × 5
según el ancho actual. La carga de buckets por visibilidad se desactiva en
vista anual (las summaries son autocontenidas); el click sigue navegando al
mes (Fase 4).

## Fase 6 (opcional) — Scrubber lateral

Con alturas deterministas por mes, un scrubber con marcas de año estilo
Google Photos sale casi gratis (solo necesita `/buckets`). Fuera de alcance.

---

## Decisiones abiertas / riesgos

| Tema | Riesgo | Propuesta |
|---|---|---|
| **Timezone** | Server agrupa por `CapturedAt` UTC; el cliente agrupa con `TimeZone.currentSystemDefault()`. Fotos cerca de medianoche de fin de mes caerían en bucket ≠ header. | Tratar `CapturedAt` como wall-clock (EXIF no lleva tz) y agrupar/etiquetar en UTC también en cliente para el timeline. Verificar semántica real con el trabajo de capture-date provenance. |
| **`toggleSelectAll`** | "Seleccionar todo" sobre buckets parcialmente cargados es ambiguo. | Restringirlo a lo cargado y renombrar la acción, o selección por día/mes. Decidir en Fase 3. |
| **Meses gigantes** | Un mes con miles de assets llega en una sola response. | Aceptarlo en v1; cursor interno de bucket como evolución. |
| **Salto de scroll al hidratar** | Altura estimada vs real difieren. | Keys estables + compensación `scrollBy`; pulir al final. |

## Orden de PRs

Cada fase es un commit/PR independiente y desplegable: 0 y 1 no tocan
cliente; 2 no toca UI; 3 es el swap (el código de cursor en
`TimelineViewModel` se elimina ahí, sin doble vía); 4 es la feature visible.
