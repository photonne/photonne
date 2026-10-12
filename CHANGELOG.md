# Changelog

## [1.198.0](https://github.com/photonne/photonne/compare/v1.197.0...v1.198.0) (2026-10-12)


### Novedades

* **api:** Crear un usuario con -1 lo deja sin cuota aunque haya una por defecto ([8041c2f](https://github.com/photonne/photonne/commit/8041c2f8d893fc875259e8adbba93e9e1cfe7ba0))
* **api:** La subida de invitados devuelve la foto como la lista la página del enlace ([89583c6](https://github.com/photonne/photonne/commit/89583c61db7ba759045fa8bec0321a314e374598))
* **api:** Un endpoint fija el favorito en vez de alternarlo ([5148dc4](https://github.com/photonne/photonne/commit/5148dc4f824477fc865f5f9b5ca8989e8c9a71d1))
* **native:** El bus de mutaciones tiene un evento para los assets movidos de carpeta ([0ad50e3](https://github.com/photonne/photonne/commit/0ad50e3159b73062d09df504bcaddfcdfce1e28a))
* **native:** El favorito se fija con PUT y cae al toggle en servidores antiguos ([613a94a](https://github.com/photonne/photonne/commit/613a94ab43e74efa537f7050130a14cbd2d25239))
* **native:** Las notas de versión del servidor se leen formateadas y sus enlaces se abren ([9d97104](https://github.com/photonne/photonne/commit/9d97104e8b01c65e063195ca54dd1127ef3f02e5))
* **native:** Las subidas manuales se ven y se anuncian desde cualquier pantalla ([c58ef63](https://github.com/photonne/photonne/commit/c58ef6300700a77e74c98e2f91bcd4aa47ff212a))
* **spa:** El panel de información del visor se puede ensanchar o estrechar arrastrando su borde ([d8ff230](https://github.com/photonne/photonne/commit/d8ff23022e8242d2581e8f927edfd416899f2b2f))
* **spa:** El visor y la acción en lote fijan el favorito en vez de alternarlo ([54231f9](https://github.com/photonne/photonne/commit/54231f9b39132d77d787811c70ce06bf1e712e35))
* **spa:** La tira de sugerencias de la bandeja de Organizar se puede plegar ([2c641f1](https://github.com/photonne/photonne/commit/2c641f17afa86b089b4caf0d64eca675a43812c6))
* **spa:** Las fotos de un invitado aparecen en el álbum sin volver a abrir el enlace ([66b9433](https://github.com/photonne/photonne/commit/66b94334940e20646ec5dfaa3e5c3b73fbc1902b))


### Correcciones

* **api:** El toggle de favorito exige poder leer la foto ([dc82684](https://github.com/photonne/photonne/commit/dc8268436601c690698e8adc9acd55ef79a82e1d))
* **api:** En la demo, una subida que supera los 25 MB responde 413 con el ApiError de Photonne ([604fee5](https://github.com/photonne/photonne/commit/604fee503ca05cecbd008d76275c05a77e8b60b6))
* **api:** La sincronización usa los mismos límites y mensajes que la subida ([86d8be9](https://github.com/photonne/photonne/commit/86d8be9975ac261d2033da27edde5aec7377255f))
* **native:** «Archivos grandes» atiende el cambio de cantidad aunque esté cargando ([f564343](https://github.com/photonne/photonne/commit/f56434356406ea739ff667eb29ba8986e92eb139))
* **native:** «Mis enlaces» y los enlaces del álbum se recargan al abrirse ([8c97add](https://github.com/photonne/photonne/commit/8c97add9133ef0437a12532c4e0d35fc6de56280))
* **native:** Aceptar o descartar una sugerencia de cara avisa si falla ([e0bc8d7](https://github.com/photonne/photonne/commit/e0bc8d7f934f09d3c9d61fd8cde6c90acde68646))
* **native:** Añadir fotos a un álbum recarga su recuento real y su portada ([2ca9057](https://github.com/photonne/photonne/commit/2ca9057ee346f2da057fc8115ee29d3e6367cae5))
* **native:** Borrar duplicados avisa al resto de la app ([5d4e85b](https://github.com/photonne/photonne/commit/5d4e85b89b244d82164c6965fbc3d4a7ea58d788))
* **native:** Cambiar el nombre de usuario recarga Carpetas ([4572717](https://github.com/photonne/photonne/commit/4572717c213f60ff1170cfb34b209fdf39ff27f0))
* **native:** Cancelar el escaneo de una biblioteca lo detiene en el servidor ([4ed7cb6](https://github.com/photonne/photonne/commit/4ed7cb60b3d91be7f9028749b6835a931a567e2d))
* **native:** Cancelar una subida manual la cancela de verdad ([9be8d77](https://github.com/photonne/photonne/commit/9be8d7712f8b53dc4a510427466951b4d5edaf15))
* **native:** Cerrar sesión avisa de las subidas manuales en curso ([5a76a9c](https://github.com/photonne/photonne/commit/5a76a9c6ec14318290b823a413b5ea34ae5c1637))
* **native:** Crear un usuario con la cuota vaciada lo deja sin cuota ([9f55c5e](https://github.com/photonne/photonne/commit/9f55c5eb6ea6a457d0ba1ead982ed93a4215527e))
* **native:** Crear un usuario respeta los valores por defecto del servidor ([c0b9e13](https://github.com/photonne/photonne/commit/c0b9e13a5376a9ea9ce455460ec366d81d4f3ef6))
* **native:** Descargar una foto y Compartir escriben el original en un fichero mientras llega ([eb65059](https://github.com/photonne/photonne/commit/eb650596f748a63358ec2b99fc279bb2ba910ee8))
* **native:** Deshacer en Fotos recarga solo los meses afectados y deja de parpadear ([c566669](https://github.com/photonne/photonne/commit/c566669ba614acf8cc630b00502da7a722dceff1))
* **native:** Desvincular fotos de una persona deja todo coherente ([571792d](https://github.com/photonne/photonne/commit/571792d75b585b24bb868b9e9c0bd80fb108aa88))
* **native:** Editar un usuario permite quitarle la cuota ([0376349](https://github.com/photonne/photonne/commit/03763498f94a994ec08120a879fee1f7c1b9fd9c))
* **native:** El destino de Mover por condiciones distingue carga y error de una lista vacía ([782dbcc](https://github.com/photonne/photonne/commit/782dbcc5a69a3a70e0568a99edb6c2e7c6c5786c))
* **native:** El mapa se entera de lo que se archiva, se borra o se restaura ([c1cedfc](https://github.com/photonne/photonne/commit/c1cedfc7837edc89d65c461139132dcc9408e8e8))
* **native:** El Shift+clic del timeline ya no se salta los meses sin cargar ([0424a58](https://github.com/photonne/photonne/commit/0424a585fff8518f3d31051c941912c8bfcbaa5d))
* **native:** El snackbar respeta los tiempos de accesibilidad, se pausa al tocarlo y no tumba un Deshacer ([43ff028](https://github.com/photonne/photonne/commit/43ff028f8f1ac8f4f941672c67223bbd9a1857c4))
* **native:** El visor de Fotos carga el mes anterior al ir hacia atrás ([658c1b8](https://github.com/photonne/photonne/commit/658c1b8f4633e8c38e08cd509247a553ee9b668e))
* **native:** El visor y el mapa atienden el evento de assets movidos de carpeta ([a77a2a2](https://github.com/photonne/photonne/commit/a77a2a236fdd03f3d027dba05153a96065adc4bf))
* **native:** El visor ya no invierte el corazón de una foto marcada desde la rejilla ([835ddbb](https://github.com/photonne/photonne/commit/835ddbb261a49c8403dcd4ceab40614a6c00c037))
* **native:** En iOS, Descargar abre la hoja para guardar el archivo y borra el temporal al cerrarla ([8d64330](https://github.com/photonne/photonne/commit/8d6433035e920360c94589b2e9cba1d3cbca3d10))
* **native:** Explorar enseña lo que sí carga cuando algo falla ([6f5224a](https://github.com/photonne/photonne/commit/6f5224a33d4ae26ae259d762774036463c4a4b5a))
* **native:** La administración avisa de lo que la demo no deja hacer ([90f2baf](https://github.com/photonne/photonne/commit/90f2bafcde944bb40a1beebd033e77dfb92f0687))
* **native:** La cuota avisa cuando está casi llena o superada ([610af98](https://github.com/photonne/photonne/commit/610af985dce00f2d8094cacedc2fb83cd11ad325))
* **native:** La descarga masiva escribe el ZIP en un fichero mientras llega, sin cargarlo en memoria ([b55d74b](https://github.com/photonne/photonne/commit/b55d74b0a9c20389094bb05670b078cce1904cf6))
* **native:** La subida manual manda los archivos en streaming sin cargarlos en memoria ([9399c0c](https://github.com/photonne/photonne/commit/9399c0c709f2b9fcd4e0fd25d3ed2e1945334c0b))
* **native:** La subida manual mira el tamaño antes de leer y avisa de lo que se queda fuera ([ac79aca](https://github.com/photonne/photonne/commit/ac79aca39dda075e98f67356cb998d2204d29604))
* **native:** Las fotos borradas ya no reaparecen en el timeline al llegar un mes rezagado ([1c9457c](https://github.com/photonne/photonne/commit/1c9457cf0d0fa01684fbcc51d06c0b50944d2d22))
* **native:** Las listas paginadas avisan cuando falla la página siguiente ([f8cc570](https://github.com/photonne/photonne/commit/f8cc570b24cbabff0dd936c4dcc74c7d5c7d4bd0))
* **native:** Las listas paginadas no vuelven a la primera página tras borrar o deshacer ([f46bdfe](https://github.com/photonne/photonne/commit/f46bdfe7e4ebf53a61aff7e208a9cbd9279e3653))
* **native:** Las notificaciones no mezclan filtros, se refrescan desde el principio y deshacen el marcado fallido ([274bf2d](https://github.com/photonne/photonne/commit/274bf2d731959502923642cc04329bbb40ee5506))
* **native:** Las tareas de mantenimiento refrescan la biblioteca y el historial dice cuándo no lo sabe ([28e97a9](https://github.com/photonne/photonne/commit/28e97a9cbcda9aa700ae51d08afdc56cfa1045d6))
* **native:** Los permisos de biblioteca no se aplican a otra biblioteca ni pierden errores ([e3124a6](https://github.com/photonne/photonne/commit/e3124a6c078af2034688fd5e83c1bc2cc9678a3c))
* **native:** Los selectores de álbum y carpeta distinguen carga y error de una lista vacía ([abb4276](https://github.com/photonne/photonne/commit/abb4276f3cba268517b13e89fc3e0f15aaccaf24))
* **native:** Los selectores de condiciones muestran el error de carga con Reintentar ([6057ffb](https://github.com/photonne/photonne/commit/6057ffb2587c5cb19495840e133409d05ce68e01))
* **native:** Los streams de progreso de administración funcionan en iOS ([cc05e11](https://github.com/photonne/photonne/commit/cc05e1189a48ba4f5fedfb5792b09051015d2934))
* **native:** Mover a carpeta cuenta lo que el servidor movió de verdad y refresca Carpetas ([ceba5cd](https://github.com/photonne/photonne/commit/ceba5cdebdda3b411d6be8579bb2c9aa824adf23))
* **native:** Parar la verificación de la copia ya no marca archivos como ilegibles ([21ae770](https://github.com/photonne/photonne/commit/21ae7709aa58097c3cfe7607c2049aa8fb0de5a6))
* **native:** Quitar y volver a poner el corazón en el visor ya no hace desaparecer la foto de Favoritos ([1d80cf3](https://github.com/photonne/photonne/commit/1d80cf3f49bec1bdc3c543b785df812c0f90e51e))
* **native:** Recargar la papelera compartida no mezcla páginas de la lista anterior ([9aa0869](https://github.com/photonne/photonne/commit/9aa0869d3e4a18c6070a0a019b5769e98ffb7c00))
* **native:** Restaurar fechas pide confirmación antes de escribir en los originales ([c8a7ec9](https://github.com/photonne/photonne/commit/c8a7ec91df07f496715bc178c319ee70d5f7bc37))
* **native:** Sin IA en el servidor, la búsqueda pasa a texto y lo avisa ([847a594](https://github.com/photonne/photonne/commit/847a5949009f6dfcb30ceef64dccf3a250c2d510))
* **native:** Un 413 de un proxy ya no se presenta como el límite de subida de Photonne ([9cec110](https://github.com/photonne/photonne/commit/9cec11050e49f05e9fff4b2339ffd89a42362cfd))
* **native:** Un fallo al desarchivar ya no pisa lo que ha cambiado en Archivo mientras tanto ([8adbcb7](https://github.com/photonne/photonne/commit/8adbcb7b3e2d27d43422e8f1dd477619a0e7119c))
* **native:** Un segundo Supr seguido en escritorio ya no se pierde ([4cc2c33](https://github.com/photonne/photonne/commit/4cc2c335a5276b0d61447d98b9a7afaec63d8da4))
* **native:** Una búsqueda que falla ya no deja los resultados de la anterior ([78ebee5](https://github.com/photonne/photonne/commit/78ebee5f49b9613af4966677e995bb2974bca4b5))
* **server:** Fijar un álbum o una carpeta responde la misma fecha que se guarda ([338388e](https://github.com/photonne/photonne/commit/338388ec77f7d17ec9c74ec6eecb78eb8ce973a3))
* **server:** La descarga en ZIP se escribe en la respuesta mientras se monta, sin cargarla en memoria ([733bb64](https://github.com/photonne/photonne/commit/733bb64dc3e4dbee59b7af7fcc1a01e6d6a323c4))
* **server:** La sincronización solo deduplica contra la biblioteca del propio usuario ([228ef83](https://github.com/photonne/photonne/commit/228ef836fc9c2e69f083a90ee2eb9466fc889300))
* **server:** La subida con sesión y la consulta por checksum solo miran la biblioteca del propio usuario ([89366ff](https://github.com/photonne/photonne/commit/89366ff9475ba357204520fa43c7a00e17065299))
* **server:** La subida conserva la fecha de modificación del archivo en Linux ([068e519](https://github.com/photonne/photonne/commit/068e519152640de6b502447e7ca45fb0edbd6fa6))
* **server:** La subida de invitados solo descarta duplicados de las fotos del dueño del enlace ([209c477](https://github.com/photonne/photonne/commit/209c477f45aecfca06b5a6edd86080ffb98802fc))
* **server:** Leer las cajas de un MP4 o HEIC ya no puede desbordar la pila ([0bd9103](https://github.com/photonne/photonne/commit/0bd91034b372066750fa395f00f33f3fc4f902c5))
* **server:** Sube ImageSharp a 4.1.3, que corrige las vulnerabilidades conocidas de la 4.1.1 ([93778f6](https://github.com/photonne/photonne/commit/93778f6a33e563a3947e09e6eca3c19e6c0c1f67))
* **spa:** Crear un usuario con «Sin límite» ya no le pone la cuota por defecto ([2c7e715](https://github.com/photonne/photonne/commit/2c7e715eba8dd2cdfdd01d6fe0f43838a729d542))
* **spa:** Un 413 de un proxy ya no se presenta como el límite de subida del servidor ([8c4ed34](https://github.com/photonne/photonne/commit/8c4ed34c80389fdb3d7a356a1162e2e626dd5d6f))

## [1.197.0](https://github.com/photonne/photonne/compare/v1.196.0...v1.197.0) (2026-10-11)


### Novedades

* **spa:** El buscador busca con IA por defecto y ocupa todo el ancho ([261bdcc](https://github.com/photonne/photonne/commit/261bdccc83eb8db2ee20dc303a88fd4d930ea4c7))
* **spa:** El menú de usuario muestra quién eres y deja el idioma en Ajustes ([a60353f](https://github.com/photonne/photonne/commit/a60353f53c5dea4195893a0460ecea1b531eebb5))
* **spa:** El visor acepta Retroceso, A, Espacio/K y ? con una ayuda de atajos ([db96f94](https://github.com/photonne/photonne/commit/db96f94f42f0d19c58b3ff8d53072a18909263ed))
* **spa:** En el móvil, el árbol de Carpetas se abre como un cajón ([a94619c](https://github.com/photonne/photonne/commit/a94619c1629b3379647cb3b82097eacc3e4d90dc))
* **spa:** En pantallas estrechas la barra lateral es un cajón superpuesto ([496757e](https://github.com/photonne/photonne/commit/496757e1da0acf34694fa720a41f9e14309023bf))
* **spa:** En pantallas táctiles una pulsación larga selecciona y el check siempre se ve ([1bfce76](https://github.com/photonne/photonne/commit/1bfce7678a921015765b25957acf727ce02044dd))
* **spa:** Explorar carga cada fila por su cuenta, con su propio esqueleto y error ([5af0e78](https://github.com/photonne/photonne/commit/5af0e7844471409bfd957e1fd1b46fbe625ebd85))
* **spa:** Gestos táctiles en el visor: deslizar para cambiar de foto o cerrar y pellizcar para ampliar ([693ad25](https://github.com/photonne/photonne/commit/693ad25b7b50667645ff6f9be4a53c48a54ecd6d))
* **spa:** La cabecera muestra un indicador de las subidas en curso ([0e5292b](https://github.com/photonne/photonne/commit/0e5292b2be2d30722e64241ac9462e7217f877a7))
* **spa:** Los avisos de error se anuncian al momento, se pausan al pasar por encima y Ctrl/Cmd+Z deshace ([0973df1](https://github.com/photonne/photonne/commit/0973df151f61d50d9ac58e697b2bdb554222868d))
* **spa:** Usuarios, Bibliotecas y Copia de seguridad avisan del modo demo y desactivan lo que el servidor rechaza ([d652abb](https://github.com/photonne/photonne/commit/d652abb36972fb8695683fc04ab2eea818a32631))


### Correcciones

* **spa:** «Reintentar todo lo fallido» en Análisis dice cuántas fotos reintenta de cuántas ([51937ad](https://github.com/photonne/photonne/commit/51937ade03e03b5cd9bfee2faa483abb3e0585a5))
* **spa:** «Restaurar fechas» pide confirmación antes de escribir en los archivos ([7119e99](https://github.com/photonne/photonne/commit/7119e9902a925fc0402cad293a2b806c12af8413))
* **spa:** /settings redirige a Perfil desde load en vez de desde el componente ([979364b](https://github.com/photonne/photonne/commit/979364b83edba6be23a53908bc782332844b2d38))
* **spa:** Al terminar una tarea de Mantenimiento se refrescan las estadísticas y la biblioteca ([27373a6](https://github.com/photonne/photonne/commit/27373a6335a61533cd1236e0555d0d25bb23f9d7))
* **spa:** Álbumes y carpetas distinguen «no existe» de un fallo de carga y permiten reintentar ([cc4e5d7](https://github.com/photonne/photonne/commit/cc4e5d7515e96c647a9be3b33a797b4cf8e9592f))
* **spa:** Álbumes y Carpetas recuerdan la búsqueda y el ámbito al volver con Atrás ([7011fa8](https://github.com/photonne/photonne/commit/7011fa80f9575816b57f5c861957fe0d8b6f068d))
* **spa:** Archivos grandes pide confirmación antes de mandar a la papelera y refresca todas sus listas ([1858540](https://github.com/photonne/photonne/commit/1858540b6c7a9f31d8c89bb9f7a0a55b70e8c050))
* **spa:** Cambiar el nombre de usuario refresca las carpetas ([51a382d](https://github.com/photonne/photonne/commit/51a382d247596a1290551d30370283620029273d))
* **spa:** Cancelar todo detiene el cálculo de huellas y el precheck carga menos en memoria ([6e8e1cb](https://github.com/photonne/photonne/commit/6e8e1cbb62617169442d181d0100b3a8860f205e))
* **spa:** Cerrar el visor con Atrás devuelve el foco a la foto de la rejilla ([4146495](https://github.com/photonne/photonne/commit/414649535d7813bf5806a010708cac6a2bb9431d))
* **spa:** Cerrar sesión vacía la caché y cancela las subidas pendientes ([fb2f79d](https://github.com/photonne/photonne/commit/fb2f79dd6cf3e61c9aa030742587b38f20f65a01))
* **spa:** Con la barra plegada, el logo ya no se sale de la cabecera ([f5c99dc](https://github.com/photonne/photonne/commit/f5c99dcc6ca1e1f3e60731f9991248ccf252ad6c))
* **spa:** Crear, editar o revocar un enlace desde el álbum actualiza «Mis enlaces» ([3278099](https://github.com/photonne/photonne/commit/32780990baf2630c6a910e6aa154497e0087af31))
* **spa:** Deshacer quitar el acceso a una biblioteca funciona con el diálogo cerrado ([edb24dc](https://github.com/photonne/photonne/commit/edb24dcdc616f18f887e149564e11651ee1b295c))
* **spa:** Detener un escaneo antes de que arranque y los rechazos del servidor ya no bloquean Bibliotecas ([0581510](https://github.com/photonne/photonne/commit/0581510ac953950cd996707c4e4605ee62003263))
* **spa:** Duplicados avisa antes de salir con un análisis en curso o archivos marcados para borrar ([c1a9f0a](https://github.com/photonne/photonne/commit/c1a9f0a3cebcd25d1b15ae34e88b91247fa73873))
* **spa:** Editar la fecha, la descripción o las etiquetas no se reinicia al refrescarse la foto ([f6316bd](https://github.com/photonne/photonne/commit/f6316bdf24e0dc75a105146177004adad8e46a72))
* **spa:** Editar un álbum inteligente carga siempre su regla actual ([5115610](https://github.com/photonne/photonne/commit/51156109ac2ced2745192ca4c96fba14d668e1ce))
* **spa:** El árbol de carpetas no reabre las ramas que el usuario ha cerrado ([3341af1](https://github.com/photonne/photonne/commit/3341af186fb290c96a2a47dfd3611831912e2133))
* **spa:** El corazón del visor avisa si falla y no admite pulsaciones en paralelo ([94d32eb](https://github.com/photonne/photonne/commit/94d32eb91c09c04ffd53f2a9242fe009656ec30b))
* **spa:** El diálogo «Nuevo usuario» vuelve a abrirse en lugar de romper la página de Usuarios ([feb6816](https://github.com/photonne/photonne/commit/feb6816d2baf812162075e16c0911029bfa5358a))
* **spa:** El diálogo de borrar la cuenta no se cierra mientras borra y muestra «Eliminando…» ([313780b](https://github.com/photonne/photonne/commit/313780b97577a2603afee61d788d65df1881a01b))
* **spa:** El editor de condiciones del álbum dice que está cargando las condiciones ([a0854c0](https://github.com/photonne/photonne/commit/a0854c0da5a0ca15a6b3d0bb6c495a9ae0b4a80e))
* **spa:** El menú de cuenta se cierra al hacer clic fuera, con Escape y al navegar ([8d76213](https://github.com/photonne/photonne/commit/8d762131b2eb809430db1110632b4d7c2b092657))
* **spa:** El nivel de acceso vuelve a su valor si no se puede guardar ([e1cb17b](https://github.com/photonne/photonne/commit/e1cb17b44d3a02f49ea6e19dfba50e9355efa940))
* **spa:** El pase de un recuerdo no repite la fecha y empieza en la foto elegida ([8614cba](https://github.com/photonne/photonne/commit/8614cbaa798036410b1edd1c81e9df5361aa41d9))
* **spa:** El scroll infinito no se salta fotos ni sugerencias tras quitar algunas ([49b8b53](https://github.com/photonne/photonne/commit/49b8b53550b9d90ed5d3b8fdf3872e1f02f49b03))
* **spa:** El visor de la línea de tiempo carga el mes anterior y el de una foto enlazada ([3a6233f](https://github.com/photonne/photonne/commit/3a6233f527007ccf640880132a42947dafcb7736))
* **spa:** En /search solo queda un buscador y el de la cabecera no guarda texto desfasado ([8e1c9b7](https://github.com/photonne/photonne/commit/8e1c9b76d880820099a6028015c77555fd709fed))
* **spa:** En pantallas táctiles, la casilla para elegir personas está siempre visible ([122727a](https://github.com/photonne/photonne/commit/122727aa7c196f30e732ec503ff02a9dfa62149c))
* **spa:** Escape limpia la selección aunque el foco no esté en la rejilla ([f81aed8](https://github.com/photonne/photonne/commit/f81aed83741c5fd43531a9ed98bcf679787e5433))
* **spa:** La barra del navegador (theme-color) sigue el tema elegido en Apariencia ([48fb763](https://github.com/photonne/photonne/commit/48fb763eb97df56b71bd1a48d4595f74cebfff7e))
* **spa:** La barra lateral se pliega y despliega al cambiar el ancho de la ventana mientras no se haya elegido ([2aacfc0](https://github.com/photonne/photonne/commit/2aacfc0126a8285502b16806953941361f983211))
* **spa:** La papelera compartida indica cuando sus totales son parciales y no mezcla listas al recargar ([4565735](https://github.com/photonne/photonne/commit/4565735f61f3999a642fdd5ae429a67dd2d59468))
* **spa:** La tarjeta de Papelera del Panel lleva a los ajustes de la papelera ([6501db3](https://github.com/photonne/photonne/commit/6501db341c6ea17d877d76635f4e72ca68ccd930))
* **spa:** La última ejecución de las tareas nocturnas se muestra como fecha legible ([d5bc1cb](https://github.com/photonne/photonne/commit/d5bc1cbada042e40e4778c5d8f4d4be9ce390a06))
* **spa:** Las acciones sobre fotos refrescan todas las vistas de la biblioteca ([90ce0a0](https://github.com/photonne/photonne/commit/90ce0a008c2d684c3b4b3d6334080e2891484e82))
* **spa:** Las acciones sobre fotos se encolan y marcar favoritas en lote refleja lo que cambió ([39a0282](https://github.com/photonne/photonne/commit/39a028201ad76c7596dd8a2be42cc2da9c35be62))
* **spa:** Las notificaciones paginan sin parpadeo, no se quedan en una página vacía y su hora avanza ([b0a19bc](https://github.com/photonne/photonne/commit/b0a19bc4f78722d4148cf6587489efa322974322))
* **spa:** Las tarjetas de álbumes y carpetas anuncian la selección como casilla ([ded9222](https://github.com/photonne/photonne/commit/ded922284a907afaa22da270f36aec1ac3051b49))
* **spa:** Los avisos no desaparecen cuando el diálogo que los acogía se desmonta ([573b392](https://github.com/photonne/photonne/commit/573b392ce497fa7552dde56adeb995584142afb6))
* **spa:** Los avisos quedan por encima de los diálogos y se pueden pulsar ([90c3274](https://github.com/photonne/photonne/commit/90c3274ff19430237d4a70d2ebdc3d369b87e6a2))
* **spa:** Los chips de opciones en Tareas usan nombres legibles y no muestran ids ([a3255e3](https://github.com/photonne/photonne/commit/a3255e399e2633ef6f01ba0765f0fee74de0de17))
* **spa:** Los diálogos solo se cierran al pulsar y soltar sobre el fondo ([0b8cf0d](https://github.com/photonne/photonne/commit/0b8cf0df52fc1f904b8549cdb12d2172363f54a1))
* **spa:** Los selectores de carpeta y álbum empiezan de cero, muestran la carga y los errores y tienen un ARIA correcto ([510270f](https://github.com/photonne/photonne/commit/510270fde2bc4e895c91ee75489760c2d6fc4063))
* **spa:** Los valores por defecto de un usuario nuevo ya no pisan lo que eligió el administrador ([794597a](https://github.com/photonne/photonne/commit/794597adce568cc94c1c1fa6e97a85b8547bca03))
* **spa:** Mayús+clic y Seleccionar todo incluyen los meses aún sin cargar ([bfa1e10](https://github.com/photonne/photonne/commit/bfa1e10bc3485994e753bf84af5246e1bb89f3bb))
* **spa:** Personas ofrece «Reintentar» si no se puede cargar la página siguiente ([e578036](https://github.com/photonne/photonne/commit/e578036b84b5ec696c6e5419e1ae42cd38eb5dbe))
* **spa:** Pulsar el recuadro de una cara vuelve a abrirla en el panel del visor ([1a9d15f](https://github.com/photonne/photonne/commit/1a9d15ffdb181d0e8871cfb1345c033fba7a9920))
* **spa:** Quitar el corazón en el visor de Favoritos no deja el visor sin flechas ([4b8d99f](https://github.com/photonne/photonne/commit/4b8d99fa73a778cda3a3377e1282443bbc068033))
* **spa:** Quitar fotos de una carpeta pide confirmación ([b1e443d](https://github.com/photonne/photonne/commit/b1e443de04b706f377ae630a7b917c13b6eec39d))
* **spa:** Quitar todas las caras de una persona vuelve a Personas en lugar de ofrecer Deshacer ([63179c5](https://github.com/photonne/photonne/commit/63179c5e4941620ee54b02dcd103c0bd392be565))
* **spa:** Refrescar una lista paginada no desmonta la rejilla ni pierde páginas ([34549c3](https://github.com/photonne/photonne/commit/34549c3e4bb854e0d47724ffdc3cb1e77d09ba4a))
* **spa:** Reintentar o silenciar en bloque en Assets con problemas ya no lanza todas las peticiones a la vez ([d415d68](https://github.com/photonne/photonne/commit/d415d6862d86a5905a52073a51c515ca613b0fb9))
* **spa:** Restaurar una copia de seguridad refresca todos los datos en caché ([150a43a](https://github.com/photonne/photonne/commit/150a43a0f5ac0d33c84b06319b93eee0534eca0b))
* **spa:** Sin IA, el buscador habilita los filtros y apaga el interruptor semántico ([f5e3d26](https://github.com/photonne/photonne/commit/f5e3d26c1ce228526c8c1d83d7a0ebd96dcaa08a))
* **spa:** Sistema muestra las notas de versión con formato y la dirección pública del servidor ([6b00f1c](https://github.com/photonne/photonne/commit/6b00f1cd6f6a24bf58cf10813067e57bb60ba110))
* **spa:** Solo el enlace de la página abierta lleva aria-current="page" en la barra lateral ([2df83fb](https://github.com/photonne/photonne/commit/2df83fb8e0f96606b7bbca766a88cbe510db5746))
* **spa:** Subir fotos a un enlace compartido ya no suma visitas por su cuenta ([a2153a5](https://github.com/photonne/photonne/commit/a2153a5b4f262d30f7adc35151e4f50b8b4acb82))
* **spa:** Tareas muestra un esqueleto mientras cargan las colas en vez de ceros ([1cbb891](https://github.com/photonne/photonne/commit/1cbb891bb6505dc35fbe9a459dffc1f7ac0c5824))
* **spa:** Un enlace público ya no se queda cargando al no haber sesión ([2838aac](https://github.com/photonne/photonne/commit/2838aac3f4c2011a0f27c67788d0c6614daab9b2))
* **spa:** Un vídeo que no carga ya no congela el pase de un recuerdo ([2f8fe8a](https://github.com/photonne/photonne/commit/2f8fe8ae9e039b9cfa960508252641d6d27438ff))
* **spa:** Usuarios y Almacenamiento avisan de la cuota con el mismo umbral y distinguen la cuota superada ([cef0a2a](https://github.com/photonne/photonne/commit/cef0a2afe52371074b3ca12bd233b723f4f4f201))
* **spa:** Volver con Atrás a Fotos recupera el punto de la línea de tiempo ([bf72be9](https://github.com/photonne/photonne/commit/bf72be951e0e2e77e6800c3eb659251b6f6f5954))


### Otros cambios

* **spa:** Un solo módulo de formato para administración, con porcentajes de escala explícita ([bb14be4](https://github.com/photonne/photonne/commit/bb14be4fce998e94fa655b79ce8f1890071a3552))
* **spa:** Un único lector de progreso en streaming para administración ([4f7a199](https://github.com/photonne/photonne/commit/4f7a1997e65ebfa22d61b732313c9b3a2f7c8ae4))

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
