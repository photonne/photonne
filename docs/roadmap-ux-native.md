# Roadmap UX/UI — Client.Native

Auditoría del 2026-09-17 sobre `Src/Photonne.Client.Native/composeApp/src/commonMain/kotlin/com/photonne/app/` (en adelante, las rutas son relativas a esa carpeta). Cinco pasadas de solo lectura: timeline/rejilla/shell, visor/mapa/recuerdos, álbumes/carpetas/organizar/utilidades, búsqueda/personas/ajustes/login/backup, y una transversal de consistencia. `ui/admin` quedó fuera porque se auditó y normalizó el mismo día.

**Estado: los 52 puntos correctivos cerrados** — Lotes A (`995cd74`), B y C (`83305d5`), D y E (`1fbb212`), I (`6a148d2`, `3490317`), F (`6427912`), G (`ad4eea2`, `232756b`, `733c4a0`) y H (`fa85fcf`, `45825d1`, `2b3e01f`). (2026-09-19, **sin verificar en dispositivo**.) Varios se cerraron en parcial: el detalle está en cada punto y el resumen por tipo de bloqueo, en "Qué queda". Marca cada punto con `[x]` al cerrarlo y anota el commit.

Fiabilidad de los hallazgos:
- Comprobados a mano: 1, 2, 4 y 6.
- El resto lo verificaron las auditorías leyendo el código, sin repaso uno a uno ni ejecución en dispositivo.
- Lo marcado como *sospecha* no está confirmado.

Esfuerzo: S pequeño, M medio, L grande.

No proponer de nuevo (rechazado por Marc): carril lateral de selección, crossfade/reflow/dos capas en el zoom, tinte sin blur en el cromo, separar fotos locales y del servidor en el timeline, `TopAppBar` acoplada, `TabRow` de ámbito en álbumes/carpetas, carpetas del dispositivo como pestaña o grupo en línea, vincular el filtro de visibilidad del timeline al backup.

## Orden recomendado

1. Lote A, en uno o dos commits. Todo es S y son errores reales.
2. Lotes B y C juntos. El puente de errores y resultados es una sola pieza; las confirmaciones reutilizan `ConfirmActionDialog`.
3. Lote E (visor) y el punto 19 (selección de fotos locales).
4. Lote I por tandas mecánicas, empezando por los textos y el código muerto.
5. Lotes F, G y H según lo que más moleste.

Puntos con más de una salida, que piden diagnóstico y opciones antes de tocar: 19, 23, 44 y 49.

---

## Lote A — Errores funcionales

- [x] (`995cd74`) **1. "Copiar enlace" no copia nada** (S).
  - `App.kt:4426-4429`: `onCopy = { actionsViewModel.dismissLink() }`. `ui/actions/ShareAssetsDialog.kt:135-172` no toca el portapapeles y el texto de la URL no es seleccionable.
  - El enlace ya está creado en el servidor (crea un álbum) y el portapapeles queda vacío. El botón lleva un icono de candado.
  - Arreglo: copiar con `LocalClipboardManager`, snackbar "Enlace copiado", mantener la hoja u ofrecer la hoja de compartir del sistema, icono ContentCopy. Referencia que sí copia: `ui/album/ShareDialogs.kt:100`, `MyLinksScreen.kt:98`.
- [x] (`995cd74`) **2. El token Bearer sale a terceros, y falta la atribución del mapa** (S).
  - `data/api/AuthRefreshPlugin.kt:128-136` añade `Authorization` a toda petición sin mirar el host. `App.kt:431` pone ese cliente como loader de Coil. Las teselas salen de `basemaps.cartocdn.com` (`ui/map/OsmMap.kt:67-72`, `:484`) y `tile.openstreetmap.org` (`ui/asset/AssetDetailScreen.kt:2160`).
  - Arreglo: mandar el token solo si el host coincide con la URL base de la API (o `SKIP_AUTH_HEADER` en las teselas). Añadir "© OpenStreetMap © CARTO".
- [x] (`995cd74`) **3. Contraseña incorrecta en el login dice "Sesión expirada"** (S).
  - `data/error/UiError.kt:92-100` traduce todo 401 a ese texto; `ui/login/LoginViewModel.kt:198-203` lo usa. El servidor devuelve `Results.Unauthorized()` (`Photonne.Server.Api/Features/Auth/LoginEndpoint.cs:48,53`). Un fallo de red dice "Error desconocido".
  - Arreglo: mapear en `LoginViewModel.submit`: 401 → "Usuario o contraseña incorrectos", sin estado → "No se pudo conectar con <host>", 400/404 → "Ese servidor no parece Photonne".
- [x] (`995cd74`) **4. El snackbar "Movidas a la papelera · Deshacer" sale antes de que la petición termine** (S-M).
  - `ui/main/MainScaffold.kt:968-977` (`runUndoable`) lanza `action()` sin esperar. El fallo llega aparte por el banner (`ui/timeline/TimelineViewModel.kt:210-227`).
  - *Sospecha*: pulsar Deshacer con la petición en vuelo puede restaurar antes de que termine el borrado.
  - Arreglo: que las acciones masivas devuelvan resultado o callback; snackbar de deshacer en el éxito y de error en el fallo. Lo heredan todas las pantallas que usan `AssetSelectionBottomBar`.
- [x] (`995cd74`) **5. Saltar a fecha y el zoom año→mes caen una fila antes** (S).
  - `ui/timeline/TimelineScreen.kt:593`, `612`, `629` hacen scroll al índice crudo. El scrubber y el traspaso del reflow sí suman las cabeceras (`TimelineScrubber.kt:136`, `TimelineScreen.kt:661`). `anchorDate` en `:457` tampoco resta.
  - Arreglo: sumar `headerCount` en esas tres llamadas y restarlo en `anchorDate`.
- [x] (`995cd74`) **6. Archivar o borrar desde el visor no actualiza Carpeta ni Favoritos** (S).
  - `App.kt:3320-3340` avisa a timeline, álbum, búsqueda, archivo, papelera y persona. `ui/folder/FolderDetailViewModel.kt:167` (`applyAssetRemovedLocal`) no tiene llamadores; el de `FavoritesViewModel` (`:192`) solo se llama desde dentro. Mapa y recuerdos tampoco se enteran.
  - Arreglo rápido: cablear carpeta y favoritos en `App.kt:3320/3330`. Arreglo de fondo: punto 52.
- [x] (`995cd74`) **7. Duplicados: mensajes invisibles y vacío engañoso** (S-M).
  - `ui/utilities/UtilitiesDuplicatesViewModel.kt:68`, `144-147`: `deleteSelected()` pone `statusMessage` y `load()` lo anula.
  - `ui/utilities/UtilitiesDuplicatesScreen.kt:101-120`: estado y error se pintan en y=0, sin `reservedTop`, detrás del cromo.
  - `:122-131`: el `when` no tiene rama de error, así que un fallo de carga enseña "no hay duplicados".
  - `:237`: confirmación con `AlertDialog` a mano y botón relleno; va a la papelera pero no ofrece Deshacer. Texto de éxito en español a fuego (`ViewModel:144`).
  - *Sospecha*: el FAB de borrar (`:215-217`, `padding(16.dp)`) choca con la nav flotante. Mismo patrón en `devicebackup/BackupPendingScreen.kt:198`.
  - Arreglo: `ResultSnackbar` con Deshacer, rama `ErrorBanner` con reintento, cápsula inferior tipo `ConfirmMoveCapsule` en lugar del FAB.
- [x] (`995cd74`) **8. En Carpetas, "Mi dispositivo" y "Para organizar" desaparecen** (S).
  - `ui/folder/FoldersListScreen.kt:374`, `404`: `inboxHeader` solo se emite en la rama con contenido; las ramas vacía, búsqueda y error (`:346-362`) lo pierden. Contradice el comentario de `:153-155`.
  - `:353-361`: el error es un `Text` rojo sin reintento y sin scroll.
  - `ui/folder/FolderDetailScreen.kt:149-153`: una carpeta vacía dice "Indexa una carpeta desde la app web".
  - El vacío raíz no ofrece "Nueva carpeta" (álbumes sí).

## Lote B — Fallos silenciosos

- [x] (`83305d5`) **9. Con contenido en pantalla, los errores de acción no se ven** (M).
  - Todas las pantallas pintan `state.error` solo con la lista vacía: `ui/album/AlbumDetailScreen.kt:217`, `ui/library/TrashScreen.kt:96`, `ui/folder/FolderDetailScreen.kt:139`, Archivo, Favoritos, `people/PeopleScreen.kt:99`, `PersonDetailScreen.kt:82`, `PersonSuggestionsScreen.kt:94`, `search/SearchScreen.kt:122`. En el visor, `state.error` solo sale como una línea roja al fondo del panel de info (`AssetDetailScreen.kt:602`, `:1712`) y `clearError()` no se llama nunca.
  - Los ViewModels masivos devuelven los elementos y ponen `error` sin que nadie lo observe (`AlbumDetailViewModel.kt:441`, `TrashViewModel.kt:159`). En `App.kt` solo hay puente para `actionsState` (`:1750-1765`).
  - El error viejo aparece luego en otro diálogo: `App.kt:3435`, `3475` ("Editar álbum", borrar), `:3673`, `:4028` (añadir a álbum, mover).
  - Papelera: vaciar, purgar y restaurar todo no reciben `errorMessage` (`App.kt:4217-4259`).
  - `InviteMemberDialog` se cierra antes del resultado (`App.kt:3619`). `MyLinksScreen.kt:181` pone `editing = null` al instante.
  - Timeline: los fallos de añadir/mover van al `error` de carga y salen como banner con "Reintentar" que refresca el timeline (`TimelineViewModel.kt:241-248`, `279-286`; `TimelineScreen.kt:1220-1228`).
  - Arreglo: un puente único en `App.kt` de error de cada ViewModel a `ResultSnackbar`, limpiando al mostrar. Separar `error` (carga) de `actionError`. Limpiar el error al abrir un diálogo. Pasar `errorMessage` a los diálogos de la papelera.
- [x] (`83305d5`) **10. Las acciones que salen bien tampoco avisan** (S-M).
  - Añadir a álbum y Mover desde el timeline: `App.kt:3679-3685`, `4035-4042`. Propuesta: snackbar con "Ver" o "Deshacer".
  - "Reagrupar" personas descarta `personsCreated` (`App.kt:2516`).
  - Archivar o borrar en el visor cierra el visor sin snackbar ni Deshacer (`App.kt:3320-3340`); archivar no confirma (`AssetDetailScreen.kt:826`, `:903`). Propuesta: `TopSnackbarHost` en el visor y avanzar a la siguiente foto; cerrar solo si la lista queda vacía.
- [x] (`83305d5`) **11. Errores a pantalla completa sin salida** (S).
  - `ErrorBanner` admite `onRetry` (`ui/error/ErrorBanner.kt:73`) y no se lo pasan: `album/AlbumsListScreen.kt:176`, `library/TrashScreen.kt:98`, `ArchivedScreen.kt:80`, `FavoritesScreen.kt:81`, `UnsupportedFilesScreen.kt:75`, `memories/MemoriesScreen.kt:97-100`, `explore/ExploreLabelGridScreen.kt:96-99`, `people/PersonDetailScreen.kt:82-85`, `PersonSuggestionsScreen.kt:94-97`. `album/MyLinksScreen.kt:117-126` y `settings/AccountStorageScreen.kt:81-84` usan un `Text` rojo.
  - La caja del error no hace scroll, así que `PullToRefreshBox` no recibe el gesto.
  - Guardas que bloquean el reintento hasta reiniciar: `attempted` en `explore/ExploreFacetsViewModel.kt:34` y `MemoriesScreen.kt:82`.
  - Abrir un recuerdo falla en silencio: `MemoryFeedViewModel.open()` (`:128-135`) pone `error`, que solo se pinta con las filas vacías; un `detail.assets` vacío (`:126`) no hace nada.
  - Arreglo: `onRetry` en todas, rama de error con `verticalScroll` (como `EmptyState`), fallos de abrir recuerdo por `ResultSnackbar`.
- [x] (`83305d5`) **12. El spinner de pull-to-refresh queda tras el cromo flotante** (S).
  - `ui/theme/PullToRefresh.kt:20-22` documenta `indicatorTopPadding`; solo lo pasa `admin/AdminListScaffold.kt:108`. Pasar `subscreenChromeReservedTop()` o el `reservedTop` del álbum.
- [x] (`83305d5`) **13. Banner de error bajo el cromo en Notificaciones y Personas** (S).
  - `ui/notifications/NotificationsScreen.kt:112-116` sin `reservedTop`; `NotificationsViewModel.kt:30,162-168`: una primera carga fallida cae en una lista vacía con "total: 0". `PeopleScreen.kt:100` igual.

## Lote C — Acciones destructivas sin red de seguridad

- [x] (`83305d5`) **14. Cerrar sesión con un solo toque** (S). `ui/main/MoreScreen.kt:314` → `App.kt:767` → `authRepository.logout()`. Confirmación que mencione los backups pendientes si `backupPendingCount > 0`. El `OutlinedButton` pequeño y centrado rompe el patrón de filas de esa pantalla.
- [x] (`83305d5`) **15. Fusionar personas** (M). `App.kt:4326-4349`: `runCatching { }.onSuccess { }` sin `onFailure`, sin progreso ni confirmación. `ui/people/PersonPickerDialog.kt:51-53,127-134`: solo las páginas cargadas, sin buscador, "Sin nombre" + número con avatar de 40 dp. Arreglo: confirmación con los dos avatares, `ResultSnackbar`, buscador que cargue todas las páginas.
- [x] (`83305d5`) **16. Acciones sin confirmación ni resultado** (S).
  - "Aceptar todas" / "Descartar todas" en sugerencias de caras: `App.kt:2491-2500`, `PersonSuggestionsViewModel.kt:126-178`, menú en `PersonSuggestionsScreen.kt:192-199`. Afecta también a páginas no cargadas; se descarta el recuento del servidor.
  - Revocar enlace desde la hoja del álbum: `ui/album/ShareDialogs.kt:137`, `App.kt:3554`. En "Mis enlaces" sí confirma (`MyLinksScreen.kt:186`).
  - Quitar miembro: `ui/album/PermissionDialogs.kt:147`, `ui/folder/FolderPermissionDialogs.kt`, `App.kt:3568`.
  - Quitar un origen de backup: `devicebackup/BackupScreen.kt:289` → `DeviceBackupViewModel.kt:543-551`. Propuesta: snackbar con Deshacer.
  - Archivar en bloque desde la hoja de clúster del mapa (la papelera sí confirma).
- [x] (`83305d5`) **17. "Quitar del álbum" sin confirmación ni Deshacer** (S-M). `App.kt:1336`. `AlbumDetailViewModel.kt:404-418` hace una petición por asset y, si falla a medias, restaura en local todo aunque el servidor ya quitó algunos. Arreglo: snackbar con Deshacer que reañada los ids, y refrescar el álbum tras un fallo parcial.
- [x] (`83305d5`) **18. "Seleccionar todo" en el timeline solo coge lo cargado** (S). `TimelineViewModel.kt:181-187` usa `loadedItems`; `App.kt:1022` pone `totalCount = loadedItems.size`. Quitarlo (la casilla de mes ya cubre el caso) o rotularlo "lo cargado (N)".

## Lote D — Selección y timeline mezclado

- [x] (`1fbb212`) **19. Las fotos solo-dispositivo no se pueden seleccionar y nada lo indica** (M). *Pide opciones antes de tocar.*
  - `TimelineScreen.kt:974-982` (pulsación larga ignora `isLocalOnly`), `:1007-1010` (`idAt` devuelve null), `ui/grid/dragselect/DragSelectAdapters.kt:131-133` y `DragSelectGesture.kt:90` (`begin` devuelve false sin vibración), `TimelineScreen.kt:949-965` (en modo selección, tocar una local abre el visor). `ui/grid/AssetGrid.kt` no tiene estado "no seleccionable". La casilla de mes se las salta.
  - Mínimo: atenuarlas al seleccionar, vibración de rechazo + aviso "Aún no se ha subido", y no abrir nunca el visor con selección activa.
  - Mayor: selección local con acciones reducidas (compartir, subir ahora, eliminar del dispositivo), indicando a qué aplica cada una ("Compartir 8 · 3 aún sin subir").
- [x] (`1fbb212`) **20. Descarga y Compartir masivos sin progreso ni cancelar** (M; la parte de UI es S). *Parcial: píldora con Cancelar hecha; el streaming a fichero (RAM) sigue pendiente.* `ui/actions/AssetSelectionActionsViewModel.kt:102-141`, `147-176` guardan todo el ZIP como `ByteArray`. `actionsState.working` solo atenúa la barra al 38 % (`MainScaffold.kt:635`). Riesgo de quedarse sin memoria en móvil (confianza media). Arreglo: píldora con progreso y Cancelar; escribir a fichero en streaming.
- [x] (`1fbb212`) **21. Retocar la pestaña Fotos activa no hace nada** (S). `switchTab` en `App.kt:1721-1732`. Reutilizar el volver arriba de `TimelineScreen.kt:1165-1170`.
- [x] (`1fbb212`) **22. Tira de Recuerdos** (S-M). `ui/timeline/MemoriesStrip.kt:173-180`: altura `(maxWidth-32)*0.62` sin tope. `:198` lee `progress.value` en composición, así que la tarjeta activa se recompone cada fotograma, también fuera de pantalla (`beyondViewportPageCount = 1`, `App.kt:1839`). *Sospecha*: el pager interior cambia de pestaña al llegar a la última página. Arreglo: `widthIn(max=560.dp)` o varias tarjetas en ventanas anchas, leer el progreso en `graphicsLayer`, pausar si la página no es la actual.

## Lote E — Visor de assets

- [x] (`1fbb212`, `9fbc285`) **23. Sin carga progresiva ni zoom nítido** (M). *Cerrado: placeholder + error con reintento (`1fbb212`); original automático al superar 2x de zoom sobre una foto del servidor, conservando el botón HD/ORIG para pedirlo antes o saber qué versión hay en pantalla (`9fbc285`, decisión de Marc 2026-09-20).*
  - `ui/asset/ZoomablePagerImage.kt:110-122`: `AsyncImage` sin `placeholderMemoryCacheKey`, sin spinner, sin `onError`. El esquema de claves de `image/AssetThumbnailImage.kt:88-95` ya permite reutilizar la `Small`.
  - Zoom a 5x sobre `Large` (`AssetDetailScreen.kt:1163-1167`); el original solo con el botón "HD/ORIG" (`:733-741`), sin `contentDescription` y con `Color(0xFFFFB300)`.
  - Arreglo: `placeholderMemoryCacheKey("$thumbUrl|Small")`, estado de error con reintento, y cadena Small→Large→original según el zoom (cambio automático por encima de ~2x) para quitar el botón.
- [x] (`1fbb212`) **24. Gestos de zoom toscos** (M). *Parcial: sin inercia.* `ZoomablePagerImage.kt:79-91` doble toque sin animación; `:98-103` el pellizco ignora el centroide; `:59-66` el arrastre se limita a la caja y no a la imagen; sin inercia. Arreglo: `Animatable` para escala y desplazamiento anclado al centroide.
- [x] (`1fbb212`) **25. Atrás cierra el visor entero** (S). `App.kt:819-822`. Añadir `PlatformBackHandler` en `AssetDetailScreen` que deshaga en orden: zoom, panel de info, pase automático, apaisado, cerrar.
- [x] (`1fbb212`) **26. Abrir una foto relacionada pierde el sitio** (M). `App.kt:3350-3362`: `onOpenAsset` sustituye `assetDetail` por un contexto de un elemento. Arreglo: pila de `AssetDetailContext` y, mejor, abrir la fila relacionada como lista del pager.
- [x] (`1fbb212`) **27. Vídeo y pase automático** (M). *Parcial: sin silencio, sin estado de error del player ni mantener la pantalla encendida (piden tocar las actuals por plataforma).*
  - `VideoPlayback.isReady` no se lee en común: no hay spinner de carga. La interfaz no tiene estado de error (`ui/asset/VideoPlayback.kt`). No hay silencio. El scrubber solo busca al soltar (`AssetDetailScreen.kt:1271`).
  - El pase avanza con `delay` fijo (`:291-295`) y corta los vídeos. `SlideshowControls` ignora `chromeAlpha` (`:915`).
  - *Sospecha*: la pantalla no se mantiene encendida durante el pase con fotos.
- [x] (`1fbb212`) **28. Huecos en apaisado** (M). `AssetDetailScreen.kt:757` (acciones ocultas con `!isLocalOnly`) y `:879` (barra inferior oculta): una foto solo-dispositivo queda sin Info ni Eliminar. Falta "Editar fecha" (`:2684`). Arreglo: un único modelo de acciones para las dos orientaciones.
- [x] (`1fbb212`) **29. Datos en crudo en el panel de info** (S). `formatInstant` (`:2266-2271`) imprime "2026-09-17 12:33:11"; GPS truncado (`:2239`); `formatBytes` con "." fijo; `AssetAiSheet.kt:126` muestra `it.message`. Reutilizar `ui/format` y `UiErrorFactory`.
- [x] (`1fbb212`) **30. El atrás del detalle de un recuerdo se va con el scroll** (S). `ui/memories/MemoryDetailScreen.kt:137-153`: el `IconButton` vive dentro de la cabecera de la rejilla. Usar `SubscreenFloatingChrome` o el cromo del álbum.

## Lote F — Mapa

- [x] **31. Teselas sin escalar por densidad, arrastre sin inercia** (M). *Parcial (`6427912`): el arrastre tiene inercia (velocidad del dedo + frenada exponencial, cortable con un toque). Las teselas @2x quedan pendientes: obligan a reescalar toda la matemática mundo↔píxel que el ViewModel comparte, y no hay dispositivo donde comprobar la mejora.* Antes: `ui/map/MapProjection.kt:15` (`TILE_SIZE_PX = 256`), `OsmMap.kt:475`, gesto en `:188-316`. Pedir teselas `@2x` y añadir inercia. La gravedad visual no está vista en dispositivo.
- [x] **32. Estados contradictorios** (S-M, `6427912`). Un fallo ya no marca `firstLoadComplete`, así que el error no convive con "no hay fotos con ubicación"; el spinner sale también al refrescar; el banner de error ofrece Reintentar; "Encajar" ajusta centro y zoom al bbox de todos los puntos según el viewport (tope 16); el "−" es un icono descrito; y los marcadores publican semántica de botón con su recuento. Antes:
  - `MapViewModel.kt:72-79` pone `firstLoadComplete = true` al fallar: `MapScreen.kt:101` enseña "no hay fotos con ubicación" junto al error.
  - Aviso de error propio (`:128-142`) sin reintento; `clearError` sin usar. Refrescar no muestra progreso (`:85`).
  - "Encajar" (`MapViewModel.kt:98-103`) salta a la foto más reciente a zoom 12.
  - "−" es un `Text` sin `contentDescription` (`MapScreen.kt:178`); marcadores con `pointerInput` sin semántica (`OsmMap.kt:524`).

## Lote G — Login, búsqueda, backup, subida, notificaciones

- [x] **33. Login** (S + M, `ad4eea2`). Ver contraseña en login y en Cambiar contraseña (con `imeAction` encadenado); la sonda pasa a `GET /api/version` (público y solo de Photonne), clasifica el fallo (DNS, tiempo agotado, TLS, no es Photonne, 5xx), nombra cada URL sondeada, reintenta con http si no se escribió esquema y guarda la URL que respondió. Antes:
  - Sin botón de ver contraseña (`ui/login/LoginScreen.kt:196-216`); tampoco en `settings/AccountSecurityScreen.kt:69-113`, que además no encadena `imeAction`.
  - `LoginViewModel.kt:218-235`: la sonda es un `HEAD /api/auth/login` que acepta todo lo que no sea 5xx. `:232` concatena `t.message`. `ServerUrlStore.kt:103-110` fuerza `https://` en silencio.
  - Arreglo: sondear un endpoint que identifique Photonne, clasificar el fallo (DNS, tiempo agotado, TLS, no es Photonne), reintentar con http si no se escribió esquema, e informar por URL cuál respondió.
- [x] **34. Búsqueda** (M, `232756b`). Los chips de filtro abren la hoja y sobreviven a cero resultados; en semántica la consulta vacía vuelve al estado inicial y el botón de filtros explica por qué no aplica; la hoja gana cabecera con Limpiar arriba, Aceptar/Limpiar en las fechas, fechas con formato de la plataforma, "Sin nombre" en personas sin nombre y scroll dentro del tope de 220 dp. Antes:
  - `search/SearchScreen.kt:298`: chips de filtro activo con `onClick = {}`. `:134-138,157`: viven solo en la cabecera de la rejilla, así que con cero resultados desaparecen. `:201-204`: filtros deshabilitados en semántico sin explicación. `SearchViewModel.kt:424-426`: semántico con consulta vacía y filtros residuales dice "sin resultados". `:237`: menú ⋮ con `contentDescription = null`.
  - Hoja de filtros (`search/SearchFiltersSheet.kt`): confirmar fecha dice "Cerrar" (`:262`), quitar fechas también (`:119-121`), fechas ISO (`:105,114`), personas sin nombre como hex (`:148`), `FlowRow` con `heightIn(max = 220.dp)` sin scroll (`:137-141,162-166`), "borrar todo" es un chip al fondo (`:207-210`).
- [x] **35. Backup** (S-M). *Parcial (`733c4a0`): el aviso de permiso denegado sube a una tarjeta visible con Permitir y Abrir ajustes, y el permiso se relee en cada ON_RESUME. Pendiente: "esperando Wi-Fi y carga" sigue saliendo de las preferencias en vez del estado real del dispositivo.* Antes:
  - `devicebackup/BackupScreen.kt:343-351`: el aviso de permiso denegado está en la sección "Ajustes", plegada por defecto (`:194`). `androidMain/.../NotificationPermission.android.kt:22-35`: `granted` no se relee al volver. Sin enlace a los ajustes del sistema.
  - `BackupScreen.kt:749-761`: "esperando Wi-Fi y carga" sale de las preferencias, no del estado del dispositivo.
- [x] **36. Subida manual** (M; L en streaming). *Parcial (`733c4a0` + `6a148d2`): el mensaje del límite ya no está en inglés, el banner privado pasa a `UploadErrorBanner` y los botones tienen descripción. Pendiente: progreso determinado por archivo y subida en streaming (el límite de 200 MB viene de cargar en RAM).* Antes: `upload/UploadScreen.kt:191-214` `ErrorBanner` privado; `:342,349,352,211` botones sin descripción; `:244-249` progreso indeterminado; `:292-299` Hecho y Omitido solo cambian de tinte; `UploadViewModel.kt:75` texto en inglés; `:220` límite de 200 MB porque va en RAM (`MediaPicker.kt:12`).
- [x] **37. Notificaciones** (M, `733c4a0`). Scroll infinito con deduplicación en vez de Anterior/Siguiente; una fila leída y sin `actionUrl` deja de parecer tocable; una ruta sin pantalla nativa lo dice con snackbar; el icono de filtro se describe como "Filtrar notificaciones". *Pendiente: `GroupCount`/`GroupKey` siguen sin DTO nativo.* Antes: `NotificationsScreen.kt:164-174,332-366` paginación "Anterior / Siguiente"; `:154-157` y `App.kt:2816-2843` todas las filas clicables aunque la `actionUrl` no lleve a nada; `:243` icono de filtro descrito como "Todas"; `GroupCount`/`GroupKey` no existen en el DTO nativo (`data/models/NotificationModels.kt:20-29`).

## Lote H — Álbumes, carpetas y compartir

- [x] **38. Formularios** (M, `fa85fcf`). Contraseña de enlace oculta con ojo y teclado de contraseña, "Máx. vistas" numérico, hojas de crear y editar con scroll, caducidad sin fechas pasadas; foco inicial y Hecho-para-enviar en nuevo álbum/carpeta y renombrar persona; el selector de carpetas acepta `confirmLabel` ("Seleccionar" en reglas). *Pendiente: barrido completo de `KeyboardOptions` en library/, organize/ y utilities/.* Antes:
  - Cero `KeyboardOptions`/`imeAction`/`KeyboardType`/`PasswordVisualTransformation` en album/, folder/, library/, organize/, utilities/ (`album/smart/` sin revisar en este punto).
  - Sin foco inicial ni Hecho-para-enviar: `ui/album/AlbumDialogs.kt:71`, `ui/folder/FolderDialogs.kt:71`, `people/RenamePersonDialog.kt:61-67`.
  - `ui/album/ShareDialogs.kt:303,518` contraseña en claro; `:319,538` "Máx. vistas" con teclado de texto; `:266,462` hojas sin scroll; `:643` caducidad admite fechas pasadas.
  - `ui/folder/FolderPickerDialog.kt:296`: el botón dice siempre "Mover".
- [x] **39. Álbumes inteligentes indistinguibles** (M-L). *Parcial (`45825d1`): distintivo propio en rejilla y lista, "Quitar del álbum" y "Portada" ocultos en álbumes de reglas, `AddToAlbumDialog` filtra los inteligentes y gana buscador. Pendiente: el chip "Reglas" que abre el editor y "Convertir en álbum normal" (el editor solo crea).* Antes: `AlbumSummary.isSmart` (`data/models/AlbumModels.kt:28`) no se usa en `ui/`. "Editar" solo abre nombre y descripción. Se ofrecen "Quitar del álbum" y "Portada". `AddToAlbumDialog` recibe `albumsState.albums` sin filtrar (`App.kt:3670`), sin miniatura ni buscador (`ui/album/AddToAlbumDialog.kt:114`).
- [x] **40. Editor de álbum inteligente con `TopAppBar` acoplada** (S-M, `45825d1`). Cromo flotante como el resto de subpantallas, spinner en Crear, error encima del formulario y confirmación al salir con cambios. Antes: `ui/album/smart/SmartAlbumEditorScreen.kt:61-79`. Referencia del patrón bueno: `ui/organize/OrganizeRuleScreen.kt`. Sin spinner al guardar, error bajo el pliegue (`:117`), atrás descarta sin avisar.
- [x] **41. Filas de enlace** (S). *Parcial (`995cd74` + `45825d1`): copiar usa icono de copiar y confirma con snackbar, y hay botón de compartir con la hoja del sistema (`shareText` nuevo en `AssetSharing`: ACTION_SEND en Android, UIActivityViewController en iOS, portapapeles en escritorio). Pendiente: que la fila abra el álbum o las subidas de una solicitud de fotos.* Antes: Copiar con icono de compartir y sin "Copiado" (`ShareDialogs.kt:205`, `MyLinksScreen.kt:296`); sin hoja de compartir del sistema; la fila no abre el álbum ni las subidas de una solicitud de fotos.
- [x] **42. Detalles menores** (S, `2b3e01f`). Los lotes de "Para organizar" tienen estado propio y hacen de fuente de blur (el cromo ya se oculta ahí), esqueletos por debajo del cromo, "Mi dispositivo" con pull-to-refresh, `FolderRow` con icono de carpeta y `CreateAction` en Nuevo álbum y Nueva carpeta. *Pendiente: el álbum vacío sin "Añadir fotos", que necesita el selector de fotos de las ideas mayores.* Antes:
  - "Para organizar": `SuggestedBatchesList` con `listState` propio y sin `hazeSource` (`ui/organize/OrganizeInboxScreen.kt:118-128`, `:172`); el cromo ni se oculta ni tiene blur.
  - `ListRowsSkeleton()` sin `contentPadding` (`FoldersListScreen.kt:347`, `DeviceFoldersScreen.kt:98`).
  - `FolderRow` con icono de lista (`FoldersListScreen.kt:453`) frente a Folder en `SubfolderRow` (`FolderDetailScreen.kt:417`).
  - "Nuevo álbum" / "Nueva carpeta" sin `CreateAction` (`AlbumsListScreen.kt:252`, `FoldersListScreen.kt:265`; ver `MainScaffold.kt:660`).
  - Álbum vacío sin "Añadir fotos" (`AlbumDetailScreen.kt:230`). "Mi dispositivo" sin pull-to-refresh.

## Lote I — Consistencia transversal

- [x] **43. Textos** (M + S). *Cerrado por decisión (2026-09-20): los ~160 literales de los ViewModels se quedan en español, sin migrar a recursos. Hecho antes (`6a148d2`): los fallos masivos repetidos en 10 ViewModels salen del objeto único `ErrorMessages` (data/error) y `selection_trash_*` ya estaban en `values-en`.*
  - Unos 160 literales en español en 37 `*ViewModel.kt`. Peores: `folder/FolderDetailViewModel.kt` (11), `album/AlbumDetailViewModel.kt` (11), `people/AssetFacesViewModel.kt` (8), `asset/AssetDetailViewModel.kt` (8). "No se pudo archivar" y "No se pudo mover a la papelera" duplicados en seis ViewModels. `data/error/UiError.kt:95-98` también.
  - Pantallas: `login/LoginScreen.kt:95-258`, `AssetDetailScreen.kt:375,726-728,737,766,1102,1140,1394,1615-1700,1801,1845,1919,1932-1948,2630`, `folder/FolderPickerDialog.kt:156,161,187,210,219,249-263`, `folder/FolderTree.kt:196`, `main/MoreScreen.kt:334,340`, `ShareAssetsDialog.kt:175`, roles en `data/models/PermissionModels.kt:41`.
  - Faltan en `values-en`: `selection_trash_confirm_message`, `selection_trash_done`.
  - ~~Decidir antes el patrón~~ → Marc decide dejarlo en español, sin traducir.
- [x] **44. Convenciones poco adoptadas** (M). *Parcial (`3490317`, `9fbc285`): `ConfirmActionDialog` enseña progreso al enviar y absorbe las confirmaciones a mano de Mis enlaces (revocar espera al servidor), liberar espacio y salir del álbum; `PrimaryActionButton` en login, mover de reglas y subir ahora. Pie de las hojas decidido y aplicado (`9fbc285`): las 10 hojas con "Cancelar + acción" llevan un `PrimaryActionButton` a todo el ancho con spinner en el sitio de la etiqueta; cancelar es deslizar o atrás. Pendientes: esqueletos en las 14 pantallas con spinner, formularios en `AlertDialog`, cromo acoplado.*
  - `PrimaryActionButton` fuera de admin solo en `settings/AccountProfileScreen.kt:141` y `AccountSecurityScreen.kt:120`. Botones principales a mano: `upload/UploadScreen.kt:136`, `organize/OrganizeRuleScreen.kt:289`, `devicebackup/EnrichmentStatusScreen.kt:199`, `BackupScreen.kt:692`, `login/LoginScreen.kt:148,241`, `asset/AssetAiSheet.kt:161`.
  - `ResultSnackbar` en 2 pantallas; `LocalSnackbarController` en 5 ficheros.
  - 14 pantallas con spinner a pantalla completa donde las hermanas usan esqueleto: `AlbumsListScreen.kt:169`, `FolderDetailScreen.kt:137`, `ExploreLabelGridScreen.kt:91`, `NotificationsScreen.kt:128`, `MyLinksScreen.kt:115`, `MemoriesScreen.kt:94`, `PersonSuggestionsScreen.kt:92`, `Utilities{Duplicates:125,LargeFiles:93,Locations:77}`, `UnsupportedFilesScreen.kt:71`, `BackupPendingScreen.kt:164`, `EnrichmentStatusScreen.kt:94`, `AccountStorageScreen.kt:79`.
  - Confirmaciones a mano en vez de `ConfirmActionDialog`: `ShareDialogs.kt:394`, `MyLinksScreen.kt:187`, `UtilitiesDuplicatesScreen.kt:237`, `BackupScreen.kt:379`. `ConfirmActionDialog` no muestra progreso al enviar.
  - Formularios en `AlertDialog`: "Añadir etiqueta" (`AssetDetailScreen.kt:1930`), asignar cara (`people/AssetFacesSheet.kt:302`).
  - Cromo: `SmartAlbumEditorScreen.kt:63`, `devicebackup/DeviceAssetPreviewScreen.kt:156`, `UploadTopBar`.
  - ~~Decisión de Marc~~ → botón principal a todo el ancho (`9fbc285`): crear/editar álbum, crear/renombrar carpeta, renombrar persona, selector de carpeta destino, crear y editar enlace, descripción y fecha del elemento, los dos selectores de condiciones de reglas, restablecer contraseña. Las hojas con solo "Cerrar" o acciones secundarias no cambian.
- [x] **45. Código muerto** (S, `6a148d2`). Fuera las 11 `*TopBar` y 4 `*SelectionTopBar` sin llamadores más la `FloatingSelectionBar` huérfana: 687 líneas. 11 `*TopBar` sin llamadores en `main/MainScaffold.kt`: Hub `:836`, FolderDetail `:1491`, Search `:1736`, More `:1749`, Settings `:1772`, Notifications `:1805`, Archived `:1869`, Trash `:1922`, Favorites `:1980`, PersonDetail `:2017`, PersonSuggestions `:2086`.
- [x] **46. Tokens del tema** (M-L). *Parcial (`3490317`, `9fbc285`): `MonthHeaderHeight` es la única fuente de los 56 dp (rejilla, scrubber y salto a fecha); los 21 `RoundedCornerShape(8.dp)` pasan a `MaterialTheme.shapes.small` (10 dp) por decisión de Marc. Pendientes: adopción masiva de `Spacing.`/`IconSize.` y deduplicar la lógica de ocultar cromo con `ImmersiveChrome`.*
  - `Spacing.` 15 usos frente a 1091 líneas con dp a mano; `IconSize.` 3 usos frente a 165 `.size(N.dp)`; 69 `RoundedCornerShape` (17 de 8 dp, que no existe en el tema); 21 scrims `Color.Black.copy(alpha)` frente a 5 usos del token.
  - Fuera de escala: padding 6 dp (21), 14 (7), 10 (3), 20 (1); iconos 18 (19), 14 (9), 28 (6).
  - Peores ficheros: `AssetDetailScreen.kt` (85), `AlbumsListScreen.kt` (46), `BackupScreen.kt` (45), `FoldersListScreen.kt` (44), `BackupPendingScreen.kt` (43), `album/smart/RuleConditionsEditor.kt` (40).
  - Timeline: los 56 dp de la cabecera de mes están en tres sitios que deben coincidir (`GroupedAssetGrid.kt:600`, `TimelineScrubber.kt:61`, `TimelineScreen.kt:592`); la lógica de ocultar al bajar está duplicada (`TimelineScreen.kt:204-248`) aunque existe `main/ImmersiveChrome.kt`.
  - ~~Decidir si 8 dp pasa a ser token de forma o se funde en small (10)~~ → fundidos en `small` (`9fbc285`).
- [x] **47. Accesibilidad** (S-M). *Parcial (`6a148d2`): atrás ya no se anuncia como "Cerrar" (action_back, 5 sitios); descripciones en Subida, menú de búsqueda, borrar búsqueda e insignias de sincronización; Role.Button y lectura única en la barra de selección; objetivos ampliados (aspa de etiqueta, chip de mapas, hoja de IA, árbol de carpetas, círculo de escritorio). Pendientes: tira del visor (34 dp es diseño deliberado del scrubbing), semántica del scrubber, roles/headings globales y gráficas Canvas.*
  - Flechas de atrás anunciadas como "Cerrar": `SubscreenChrome.kt:234`, `MainScaffold.kt:1496`, `AlbumDetailScreen.kt:447`.
  - `IconButton` sin descripción: `UploadScreen.kt:210,341,348,351`, `SearchScreen.kt:236`, `main/SearchFieldPill.kt:86`.
  - Objetivos pequeños: ✕ de etiqueta 16 dp (`AssetDetailScreen.kt:1890-1893`), tira del visor 34 dp (`:2382`), chip "Abrir en mapas" (`:2200-2205`), botones de la hoja de IA en 40 dp (`AssetAiSheet.kt:222`), `SearchFieldPill.kt:86` y `folder/FolderTree.kt:176` a 32 dp, círculo de selección de escritorio 20 dp (`AssetGrid.kt:496`).
  - 119 `clickable` frente a 7 `Role`; 0 `onClickLabel`, `stateDescription`, `heading()`. `FloatingSelectionBarItem` se lee dos veces y no tiene `Role.Button` (`MainScaffold.kt:641`, `989-992`). Scrubber sin semántica (`TimelineScrubber.kt:235-312`). Insignias de subida sin describir (`AssetGrid.kt:402-409`, `503-528`). Gráficas solo Canvas (`ui/charts/*`).
- [x] **48. Iconos de la barra de estado** (S-M, `6a148d2`). `SyncSystemBarIcons` (expect/actual) sigue el tema efectivo y fuerza iconos claros con el visor abierto; Android vía WindowCompat. iOS queda no-op anotado (requiere tocar el view controller anfitrión). `androidMain/.../MainActivity.kt:14` llama a `enableEdgeToEdge()` una vez; `ui/theme/PhotonneTheme.kt:129-134` respeta la preferencia de la app. Sin `isAppearanceLightStatusBars` ni `SystemBarStyle`. Arreglo: efecto expect/actual guiado por `LocalIsDarkTheme`, forzando iconos claros sobre el scrim de fotos. iOS sin revisar.
- [x] **49. Bloqueo en vertical y estado que no sobrevive** (M). *Parcial, opción mínima (`6a148d2`): pestaña y subpantalla de Más con `rememberSaveable`; el bloqueo vertical no aplica en sw >= 600 dp (tablets rotan libres, también al salir del visor). Pendiente: ids de álbum/carpeta/persona abiertos (guardan objetos completos; necesitan rehidratación por id).* `androidMain/AndroidManifest.xml:53` (`screenOrientation="portrait"` también en tablets). `App.kt:663-765` todo con `remember` plano. Propuesta: bloqueo solo en anchos compactos; `rememberSaveable` al menos para pestaña, subpantalla e ids de álbum/carpeta abiertos.
- [x] **50. Diseño adaptable** (M). *Parcial (`3490317`): Explorar pasa a `GridCells.Adaptive(160.dp)` y las tarjetas de recuerdo piden `Medium`. Pendientes: `WindowSizeClass` y el envoltorio `ContentWidth` de subpantallas.* Cero `WindowSizeClass`. Ancho máximo solo en `LoginScreen.kt:69` y `EmptyState`. `ExploreLabelGridScreen.kt:106` usa `Fixed(2)` con miniaturas `Small`. Tarjetas de recuerdo de 150x190 dp pidiendo `Large` (`MemoriesScreen.kt:203`). Propuesta: un envoltorio `ContentWidth` (`widthIn(max = 720.dp)`) en el andamio de subpantalla.
- [x] **51. Movimiento** (S-M). *Parcial (`3490317`): duraciones canónicas en `MotionDurations` (280/320/220) consumidas por los 9 tween a mano. Pendientes: `animateItem()` en listas, `PredictiveBackHandler` y la animación de salida de overlays.* `animateItem()` en 1 de 45 listas. Vibración solo en selección (`haptics/`). Sin tokens de duración (280 ×6, 320 ×2, 220 ×1). Transición de overlays centralizada en `App.kt:2017-2035`, sin animación de salida. Sin `PredictiveBackHandler`.
- [x] **52. Bus de mutaciones** (M, `3490317`). `AssetMutationBus` en data/events; `AssetDetailRepository` emite tras confirmar el servidor (todos los flujos masivos pasan por él) y Álbumes y Carpetas refrescan portadas y recuentos en silencio. *Pendiente: migrar los parches manuales de App.kt (1307-1554, 3320-3339) al bus.* `AssetMutationBus` (SharedFlow de Removed/Restored/FavoriteChanged) al que se suscriben los ViewModels, en lugar de las listas a mano de `App.kt:1307-1554` y `:3320-3339`. Tras acciones masivas tampoco se refrescan portadas y recuentos de álbumes y carpetas.
- [x] **Pull-to-refresh que falta** (S, `6a148d2`): `PersonDetailScreen` (refresh nuevo que conserva la selección válida), `PersonSuggestionsScreen`, `AccountStorageScreen` y `EnrichmentStatusScreen`. *`MapScreen` va con el punto 32 (el gesto chocaría con el paneo) y `MemoryDetailScreen` muestra un contexto estático sin id; ambos pendientes.*

## Qué queda (inventario al cerrar los 52 puntos)

Los 52 puntos correctivos están implementados, pero varios se cerraron en
parcial. Esto agrupa lo que falta por **tipo de bloqueo**, que es lo que decide
cuándo se puede retomar cada cosa. El detalle de cada uno sigue anotado en su
punto.

### Decisiones tomadas (2026-09-20, rama `claude/roadmap-ux-pendientes`)

Las cuatro preguntas que bloqueaban trabajo ya tienen respuesta de Marc:

- **43 — patrón de i18n.** Se queda en español, sin traducir los ~160 literales de los ViewModels. Cerrado sin migración.
- **44 — pie de las hojas.** Botón principal a todo el ancho (`PrimaryActionButton`); cancelar es deslizar la hoja o el gesto atrás. Aplicado en las 10 hojas (`9fbc285`).
- **46 — radio de 8 dp.** Fundido en `MaterialTheme.shapes.small` (10 dp). Aplicado (`9fbc285`).
- **23 — cadena de calidad del visor.** Original automático al superar 2x de zoom, conservando el botón HD/ORIG. Aplicado (`9fbc285`).

### Necesita trabajo de plataforma (androidMain/iosMain, no común)

Lo más arriesgado de hacer a ciegas: no hay forma de compilar ni probar en
dispositivo desde el entorno de trabajo habitual de estas tandas.

- **27 — vídeo**: silencio, estado de error del reproductor y mantener la
  pantalla encendida durante la reproducción.
- **20 y 36 — streaming**: subida y descarga pasan por memoria, y de ahí sale el
  límite de 200 MB por archivo.
- **35 — backup**: "esperando Wi-Fi y carga" sale de las preferencias, no del
  estado real del dispositivo.
- **31 — teselas @2x**: obliga a reescalar la matemática mundo↔píxel que el mapa
  comparte con su ViewModel.

### Deuda técnica acotada (se puede hacer en cualquier momento)

- **52**: migrar los parches manuales de `App.kt` (1307-1554, 3320-3339) al
  `AssetMutationBus`, que ya existe y funciona pero convive con ellos.
- **44**: esqueletos en las 14 pantallas que aún usan spinner a pantalla completa.
- **46**: adopción de `Spacing.`/`IconSize.` (~1.100 líneas con dp a mano) y
  deduplicar la lógica de ocultar cromo con `ImmersiveChrome`.
- **51**: `animateItem()` en las listas, `PredictiveBackHandler` y animación de
  salida de los overlays.
- **50**: `WindowSizeClass` y el envoltorio `ContentWidth` de subpantallas.
- **49**: que sobrevivan los ids de álbum/carpeta/persona abiertos (hoy guardan
  el objeto completo; necesitan rehidratación por id).
- **47**: semántica del scrubber, roles y `heading()` globales, y las gráficas
  que son solo Canvas.
- **38**: barrido de `KeyboardOptions` en library/, organize/ y utilities/.
- **37**: `GroupCount`/`GroupKey` siguen sin DTO nativo.

### Depende de una idea mayor

- **39** — el chip "Reglas" que abre el editor y "Convertir en álbum normal"
  (hoy el editor solo crea).
- **41** — que la fila de enlace abra el álbum o las subidas de una solicitud.
- **42** — el "Añadir fotos" del álbum vacío, que necesita el selector de fotos
  desde dentro del álbum.

### Verificación pendiente

Nada de lo implementado se ha probado en dispositivo: la CI compila
common + desktop + android en cada push, y eso es todo lo que garantiza. Queda
por repasar en un teléfono, sobre todo los gestos (inercia del mapa, zoom y
atrás por capas del visor) y los permisos de Android.

El **PWA no entra en esa CI**, así que los cambios de `mapHelpers.js` y las
páginas Blazor solo están revisados a mano.

## Fuera del roadmap: clave de API del mapa (`734daa8`)

CARTO empezó a exigir una clave en sus teselas raster a finales de agosto de
2026; sin ella las sirve con marca de agua "API KEY REQUIRED". No era un fallo
de la auditoría — es un cambio del proveedor posterior.

La clave se guarda como `ServerSettings.MapTileApiKey`, ajuste global del
servidor (solo lo escribe un admin, lo lee cualquier usuario autenticado). Va en
el servidor y no en el binario porque cada instalación usa la suya. Se edita
desde Administración → Ajustes → Servidor, en el nativo y en la web.

*Pendiente de verificar en un despliegue real*: el parámetro implementado es
`?key=` según la documentación de CARTO, pero no se pudo probar contra sus
servidores. Si la marca de agua no desaparece, el parámetro del endpoint
concreto es otro y se corrige en una línea. El minimapa del visor no se ve
afectado: usa `tile.openstreetmap.org`, que no pide clave.

## Ideas mayores (funciones, no correcciones)

- [ ] **Criba en el visor**: avanzar con Deshacer (depende del punto 10) y atajos de escritorio (Supr, A, F, flechas, Espacio).
- [ ] **Salto por mes y año con recuentos** en lugar del `DatePicker` de días (`ui/timeline/JumpToDateDialog.kt`). El esqueleto de buckets ya tiene los datos.
- [ ] **Píldora única de operaciones** (backup, escaneo, descarga, ZIP, mover) con progreso y Cancelar. Cubre los puntos 10 y 20.
- [ ] **Resumen de selección**: rango de fechas y tamaño total junto al recuento, y "x/y" en la cabecera de mes.
- [ ] **Pantalla inicial de búsqueda**: recientes y filas de personas, escenas, objetos y lugares. Las facetas ya se cargan (`ensureFacetsLoaded`).
- [ ] **Nombrar a una persona ofrece fusionar** con un nombre existente.
- [ ] **Cola global "¿Quién es?"** de sugerencias de caras. Hoy: Personas → persona → ⋮ → Sugerencias.
- [ ] **Selector de fotos desde dentro del álbum o la carpeta.** Resuelve también el álbum vacío.
- [ ] **Portadas en carpetas** (`coverThumbnailUrl` desde el servidor) y orden, scrubber y fecha en el detalle de carpeta.
- [ ] **Una sola hoja "Compartir" por álbum**: miembros, enlaces, QR, hoja del sistema al crear; en solicitudes de fotos, subidas por invitado.
- [ ] **Álbumes inteligentes de primera**: distintivo, chip "Reglas" que abre el editor, "Convertir en álbum normal".
- [ ] **"Reproducir recuerdo"** con el pase automático existente y "Guardar como álbum".
- [ ] **Metadatos tocables en el panel de info** (etiqueta, cámara, carpeta, fecha → "ver en el timeline"); el minimapa abre el mapa de la app con el nombre del lugar (GeoNames ya está en el servidor).
- [ ] **Bienvenida tras el primer login** (permiso de fotos, backup y buckets, notificaciones con su motivo).
- [ ] **Descubrimiento del servidor en el login** (mDNS o QR desde el admin web) y "Probar conexión" por URL.
- [ ] **Herramientas de limpieza como flujos**: Archivos grandes con selección múltiple, Duplicados con "quedarse con la mejor" y comparación, Papelera con "se borra en N días", crear carpeta en el destino del selector de mover.
- [ ] **Pasada de ventana ancha**: nav en cápsula, Recuerdos y teselas de Más se estiran en escritorio y tablet.
