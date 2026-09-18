# PostgreSQL y pgvector — operación y diagnóstico

Este documento recoge lo que hay que saber para operar la base de datos de
Photonne en un servidor propio: qué necesita el contenedor de Postgres, cómo
se aplican las migraciones al arrancar, qué hacen los índices vectoriales y
cómo diagnosticar cuándo algo va lento. Termina con los casos reales que
motivaron cada punto, para que no haya que redescubrirlos.

## El contenedor de Postgres

La imagen es `pgvector/pgvector:pg17`. Hay dos ajustes que el compose del
repositorio ya lleva y que conviene mantener en cualquier compose propio:

```yaml
postgresql:
  image: pgvector/pgvector:pg17
  shm_size: 1g
```

- **`shm_size: 1g`.** Docker da 64 MB a `/dev/shm`, y Postgres guarda ahí la
  memoria dinámica compartida de las consultas paralelas y de la
  construcción paralela de índices. Con 64 MB, una construcción paralela de
  un HNSW falla con `could not resize shared memory segment ... No space
  left on device`. Este cambio necesita **recrear** el contenedor
  (`docker compose up -d`); un `docker restart` no lo aplica.
- **`shared_buffers`.** Por defecto son 128 MB. Un índice HNSW de cientos de
  miles de caras ocupa varios cientos de MB, así que con el valor por defecto
  se lee del disco. Si en Grafana ves I/O de lectura alto en Postgres con el
  reconocimiento de caras en marcha, súbelo:

  ```yaml
  command: -c shared_buffers=1GB
  ```

Un dato útil para dimensionar: en el log de Postgres, cada
`checkpoint complete: wrote N buffers (P%)` permite calcular `shared_buffers`
como `N / P × 100 × 8 KB`. `126 buffers (0.8%)` son los 128 MB por defecto.

## Migraciones al arrancar

La API aplica las migraciones pendientes en cada arranque
(`ExecuteMigrations`, en `DependencyInjection.cs`). Tres cosas a tener
en cuenta:

1. **El contexto de migraciones lleva un timeout de una hora**, en lugar de
   los 30 s por defecto de Npgsql. Una migración que construye un índice
   sobre toda una tabla tarda minutos y con 30 s se cancelaba y se deshacía.
2. **Si una migración falla, la API arranca igual.** Es una decisión
   deliberada, pero significa que el esquema puede ir por detrás del código
   sin que nada se caiga. El log de arranque lo dice en una línea que
   empieza por `[ERROR] Error applying migrations — the schema is BEHIND the
   code:` seguida de la excepción entera. Tras cada despliegue conviene
   mirarlo:

   ```bash
   docker logs photonne 2>&1 | grep -A25 "migrations"
   ```

   Debe terminar en `Database migrations applied successfully.`.
3. **Las migraciones son transaccionales.** Una migración que falla no deja
   nada a medias ni queda registrada en `__EFMigrationsHistory`, así que se
   reintenta entera en el siguiente arranque. Por eso mismo **no hay que
   crear a mano** lo que una migración fallida iba a crear: cuando vuelva a
   ejecutarse fallará con `already exists`.

Para ver qué migraciones están aplicadas:

```sql
SELECT "MigrationId" FROM "__EFMigrationsHistory" ORDER BY 1 DESC LIMIT 5;
```

## Índices vectoriales

Las tablas `Faces` (embeddings de caras, 512 dimensiones) y
`AssetEmbeddings` (CLIP) tienen un índice HNSW con `vector_cosine_ops`
(`IX_Faces_Embedding`, `IX_AssetEmbeddings_Embedding`), creados por la
migración `20260916201358_AddVectorIndexes`.

Sin ese índice, cada búsqueda de vecinos más cercanos (kNN) recorre la
tabla entera calculando la distancia coseno de todos los vectores. Con
180.000 caras son unos 40 s por consulta y un core de CPU entero; con el
índice son milisegundos. El reconocimiento de caras hace un kNN por cada
cara nueva, así que la diferencia es entre una tarea que avanza a varias
fotos por segundo y una que no avanza.

La migración construye los índices **en serie** (`max_parallel_maintenance_workers = 0`)
y con `maintenance_work_mem = 1GB` solo para su transacción. En serie no
depende de `/dev/shm`, y el paralelismo no aporta nada en un Postgres de un
core. Cuenta con **5 a 15 minutos de CPU al 100 %** en el primer arranque
tras desplegar la migración; durante ese rato las escrituras en `Faces` y
`AssetEmbeddings` esperan.

Para comprobar que existen:

```sql
SELECT indexrelid::regclass, indisvalid, pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_index WHERE indrelid = '"Faces"'::regclass;
```

Y que se usan (debe salir `Index Scan using IX_Faces_Embedding`, no
`Seq Scan`):

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT f."Id" FROM "Faces" f
ORDER BY f."Embedding" <=> (SELECT "Embedding" FROM "Faces" LIMIT 1) LIMIT 40;
```

## Diagnóstico

### Qué está ejecutando Postgres ahora

```sql
SELECT pid, now() - query_start AS running, state, left(query, 120)
FROM pg_stat_activity
WHERE state <> 'idle' AND pid <> pg_backend_pid()
ORDER BY query_start;
```

Dos filas con el mismo `query_start` son una consulta y su worker paralelo,
no dos consultas. Cualquier consulta de la aplicación con `running` de
segundos merece un `EXPLAIN (ANALYZE, BUFFERS)`.

### Leer el log de Postgres

- `ERROR: canceling statement due to user request`: la consulta la canceló
  el cliente. Si aparece en parejas separadas ~30 s, es el timeout de
  comando de Npgsql (30 s por defecto para las consultas normales): la
  consulta tarda más de eso. Si aparecen varias en el mismo milisegundo, es
  una petición HTTP abortada (el usuario salió de la pantalla) y no
  significa nada.
- `ERROR: invalid byte sequence for encoding "UTF8": 0x00`: un texto con
  bytes NUL. Postgres no los admite en columnas de texto. Ver el primer caso
  de abajo.
- Un mismo error **exactamente cada 15 minutos** es la recuperación de
  tareas de enriquecimiento atascadas en `Processing`
  (`StaleProcessingTimeout` del `EnrichmentWorker`), no el backoff de
  reintentos (1, 5, 15, 60 min, 6 h).

## Casos resueltos

Los dos se diagnosticaron a partir del log de Postgres el 17 y 18 de
septiembre de 2026.

### Tarea EXIF reintentada cada 15 minutos por un caption con NUL (1.149.1)

**Síntoma.** Dos `invalid byte sequence for encoding "UTF8": 0x00` sobre el
`INSERT INTO "AssetExifs"`, a 7 ms uno del otro, repetidos cada 15:00
exactos durante horas. La tarea no aparecía en "Assets con problemas".

**Causa.** Una foto con el caption relleno de bytes `0x00` (relleno habitual
de cámaras y editores en campos de tamaño fijo). Dos defectos encadenados:

1. `ExifExtractorService` guardaba el texto tal cual.
2. El `catch` del `EnrichmentWorker` anotaba el fallo con el mismo
   `DbContext`, que seguía rastreando la fila rechazada: el `SaveChanges`
   reenviaba el INSERT malo (segundo error), lanzaba desde dentro del
   `catch`, la tarea se quedaba en `Processing` sin contar el intento y la
   recuperación de atascadas la devolvía a `Pending` cada 15 minutos.

**Arreglo.** `CleanText()` quita los NUL de `Description`, `Keywords`,
`CameraMake` y `CameraModel`; el `catch` vacía el `ChangeTracker` y vuelve
a adjuntar la tarea antes de anotar el fallo, así cualquier tarea cuyo
guardado falle queda `Failed` con su backoff y su notificación.

### El índice HNSW de caras nunca llegó a existir (1.149.2, 1.149.3)

**Síntoma.** El reconocimiento de caras saturaba el core de Postgres y no
avanzaba. En el log, `canceling statement` sobre el kNN de `Faces` en
parejas separadas 31 s. `pg_index` no mostraba `IX_Faces_Embedding`, y el
`EXPLAIN` daba `Parallel Seq Scan` de 41 s.

**Causa, en dos capas.**

1. `ExecuteMigrations` se tragaba la excepción en una línea sin detalle, y
   la API arrancaba igual. La migración llevaba dos días fallando en cada
   arranque sin que se viera.
2. Con la excepción a la vista, el motivo real: la construcción paralela del
   HNSW pedía 1 GB de memoria dinámica compartida en `/dev/shm`, que Docker
   limita a 64 MB (`could not resize shared memory segment ... No space
   left on device`). La primera hipótesis, el timeout de 30 s, era
   plausible pero no era esta: la migración fallaba en 5 ms.

**Arreglo.** Timeout de una hora y excepción entera en el log para las
migraciones (1.149.2); construcción en serie en la migración y `shm_size:
1g` en el compose (1.149.3). Tras desplegar, el kNN pasó de más de 30 s a
milisegundos y la tarea de caras dejó de saturar Postgres.

**Lección.** Un error tragado en el arranque cuesta días; un `EXPLAIN` y un
`pg_index` cuestan un minuto. Ante una consulta lenta, comprobar primero que
el índice que el código da por hecho existe de verdad.
