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
- **Vídeos resumen** — Monta bajo demanda un vídeo de 1 a 10 minutos con lo mejor de un año o de un álbum (con fotos, si quieres; solo horizontales, solo verticales —en formato vertical— o todo) y lo guarda en tu biblioteca, en la carpeta Recuerdos

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
- **Apps nativas** — Android e iOS (Kotlin Multiplatform + Compose). Fuente principal para el consumo de la biblioteca
- **Web** — tu biblioteca completa en el navegador, instalable como PWA y pensada para escritorio (selección, arrastrar y soltar, atajos), con la administración del servidor: usuarios, bibliotecas, tareas, estadísticas y ajustes

**Técnico**
- **Indexación automática** — Escanea directorios para indexar todos los assets (foto y vídeo)
- **Miniaturas** — Generación paralela en tres tamaños (small, medium, large)
- **Extracción de metadatos** — Lectura de datos EXIF de imágenes y vídeos (fecha, GPS, cámara, etc.)
- **JWT + Refresh Token** — Autenticación con soporte multi-dispositivo

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend | ASP.NET Core 10, EF Core, PostgreSQL |
| Apps nativas | Kotlin Multiplatform + Compose Multiplatform (Android, iOS) |
| Web | SvelteKit (SPA, PWA), Svelte 5, TypeScript, TanStack Query |
| Imágenes | ImageSharp, Magick.NET |
| Video | FFmpeg (vía Xabe.FFmpeg) |
| EXIF | MetadataExtractor |
| Auth | JWT Bearer + Refresh Tokens |
| Contenedores | Docker, Docker Compose |

## Estructura del proyecto

```
Photonne.sln
Directory.Build.props    # Versión del servidor, mínimos app↔servidor y prefijo Photonne. de las DLL
├── src/
│   ├── Server.Api/      # API REST ASP.NET Core 10
│   ├── Server.Ml/       # Servicio ML en Python (FastAPI)
│   ├── Client.SPA/      # Cliente web (SvelteKit en modo SPA, PWA)
│   └── Client.Native/   # Apps nativas (Android, iOS) en KMP + Compose
├── tests/
│   └── Server.Api.Tests/
└── docs/
    └── site/            # Página pública de GitHub Pages (política de privacidad)
```

### Responsabilidades por proyecto

**`Server.Api`** — Backend completo:
- API REST con endpoints por feature (`/api/assets`, `/api/albums`, `/api/users`, etc.)
- Indexación incremental de assets con extracción EXIF, miniaturas y jobs ML
- Autenticación JWT + Refresh Tokens, gestión de usuarios y permisos
- Migraciones de base de datos (EF Core + PostgreSQL)

**`Client.SPA`** — Cliente web (SvelteKit + Svelte 5, TypeScript, PWA):
- Todo lo que ofrece la app nativa salvo la copia de seguridad del dispositivo, más la administración del servidor; pensado para escritorio (selección con ratón y teclado, arrastrar y soltar, atajos).
- Lo sirve el propio servidor desde `/` (la imagen lo compila en una etapa de Node y lo copia a `wwwroot`).
- Cliente de API generado del contrato OpenAPI (`src/Server.Api/openapi/v1.json`); i18n en español e inglés.
- Decisión técnica documentada en [`docs/ADR-004-client-spa-sveltekit.md`](docs/ADR-004-client-spa-sveltekit.md); desarrollo y tests en [`src/Client.SPA/README.md`](src/Client.SPA/README.md).

**`Client.Native`** — Apps nativas (fuente principal de consumo):
- Kotlin Multiplatform + Compose Multiplatform.
- Targets Android e iOS con UI compartida. En el ordenador se usa la web (PWA); la app de escritorio se retiró ([ADR-005](docs/ADR-005-retirar-escritorio.md)).
- Cliente Ktor con refresh-on-401.
- Decisión técnica documentada en [`src/Client.Native/docs/ADR-001-kotlin-multiplatform.md`](src/Client.Native/docs/ADR-001-kotlin-multiplatform.md).

## Requisitos

- [.NET 10 SDK](https://dotnet.microsoft.com/download)
- [Node.js 22](https://nodejs.org/) (solo para desarrollar el cliente web)
- [Docker](https://www.docker.com/) y Docker Compose
- FFmpeg (descargado automáticamente si no está disponible)

## Inicio rápido

### 1. Clonar el repositorio

```bash
git clone https://github.com/photonne/photonne.git
cd photonne
```

### 2. Configurar las variables de entorno

```bash
cp .env.example .env
```

Edita `.env` y rellena al menos `POSTGRES_PASSWORD`. `JWT_KEY` y `ADMIN_PASSWORD` son opcionales: si los dejas vacíos, el servidor genera una clave JWT (en `CONFIG_HOST_PATH/jwt.key`) y una contraseña para el admin, que escribe una sola vez en el log (`docker compose logs photonne-api`). Los valores de ejemplo publicados (`TOKEN_SUPER_SECRET`, `admin123`…) se tratan como vacíos. Si prefieres fijar tú la clave JWT:

```bash
openssl rand -base64 48
```

### 3. Levantar la infraestructura (producción)

```bash
docker compose up -d
```

Usa la imagen pre-construida de GitHub Container Registry (`ghcr.io/photonne/photonne:latest`). Levanta:
- **PostgreSQL 17** (interno, sin puerto expuesto)
- **PgAdmin 4** solo bajo demanda: `docker compose --profile pgadmin up -d` (en `http://127.0.0.1:5050`)
- **Photonne API** en `http://localhost:1107`

### 4. Ejecutar en desarrollo (sin Docker)

```bash
cd src/Server.Api
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

Con la API en marcha (por defecto en `http://localhost:5030`; otra con `PHOTONNE_API_URL`):

```bash
cd src/Client.SPA
npm install
npm run dev
```

El servidor de desarrollo de Vite hace de proxy de `/api`, así que el navegador ve un único origen como en producción.

## Configuración

### Variables de entorno

| Clave | Descripción | Default |
|---|---|---|
| `ConnectionStrings__Postgres` | Cadena de conexión PostgreSQL | `Host=photonne-db;...` |
| `POSTGRES_DB` | Nombre de la base de datos | `photonne` |
| `POSTGRES_USER` | Usuario de PostgreSQL | `photonne_user` |
| `POSTGRES_PASSWORD` | Contraseña de PostgreSQL | `photonne_password` |
| `Jwt__Key` | Clave secreta para JWT (mín. 32 caracteres) — usar `JWT_KEY` en `.env`. Vacía o de ejemplo: se genera en `/data/config/jwt.key` | *(generada)* |
| `Jwt__Issuer` | Emisor del token | `Photonne` |
| `Jwt__Audience` | Audiencia del token | `Photonne` |
| `HTTPS_REDIRECT` | Forzar redirección HTTPS | `false` |
| `API_PORT` | Puerto expuesto de la API | `1107` |
| `PGADMIN_PORT` | Puerto expuesto de PgAdmin | `5050` |
| `PGADMIN_EMAIL` | Email de acceso a PgAdmin | `admin@example.com` |
| `PGADMIN_PASSWORD` | Contraseña de PgAdmin | `admin` |
| `ASSETS_HOST_PATH` | Ruta absoluta del host montada como volumen de assets (ver [Rutas en el host](#rutas-en-el-host)) | `./photonne/data/assets` |
| `THUMBNAILS_HOST_PATH` | Ruta absoluta del host montada como volumen de miniaturas | `./photonne/data/thumbnails` |
| `CONFIG_HOST_PATH` | Ruta del host montada en `/data/config` (clave JWT generada) | `./data/config` |
| `VIDEO_PLAYBACK_COPIES` | Genera copias H.264 (hasta 1080p) de los vídeos que no se reproducen en todas partes (HEVC, 4K, AVI, MKV…), de una en una, en `/data/thumbnails/.playback`. `false` en un servidor sin potencia para transcodificar | `true` |
| `OIDC_ENABLED` | Inicio de sesión con un proveedor OpenID Connect (Authentik, Keycloak, Authelia…). URI de redirección a registrar: `<OIDC_PUBLIC_URL>/api/auth/oidc/callback` | `false` |
| `OIDC_AUTHORITY` / `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | Emisor del proveedor y credenciales del cliente | *(vacío)* |
| `OIDC_DISPLAY_NAME` | Texto del botón: «Entrar con …» | `SSO` |
| `OIDC_AUTO_CREATE_USERS` | Crea la cuenta a quien el proveedor reconoce con correo verificado y no tiene una; si no, solo entran las cuentas existentes con ese correo | `false` |
| `OIDC_PUBLIC_URL` | Dirección pública del servidor, para la URI de redirección (necesaria tras un proxy que no reenvía el esquema) | *(de la petición)* |
| `ADMIN_USERNAME` | Nombre de usuario del admin inicial | `admin` |
| `ADMIN_EMAIL` | Email del admin inicial | `admin@photonne.local` |
| `ADMIN_PASSWORD` | Contraseña del admin inicial. Vacía o de ejemplo: se genera y se escribe en el log | *(generada)* |
| `ADMIN_FIRSTNAME` | Nombre del admin inicial | `Administrador` |
| `ADMIN_LASTNAME` | Apellido del admin inicial | `Sistema` |

### Usuario administrador

El admin inicial se configura mediante variables de entorno (ver tabla anterior) o en `appsettings.Development.json` para desarrollo local:

```json
{
  "AdminUser": {
    "Username": "admin",
    "Email": "admin@photonne.local",
    "Password": "una-contraseña-larga",
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

# JWT (opcional; vacío = se genera una). Genera la tuya con: openssl rand -base64 48
JWT_KEY=

# Rutas de datos en el host (absolutas — ver "Rutas en el host" más abajo)
ASSETS_HOST_PATH=/home/<TuUsuario>/photonne/data/assets
THUMBNAILS_HOST_PATH=/home/<TuUsuario>/photonne/data/thumbnails

# Admin inicial (opcional; vacío = se genera y se escribe en el log)
ADMIN_PASSWORD=
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

### PostgreSQL

Qué necesita el contenedor de Postgres (`shm_size`, `shared_buffers`), cómo
se aplican las migraciones al arrancar, los índices vectoriales de pgvector
y cómo diagnosticar consultas lentas desde el log:
[`docs/postgres-operations.md`](docs/postgres-operations.md).

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

Las releases se publican a mano (ver [Versionado](#versionado)): `:latest`
solo apunta a versiones publicadas, nunca a cada push. Cada release crea la
[release de GitHub](https://github.com/photonne/photonne/releases) `v<versión>`
(con sus notas), que es la que consulta el
aviso de actualización del panel de administración. Además de `:latest`, cada
imagen lleva la etiqueta de su versión (`:1.158.1`) y la del menor (`:1.158`):
fíjala en `docker-compose.yml` si quieres actualizar solo cuando tú decidas o
volver a una versión anterior. Para probar lo último de `main` sin esperar a
una release están las etiquetas `:edge` (y `:edge-gpu` para ML).

### Compilando localmente

```bash
docker compose up --build
```

El `docker-compose.override.yml` se aplica automáticamente y compila desde el
`Dockerfile` en `src/Server.Api/Dockerfile`. La build de
`photonne-ml` se adapta a tu arquitectura automáticamente (`ARG TARGETARCH`):
en `amd64` usa los wheels `+cpu` de PyTorch (slim, ~200 MB); en `arm64` usa
los wheels nativos de PyPI, también CPU-only.

## Versionado

Photonne es un monorepo con **tres componentes que se versionan y se publican
por separado**. La versión de cada uno la gestiona
[release-please](https://github.com/googleapis/release-please) a partir de los
[Conventional Commits](https://www.conventionalcommits.org/):

| Componente | Qué incluye | Versión en | Etiqueta | Publica |
|---|---|---|---|---|
| `server` | `src/Server.Api` y `src/Client.SPA` (la SPA va dentro de la imagen) | `Directory.Build.props` (`<Version>`), `version.txt` | `server-vX.Y.Z` | `ghcr.io/photonne/photonne` |
| `ml` | `src/Server.Ml` | `src/Server.Ml/version.txt` | `ml-vX.Y.Z` | `ghcr.io/photonne/photonne-ml` (+ `-gpu`) |
| `native` | `src/Client.Native` (Android, iOS) | `src/Client.Native/version.txt` | `native-vX.Y.Z` | solo la release con sus notas (las tiendas, a mano desde la etiqueta) |

Los commits **no** tocan la versión. En cada push a `main` release-please
mantiene abierta **una PR de release por componente con cambios**
(`chore: release server X.Y.Z`, `chore: release native X.Y.Z`...). Un commit
cuenta para los componentes cuyos ficheros toca: un cambio solo en la SPA abre
(o actualiza) la PR de `server`, nunca la de `native`. Lo que no es de ML ni de
la app nativa (workflows, docs...) cuenta para `server`. Cada PR acumula el
changelog de su componente y calcula su siguiente versión:

| Commits del componente desde su última release | Bump (semver) |
|---|---|
| algún `<tipo>!: ...` o footer `BREAKING CHANGE:` | major |
| algún `feat: ...` / `feat(scope): ...` | minor |
| solo `fix:`, `perf:`, `refactor:`, `revert:` | patch |

`docs:`, `chore:`, `ci:`, `test:`, `build:` y `style:` por sí solos no abren
release.

**Publicar un componente = hacer merge de su PR**, cuando quieras y sin tocar
los demás. Al hacerlo, release-please etiqueta el commit y crea la release como
borrador; `release.yml` construye solo ese componente desde la etiqueta
(imágenes `:X.Y.Z`, `:X.Y`, `:latest`; `native` no construye nada) y,
cuando todo está subido, publica la release. Solo las del servidor se marcan
como *Latest* en GitHub. Para probar antes de publicar están las imágenes
`:edge`, que salen de cada push a `main` (ver [CI/CD](#cicd)).

Cada PR actualiza el `CHANGELOG.md` y los ficheros de versión de su
componente. En `native`, `version.txt` cascada a Android
(`versionName`/`versionCode`) y la constante `PhotonneVersion`; iOS no
deriva de ahí, así que el workflow sincroniza también `MARKETING_VERSION` y
`CURRENT_PROJECT_VERSION` en el `project.pbxproj` dentro de esa PR. No edites
las versiones a mano.

El aviso de actualización del panel de administración compara con la última
release `server-v*`.
Las etiquetas `vX.Y.Z` de antes de separar los componentes valen para todos.

Para que el workflow pueda abrir las PR hace falta activar *Allow GitHub
Actions to create and approve pull requests* (primero en los ajustes de la
organización, luego en los del repo) o crear el secret `RELEASE_PLEASE_TOKEN`
con un PAT fine-grained del repo (Contents y Pull requests en lectura/escritura).

Los mínimos de compatibilidad `PhotonneMinClientVersion` (en versiones de la
app) y `PhotonneMinServerVersion` (en versiones del servidor) de
`Directory.Build.props` se siguen subiendo a mano (ver el comentario del
fichero y [docs/api-compatibilidad.md](docs/api-compatibilidad.md)).

## CI/CD

| Workflow | Cuándo | Qué publica |
|---|---|---|
| `api-image.yml` | push a `main` que toca el servidor/cliente web | `ghcr.io/photonne/photonne:edge` y `:sha-<commit>` |
| `ml-image.yml` | push a `main` que toca `src/Server.Ml` | `ghcr.io/photonne/photonne-ml:edge`, `:edge-gpu` y `:sha-<commit>[-gpu]` |
| `release.yml` | cada push a `main` (mantiene una PR de release por componente); merge de una de ellas | solo el componente publicado: `:X.Y.Z`, `:X.Y`, `:latest` de su imagen (+ `-gpu` en ML) y su release de GitHub |
| `native-build.yml` | cambios en `src/Client.Native` | solo compila y prueba (en las PR, los tests del simulador de iOS se saltan: los mismos tests comunes corren en la JVM) |

Las imágenes Docker se construyen para `linux/amd64` y `linux/arm64`, cada
arquitectura en su runner nativo; la variante GPU de ML solo para `amd64`.

## Licencia

GNU Affero General Public License v3.0 — ver [LICENSE](LICENSE) para más detalles.

### Datos de terceros

La imagen del servidor incluye el dataset **cities500** de [GeoNames](https://www.geonames.org/),
usado para resolver nombres de lugar sin conexión a partir de las coordenadas GPS de las fotos.

> Datos de lugares © GeoNames, bajo licencia [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

La atribución es una condición de la licencia, no una cortesía: redistribuir el dataset dentro
de la imagen solo está permitido si se acredita. El servidor la expone además en
`GET /api/attributions` (público) y la app la muestra en la pestaña Más.

--

Created by: Marc Caralps Fontrubí
