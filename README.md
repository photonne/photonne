# Photonne

Sistema de gestión de fotos y videos auto-hospedado. Indexa, organiza y visualiza tu biblioteca multimedia desde cualquier dispositivo.

## Características

**Biblioteca**
- **Línea de tiempo** — Vista cronológica de toda la biblioteca
- **Álbumes** — Álbumes propios, compartidos con otros usuarios y compartibles mediante enlaces públicos
- **Carpetas** — Navegación por espacio propio y espacio compartido
- **Buscador** — Búsqueda por nombre, fecha, etiquetas y metadatos
- **Mapa** — Visualización geográfica por metadatos de ubicación
- **Favoritos** — Marca assets como favoritos para acceso rápido
- **Etiquetas** — Tags automáticos por ML y tags manuales de usuario
- **Memorias** — Revive fotos y vídeos de este mismo día en años anteriores

**Gestión de archivos**
- **Sincronización desde dispositivo** — Subida de assets desde el navegador
- **Compartir** — Comparte assets y álbumes con otros usuarios o mediante enlaces públicos con token
- **Archivado** — Archiva assets para ocultarlos de la vista principal sin eliminarlos
- **Papelera** — Eliminación suave con posibilidad de restauración
- **Detección de duplicados** — Identifica assets duplicados en la biblioteca
- **Comprobación de archivos pesados** — Detecta y lista los archivos de mayor tamaño

**Notificaciones**
- **Notificaciones** — Sistema de notificaciones para eventos del sistema e indexación

**Administración**
- **Gestión de usuarios** — Administración de usuarios, roles y permisos desde el panel de admin. Admin principal protegido
- **Bibliotecas externas** — Integración de directorios externos a la biblioteca principal
- **Copia de seguridad** — Exportación y restauración de la base de datos y configuración

**Clientes**
- **Apps nativas** — Android, iOS y escritorio (Kotlin Multiplatform + Compose). Fuente principal para el consumo de la biblioteca
- **Panel de administración web** — PWA Blazor reservada a administradores: usuarios, bibliotecas, tareas, estadísticas y ajustes del servidor

**Técnico**
- **Indexación automática** — Escanea directorios para indexar todos los assets (foto y vídeo)
- **Miniaturas** — Generación paralela en tres tamaños (small, medium, large)
- **Extracción de metadatos** — Lectura de datos EXIF de imágenes y vídeos (fecha, GPS, cámara, etc.)
- **JWT + Refresh Token** — Autenticación con soporte multi-dispositivo

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend | ASP.NET Core 10, EF Core, PostgreSQL |
| Apps nativas | Kotlin Multiplatform + Compose Multiplatform (Android, iOS, Desktop) |
| Panel de admin | Blazor WebAssembly (PWA), MudBlazor 9 |
| Imágenes | ImageSharp, Magick.NET |
| Video | FFmpeg (vía Xabe.FFmpeg) |
| EXIF | MetadataExtractor |
| Auth | JWT Bearer + Refresh Tokens |
| Contenedores | Docker, Docker Compose |

## Estructura del proyecto

```
Photonne.sln
└── Src/
    ├── Photonne.Server.Api/      # API REST ASP.NET Core 10
    ├── Photonne.Client.Web/      # Panel de administración (Blazor WASM PWA)
    ├── Photonne.Client.Native/   # Apps nativas (Android, iOS, Desktop) en KMP + Compose
    └── Photonne.MlService/       # Servicio ML en Python (FastAPI)
```

### Responsabilidades por proyecto

**`Server.Api`** — Backend completo:
- API REST con endpoints por feature (`/api/assets`, `/api/albums`, `/api/users`, etc.)
- Indexación incremental de assets con extracción EXIF, miniaturas y jobs ML
- Autenticación JWT + Refresh Tokens, gestión de usuarios y permisos
- Migraciones de base de datos (EF Core + PostgreSQL)

**`Client.Web`** — Panel de administración (Blazor WASM PWA):
- Reservado a usuarios con rol `Admin`; los no administradores se redirigen a un aviso para usar la app nativa.
- Páginas: Panel (dashboard), Usuarios, Bibliotecas externas, Estadísticas, Tareas/Colas, Mantenimiento, Copia de seguridad, Ajustes del servidor, Sistema, Utilidades y Notificaciones.
- Layout: `MainLayout`, `NavMenu`, `LoginLayout`; servicios con interfaces + implementaciones (AuthService usa `IJSRuntime`/`localStorage`).
- PWA: `manifest.webmanifest` («Photonne Admin»), `service-worker.js` con cache del app shell.
- Decisión técnica documentada en [`Src/Photonne.Client.Web/docs/ADR-002-pwa-admin-console.md`](Src/Photonne.Client.Web/docs/ADR-002-pwa-admin-console.md).

**`Client.Native`** — Apps nativas (fuente principal de consumo):
- Kotlin Multiplatform + Compose Multiplatform.
- Targets Android, iOS y Desktop JVM con UI compartida.
- Cliente Ktor con refresh-on-401 que replica `AuthRefreshHandler` del web.
- Decisión técnica documentada en [`Src/Photonne.Client.Native/docs/ADR-001-kotlin-multiplatform.md`](Src/Photonne.Client.Native/docs/ADR-001-kotlin-multiplatform.md).

## Requisitos

- [.NET 10 SDK](https://dotnet.microsoft.com/download)
- [Docker](https://www.docker.com/) y Docker Compose
- FFmpeg (descargado automáticamente si no está disponible)

## Inicio rápido

### 1. Clonar el repositorio

```bash
git clone https://github.com/photonne/photonne.git
cd photonne
```

> **Activa los git hooks del repo (una vez por clon):**
> ```bash
> ./scripts/setup-hooks.sh
> ```
> Fija `core.hooksPath` a `.githooks/`, que incluye el auto-bump de versión en
> cada commit. Es config local de git y **no se clona**, por eso hay que
> ejecutarlo una vez en cada máquina. Ver [Versionado](#versionado).

### 2. Configurar las variables de entorno

```bash
cp .env.example .env
```

Edita `.env` y rellena al menos los valores marcados como obligatorios: `POSTGRES_PASSWORD`, `JWT_KEY` y `ADMIN_PASSWORD`. Puedes generar una clave JWT segura con:

```bash
openssl rand -base64 48
```

### 3. Levantar la infraestructura (producción)

```bash
docker compose up -d
```

Usa la imagen pre-construida de GitHub Container Registry (`ghcr.io/photonne/photonne:latest`). Levanta:
- **PostgreSQL 17** (interno, sin puerto expuesto)
- **PgAdmin 4** en `http://localhost:5050`
- **Photonne API** en `http://localhost:1107`

### 4. Ejecutar en desarrollo (sin Docker)

```bash
cd Src/Photonne.Server.Api
dotnet run
```

La API estará disponible en `https://localhost:5001`.
Documentación interactiva: `https://localhost:5001/scalar`

### 5. Ejecutar en desarrollo con Docker

```bash
docker compose up -d
```

El archivo `docker-compose.override.yml` se aplica automáticamente en desarrollo: compila la imagen localmente y expone la API en `http://localhost:5000` y PostgreSQL en `localhost:5432`.

### 6. Cliente web

```bash
cd Src/Photonne.Client.Web
dotnet run
```

## Configuración

### Variables de entorno

| Clave | Descripción | Default |
|---|---|---|
| `ConnectionStrings__Postgres` | Cadena de conexión PostgreSQL | `Host=photonne-db;...` |
| `POSTGRES_DB` | Nombre de la base de datos | `photonne` |
| `POSTGRES_USER` | Usuario de PostgreSQL | `photonne_user` |
| `POSTGRES_PASSWORD` | Contraseña de PostgreSQL | `photonne_password` |
| `Jwt__Key` | Clave secreta para JWT (mín. 32 caracteres) — usar `JWT_KEY` en `.env` | `TOKEN_SUPER_SECRET` |
| `Jwt__Issuer` | Emisor del token | `Photonne` |
| `Jwt__Audience` | Audiencia del token | `Photonne` |
| `HTTPS_REDIRECT` | Forzar redirección HTTPS | `false` |
| `API_PORT` | Puerto expuesto de la API | `1107` |
| `PGADMIN_PORT` | Puerto expuesto de PgAdmin | `5050` |
| `PGADMIN_EMAIL` | Email de acceso a PgAdmin | `admin@example.com` |
| `PGADMIN_PASSWORD` | Contraseña de PgAdmin | `admin` |
| `ASSETS_HOST_PATH` | Ruta absoluta del host montada como volumen de assets (ver [Rutas en el host](#rutas-en-el-host)) | `./photonne/data/assets` |
| `THUMBNAILS_HOST_PATH` | Ruta absoluta del host montada como volumen de miniaturas | `./photonne/data/thumbnails` |
| `ADMIN_USERNAME` | Nombre de usuario del admin inicial | `admin` |
| `ADMIN_EMAIL` | Email del admin inicial | `admin@photonne.local` |
| `ADMIN_PASSWORD` | Contraseña del admin inicial | `admin123` |
| `ADMIN_FIRSTNAME` | Nombre del admin inicial | `Administrador` |
| `ADMIN_LASTNAME` | Apellido del admin inicial | `Sistema` |

### Usuario administrador

El admin inicial se configura mediante variables de entorno (ver tabla anterior) o en `appsettings.Development.json` para desarrollo local:

```json
{
  "AdminUser": {
    "Username": "admin",
    "Email": "admin@photonne.local",
    "Password": "admin123",
    "FirstName": "Administrador",
    "LastName": "Sistema"
  }
}
```

El primer usuario admin creado al arrancar se marca automáticamente como **admin principal** (`IsPrimaryAdmin = true`). El admin principal no puede ser eliminado, desactivado ni degradar su rol desde la API, garantizando que siempre exista al menos un administrador en el sistema.

> **Ciclo de vida**: las variables `ADMIN_*` solo se aplican en el **primer arranque sobre una BD vacía** (cuando no existe ningún usuario con rol `Admin`). A partir de ese momento, los valores del `.env` se ignoran en cada arranque siguiente — renombrar el admin, cambiar su email o resetear su contraseña desde la UI no provoca duplicados, y editar `ADMIN_*` después no tiene efecto. Para recuperar acceso si pierdes la contraseña, usa el endpoint de reset desde otro admin o (en último recurso) elimina manualmente todos los admins de la BD y reinicia el contenedor con los nuevos valores en `.env`.

Además, el username del admin se valida con la misma regla que cualquier otro usuario (`^[a-zA-Z0-9._-]{1,64}$`) porque se usa como nombre de la carpeta en disco (`/assets/users/{username}/`). Si `ADMIN_USERNAME` contiene caracteres inválidos, el arranque registra un error y omite la creación en lugar de crear un admin que rompa el layout del filesystem.

### Docker Compose — variables personalizables

Se recomienda crear un archivo `.env` en la raíz del proyecto:

```env
# Puertos
API_PORT=1107
PGADMIN_PORT=5050

# Base de datos
POSTGRES_DB=photonne
POSTGRES_USER=photonne_user
POSTGRES_PASSWORD=photonne_password

# JWT (cambiar en producción)
JWT_KEY=clave-secreta-muy-larga-de-al-menos-32-caracteres

# Rutas de datos en el host (absolutas — ver "Rutas en el host" más abajo)
ASSETS_HOST_PATH=/home/<TuUsuario>/photonne/data/assets
THUMBNAILS_HOST_PATH=/home/<TuUsuario>/photonne/data/thumbnails

# Admin inicial
ADMIN_PASSWORD=contraseña-segura
```

### Rutas en el host

Photonne usa un único árbol `photonne/data/` con dos subcarpetas (`assets/` y
`thumbnails/`). Se recomienda colocarlo **bajo el home del usuario**: queda
fuera del repo, no requiere permisos de root/admin y es accesible desde el
explorador de archivos sin tocar la configuración de Docker.

> **Importante**: Docker Compose **no expande** `~` ni `${HOME}` en los valores
> del `.env`. Usa siempre rutas absolutas (con letra de unidad en Windows).

| SO       | Rutas recomendadas                                                                                            |
|----------|---------------------------------------------------------------------------------------------------------------|
| Linux    | `/home/<TuUsuario>/photonne/data/assets`<br>`/home/<TuUsuario>/photonne/data/thumbnails`                      |
| macOS    | `/Users/<TuUsuario>/photonne/data/assets`<br>`/Users/<TuUsuario>/photonne/data/thumbnails`                    |
| Windows  | `C:/Users/<TuUsuario>/photonne/data/assets`<br>`C:/Users/<TuUsuario>/photonne/data/thumbnails`                |

Si las carpetas no existen, Docker las crea al arrancar. En macOS Docker Desktop
ya tiene autorizado `/Users` en `Settings → Resources → File Sharing` por
defecto; en Windows ocurre lo mismo con `C:/Users` vía WSL2; en Linux el home
es escribible sin `sudo`.

### Layout en disco

Dentro de `ASSETS_HOST_PATH` cada usuario tiene su propia carpeta nombrada por
su **username** (no por UUID), lo que permite identificar a quién pertenece
cada directorio de un vistazo:

```
<ASSETS_HOST_PATH>/
└── users/
    ├── alice/
    │   ├── Uploads/                 ← subidas manuales (web/PWA, app móvil "compartir")
    │   ├── MobileBackup/            ← copias de seguridad automáticas del móvil
    │   │   ├── Pixel_8_Pro/         ← una subcarpeta por dispositivo
    │   │   └── Marcs_iPhone/
    │   └── _trash/
    └── bob/
        └── Uploads/
```

Distinción importante:
- `Uploads/` — destino plano para subidas iniciadas por el usuario (drag&drop
  en la web, botón "subir" en la app, share desde otra app).
- `MobileBackup/{deviceName}/` — destino para la copia de seguridad
  automática del móvil. El cliente envía un `deviceName` y el servidor lo
  sanitiza antes de usarlo como subcarpeta (rechaza barras, traversal,
  cualquier cosa fuera de `[a-zA-Z0-9._-]`; cap 64 chars). Permite que un
  usuario haga backup desde varios dispositivos sin mezclarlos.

El username solo admite `[a-zA-Z0-9._-]` (máx. 64 caracteres) para garantizar
compatibilidad con cualquier sistema de archivos. Cambiar el username de un
usuario lanza una migración atómica (renombra la carpeta y actualiza todas las
referencias en BD); la web pide confirmación mostrando el impacto antes de
aplicarla.

## API

La API sigue una estructura por features. Endpoints principales:

| Área | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `POST /api/auth/refresh` |
| Assets | `GET /api/assets`, `GET /api/assets/{id}`, `GET /api/assets/{id}/content` |
| Búsqueda | `GET /api/assets/search` |
| Indexación | `GET /api/assets/index/stream` (SSE), `GET /api/assets/index` |
| Subida | `POST /api/assets/upload` (form fields: `file`, `destination`=`uploads`\|`mobile-backup`, `deviceName` opcional) |
| Sincronización | `POST /api/assets/sync?path=&deviceName=` |
| Enriquecimiento | `GET /api/assets/{id}/enrichment`, `POST /api/assets/{id}/enrichment/retry?taskType=`, `POST /api/assets/{id}/enrichment/retry-all`, `GET /api/assets/enrichment/pending` |
| Miniaturas | `GET /api/assets/{id}/thumbnail` |
| Memorias | `GET /api/assets/memories` |
| Favoritos | `GET /api/assets/favorites`, `POST /api/assets/{id}/favorite` |
| Etiquetas | `GET /api/tags`, `POST/DELETE /api/assets/{id}/tags` |
| Compartir | `GET/POST /api/share`, `GET/PATCH/DELETE /api/share/{token}` |
| Álbumes | `GET/POST /api/albums`, `GET/PUT/DELETE /api/albums/{id}` |
| Carpetas | `GET /api/folders`, `GET /api/folders/tree` |
| Línea de tiempo | `GET /api/assets/timeline` |
| Mapa | `GET /api/assets/map` |
| Notificaciones | `GET /api/notifications`, `GET /api/notifications/unread-count`, `POST /api/notifications/read-all` |
| Usuarios | `GET /api/users/me`, `GET/PUT/DELETE /api/users/{id}`, `POST /api/users/{id}/reset-password` |
| Tareas | `GET /api/tasks`, `GET /api/tasks/{id}/stream` |
| Bibliotecas externas | `GET/POST/PUT/DELETE /api/libraries`, `GET /api/libraries/{id}/scan/stream` |
| Utilidades | `GET /api/utilities/duplicates`, `GET /api/utilities/large-files` |
| Configuración | `GET/PUT /api/settings` |
| Administración | `GET /api/admin/stats`, `GET /api/admin/version`, `GET /api/admin/trash/stats` |

Documentación interactiva disponible en `/scalar` (modo desarrollo).

## Indexación

El proceso de indexación es incremental y transmite progreso en tiempo real vía streaming:

1. **Descubrimiento** — Escaneo recursivo del directorio `/data/assets`
2. **Comparación** — Detección de archivos nuevos, modificados y huérfanos
3. **Extracción EXIF** — Metadatos de imagen/video (fecha, GPS, cámara, etc.)
4. **Detección de tags** — Clasificación automática por ML
5. **Miniaturas** — Generación paralela (small/medium/large)
6. **Base de datos** — Persistencia y limpieza de huérfanos

Accesible desde: `Admin > Colas > Indexar`

## Copia de seguridad móvil

El flujo de backup desde el móvil está **desacoplado del enriquecimiento**:

- **Backup** (`/api/assets/upload?destination=mobile-backup` o `/api/assets/sync`):
  garantiza síncronamente que el archivo está físicamente a salvo —
  copiado a su ruta final, fila `Asset` en BD, checksum SHA-256 verificado
  (en `/sync` se compara source↔destination tras el copy). Responde 200 con
  `assetId` inmediatamente. Nunca falla por culpa del enriquecimiento.
- **Enriquecimiento** (`AssetEnrichmentTasks` + `EnrichmentWorker`):
  EXIF, miniaturas, detección de tipo de medio y las 5 tareas de ML
  (rostros, objetos, escenas, OCR, embeddings CLIP) corren en segundo
  plano. Cada tarea se reintenta con backoff exponencial
  (`1m, 5m, 15m, 1h, 6h`) hasta 5 intentos antes de quedar Failed
  permanente. El usuario puede consultar el estado y reintentar fallidas
  desde la pantalla "Estado de enriquecimiento" en la app.

Detalles de arquitectura, modelo de datos, configuración de workers y
troubleshooting: [`docs/mobile-backup.md`](docs/mobile-backup.md).

## Despliegue con Docker

### Plataformas soportadas

Las imágenes oficiales se publican como **manifest list multi-arch**, así que
`docker pull` (y `docker compose up`) resuelven automáticamente la variante
nativa de tu hardware. No tienes que indicar arquitectura manualmente.

| Sistema operativo | Arquitectura | Estado | Notas |
|---|---|---|---|
| Linux | amd64 (x86_64) | ✅ Nativo | NAS Intel/AMD, servidores x86 |
| Linux | arm64 (aarch64) | ✅ Nativo | Raspberry Pi 4/5, Synology DSM 7+ ARM, AWS Graviton |
| macOS | amd64 (Intel) | ✅ Nativo vía Docker Desktop | |
| macOS | arm64 (Apple Silicon) | ✅ Nativo vía Docker Desktop | |
| Windows + WSL2 | amd64 | ✅ Nativo | |
| Windows | arm64 | ⚠️ Compatible, menos probado | |

Para verificar que tu instalación está descargando la variante correcta:

```bash
docker manifest inspect ghcr.io/photonne/photonne:latest \
    | grep -A1 '"architecture"'
docker manifest inspect ghcr.io/photonne/photonne-ml:latest \
    | grep -A1 '"architecture"'
```

Ambas deben mostrar entradas `amd64` y `arm64` en `linux`. Las líneas
`unknown/unknown` son las attestations (SBOM + provenance) firmadas por
Buildx — son metadata, no se ejecutan.

### Usando la imagen pre-construida (recomendado)

Las imágenes se publican automáticamente en GitHub Container Registry con cada
push a `main`:

```bash
git clone https://github.com/photonne/photonne.git && cd photonne
cp .env.example .env       # edita los 3 secretos obligatorios
docker compose up -d
```

Las imágenes `ghcr.io/photonne/photonne:latest` y `ghcr.io/photonne/photonne-ml:latest`
se descargan automáticamente para tu arquitectura. La primera vez puede tardar
varios minutos en función de tu conexión (la imagen ML lleva los modelos YOLO,
CLIP y Places365 baked-in para que el primer arranque no dependa de descargas
externas).

### Compilando localmente

```bash
docker compose up --build
```

El `docker-compose.override.yml` se aplica automáticamente y compila desde el
`Dockerfile` en `Src/Photonne.Server.Api/Dockerfile`. La build de
`photonne-ml` se adapta a tu arquitectura automáticamente (`ARG TARGETARCH`):
en `amd64` usa los wheels `+cpu` de PyTorch (slim, ~200 MB); en `arm64` usa
los wheels nativos de PyPI, también CPU-only.

## Versionado

La versión de la app se incrementa **automáticamente en cada commit**, derivada
del tipo del [Conventional Commit](https://www.conventionalcommits.org/), y se
pliega en ese mismo commit (vía `git commit --amend`) mediante el hook
`.githooks/post-commit` (activar con `./scripts/setup-hooks.sh`).

| Commit | Bump (semver) |
|---|---|
| `feat: ...` / `feat(scope): ...` | minor |
| `<tipo>!: ...` o footer `BREAKING CHANGE:` | major |
| `fix: ...` y cualquier otro tipo | patch |

La fuente de verdad es `Src/Directory.Build.props` (`<Version>`), que cascada
al servidor .NET, el cliente web, Android (`versionName`/`versionCode`), Desktop
y la constante `PhotonneVersion`. iOS no deriva de ahí, así que el hook también
sincroniza `MARKETING_VERSION` y `CURRENT_PROJECT_VERSION` en el
`project.pbxproj`. No re-bumpea en `--amend` manual, merges ni rebases. Más
detalle en [`.githooks/README.md`](.githooks/README.md).

## CI/CD

GitHub Actions publica automáticamente las dos imágenes Docker en cada push a
`main`, construyendo ambas arquitecturas en paralelo:

- **Registros**: `ghcr.io/photonne/photonne` (API) y `ghcr.io/photonne/photonne-ml` (servicio ML).
- **Plataformas**: `linux/amd64` (nativo en el runner) + `linux/arm64` (vía emulación QEMU).
- **Tags**: `latest` (rama `main`) y `sha-<commit>` por cada build.
- **Workflow**: `.github/workflows/docker-image.yml`.

## Licencia

GNU Affero General Public License v3.0 — ver [LICENSE](LICENSE) para más detalles.

--

Created by: Marc Caralps Fontrubí
