# Changelog

## [2.6.0](https://github.com/photonne/photonne/compare/native-v2.5.1...native-v2.6.0) (2026-10-28)


### Novedades

* **native:** Compartir con Photonne desde Fotos y otras apps en iOS ([6e9d286](https://github.com/photonne/photonne/commit/6e9d286417cbe59693a879b570238c6d4478ea62))
* **native:** En tablet la navegación pasa a una barra lateral ([edea517](https://github.com/photonne/photonne/commit/edea517803c2c72c1638b0ae871f8605e999871c))
* **native:** iOS avisa cuando la copia en segundo plano deja archivos sin subir ([e512763](https://github.com/photonne/photonne/commit/e512763f47e1e19c1c8d26fbe45ada3e0becbbed))
* **native:** Widget de Recuerdos en la pantalla de inicio de Android ([d06286e](https://github.com/photonne/photonne/commit/d06286e68e499c89e71df956644bc49695184e5f))
* **native:** Widget de Recuerdos en la pantalla de inicio de iOS ([7a3427a](https://github.com/photonne/photonne/commit/7a3427a2bd5ab748cb9b39b819242c408281f52c))


### Correcciones

* **native:** Escribe el contenido de la notificación de iOS con sus setters ([327e52f](https://github.com/photonne/photonne/commit/327e52f6bd46ca9e1bf833a4fb5aac324de6e8b7))

## [2.5.1](https://github.com/photonne/photonne/compare/native-v2.5.0...native-v2.5.1) (2026-10-28)


### Dependencias

* **native:** bump dev.chrisbanes.haze:haze from 1.7.3 to 2.0.1 in /src/Client.Native ([#141](https://github.com/photonne/photonne/issues/141)) ([0f040b0](https://github.com/photonne/photonne/commit/0f040b090e7e07b459af6f54215004b818d7b653))
* **native:** bump the gradle-minor group ([#140](https://github.com/photonne/photonne/issues/140)) ([fa52804](https://github.com/photonne/photonne/commit/fa5280416ceb6c71eed534bbdcf2cbcb542b782e))

## [2.5.0](https://github.com/photonne/photonne/compare/native-v2.4.0...native-v2.5.0) (2026-10-27)


### Novedades

* Conectar la app móvil al servidor con un QR desde la web ([800a918](https://github.com/photonne/photonne/commit/800a918a906fc040c08fd5641bd080097bf1f46f))
* Duplicados: conservar la mejor copia, «solo esta» y la carpeta que distingue cada copia ([2fd94fc](https://github.com/photonne/photonne/commit/2fd94fcad8be5376ece6831006270ffc4e66f4e7))
* Guardar un recuerdo (un viaje, un año…) como álbum ([a5328fa](https://github.com/photonne/photonne/commit/a5328fa0bdd0f30d904bfddc224bcd8664d0c2ea))
* **native:** Añadir fotos desde dentro de un álbum o una carpeta ([7b567bf](https://github.com/photonne/photonne/commit/7b567bfdffc93dfcf653f6495c7983150d0f42e0))
* **native:** Bienvenida tras el primer inicio de sesión ([55ebfb9](https://github.com/photonne/photonne/commit/55ebfb901c6698d3c570c93ee536c9053600d168))
* **native:** Cada foto de la papelera dice cuántos días le quedan ([1fc9957](https://github.com/photonne/photonne/commit/1fc99572c8c2cc7d4659eb01ada87a166bcb9b7d))
* **native:** Cola «¿Quién es?» y fusionar al dar un nombre que ya existe ([5c261a1](https://github.com/photonne/photonne/commit/5c261a1c70eb8f740714f33a30785e46d4696c4d))
* **native:** El visor reproduce los vídeos desde /playback ([ba22326](https://github.com/photonne/photonne/commit/ba223261da36f8a5ed499b243b254f43fe903d01))
* **native:** Entrar con el código de verificación en dos pasos ([1468eb8](https://github.com/photonne/photonne/commit/1468eb8581ac07470f85fa06b9e9ee1351de7547))
* **native:** Entrar con el proveedor OpenID del servidor ([92c66d8](https://github.com/photonne/photonne/commit/92c66d85c3d797962799acffd27e5598dc7a1e40))
* **native:** La app escucha el flujo de eventos y recarga lo que ha cambiado ([0283208](https://github.com/photonne/photonne/commit/0283208934d13656f6e3639856b61ee4dcb8b9ba))
* **native:** La búsqueda empieza con las búsquedas recientes y los lugares ([5d5d369](https://github.com/photonne/photonne/commit/5d5d3698a3ba024f0237afac3253000fc1042224))
* **native:** La copia dice que espera Wi-Fi o carga solo si de verdad falta ([b4dce5b](https://github.com/photonne/photonne/commit/b4dce5bf15606319e613d6d4f68ea0448fad700b))
* **native:** La rejilla pinta el boceto ThumbHash de cada foto hasta que carga ([faca6c3](https://github.com/photonne/photonne/commit/faca6c3ed56f1c8e4145e94a5d72fad80dbfbaa1))
* **native:** La selección dice qué fechas abarca y cuánto ocupa ([471ef59](https://github.com/photonne/photonne/commit/471ef59ca7ed34e8b2cc3a405235c91715953327))
* **native:** Los metadatos del visor llevan a sus fotos ([3932dce](https://github.com/photonne/photonne/commit/3932dce91e4b6edf9cecfe1afb74a65ed369b948))
* **native:** Progreso real de cada archivo en la subida manual ([c575688](https://github.com/photonne/photonne/commit/c575688d7ca553ddd478a84b7291615eb222f4f2))
* **native:** Reglas a un toque, convertir en álbum normal y fechas móviles en los álbumes inteligentes ([da4658a](https://github.com/photonne/photonne/commit/da4658ad90c4312b3c54a46230c5404b255120cb))
* **native:** Saltar a un mes con fotos, con los recuentos de cada mes y año ([0a25b0b](https://github.com/photonne/photonne/commit/0a25b0b5eaa4c779d61b76f52404ca958eae583a))
* **native:** Selección múltiple en Archivos grandes para mandarlos a la papelera ([87abf9e](https://github.com/photonne/photonne/commit/87abf9e42986ebe8ca1f0db7e3f85420748e122e))


### Correcciones

* **native:** Compila: importa los textos usados y etiqueta del selector como @Composable ([18ebe52](https://github.com/photonne/photonne/commit/18ebe528a08a5fa16883f04cb16d57db929d9d4d))

## [2.4.0](https://github.com/photonne/photonne/compare/native-v2.3.0...native-v2.4.0) (2026-10-25)


### Novedades

* **native:** Fijados en el orden que el usuario elige en la web ([a6e96cd](https://github.com/photonne/photonne/commit/a6e96cdc93242a1bffebb7c9797ddc19f492bd68))
* **native:** Quitadas recientemente en los álbumes, y confirmar al quitar muchas fotos ([676593a](https://github.com/photonne/photonne/commit/676593aa407adf46acd98ad4a6fb62c473a798ba))


### Correcciones

* **native:** Import correcto de ExperimentalLayoutApi en Quitadas recientemente ([a9c35e7](https://github.com/photonne/photonne/commit/a9c35e762120d5846faf9736468812752dcbb55c))

## [2.3.0](https://github.com/photonne/photonne/compare/native-v2.2.0...native-v2.3.0) (2026-10-21)


### Novedades

* **native:** botones compactos y pie común para hojas y diálogos ([4748156](https://github.com/photonne/photonne/commit/4748156a31eaf1945714b92727c353b8e3db2052))


### Correcciones

* **native:** fechas y horas con un campo común y diálogos iguales ([940c540](https://github.com/photonne/photonne/commit/940c5407d7222685afa5ce14ff01f1024fd5f32a))
* **native:** la acción principal de cada pantalla va en su sitio ([8c154d2](https://github.com/photonne/photonne/commit/8c154d2908a9e04164347c5ec55eeca2f35ff619))
* **native:** las hojas llevan el mismo pie compacto ([dd8729c](https://github.com/photonne/photonne/commit/dd8729c0a8e84304fc5e3cc85d8d183b62348f55))
* **native:** los diálogos hechos a mano usan los botones comunes ([c889a07](https://github.com/photonne/photonne/commit/c889a07368165eaa0d3e7dff4ae202906b185f8b))
* **native:** los formularios de los diálogos pasan a hoja ([15b1dce](https://github.com/photonne/photonne/commit/15b1dcea48b9bd8911c5d7795cb9d53d246656dd))


### Otros cambios

* **native:** filas de opción comunes y detalles de las hojas ([53df33f](https://github.com/photonne/photonne/commit/53df33f8936f97cf0cd41c1ebc9d5e33975a5539))

## [2.2.0](https://github.com/photonne/photonne/compare/native-v2.1.0...native-v2.2.0) (2026-10-20)


### Novedades

* **native:** Diálogo del vídeo resumen en Android e iOS ([#109](https://github.com/photonne/photonne/issues/109)) ([c5dcf9c](https://github.com/photonne/photonne/commit/c5dcf9cda52e5bbd5550971bda5f520e5fb051d4))

## [2.1.0](https://github.com/photonne/photonne/compare/native-v2.0.0...native-v2.1.0) (2026-10-18)


### Novedades

* **native:** Colección Vídeos, filtro de tipo en la búsqueda y condición Tipo en álbumes inteligentes ([8802e4d](https://github.com/photonne/photonne/commit/8802e4d1f4f638c1604c2773305825a4efd8a4a5))
* **native:** El selector de destino al mover fotos deja crear la carpeta dentro de la elegida ([9ac9878](https://github.com/photonne/photonne/commit/9ac9878ddadc3c983c030fa1880cd2e6a4183e01))
* **native:** Un lote propuesto se abre solo y deja añadirle fotos de la bandeja ([b03193d](https://github.com/photonne/photonne/commit/b03193dce0d1d7484be7258118c2da358f84a899))

## [2.0.0](https://github.com/photonne/photonne/compare/native-v1.200.1...native-v2.0.0) (2026-10-15)


### ⚠ BREAKING CHANGES

* **native:** ya no se publican instaladores de escritorio (DMG/MSI/DEB).

### Correcciones

* **native:** el enlace de actualización de escritorio apunta a las releases de la app ([1a81f11](https://github.com/photonne/photonne/commit/1a81f11b342e5d76ce76a842da413aed4bba9f62))


### Otros cambios

* **native:** retirar la app de escritorio ([8d0ca8e](https://github.com/photonne/photonne/commit/8d0ca8e998020af29e276c335b338d98d3a91109))
