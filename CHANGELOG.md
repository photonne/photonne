# Changelog

## [1.196.0](https://github.com/photonne/photonne/compare/v1.195.0...v1.196.0) (2026-10-10)


### Novedades

* **spa:** Carpetas muestra Personal, Compartido y Bibliotecas en vez del árbol de disco ([76bf783](https://github.com/photonne/photonne/commit/76bf7833f5a74197be8143bdbdb7a17998152e81))
* **spa:** Estado vacío más grande en las páginas ([ea02e67](https://github.com/photonne/photonne/commit/ea02e67fcbb3212c36d27a7a0fa792694ab3506a))
* **spa:** Las páginas de columna limitada ocupan todo el ancho ([fe6cbb4](https://github.com/photonne/photonne/commit/fe6cbb48c3cb6667304663a38e1b4adb63034312))


### Correcciones

* **api:** La cookie de medios autentica los fotogramas de "Elegir fotograma" ([f248060](https://github.com/photonne/photonne/commit/f248060fbb1d0935a1f041a539b8cc091eae84e9))


### Otros cambios

* Normaliza la estructura de carpetas de la solución ([f906f5c](https://github.com/photonne/photonne/commit/f906f5c9d8b517e14e3c97ec0922c6d8c733d8bb))

## [1.195.0](https://github.com/photonne/photonne/compare/v1.194.0...v1.195.0) (2026-10-10)


### Novedades

* **native:** El vídeo de las Live Photos se ve en el progreso de la copia y su recuperación es una fase propia ([ede559d](https://github.com/photonne/photonne/commit/ede559d2bc19cd5b51f59992863f254fd67d6087))
* **native:** La subida manual de iOS sube también el vídeo de las Live Photos ([51e88db](https://github.com/photonne/photonne/commit/51e88db685fe4cbf6791b4a0e7a0d84ea9cba5b2))
* **spa:** El mapa abre en la foto más reciente y agrupa las fotos en el navegador ([072ddc6](https://github.com/photonne/photonne/commit/072ddc62807454f023b245b5af2d7ad80d936171))
* **spa:** Fijados reúne álbumes, también los inteligentes, y carpetas en el menú, Álbumes y Carpetas ([bb1c238](https://github.com/photonne/photonne/commit/bb1c2385ff478d577432c26e2839920acff29129))
* **spa:** Fijados usa mergePinned en el menú, Álbumes y Carpetas ([bd37e21](https://github.com/photonne/photonne/commit/bd37e21e8251ae9f5f81162a27a7f7b7b69d8fb6))


### Otros cambios

* **api:** Fuera GET /api/assets/map, el agrupado del mapa en el servidor ([ec7ac3c](https://github.com/photonne/photonne/commit/ec7ac3c0d211059438730b3b93f858e5150bed45))

## [1.194.0](https://github.com/photonne/photonne/compare/v1.193.0...v1.194.0) (2026-10-09)


### Novedades

* **spa:** Duplicados muestra cada copia en una fila con su ruta completa y lo que la distingue resaltado ([a67e0e5](https://github.com/photonne/photonne/commit/a67e0e58fc239d9cb712f1b364aca93ff0d783c2))


### Correcciones

* **native:** Archivar y mover a la papelera ya no se deshacen solos al irse el aviso ([b13b25b](https://github.com/photonne/photonne/commit/b13b25b035545bd26053f0bccd1162999da5d438))

## [1.193.0](https://github.com/photonne/photonne/compare/v1.192.1...v1.193.0) (2026-10-09)


### Novedades

* **api:** El contrato marca como obligatorios los campos que siempre llegan en las respuestas ([68b36a3](https://github.com/photonne/photonne/commit/68b36a3e049b34aa403db435ade29a2809752f4a))
* **api:** El contrato OpenAPI se versiona en el repo y empieza a estar tipado ([16f52f7](https://github.com/photonne/photonne/commit/16f52f7e0dc8d9f637a0e30f2a77015cfa604740))
* **api:** El timeline expone aspectRatio con la orientación EXIF aplicada ([1314fbe](https://github.com/photonne/photonne/commit/1314fbed8213a3258891474e9cb8f054fddfb639))
* **api:** Favoritos, archivo, papelera, álbumes, carpetas y búsqueda traen forma y color de la miniatura ([b9340ea](https://github.com/photonne/photonne/commit/b9340ead203958064b44553c93659630122ac2ce))
* **api:** La media se cachea con ETag y con URLs versionadas inmutables ([5c7bcbd](https://github.com/photonne/photonne/commit/5c7bcbd20674b7d919e22fe536d85bbd378e6756))
* **api:** Los clientes web pueden guardar el refresh token en una cookie HttpOnly ([076bc6f](https://github.com/photonne/photonne/commit/076bc6fcff444df883a4c3b87494d7d42c036a1a))
* **native:** Ajustes del servidor por secciones, pantalla de Rendimiento y nombres como en la web ([0b268fe](https://github.com/photonne/photonne/commit/0b268fede6f3c928f18295ce157d5347ed63c7b6))
* **native:** La hoja "Fotos del dispositivo" llega a iOS con Todo, Solo biblioteca y Nada del dispositivo ([dba145c](https://github.com/photonne/photonne/commit/dba145c4159ea393c6e5bc5fd4f6317ce29a36ec))
* **server:** El servidor sirve el SPA y se retira Client.Web ([d673cb9](https://github.com/photonne/photonne/commit/d673cb966f5952f50352cd07620c334001a0ba4d))
* **spa:** Acciones en lote, arrastrar a álbumes y carpetas, y navegación completa ([6da698d](https://github.com/photonne/photonne/commit/6da698d2b7906e39f0baba5d0351b437b2cd2df9))
* **spa:** Administración con el sistema de diseño: pestañas, cabecera común, tarjetas unificadas y menú de acciones ([e969b16](https://github.com/photonne/photonne/commit/e969b16754fed7503d12e2332f6207264d42b0c6))
* **spa:** Administración: panel, usuarios, bibliotecas externas, copia de seguridad, papelera compartida y sistema ([3fcfa1f](https://github.com/photonne/photonne/commit/3fcfa1ff41729e82aa49d797055aaf1c5b9a86f0))
* **spa:** Administración: tareas y colas, mantenimiento y ajustes del servidor ([59613f8](https://github.com/photonne/photonne/commit/59613f88675474c7239898a979cfa30ff42387ee))
* **spa:** Álbumes y carpetas: lista, detalle, álbumes inteligentes, compartición y árbol de carpetas ([5dbedd3](https://github.com/photonne/photonne/commit/5dbedd311210d3a4cf275e7651d89cb348bc20c1))
* **spa:** Álbumes, carpetas, cuenta y enlace público con el sistema de diseño: cabeceras, controles comunes, esqueletos y estados vacíos ([28d984a](https://github.com/photonne/photonne/commit/28d984aa15471f7d4ed0eeb45979859d8bbac7b1))
* **spa:** Archivo, papelera, recuerdos y explorar ([f83aa54](https://github.com/photonne/photonne/commit/f83aa54e1ab1bafaddad662614ac60d28b0e3666))
* **spa:** Barra lateral plegable a una columna de iconos, que recuerda cómo se dejó ([006aa84](https://github.com/photonne/photonne/commit/006aa848e64efb0fe77e2897a949b905ec628acd))
* **spa:** Base compartida para las vistas de colecciones y la administración, y Favoritos ([3520d74](https://github.com/photonne/photonne/commit/3520d749dcb310f32400a0bf45a56891192f731b))
* **spa:** Base de diseño común: tokens, controles, cabecera de página, estados vacíos, esqueletos y pestañas ([d909fb3](https://github.com/photonne/photonne/commit/d909fb3f522d22f807b91f21fd59350fc2dd3ea8))
* **spa:** Base del nuevo cliente web: sesión, cliente de la API, i18n y shell ([a8a0fde](https://github.com/photonne/photonne/commit/a8a0fde38d3a8a2cc7469744447037d0c62fa8e0))
* **spa:** Búsqueda con filtros y semántica, organizar (bandeja, reglas y apartadas) y utilidades ([2b6d266](https://github.com/photonne/photonne/commit/2b6d266ab510e6359687bc8caf7ae2dd03ee97d8))
* **spa:** Colecciones, personas, búsqueda y organizar con el sistema de diseño: cabeceras comunes, controles compartidos, esqueletos y estados vacíos ([2ec712b](https://github.com/photonne/photonne/commit/2ec712b305ee1f4e30cd236abcc1571b07810e57))
* **spa:** Compartir con un enlace también desde álbumes, carpetas, archivo y organizar ([73e1e91](https://github.com/photonne/photonne/commit/73e1e9129021e88101a2ae4353a133d135f3f2e6))
* **spa:** Compartir la selección como enlace, mis enlaces y formato de descarga ([aa61e49](https://github.com/photonne/photonne/commit/aa61e4994837cb19d0333adfedb0cbfdff9649c4))
* **spa:** El menú lateral se organiza como la app nativa: Fotos, Colecciones, Fijados y Acciones ([f22f825](https://github.com/photonne/photonne/commit/f22f82530a646a653f3072a45adef02e7148bf4b))
* **spa:** El visor comparte con un enlace y descarga eligiendo formato; un fotograma guardado aparece en la rejilla ([48408c3](https://github.com/photonne/photonne/commit/48408c324c87ce33561ffede043127189c12f6c7))
* **spa:** Instalable como PWA, abre sin conexión y sustituye al service worker de la web anterior ([e20214f](https://github.com/photonne/photonne/commit/e20214f4ce52c776cfcbe317269dce89cb016e4f))
* **spa:** Las direcciones de la web anterior llevan a su página y una página propia para lo que no existe ([f079059](https://github.com/photonne/photonne/commit/f079059e5d21ae84a7bc844414570bb4d53ec664))
* **spa:** Los recuentos separan los miles según el idioma (12.345 fotos) ([e43e11c](https://github.com/photonne/photonne/commit/e43e11cbbf012a8e077fc6a0168aab5e37e66ce7))
* **spa:** Marca de la app nativa, administración a todo el ancho, árbol de carpetas redimensionable, portada en los álbumes y enlaces compartidos dentro de la app ([26b2189](https://github.com/photonne/photonne/commit/26b21894efb394468efb4c06f90496b1773ab30f))
* **spa:** Personas y mapa: rejilla, ficha, caras, sugerencias, fusión y mapa con grupos ([e52660c](https://github.com/photonne/photonne/commit/e52660cebbfdefac8537c302c9e56a756a6172b1))
* **spa:** Rejilla, visor y mapa con el sistema de diseño: selección legible, menú «⋮» en el visor y controles de Leaflet con los tokens ([51b75d6](https://github.com/photonne/photonne/commit/51b75d60f2ab261bc910d0771cb110351945c7da))
* **spa:** Subida, notificaciones, ajustes y enlaces públicos ([279ba9a](https://github.com/photonne/photonne/commit/279ba9a49e7fb92e0f3c696ddaf7b1feebe07356))
* **spa:** Subir en la navegación, notificaciones sin leer y soltar archivos en toda la app ([9ad1565](https://github.com/photonne/photonne/commit/9ad1565470a0e74be68fdfa4a75326a8c3924254))
* **spa:** Tema claro, oscuro o del sistema elegido por el usuario ([7218bf3](https://github.com/photonne/photonne/commit/7218bf38b39bc8534959321e585e86d76a5f13ef))
* **spa:** Timeline con rejilla justificada, virtualizada y selección de escritorio ([dd176c2](https://github.com/photonne/photonne/commit/dd176c2f7d17f14365e7e4a60f557526f6fb820e))
* **spa:** Visor de fotos y vídeos con zoom, Live Photo y panel de información editable ([962e645](https://github.com/photonne/photonne/commit/962e645e1d4147404517dee170a2c96627bd46af))
* **spa:** Visor: caras, análisis con IA, presentación, texto reconocido, fecha sugerida y fotograma de Live Photo ([e0503dd](https://github.com/photonne/photonne/commit/e0503dd58b38f5c1bd09f73da073a5a6e7b15f5c))
* **spa:** Zoom y salto a fecha en la línea de tiempo, selección múltiple de álbumes y carpetas, compañeros de una persona y estado del análisis ([828aad9](https://github.com/photonne/photonne/commit/828aad92fd66d6781c945f05adb0876cc9b4aeeb))


### Correcciones

* **api:** La media y el detalle de un asset exigen sesión y permiso de lectura ([5a30963](https://github.com/photonne/photonne/commit/5a30963a3c356b5213fd24ad20c16f0ec648035e))
* **api:** Los enlaces públicos a álbumes inteligentes muestran sus fotos y nunca las de la papelera ([2222640](https://github.com/photonne/photonne/commit/2222640338e69da913e08eb7e765f55853fcaea8))
* **native:** La lista de ajustes por secciones tiene nombre propio para no chocar en la JVM con la de entradas sueltas ([8c7a21a](https://github.com/photonne/photonne/commit/8c7a21aec72d54231f49939450211ed047816afc))
* **spa:** Botón de peligro deshabilitado legible en oscuro, ancho de tarjeta en los esqueletos y contador en las pestañas ([0b1ec32](https://github.com/photonne/photonne/commit/0b1ec32373d8ad4605db5fee931aced0b4b3036b))
* **spa:** El mapa sigue el tema elegido por el usuario además del del sistema ([4bf0adf](https://github.com/photonne/photonne/commit/4bf0adff21d9ad34f3729dd245baed9d5d03208b))
* **spa:** Foco visible en las pestañas y esqueletos dentro de tarjetas y para cifras ([b10d29b](https://github.com/photonne/photonne/commit/b10d29b3ae820ea306bdf9f9d02802bb55be8a3f))
* **spa:** La web abre por http en la red local (ip o nombre sin https) ([dfbfd56](https://github.com/photonne/photonne/commit/dfbfd56b5a7c65eff763c91e66dda768dc82a34c))
* **spa:** Los controles nativos toman el tamaño común, pastillas de estado y botón de peligro sin relleno ([46d4573](https://github.com/photonne/photonne/commit/46d4573505e47a6c813556c1b82c0bf5490c3828))
* **spa:** Rejillas sin cabeceras, acciones propias en el visor y e2e en paralelo ([3c107b4](https://github.com/photonne/photonne/commit/3c107b49d187a69c2522917200f834d7898ad7e3))
* **spa:** Una zona de subida no duplica los archivos soltados y los ajustes de papelera no se pisan en los fakes ([6bfce99](https://github.com/photonne/photonne/commit/6bfce99a5296b3558dcc2d73a73033bd336605e9))
* **web:** La rejilla del workspace compila con el SDK 10.0.1xx actual ([faeb11d](https://github.com/photonne/photonne/commit/faeb11dd0fa6bb08e187befafc74557e5c441bdd))


### Otros cambios

* **api:** Admin, enriquecimiento, bibliotecas externas, mantenimiento, tareas y backup responden con resultados tipados ([1427c26](https://github.com/photonne/photonne/commit/1427c26b294695462345db6f1e5f6104ed0b4a4f))
* **api:** Albums, Folders, Share, Tags, Organize, Texts, Objects y Scenes responden con resultados tipados ([7d0ba94](https://github.com/photonne/photonne/commit/7d0ba94a27883db6b81a0c457435c98ab0cd6baa))
* **api:** Detalle, assets, subida, sync, dispositivo, archivo, favoritos, mapa, búsqueda y recuerdos responden con resultados tipados ([73d24da](https://github.com/photonne/photonne/commit/73d24da37b4ce4a660fbf4a1b032d05433332c1e))
* **api:** El backfill de caras por usuario usa el runner de ML tipado ([a95c7c2](https://github.com/photonne/photonne/commit/a95c7c262936aa611322b46920025d6fde03b63f))
* **api:** People, Users, Notifications, Settings, Utilities, Duplicates y UnsupportedFiles responden con resultados tipados ([9082a69](https://github.com/photonne/photonne/commit/9082a698f5029e7ac61b3517941e4dda4c5274a8))
* **spa:** Los botones propios de archivo y papelera en el visor usan viewerExtra ([ca246f0](https://github.com/photonne/photonne/commit/ca246f044699bda464ab48dcd0b83d5440c51bdc))
* **spa:** Un solo menú desplegable para toda la app y prueba del visor del archivo sobre el menú «⋮» ([af8f2f4](https://github.com/photonne/photonne/commit/af8f2f4b7e7eb47044a265791472b2af5d29b390))
* **spa:** Un único diálogo de confirmación, la selección suelta lo que sale de la vista y fakes con rol de administrador ([46f64a5](https://github.com/photonne/photonne/commit/46f64a5986b88586065096fcf81ccdc094b57149))
