# Roadmap — Vídeos resumen

Plan del 2026-10-18 para los vídeos resumen bajo demanda (`src/Server.Api/Features/VideoSummaries`, `src/Client.SPA/src/lib/summaries`). La fase 1 llegó con la PR #97 (servidor 2.6.0). Las rutas son relativas a la raíz del repositorio.

El problema que motiva las fases siguientes: el planificador (`SummaryPlanner`) elige **a ciegas**. De cada vídeo toma un tramo fijo (4 s, hasta 8 s si sobra tiempo) un poco antes de la mitad, sin mirar qué pasa en él. Por eso corta a media frase o a media acción, se pierde lo bueno si está al principio o al final, y da el mismo tiempo a un vídeo aburrido que a uno lleno de momentos.

Esfuerzo: S pequeño, M medio, L grande.

## Fase 1 — Resumen bajo demanda (hecho, PR #97)

- [x] **1. Resumen de un año o de un álbum** (L). Worker en segundo plano, de uno en uno. El resultado se guarda como asset en `Recuerdos/` del usuario y se vuelve a montar sobre el mismo asset. Llega una notificación al terminar.
- [x] **2. Opciones del usuario** (M). Duración de 1 a 10 minutos y fotos opcionales con efecto Ken Burns. Orientación todos/horizontales/verticales; con verticales, el vídeo sale en 1080×1920.
- [x] **3. Normalización con ffmpeg** (M). 1080p a 30 fps, fondo desenfocado para lo que no encaja, HDR→SDR, `loudnorm`, fundidos, unión de segmentos copiando sin recodificar.

## Fase 2 — Cortes inteligentes sin ML (hecho, PR #103)

Analizar cada vídeo antes de cortar, con ffmpeg a baja resolución (unos 160 px a 5 fps) para que sea rápido. Coste estimado: un 5-10 % de la duración de los vídeos de origen, y solo la primera vez.

- [x] **4. Puntuación por segundo** (M). Por cada tramo de 1 s:
  - movimiento (diferencia entre fotogramas);
  - sonoridad momentánea (`ebur128`) para captar risas, aplausos y gritos;
  - nitidez.

  Penalizar lo negro (`blackdetect`), lo congelado (`freezedetect`) y los primeros y últimos segundos movidos (el móvil subiendo o bajando).
- [x] **5. Cortes en puntos naturales** (M). Ajustar el inicio y el final de cada fragmento a cambios de plano (`scdet`) o a pausas del audio (`silencedetect`), nunca a media frase. Hecho, y además un corte que cae mientras suena voz se alarga hasta la pausa (hasta 4 s).
- [x] **6. Fragmentos de longitud variable** (M). De 3 a 12 s según el interés. Un vídeo largo y bueno aporta dos o tres momentos; los vídeos cortos (menos de unos 8 s) entran enteros.
- [x] **7. Reparto del tiempo por interés** (S). Los vídeos más vivos se llevan más tiempo, sin dejar de cubrir toda la cronología. Los favoritos tienen prioridad.
- [x] **8. Caché del análisis por vídeo** (S). Tabla nueva, invalidada por el checksum del archivo. Volver a montar un resumen, o hacer otro con los mismos vídeos, no repite el análisis.

## Fase 3 — ML con el servicio existente (`src/Server.Ml`) (hecho)

- [x] **9. Caras en los vídeos** (M). Detectar caras en 1-2 fotogramas por segundo y dar prioridad a los tramos con personas con nombre. Si el resumen es de un viaje con Martina, que salga Martina. Hecho con un fotograma cada 2 s como mínimo (hasta 20 por vídeo) para acotar el coste; las caras se agrupan por persona dentro del vídeo y se comparan con la cara media de cada persona con nombre del usuario.
- [~] **10. Escenas, objetos y calidad visual** (M). Reutilizar Places365, COCO y CLIP para puntuar los tramos. Hecha la calidad visual con CLIP (parecido a "foto nítida, bien iluminada y encuadrada" frente a "borrosa, oscura y mal encuadrada"). Escenas y objetos no se usan: no dicen qué momento de un vídeo es mejor, solo de qué trata.
- [x] **11. Quitar repetidos** (M). Comparar los embeddings CLIP de los fragmentos candidatos: de cinco vídeos casi iguales del mismo momento, quedarse con uno. Hecho para vídeos (embedding medio, mismo momento en 2 días) y para ráfagas de fotos (30 minutos), que se quedan en una, la favorita si la hay.
- [x] **12. Detección de voz** (M). VAD (por ejemplo, Silero) para no cortar nunca a alguien hablando. Completa el punto 5. Hecho con Silero VAD v5 en el servicio de ML (`POST /v1/audio/voice`); el viento o la música ya no mantienen un corte abierto.

## Fase 4 — Acabado

- [ ] **13. Transiciones** (S). Encadenar los fragmentos (`xfade`) en vez de fundir a negro, y adelantar o alargar un poco el audio respecto a la imagen para suavizar los cortes.
- [ ] **14. Títulos** (S). Título del resumen al principio, y fechas o lugares sobreimpresos (`drawtext`).
- [ ] **15. Música** (L). Música elegida por el usuario de su propia biblioteca, sin problemas de licencias. Los cortes se sincronizan con el ritmo y el audio original baja de volumen.

## Fase 5 — Control del usuario y otras plataformas

- [ ] **16. Revisar antes de montar** (L). Ver los fragmentos elegidos, quitar o cambiar alguno, ajustar dónde empieza y dónde acaba, y marcar "incluir siempre". Necesita separar el plan del render en la API.
- [ ] **17. Apps nativas** (M). El mismo diálogo y las mismas opciones en Android e iOS. Hoy solo ven el vídeo resultante.
- [ ] **18. Resumen automático del año** (S). Opcional: generarlo en diciembre como una memoria más, con las mismas reglas.

## Pendientes detectados

- **Etiqueta de captura de pantalla.** `MediaRecognitionService.IsScreenshot` marca como captura cualquier archivo de 1920×1080, así que probablemente también marque los resúmenes horizontales. Ya pasaba antes con cualquier vídeo de esa resolución.
