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

**Técnico**
- **Indexación automática** — Escanea directorios para indexar todos los assets (foto y vídeo)
- **Miniaturas** — Generación paralela en tres tamaños (small, medium, large)
- **Extracción de metadatos** — Lectura de datos EXIF de imágenes y vídeos (fecha, GPS, cámara, etc.)
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
git clone https://github.com/marccafo/photonne.git
cd photonne
```

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
| `ASSETS_HOST_PATH` | Ruta del host montada como volumen de assets | `./data/photos` |
| `THUMBNAILS_HOST_PATH` | Ruta del host montada como volumen de miniaturas | `./data/thumbnails` |
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

# Rutas de datos en el host
ASSETS_HOST_PATH=/ruta/a/tus/fotos
THUMBNAILS_HOST_PATH=/ruta/a/tus/miniaturas

# Admin inicial
ADMIN_PASSWORD=contraseña-segura
```

## API

La API sigue una estructura por features. Endpoints principales:

| Área | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `POST /api/auth/refresh` |
| Assets | `GET /api/assets`, `GET /api/assets/{id}`, `GET /api/assets/{id}/content` |
| Búsqueda | `GET /api/assets/search` |
| Indexación | `GET /api/assets/index/stream` (SSE), `GET /api/assets/index` |
| Subida | `POST /api/assets/upload` |
| Sincronización | `POST /api/assets/sync` |
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

## Despliegue con Docker

### Usando la imagen pre-construida (recomendado)

La imagen se publica automáticamente en GitHub Container Registry con cada push a `main`:

```bash
docker compose up -d
```

La imagen `ghcr.io/photonne/photonne:latest` se descarga automáticamente.

### Compilando localmente

```bash
docker compose up --build
```

El `docker-compose.override.yml` se aplica automáticamente y compila desde el `Dockerfile` en `Src/Photonne.Server.Api/Dockerfile`.

## CI/CD

GitHub Actions publica automáticamente la imagen Docker en cada push a `main`:

- **Registro**: `ghcr.io/photonne/photonne`
- **Tags**: `latest` (rama main) y `sha-<commit>` por cada build
- **Workflow**: `.github/workflows/docker-image.yml`

## Licencia

GNU Affero General Public License v3.0 — ver [LICENSE](LICENSE) para más detalles.

--

Created by: Marc Caralps Fontrubí
