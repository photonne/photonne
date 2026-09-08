# Photonne para escritorio

La app de escritorio es el mismo cliente Compose Multiplatform del móvil
(mismas pantallas, mismas funciones de gestión) empaquetado para
macOS/Windows/Linux. Los instaladores se publican como assets de cada
[release de GitHub](https://github.com/photonne/photonne/releases/latest);
la propia app avisa en **Más** cuando el servidor va por delante de tu versión.

## Instalación

| SO | Paquete | Notas |
|---|---|---|
| macOS | `Photonne-x.y.z.dmg` | App sin firmar: la primera vez, clic derecho sobre Photonne.app → **Abrir** (o `xattr -cr /Applications/Photonne.app`). |
| Windows | `Photonne-x.y.z.msi` | SmartScreen avisará por falta de firma: **Más información → Ejecutar de todas formas**. |
| Linux (deb) | `photonne_x.y.z.deb` | `sudo apt install ./photonne_x.y.z.deb`. |

Actualizar = descargar el paquete nuevo e instalar encima; los datos y la
sesión se conservan.

## Primer arranque

1. Escribe la URL de tu servidor (y opcionalmente la URL local de tu red
   doméstica: la app usa la local cuando está al alcance y salta a la pública
   fuera de casa).
2. Inicia sesión con tu usuario de Photonne.

## Reproducción de vídeo: requiere VLC

El motor de vídeo de escritorio es el libvlc de [VLC](https://www.videolan.org/vlc/).
Sin VLC instalado, las fotos funcionan igual pero los vídeos y Live Photos
muestran un aviso con el enlace de descarga. Instálalo desde la web oficial
(macOS/Windows) o con tu gestor de paquetes (`apt install vlc`, etc.) y
reinicia Photonne.

## Dónde guarda las cosas

- `~/.photonne/photonne.db` — caché local (SQLite).
- `~/.photonne/settings.enc` — ajustes y credenciales, cifrados AES-256-GCM
  con la clave en el llavero del SO (Keychain / Credential Manager / Secret
  Service).
- Geometría de la ventana en las preferencias de Java del usuario.

## Atajos de escritorio

- **Escape / Alt+← / botón atrás del ratón** — volver (cerrar visor, salir de
  selección, subir de pantalla).
- **Shift+clic** — seleccionar un rango desde la última celda tocada;
  **Ctrl/Cmd+clic** — alternar una foto sin mantener pulsado.
- **Ctrl/Cmd+rueda** — cambiar el nivel de zoom del timeline.
- Pasa el ratón por el borde derecho para revelar el scrubber; clic o arrastre
  sobre el carril salta directamente.
