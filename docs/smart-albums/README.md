# Álbumes inteligentes (condicionales)

Estado: **diseño / en construcción**. Fase 1 (extraer `AssetQueryBuilder`) iniciada.
Última actualización: 2026-07-09.

## 1. Problema

La creación de álbumes hoy (`Features/Albums/AlbumsEndpoint.cs`) es 100% manual: el
usuario añade fotos una a una a una tabla puente `AlbumAsset`. Queremos **álbumes que se
describen por una condición** y se rellenan solos:

- a partir de **personas** seleccionadas
- a partir de **lugares**
- especificando **carpetas** (personales y compartidas)
- fechas, escenas, objetos, tags… (extensible)

Un álbum inteligente no *contiene* fotos: **describe** qué fotos contiene. Al abrirlo se
ejecuta la consulta; una foto nueva que cumple la regla aparece sola, sin trabajo manual.

## 2. Punto de partida: el motor ya existe

El 90% del motor de filtrado ya está construido en `Features/Search/SearchEndpoint.cs`.
Ese endpoint ya compone un `IQueryable<Asset>` y encadena `.Where(...)` por casi todas las
dimensiones. Un álbum inteligente **no es un motor nuevo**: es **guardar una consulta** y
resolverla contra ese pipeline.

| Dimensión | Dónde vive | Cómo se filtra hoy |
|---|---|---|
| **Personas** | `UserFaceAssignment` (identidad **por-usuario**) | `Faces` → `UserFaceAssignment` con `UserId==u && PersonId==p && !IsRejected` |
| **Carpetas** (personal/compartida) | `Folder` + `FolderPermission` | `allowedFolderIds.Contains(a.FolderId)` o `FullPath.Contains(path)` |
| **Fechas** | `Asset.CapturedAt` (indexado) | rango `from`/`to` |
| **Objetos** (perro, coche…) | `AssetDetectedObject.Label` | `DetectedObjects.Any(o => o.Label==…)` |
| **Escenas** (playa, montaña…) | `AssetClassifiedScene.Label` (Places365) | `ClassifiedScenes.Any(…)` |
| **Tags** usuario / estructurales | `UserTag` / `AssetTag` (enum LivePhoto, Portrait…) | `UserTags.Any` / `Tags.Any` |
| **Texto en imagen (OCR)** | `AssetRecognizedTextLine` | full-text `tsvector` |
| **Semántica (CLIP)** | `AssetEmbedding` (pgvector) | `SemanticSearchEndpoint.cs` |
| **Lugares con nombre** | ❌ **no existe** | solo `AssetExif.Latitude/Longitude` en crudo, sin geocoding inverso |

Piezas de visibilidad reutilizables:

- `Features/Timeline/TimelineQuery.cs` → `VisibleAssets(db, allowedFolderIds)`: `IQueryable<Asset>`
  base gateado por carpetas (lo consumen ~15 endpoints).
- `Shared/Authorization/AssetVisibilityService.cs` → `AssetPredicate()`: `Expression<Func<Asset,bool>>`
  que une owner + carpeta + librería externa + álbum.
- `Features/Timeline/AllowedFolderCache.cs` → resuelve el `HashSet<Guid>` de carpetas legibles
  (con herencia de subárbol y opt-out por usuario), cacheado 30s.

## 3. Idea central

> **Álbum condicional = consulta guardada** (árbol de predicados) que se resuelve
> **dinámicamente** contra el pipeline de assets, en vez de una lista fija de `AlbumAsset`.

## 4. Modelo de datos (mínimo, reutilizando `Album`)

No se crea una entidad paralela: se extiende `Shared/Models/Album.cs`.

```csharp
public AlbumKind Kind { get; set; }               // Manual = 0, Smart = 1
public string? SmartRule { get; set; }             // JSONB, null en manuales
public SmartResolveMode ResolveMode { get; set; }  // Dynamic | Materialized
public DateTime? LastMaterializedAt { get; set; }
```

- `Kind == Manual` → todo sigue igual (`AlbumAsset`). **Cero regresión.**
- `Kind == Smart` → la membresía sale de `SmartRule` (ver `rule-schema.md`).
- **Compartición, portada y permisos sin tocar**: un smart album se comparte con el mismo
  `AlbumPermission` (flags `CanRead/Write/Delete/ManagePermissions`) que ya existe.
- **Overrides manuales sobre un smart album**: reutilizar `AlbumAsset` con una columna
  `Pinned` (incluir siempre) + una lista de exclusiones (`AlbumExcludedAsset`). Permite
  "todo lo de la abuela **menos** estas 3" o "…**más** esta que la cara no se detectó bien".
  Es el detalle que separa un juguete de algo usable.

## 5. Motor de reglas

Regla = **árbol booleano** (AND / OR / NOT) de condiciones tipadas, serializado a JSON en
`Album.SmartRule`. Cada condición mapea 1:1 a un `.Where()` que **ya existe** en Search.
Detalle completo del esquema y el catálogo de condiciones en **`rule-schema.md`**.

Diseño para extensibilidad: cada `type` de condición es un **handler**
(`IConditionHandler`) registrado en un diccionario; añadir una dimensión futura (cámara EXIF,
solo vídeos, favoritas…) = una clase nueva sin tocar el resto. Eso da el "etc." gratis.

**Refactor base (fase 1):** extraer la cadena de `.Where()` de `SearchEndpoint.cs` a un
`AssetQueryBuilder` reutilizable, para que **Search y Smart Albums compartan exactamente el
mismo código de filtrado** (un solo sitio que mantener, consistencia garantizada).

## 6. Resolución: dinámica vs materializada → híbrido

| | Dinámica (al abrir) | Materializada (job llena `AlbumAsset`) |
|---|---|---|
| Frescura | Siempre exacta | Con retraso hasta el job |
| Coste lectura | Query por apertura | Barato (lista escrita) |
| Portada/orden | Recalcular | Estable |

**Decisión: híbrido, arrancando en dinámico.**

- **Dinámico por defecto**: al abrir, ejecutar `AssetQueryBuilder` con la regla + gate de
  visibilidad + paginado keyset por `CapturedAt` (idéntico al timeline). Simple y siempre correcto.
- **Materialización opcional** (`ResolveMode.Materialized`) para álbumes pesados o muy
  compartidos: un worker estilo `AssetEnrichmentTask` reconcilia `AlbumAsset` en background,
  re-disparado por eventos: import de asset, `FaceRecognitionCompletedAt`, asignación de cara,
  cambio de permisos de carpeta. Invalida la caché `albums:{userId}` existente.

Empezar dinámico; añadir materialización solo si la medición lo pide.

## 7. Los tres temas espinosos

### a) Las personas son por-usuario
`UserFaceAssignment` está keyed por `UserId`: "la abuela" (cluster mío) no es la tuya. Un
smart album de personas compartido no puede resolver `PersonId` ajenos.
→ **Decisión (confirmada con Marc): resolución anclada al dueño (owner-anchored).** El álbum
muestra "las fotos del dueño que cumplen la regla, tal como el dueño las ve"; a los invitados
se les muestra ese resultado. Descartada la "vista personal por-espectador" para el MVP.

### b) Visibilidad al compartir
Aun anclando al dueño, hay que gatear: un invitado no debe ver por la regla una foto a la
que no llega. → resolver con la **intersección** de `AssetVisibilityService` del dueño **y**
del espectador (`AssetPredicate()` ya da ese `Expression`).

### c) Lugares = el único hueco real
Hoy solo hay lat/long crudas (`AssetExif`), sin nombres ni índice geo.
→ **Decisión (confirmada con Marc): sin lugares en el MVP.** Arrancamos con personas,
carpetas, fechas, escenas, objetos y tags. Lugares en fase posterior, en dos niveles:
1. radio/bbox sobre un pin del mapa (+ índice en `Latitude/Longitude`);
2. geocoding inverso en el pipeline de enrichment → `City`/`Country` → reglas por nombre.

## 8. Creación del álbum

**No se crea desde el buscador** (decisión de Marc). El flujo de creación es una superficie
de primera clase, en discusión. Ver **`creation-ux.md`**.

## 9. Fases

1. ✅ **Refactor** — extraído `AssetQueryBuilder` de `SearchEndpoint` (sin cambio funcional),
   con `AssetConditions` como fuente única de predicados. 6 tests (`SearchFilterTests`).
2. ✅ **Motor + preview** — árbol de reglas (`SmartRuleNode` → `SmartRuleCompiler` con
   AND/OR/NOT + `PredicateBuilder`), `SmartAlbumResolver` (expande subcarpetas + gate de
   visibilidad owner-anchored, interseca con el espectador si difiere), y endpoint dry-run
   `POST /api/albums/preview`. Condiciones: person (any/all), folder (subárbol), dateRange,
   scene, object, tag, favorite, mediaType, ocr, text.
   ✅ **Persistencia** — `Album.Kind/SmartRule(jsonb)/ResolveMode/LastMaterializedAt` +
   migración `AddSmartAlbums`; `POST /api/albums` acepta `smartRule` (valida compilando →
   400 si es inválido) y `GET /api/albums/{id}/assets` resuelve dinámico para smart albums.
   Tests: `SmartAlbumPreviewTests` (11) + `SearchFilterTests` (6), verdes contra pgvector real.
3. **UI** — superficie de creación "Nuevo álbum inteligente" (ver `creation-ux.md`) en cliente KMP.
4. **Overrides** manuales (pin/exclude) sobre smart albums.
5. **Lugares** (radio → geocoding inverso).
6. **Materialización** en background (solo si el rendimiento lo pide).

Piezas implementadas (fases 1-2): `Shared/Services/AssetQueryBuilder.cs` + `AssetFilter`,
`Shared/Services/SmartAlbums/{PredicateBuilder,AssetConditions,SmartRuleNode,SmartRuleCompiler,SmartAlbumResolver}.cs`,
`Features/Albums/AlbumPreviewEndpoint.cs`, `Album` model + `AddSmartAlbums` migración,
`AlbumsEndpoint` (create/read). Tests: `Tests/…/Search/SearchFilterTests.cs`,
`Tests/…/Albums/SmartAlbumPreviewTests.cs`.

## 10. Archivos que se tocarán (referencia)

- `Shared/Models/Album.cs`, `AlbumAsset.cs` — modelo.
- `Shared/Data/ApplicationDbContext.cs` — config EF + migración.
- `Features/Albums/AlbumsEndpoint.cs` — crear/leer smart albums.
- `Features/Search/SearchEndpoint.cs` — pasa a usar `AssetQueryBuilder`.
- `Shared/Services/AssetQueryBuilder.cs` (nuevo) — composición de filtros compartida.
- Cliente KMP: `data/album/`, `data/models/AlbumModels.kt`, `ui/album/`.