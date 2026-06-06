# Copia de seguridad móvil — arquitectura y operación

Este documento describe cómo funciona la copia de seguridad del cliente móvil
en Photonne, desde que el usuario abre la pantalla de Backup hasta que el
asset queda totalmente enriquecido en el servidor. Pensado para self-hosters
que necesitan operar el sistema y contribuidores que vayan a tocar el código.

## Principio rector

El backup tiene **dos niveles de garantía**, independientes:

1. **Backup mínimo (contrato duro)** — el archivo está físicamente a salvo:
   copiado a su ruta final en disco, con su fila `Asset` en BD y su checksum
   SHA-256 verificado. Una vez aquí, el archivo **no se puede perder** salvo
   por borrado físico del disco.
2. **Enriquecimiento (best-effort, reintentable)** — extracción EXIF,
   miniaturas, detección de tipo de medio, reconocimiento facial, objetos,
   escenas, OCR, embeddings CLIP. Cada uno puede fallar y reintentarse sin
   afectar al backup.

Esta separación está implementada a propósito: los thumbnails de un vídeo
raro no deben tirar abajo la copia de seguridad de tu boda.

## Layout en disco

```
<ASSETS_HOST_PATH>/users/<username>/
├── Uploads/                  ← subidas manuales del usuario
└── MobileBackup/
    ├── <deviceName1>/        ← una subcarpeta por dispositivo
    ├── <deviceName2>/
    └── ...
```

### Background sync por plataforma

| Plataforma | Cómo se despierta la app | Constraints respetadas |
|---|---|---|
| Android | `WorkManager` periodic work, cadencia mínima 15 min | Wi-Fi vs cualquier red, charging, batería suficiente |
| iOS | `BGTaskScheduler` (`BGProcessingTaskRequest`), identificador `com.photonne.app.backup` | Charging (`requiresExternalPower`), red disponible. Wi-Fi-only se aplica client-side en el handler via `NWPathMonitor` |
| Desktop | No-op | — |

El handler iOS está registrado en `iosApp/iosApp/AppDelegate.swift` y vive sólo mientras el `BGTask` está activo (deadline de ~30s para `BGAppRefresh`, varios minutos para `BGProcessing`). Si la app es matada por el OS mid-backup, el siguiente despertar retoma desde donde quedó gracias a la dedup del servidor por checksum.

### Cómo identificarse el dispositivo

El nombre del dispositivo viene del cliente:

| Plataforma | Fuente |
|---|---|
| Android | `Build.MANUFACTURER + Build.MODEL` (deduplica prefijos: `Samsung SM-G998B`) |
| iOS | `UIDevice.currentDevice.name` (lo que el usuario haya puesto en Ajustes) |
| Desktop | `InetAddress.getLocalHost().hostName` (solo dev/test) |

El servidor **sanitiza** ese string antes de usarlo como ruta:
`[a-zA-Z0-9._-]` permitido, espacios → `_`, cualquier otra cosa se descarta,
trim de `.` y `_` a los extremos, cap a 64 chars. Path traversal está
neutralizado por construcción. Si tras sanitizar queda vacío, el backup cae
en `MobileBackup/` plano (sin subcarpeta) en lugar de fallar.

Reglas de sanitización + edge cases en
`Src/Photonne.Server.Api/Shared/Services/DeviceFolderSanitizer.cs` y sus 19
tests unitarios en `Tests/.../Services/DeviceFolderSanitizerTests.cs`.

## Flujo de subida

### Cliente nativo (Android/iOS)

1. El usuario abre "Backup" en la app, selecciona la carpeta del dispositivo.
2. Para cada media seleccionada, `DeviceBackupRepository.upload()`:
   - Lee los bytes locales.
   - Llama a `uploads.upload(..., destination = "mobile-backup", deviceName = currentDeviceName())`.
   - Reintenta automáticamente errores transitorios (red, 5xx, 429) con
     backoff `[1s, 5s, 30s]` hasta 3 intentos.
   - Errores permanentes (cuota agotada, archivo demasiado grande, forbidden)
     fallan al primer intento — no tiene sentido reintentar algo que el
     servidor siempre va a rechazar.
3. El item se marca en UI con su estado (`Synced`, `Failed(reason)`, etc).
   `UploadFailureReason` categoriza el error a partir del HTTP status, y la
   UI lo mapea a un mensaje localizado.

### Servidor — `/api/assets/upload`

1. Auth + cuota + tamaño máximo + rate-limit (`demo-upload`).
2. Resolución del path destino vía `ResolveDestinationVirtualPath`:
   - `destination=uploads` (default) → `/assets/users/{u}/Uploads/` (ignora `deviceName`).
   - `destination=mobile-backup` → `/assets/users/{u}/MobileBackup/{sanitizedDevice}/`.
3. Hash SHA-256 del archivo subido + dedup contra cualquier asset existente
   del usuario con mismo checksum (responde 200 con el `assetId` existente).
4. Move del temp al destino final, resolución de colisiones por nombre
   (rename con prefijo GUID).
5. Fila `Asset` creada con metadata del filesystem.
6. **Aquí termina el backup**. Devuelve `200 { assetId }`.
7. Encola 3 tareas siempre (`Exif`, `Thumbnails`, `MediaRecognition`)
   + tareas de ML según `MediaRecognitionService.ShouldTriggerMlJob`.

### Servidor — `/api/assets/sync`

Variante para sincronizar archivos que ya están en el filesystem del servidor
(usada por la PWA). Diferencias clave:

- Recibe `path` por query, no multipart.
- Path restringido a `/assets/users/{currentUser}/...` — un usuario no puede
  sincronizar archivos de otro usuario aunque pueda enumerarlos.
- Tras `File.Copy`, **verifica el checksum del destino contra el del origen**
  (no fiable solo con `File.Copy`). Si difieren, borra el destino y devuelve
  500. Esta es la "verificación 100%" que cierra el bucle de integridad.

## Modelo de enriquecimiento

### Tabla `AssetEnrichmentTasks`

Una fila por tarea pendiente o histórica:

| Columna | Tipo | Descripción |
|---|---|---|
| `Id` | Guid | PK |
| `AssetId` | Guid | FK a `Assets` (cascade delete) |
| `TaskType` | enum | 8 valores: `Exif`, `Thumbnails`, `MediaRecognition`, `FaceRecognition`, `ObjectDetection`, `SceneClassification`, `TextRecognition`, `ImageEmbedding` |
| `Status` | enum | `Pending`, `Processing`, `Completed`, `Failed` |
| `AttemptCount` | int | Veces que el worker intentó esta tarea |
| `NextRetryAt` | DateTime? | Cuándo el worker debe volver a intentarlo (null = no programado / permanente) |
| `ErrorMessage` | string(2000)? | Mensaje del último fallo |
| `ResultJson` | text? | Salida estructurada de la tarea exitosa |
| `CreatedAt`, `StartedAt`, `CompletedAt` | DateTime | Trazabilidad |

Índices: `(AssetId)`, `(AssetId, TaskType, Status)` para dedup, `(Status, NextRetryAt)` para la recovery del worker.

### `EnrichmentWorker`

`BackgroundService` que mantiene un pool de workers **por tipo**, con
cantidad configurable vía `TaskSettings.{Type}Workers` en la tabla de
settings (defaults: 2 para tareas baratas — Exif/Thumbnails/MediaRecognition;
1 para ML — caras CPU/GPU). Cambiar la config requiere reiniciar el server.

Cada worker:
1. Lee tareas de un `Channel<Guid>` específico de su tipo
   (`EnrichmentQueue`).
2. Hace **atomic claim** vía `ExecuteUpdateAsync` (`Pending → Processing`)
   para evitar races entre workers.
3. Dispatch a la implementación correcta (`ExifExtractorService`,
   `ThumbnailGeneratorService`, los servicios de ML, etc.).
4. En éxito → `Completed` + `ResultJson`.
5. En fallo → `Failed` + `AttemptCount++` + `NextRetryAt = ComputeNextRetry(...)`.

### Backoff

```
AttemptCount  →  espera antes del siguiente intento
       1            1 minuto
       2            5 minutos
       3           15 minutos
       4            1 hora
       5            6 horas
       6+           null (permanente)
```

Cuando una tarea queda permanente, el worker manda una notificación al
owner (`NotificationType.JobFailed`) con la causa. Solo en el último
intento — fallos transitorios no spamean la bandeja.

La función vive en `EnrichmentBackoff.cs` (pura, sin dependencias) y está
cubierta por unit tests sin Docker.

### Recovery

Cada 5 minutos el worker:
1. Resetea tareas en `Processing` que llevan ahí > 15 min
   (worker crasheó / server reinició mid-task) → vuelven a `Pending`.
2. Re-encola tareas `Failed` cuyo `NextRetryAt` ya venció con
   `AttemptCount` aún bajo el cap → vuelven a `Pending`.
3. Re-empuja al canal toda fila `Pending` por si su id se perdió de
   memoria (restart del proceso, enqueue en otro proceso, etc.).

Esto hace el sistema **self-healing across restarts**. Si el server cae a
medio batch, al volver arriba retoma exactamente donde quedó.

## Endpoints de operación

| Método | Ruta | Hace |
|---|---|---|
| `GET` | `/api/assets/{id}/enrichment` | Lista las tareas del asset con su estado, error, intentos, timestamps. |
| `POST` | `/api/assets/{id}/enrichment/retry?taskType=X` | Resetea **una** tarea a Pending. Crea la fila si no existía. Permite forzar rerun de cualquier estado. |
| `POST` | `/api/assets/{id}/enrichment/retry-all` | Resetea **todas las Failed** del asset. Devuelve count. No toca Pending/Processing/Completed. |
| `GET` | `/api/assets/enrichment/pending?pageSize=&cursor=` | Lista paginada de los assets del usuario con ≥1 tarea Pending o Failed. Cursor por `FileCreatedAt` desc. Cap 200. |

Todos son owner-only (el caller tiene que ser dueño del asset). Los dos POST
están rate-limited por `demo-upload`.

## Troubleshooting

### "Access to the path '/data/assets/…' is denied" al subir

El volumen de assets pertenece a un uid distinto del que usa el servidor
dentro del contenedor, así que no puede crear carpetas nuevas (p. ej.
`MobileBackup/<dispositivo>`). Desde la imagen con entrypoint propio esto
se corrige solo: el contenedor arranca como root, ajusta la propiedad de
`/data/assets` y `/data/thumbnails` y baja privilegios al usuario `app`
antes de lanzar el servidor.

Variables relacionadas (en `docker-compose.yml` o `.env`):

| Variable | Default | Uso |
|---|---|---|
| `PUID` / `PGID` | `1654` | uid/gid con el que corre el servidor y quedan los ficheros. Ponlos al uid/gid de tu usuario del NAS si compartes el volumen con otros servicios. |
| `PHOTONNE_SKIP_CHOWN` | `0` | A `1` para saltarte el ajuste de permisos en el arranque (bibliotecas enormes donde prefieres gestionarlos tú). |

Si corres una imagen anterior (sin entrypoint) o has puesto
`PHOTONNE_SKIP_CHOWN=1`, el arreglo manual en el host es:

```bash
chown -R 1654:1654 /ruta/del/volumen/assets   # o tu PUID:PGID
```

### "He subido una foto pero no veo el thumbnail"

Normal durante 1-2s post-upload. El worker corre asíncrono. Si pasados varios
minutos sigue sin thumbnail:

```sql
SELECT "TaskType", "Status", "AttemptCount", "ErrorMessage", "NextRetryAt"
FROM "AssetEnrichmentTasks"
WHERE "AssetId" = '<asset-guid>';
```

Si la fila `Thumbnails` está en `Failed`, el `ErrorMessage` te dice por qué
(corrupción del archivo, formato no soportado, Magick.NET sin dependencia,
etc.). Reintenta vía `POST /api/assets/{id}/enrichment/retry?taskType=Thumbnails`
o desde la pantalla "Estado de enriquecimiento" del cliente nativo.

### "Tarea atascada en Processing"

Las tareas que pasan más de 15 minutos en `Processing` las recoge la recovery
periódica del worker y las devuelve a `Pending`. Si quieres acelerar la
recuperación, reinicia el server: en el arranque corre la misma recovery
inmediatamente.

### "Quiero subir el throughput de un tipo concreto"

Edita la configuración del server (tabla `Settings` o vía admin UI):

```
TaskSettings.ExifWorkers = 4
TaskSettings.ThumbnailsWorkers = 4
TaskSettings.FaceRecognitionWorkers = 2
```

Requiere reiniciar para que el `EnrichmentWorker` lea el nuevo valor.
Mantén los ML bajos si tienes GPU única — paralelizar saturará VRAM.

### "Una tarea quedó permanente — ¿cómo la fuerzo?"

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  "https://photonne.example/api/assets/$ASSET_ID/enrichment/retry?taskType=FaceRecognition"
```

Esto resetea `AttemptCount=0`, `NextRetryAt=null`, la encola y arranca de cero.

### "¿Cómo veo todos los assets con problemas?"

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://photonne.example/api/assets/enrichment/pending?pageSize=200"
```

Devuelve cada asset con contadores `pending/processing/failed` y la lista
`failedTaskTypes`. Mismo dato que rinde la pantalla "Estado de
enriquecimiento" en el cliente nativo.

## Archivos relevantes

| Componente | Path |
|---|---|
| Modelo | `Src/Photonne.Server.Api/Shared/Models/AssetEnrichmentTask.cs` |
| Servicio enqueue | `Src/Photonne.Server.Api/Shared/Services/EnrichmentService.cs` |
| Cola in-memory | `Src/Photonne.Server.Api/Shared/Services/EnrichmentQueue.cs` |
| Worker | `Src/Photonne.Server.Api/Shared/Services/EnrichmentWorker.cs` |
| Backoff (pure) | `Src/Photonne.Server.Api/Shared/Services/EnrichmentBackoff.cs` |
| Sanitizer | `Src/Photonne.Server.Api/Shared/Services/DeviceFolderSanitizer.cs` |
| Endpoints | `Src/Photonne.Server.Api/Features/AssetEnrichment/*.cs` |
| Upload | `Src/Photonne.Server.Api/Features/UploadAssets/UploadAssetsEndpoint.cs` |
| Sync | `Src/Photonne.Server.Api/Features/SyncAsset/SyncAssetEndpoint.cs` |
| Cliente nativo — backup | `Src/Photonne.Client.Native/.../data/devicebackup/DeviceBackupRepository.kt` |
| Cliente nativo — enrich UI | `Src/Photonne.Client.Native/.../ui/devicebackup/EnrichmentStatusScreen.kt` |
| Tests | `Tests/Photonne.Server.Api.Tests/AssetEnrichment/`, `Tests/.../Services/EnrichmentBackoffTests.cs` |
