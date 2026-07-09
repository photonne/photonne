# Esquema de reglas de álbum inteligente

Documenta el JSON que se guarda en `Album.SmartRule` y cómo cada condición mapea al
filtrado que ya existe en `SearchEndpoint.cs`. Ver visión general en `README.md`.

## Forma general

Una regla es un **árbol booleano**. Dos tipos de nodo:

- **Nodo lógico**: `{ "op": "AND" | "OR", "conditions": [ ...nodos... ] }`
- **Nodo condición** (hoja): `{ "type": "...", ...campos... }`
- **Negación**: `{ "type": "not", "condition": { ...nodo... } }`

```jsonc
{
  "version": 1,
  "root": {
    "op": "AND",
    "conditions": [
      { "type": "person", "match": "all", "personIds": ["<guid-abuela>", "<guid-nieto>"] },
      { "op": "OR", "conditions": [
          { "type": "folder", "folderIds": ["<viajes>"], "includeSubfolders": true }
      ]},
      { "type": "dateRange", "from": "2024-01-01", "to": null },
      { "type": "scene", "match": "any", "labels": ["beach", "mountain"] },
      { "type": "not", "condition": { "type": "tag", "tagType": "Screenshot" } }
    ]
  }
}
```

Notas de diseño:
- `version` permite migrar el esquema sin romper reglas guardadas.
- `match: "all"` = intersección (AND entre valores), `match: "any"` = unión (OR entre valores).
  Refleja la semántica que Search ya usa (objetos/escenas = intersección; personas = intersección).
- Un nodo condición traduce a un `.Where(...)` sobre `IQueryable<Asset>`. Un `AND` encadena
  `.Where`; un `OR` combina predicados con `||`; `not` niega el predicado.

## Catálogo de condiciones (MVP)

Cada condición referencia la línea de `SearchEndpoint.cs` que ya implementa el mismo filtro.

### `person` — personas
```jsonc
{ "type": "person", "match": "all", "personIds": ["<guid>", ...] }
```
Mapea a `SearchEndpoint.cs:159-183`. Identidad **por-usuario**, anclada al dueño del álbum:
`a.Faces.Any(f => UserFaceAssignments.Any(uf => uf.FaceId==f.Id && uf.UserId==ownerId && uf.PersonId==pid && !uf.IsRejected))`.
Validar propiedad de cada `personId` contra `People.OwnerId == ownerId` antes de aplicar.

### `folder` — carpetas (personales y compartidas)
```jsonc
{ "type": "folder", "folderIds": ["<guid>", ...], "includeSubfolders": true }
```
Expandir `folderIds` a su subárbol (BFS por `ParentFolderId`, como `AllowedFolderCache.cs:97-112`)
y filtrar `folderSet.Contains(a.FolderId)`. Personal vs compartida se deriva del `Path`
(`/assets/users/{u}/…` vs `/assets/shared/{n}/…`), no hay enum. Alternativa por substring:
`a.FullPath.Contains(path)` (`SearchEndpoint.cs:104-106`).

### `dateRange` — fechas
```jsonc
{ "type": "dateRange", "from": "2024-01-01", "to": "2024-12-31" }  // ISO date, cualquiera nullable
```
Mapea a `SearchEndpoint.cs:88-102` sobre `Asset.CapturedAt` (indexado). `to` incluye el día completo.
Futuro: `relative` (`"last": "90d"`) para álbumes rodantes ("últimos 3 meses").

### `scene` — escenas (Places365)
```jsonc
{ "type": "scene", "match": "any", "labels": ["beach", "mountain"] }
```
Mapea a `SearchEndpoint.cs:126-142`. `a.ClassifiedScenes.Any(s => s.Label.ToLower()==label)`.

### `object` — objetos detectados
```jsonc
{ "type": "object", "match": "all", "labels": ["dog"] }
```
Mapea a `SearchEndpoint.cs:108-124`. `a.DetectedObjects.Any(o => o.Label.ToLower()==label)`.

### `tag` — tags de usuario o estructurales
```jsonc
{ "type": "tag", "userTagIds": ["<guid>"] }           // tags manuales del usuario
{ "type": "tag", "tagType": "Portrait" }               // AssetTagType enum (LivePhoto, Burst, Panorama, Screenshot, HDR, Portrait…)
```
Mapea a `a.UserTags.Any(...)` / `a.Tags.Any(t => t.TagType==...)` (`SearchEndpoint.cs:79-85`).

### `favorite` / `type` — flags simples
```jsonc
{ "type": "favorite", "value": true }                  // a.IsFavorite == true
{ "type": "mediaType", "value": "Video" }              // a.Type == AssetType.Video
```
No están en Search todavía pero son `.Where` triviales sobre columnas de `Asset`.

### `text` — OCR / texto en imagen
```jsonc
{ "type": "ocr", "query": "factura" }
```
Mapea a `SearchEndpoint.cs:144-155` (tsvector `simple`).

## Fuera del MVP (fases posteriores)

### `place` — lugares
```jsonc
{ "type": "place", "mode": "radius", "lat": 41.38, "lng": 2.17, "radiusKm": 25 }   // fase 5a
{ "type": "place", "mode": "named", "city": "Girona" }                              // fase 5b (requiere geocoding)
```
Requiere índice geo en `AssetExif.Latitude/Longitude` (radio) y geocoding inverso (nombre).

### `semantic` — CLIP
```jsonc
{ "type": "semantic", "query": "puesta de sol en la playa", "threshold": 0.28 }
```
Sobre `AssetEmbedding` (pgvector). Es un ORDER BY por distancia, no un `.Where` limpio;
encaja mejor como criterio de orden/curación que como filtro booleano duro.

## Visibilidad (siempre, independiente de la regla)

Toda resolución se **interseca** con el gate de visibilidad antes de devolver resultados:

1. Base: `DeletedAt == null && !IsArchived && !IsFileMissing` (como `TimelineQuery.VisibleAssets`).
2. Alcance: intersección del `AssetVisibilityService.AssetPredicate()` del **dueño** y del
   **espectador** (ver `README.md` §7b).

El `AssetQueryBuilder` (fase 1) aplica **solo las condiciones de la regla**; la visibilidad
la pone el llamante (endpoint), igual que hoy Search pone su propio gate por carpetas.
