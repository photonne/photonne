# ADR-002 — La PWA pasa a ser el panel de administración

- **Estado**: Aceptado
- **Fecha**: 2026-05-28
- **Contexto previo**: [ADR-001 — Kotlin Multiplatform para el cliente nativo](../../Photonne.Client.Native/docs/ADR-001-kotlin-multiplatform.md)

## Contexto

Históricamente `Photonne.Client.Web` (Blazor WebAssembly) ha sido el cliente
principal y completo de Photonne: consumo de la biblioteca (timeline, álbumes,
carpetas, mapa, búsqueda, personas, favoritos, archivo, papelera,
notificaciones, subida, sincronización de dispositivo, compartir, detalle de
asset) **y** administración del servidor (usuarios, bibliotecas externas,
estadísticas, ajustes, tareas/colas, mantenimiento, copia de seguridad).

Con la madurez del cliente nativo (`Photonne.Client.Native`, Kotlin
Multiplatform + Compose), las apps de Android, iOS y escritorio ya cubren
**todo el consumo** de la biblioteca y también una sección de administración.
Mantener dos clientes completos en paralelo duplica el esfuerzo y diluye el
foco de cada uno.

## Decisión

`Photonne.Client.Native` pasa a ser la **fuente principal** para el consumo de
la biblioteca en todas las plataformas.

`Photonne.Client.Web` se reestructura como **consola de administración** de
Photonne, accesible desde cualquier navegador de escritorio sin instalar nada.
Su valor diferencial es el formato de escritorio: pantallas amplias para
tablas, operaciones sobre muchos usuarios/bibliotecas y gestión cómoda del
servidor.

Alcance acordado de esta reestructuración:

1. **Solo administración del servidor.** Se elimina toda la navegación de
   consumo y el visor de fotos. El panel no muestra ni modera la biblioteca de
   los usuarios.
2. **Acceso restringido a administradores.** Cualquier usuario autenticado sin
   rol `Admin` es redirigido a un aviso (`/use-the-app`) que le indica usar la
   app nativa, con opción de cerrar sesión.
3. **Sin funciones nuevas en esta fase.** Se conservan y pulen las
   funcionalidades de administración que ya existían; no se añaden capacidades
   web-only (logs, auditoría, monitorización en vivo) por ahora.

## Consecuencias

- La ruta `/` deja de ser el timeline y pasa a ser un **panel** con
  estadísticas de cabecera y accesos rápidos a cada área de administración.
- Se elimina del proyecto Web el código exclusivo de consumo: páginas,
  componentes y servicios de timeline, álbumes, carpetas, mapa, búsqueda,
  personas, favoritos, archivo, papelera, subida, sincronización de
  dispositivo, compartir y detalle de asset; junto con sus registros de
  inyección de dependencias.
- Se conservan las áreas de administración (usuarios, bibliotecas externas,
  estadísticas, ajustes, tareas/colas, mantenimiento, copia de seguridad),
  las utilidades de servidor (duplicados, archivos pesados, ubicaciones), las
  notificaciones del sistema y los ajustes de la cuenta del propio admin
  (perfil, seguridad, apariencia).
- El `manifest.webmanifest` se renombra a «Photonne Admin» con accesos directos
  de administración.
- Las apps nativas pasan a ser el único punto de entrada para usuarios no
  administradores.

## Alternativas consideradas

- **Mantener la PWA como cliente completo en paralelo al nativo.** Descartada:
  duplica funcionalidad y mantenimiento sin aportar valor frente al nativo.
- **Incluir un visor de solo lectura para moderación.** Descartada en esta
  fase para mantener el panel centrado en administración del servidor; puede
  reconsiderarse más adelante.
