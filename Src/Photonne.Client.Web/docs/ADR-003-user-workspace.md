# ADR-003: La web recupera una superficie de usuario — el workspace de gestión

- Fecha: 2026-09-08
- Estado: aceptado
- Revierte parcialmente: [ADR-002](ADR-002-pwa-admin-console.md)

## Contexto

ADR-002 (2026-05-28) redujo Client.Web a consola de administración: el cliente
nativo KMP cubría el consumo y mantener dos UIs de usuario no compensaba. Ese
razonamiento sigue siendo válido para el CONSUMO (ver fotos en el sofá, en el
móvil), pero la práctica demostró un hueco distinto: la GESTIÓN.

Se intentó cubrirlo puliendo el target desktop de Compose (vídeo VLCJ,
ergonomía de ratón/teclado, 2026-09-08) y la conclusión al usarlo fue clara:
portar una UI táctil de consumo al escritorio no produce una herramienta de
productividad. Organizar miles de fotos —mover en masa a carpetas, limpiar,
etiquetar, revisar duplicados— pide un paradigma de gestor: rejilla densa,
selección con Shift/Ctrl y arrastre, operaciones en lote, teclado.

Se evaluó construir ese gestor en un stack web nuevo (React/Svelte) y se
descartó: para un único mantenedor, el coste permanente de un tercer stack no
compensa cuando Blazor ya aporta auth con refresh, manejo de errores, tema,
PWA y despliegue integrado en la imagen del servidor — y el API ya tiene todos
los endpoints (el cliente KMP los consume).

## Decisión

1. Client.Web deja de ser solo-admin. Cualquier usuario autenticado accede al
   **workspace** (`/fotos`, su landing), ajustes personales y notificaciones;
   las superficies de administración siguen tras el rol Admin (`App.razor`).
2. El workspace se construye sobre el **modelo de buckets** del timeline
   (esqueleto de meses con conteo exacto + hidratación por viewport), con
   layout justificado calculado en cliente (`JustifiedLayout`), celdas ligeras
   sin componentes MudBlazor por celda, y `content-visibility: auto` como
   virtualización.
3. La selección es primero-teclado/ratón (`SelectionState`: Shift+clic por
   rango con ancla, Ctrl/Cmd+clic, pintado por arrastre, Escape) y las
   acciones son en lote contra los endpoints existentes; papelera y archivar
   ofrecen Deshacer rehidratando solo los meses afectados.
4. El visor es un overlay sin navegación de ruta (el scroll de la rejilla se
   conserva), con el panel de información editable (descripción, fecha de
   captura, etiquetas de usuario).
5. El cliente nativo KMP queda como cliente de CONSUMO (móvil ante todo; el
   desktop Compose se congela como cliente "de sofá"). La app deja de ser
   requisito: `/use-the-app` pasa de jaula a página informativa.

## Consecuencias

- Los DTOs del workspace siguen escritos a mano pero los nuevos se limitan al
  subconjunto usado; queda como mejora generar cliente/DTOs del OpenAPI que el
  servidor ya expone.
- El Bearer viaja por petición (`AuthHeaderHandler`); el patrón legado de
  mutar `DefaultRequestHeaders` queda obsoleto y se irá retirando.
- Deuda consciente heredada del modelo actual (igual que el cliente KMP):
  thumbnail/content/motion son endpoints anónimos protegidos solo por GUID no
  adivinable. Endurecerlos exige tocar servidor + KMP + web + service worker a
  la vez y queda para una fase de seguridad propia.
- Fase C completada (2026-09-08): álbumes (CRUD + smart con editor de reglas y
  preview + compartición), carpetas (árbol persistente + gestión + permisos),
  colecciones (favoritas/archivadas/papelera), búsqueda con filtros y
  semántica, organizar (sugerencias + reglas + bandeja con badge), duplicados,
  subida drag & drop con fechas preservadas, scrubber por buckets y navegación
  completa por teclado. Piezas transversales: `BatchActions`/`IAssetGridHost`,
  `FlatPhotoGrid`/`WorkspaceCollection`, `SmartRuleEditor`, `FolderTree`,
  `SharePermissionsDialog`, `IdThumbGrid`/`IdReviewDialog`.
- Backlog restante: endurecer los endpoints de media anónimos (deuda de
  seguridad transversal), jubilar el timeline clásico por cursor, editar la
  regla de un álbum smart existente y la condición de tags de usuario en el
  editor (ambas requieren API nueva: PUT de regla y catálogo de ids de tag),
  grupos anidados/"not" en el editor de reglas, sugerencia de fecha en el
  panel de info, y mapa/personas como vistas web.
