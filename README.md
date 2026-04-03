# Photonne

Sistema de gestión de fotos y videos auto-hospedado. Indexa, organiza y visualiza tu biblioteca multimedia desde cualquier dispositivo.

## Características

- **Indexación automática** — Escanea directorios, extrae metadatos EXIF y genera miniaturas
- **Línea de tiempo** — Vista cronológica de toda la biblioteca
- **Álbumes y carpetas** — Organización manual y por estructura de directorios
- **Mapa** — Visualización geográfica por metadatos de ubicación
- **Miniaturas** — Generación paralela en tres tamaños (small, medium, large)
- **Etiquetas** — Tags automáticos por ML y tags manuales de usuario
- **Archivado** — Archiva assets para ocultarlos de la vista principal sin eliminarlos
- **Multi-usuario** — Roles, permisos por carpeta y álbum, admin principal protegido
- **Sincronización desde dispositivo** — Subida de assets desde el navegador
- **JWT + Refresh Token** — Autenticación con soporte multi-dispositivo
- **PWA** — Instalable como app en escritorio y móvil, carga offline del app shell

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend | ASP.NET Core 10, EF Core, PostgreSQL |
| Frontend | Blazor WebAssembly (PWA) |
| UI | MudBlazor 9 |
| Imágenes | ImageSharp, Magick.NET |
| Video | FFmpeg (vía Xabe.FFmpeg) |
| EXIF | MetadataExtractor |
| Auth | JWT Bearer + Refresh Tokens |
| Contenedores | Docker, Docker Compose |

## Estructura del proyecto

```
Photonne.sln
└── Src/
    ├── Photonne.Server.Api/    # API REST ASP.NET Core 10
    └── Photonne.Client.Web/    # Blazor WASM PWA
```

### Responsabilidades por proyecto

**`Server.Api`** — Backend completo:
- API REST con endpoints por feature (`/api/assets`, `/api/albums`, `/api/users`, etc.)
- Indexación incremental de assets con extracción EXIF, miniaturas y jobs ML
- Autenticación JWT + Refresh Tokens, gestión de usuarios y permisos
- Migraciones de base de datos (EF Core + PostgreSQL)

**`Client.Web`** — Cliente Blazor WASM completo:
- Componentes: `AssetCard`, `ApiErrorDialog`, `EmptyState`, etc.
- Layout: `MainLayout`, `NavMenu`, `LoginLayout`
- Páginas: Albums, Timeline, Folders, Trash, AssetDetail, Login, etc.
- Servicios: interfaces + implementaciones (AuthService usa `IJSRuntime`/`localStorage`)
- PWA: `manifest.webmanifest`, `service-worker.js` con cache del app shell

## Requisitos

- [.NET 10 SDK](https://dotnet.microsoft.com/download)
- [Docker](https://www.docker.com/) y Docker Compose
- FFmpeg (descargado automáticamente si no está disponible)

## Inicio rápido

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-usuario/photonne.git
cd photonne
```

### 2. Levantar la infraestructura

```bash
docker compose up -d
```

Esto levanta:
- **PostgreSQL 16** en `localhost:5432`
- **PgAdmin 4** en `http://localhost:5050`
- **Photonne API** en `http://localhost:5000`

### 3. Ejecutar en desarrollo (sin Docker)

```bash
cd Src/Photonne.Server.Api
dotnet run
```

La API estará disponible en `https://localhost:5001`.
Documentación interactiva: `https://localhost:5001/scalar`

### 4. Cliente web

```bash
cd Src/Photonne.Client.Web
dotnet run
```

## Configuración

### Variables de entorno / appsettings

| Clave | Descripción | Default |
|---|---|---|
| `ConnectionStrings:Postgres` | Cadena de conexión PostgreSQL | `Host=localhost;Port=5432;Database=photonne;...` |
| `Jwt:Key` | Clave secreta para JWT (mín. 32 caracteres) | — |
| `Jwt:Issuer` | Emisor del token | `Photonne` |
| `Jwt:Audience` | Audiencia del token | `Photonne` |
| `ASSETS_PATH` | Ruta al directorio de assets | `C:\PhotoHubAssets\NAS\Assets` |
| `THUMBNAILS_PATH` | Ruta donde se guardan las miniaturas | `{WorkDir}/thumbnails` |
| `FFMPEG_PATH` | Ruta al directorio con los binarios de FFmpeg | Descargado automáticamente si no se especifica |

### Usuario administrador (desarrollo)

Configurado en `appsettings.Development.json`:

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

### Docker Compose — variables personalizables

```yaml
# compose.yaml
POSTGRES_DB: photonne
POSTGRES_USER: photonne_user
POSTGRES_PASSWORD: photonne_password
ASSETS_PATH: /ruta/a/tus/fotos
```

## API

La API sigue una estructura por features. Endpoints principales:

| Área | Endpoints |
|---|---|
| Auth | `POST /api/login`, `POST /api/refresh-token` |
| Assets | `GET /api/assets`, `GET /api/assets/{id}`, `GET /api/assets/{id}/content` |
| Indexación | `GET /api/assets/index/stream` (SSE), `GET /api/assets/index` |
| Subida | `POST /api/assets/upload` |
| Miniaturas | `GET /api/assets/{id}/thumbnail/{size}` |
| Álbumes | `GET/POST /api/albums`, `GET /api/albums/{id}` |
| Carpetas | `GET /api/folders` |
| Línea de tiempo | `GET /api/timeline` |
| Mapa | `GET /api/map` |
| Configuración | `GET/PUT /api/settings` |
| Administración | `GET /api/admin/stats`, `GET /api/admin/users` |

Documentación interactiva disponible en `/scalar` (modo desarrollo).

## Indexación

El proceso de indexación es incremental y transmite progreso en tiempo real vía streaming:

1. **Descubrimiento** — Escaneo recursivo del directorio `ASSETS_PATH`
2. **Comparación** — Detección de archivos nuevos, modificados y huérfanos
3. **Extracción EXIF** — Metadatos de imagen/video (fecha, GPS, cámara, etc.)
4. **Detección de tags** — Clasificación automática por ML
5. **Miniaturas** — Generación paralela (small/medium/large)
6. **Base de datos** — Persistencia y limpieza de huérfanos

Accesible desde: `Admin > Colas > Indexar`

## Despliegue con Docker

```bash
# Construir la imagen
docker build -t photonne-api ./Src/Photonne.Server.Api

# O usar Docker Compose completo
docker compose up --build
```

La imagen base es `mcr.microsoft.com/dotnet/aspnet:10.0` con `ffmpeg` y `libgdiplus` instalados.

## Licencia

GNU Affero General Public License v3.0 — ver [LICENSE](LICENSE) para más detalles.
