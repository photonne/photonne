# ADR-005 — Se retira la app de escritorio (target JVM de Compose)

- **Estado**: Aceptado
- **Fecha**: 2026-10-15
- **Completa**: [ADR-003](ADR-003-user-workspace.md) (que congeló el escritorio como cliente "de sofá") y [ADR-004](ADR-004-client-spa-sveltekit.md)

## Contexto

ADR-003 concluyó que portar la UI táctil de consumo al escritorio no produce
una herramienta de gestión, y congeló el target desktop de Compose. ADR-004
construyó esa herramienta en `Client.SPA`, que se instala como PWA en Windows,
macOS y Linux.

Desde entonces el escritorio solo costaba:

- un tercer conjunto de `actual` (34 ficheros en `desktopMain`: VLCJ, llavero
  del SO, Swing, CIO, SQLite JDBC) que había que mantener al día con cada
  `expect` nuevo en `commonMain`;
- instaladores sin firmar (avisos de SmartScreen y Gatekeeper) construidos en
  macOS, Windows y Ubuntu en cada release `native-v*`;
- un paso de compilación más en el CI de cada PR.

Lo único que la PWA no cubre es la copia de seguridad de una carpeta local del
PC. Para eso, en el ordenador, lo natural es montar la carpeta en el servidor
como biblioteca externa o sincronizarla con Syncthing/rclone.

## Decisión

1. Se elimina el target `jvm("desktop")`: `desktopMain`, el bloque
   `compose.desktop`, los iconos y las dependencias solo de escritorio.
2. Los tests comunes pasan a correr en la JVM como tests unitarios de Android
   (`testDebugUnitTest`). Los que solo existen en la JVM van en
   `androidUnitTest`; el de interfaz (`ScrubberTouchPassThroughTest`), en
   `iosTest`.
3. Desaparece `desktop-release.yml`: una release `native-v*` solo publica sus
   notas; los builds de las tiendas se hacen a mano desde la etiqueta.
4. En las PR, el job de iOS ya no ejecuta los tests comunes en el simulador
   (~13 min): los mismos tests corren en la JVM del job de Linux. Se siguen
   ejecutando en cada push a `main` y a mano (`workflow_dispatch`).

## Consecuencias

- En el ordenador el cliente es la web (PWA).
- Las instalaciones de escritorio existentes siguen funcionando, pero no
  reciben más versiones. Las releases antiguas con instaladores se quedan
  publicadas. Si algún día se sube `PhotonneMinClientVersion`, esas apps
  pedirán actualizar y no habrá versión nueva: el mensaje debe remitir a la web.
- `GET /api/version/latest-release` solo lo consultaba el escritorio. Se
  mantiene porque la API es un contrato público ([api-compatibilidad.md](api-compatibilidad.md))
  y las instalaciones existentes lo siguen llamando.
- Código de `commonMain` pensado para ratón y teclado (atajos de selección,
  clic secundario, scrollbar de plataforma) sigue ahí: sirve en tablets con
  teclado y no estorba. Se puede podar más adelante.
