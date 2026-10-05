# Textos de las fichas de Google Play y App Store

Fuente de verdad de lo que se publica en las fichas de la app nativa, en
español (`es-ES`) e inglés (`en-US`). Se copian a mano en Play Console y App
Store Connect; la estructura es la de fastlane (`supply` para Android,
`deliver` para iOS), así que si algún día se automatiza la subida basta con
apuntar `metadata_path` a `android/` o `ios/`.

Plan de publicación y formularios de las consolas: `../store-release.md`.

## Archivos y límites

| Archivo | Dónde se pega | Límite |
| --- | --- | --- |
| `android/<idioma>/title.txt` | Play → Ficha principal → Nombre de la app | 30 |
| `android/<idioma>/short_description.txt` | Play → Descripción breve | 80 |
| `android/<idioma>/full_description.txt` | Play → Descripción completa | 4.000 |
| `android/<idioma>/changelogs/default.txt` | Play → versión → Notas de la versión | 500 |
| `ios/<idioma>/name.txt` | App Store Connect → Información de la app → Nombre | 30 |
| `ios/<idioma>/subtitle.txt` | Información de la app → Subtítulo | 30 |
| `ios/<idioma>/promotional_text.txt` | Versión → Texto promocional (editable sin revisión) | 170 |
| `ios/<idioma>/description.txt` | Versión → Descripción | 4.000 |
| `ios/<idioma>/keywords.txt` | Versión → Palabras clave (comas, sin espacios) | 100 |
| `ios/<idioma>/release_notes.txt` | Versión → Novedades | 4.000 |
| `ios/<idioma>/{support,marketing,privacy}_url.txt` | URL de soporte, marketing y política de privacidad | — |

## Reglas al editarlos

- **Sin nombres de otras apps ni marcas** (Apple 2.3.7, política de metadatos
  de Play): las comparaciones van sin nombrar a nadie ("la mayoría de apps de
  fotos…").
- **Solo funciones que la app tiene de verdad**: el revisor las comprueba con
  la instancia demo. Si una función se quita o cambia, se actualiza aquí.
- **Sin jerga técnica** (Docker, API, embeddings…): eso vive en la
  documentación del repo. La ficha habla a cualquiera que quiera sus fotos
  en su propio servidor.
- **Palabras clave de Apple**: no repetir palabras del nombre ni del
  subtítulo (ya cuentan para la búsqueda) y separar solo con comas.
- Las descripciones de Play e iOS son el mismo texto; si se cambia una, se
  cambia la otra. Play no interpreta Markdown: texto plano con viñetas `•`.
- Mantener el mensaje principal: álbumes **y** carpetas a la vez (organizar
  sin mover archivos o con carpetas reales), IA que corre en el servidor del
  usuario y privacidad.
