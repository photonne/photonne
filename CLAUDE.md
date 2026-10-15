# Photonne

## Git: ramas y commits

- Los nombres de rama no llevan «claude». Usa el prefijo convencional del repo
  según el tipo de cambio: `fix/…`, `feat/…`, `chore/…` (p. ej.
  `fix/organize-filter-picker`). Si la sesión asigna una rama `claude/…`,
  trabaja en una con nombre propio en su lugar.
- Los commits van a nombre del usuario, no de Claude. Antes del primer commit:

  ```sh
  git config user.name "Marc Caralps Fontrubí"
  git config user.email "marc.caralps.fontrubi@gmail.com"
  ```

- Los mensajes de commit no llevan líneas `Co-Authored-By: Claude …` ni
  `Claude-Session: …`, ni otras menciones a Claude.
- Formato de mensaje: Conventional Commits con ámbito, como el historial
  (`fix(spa): …`, `feat(spa): …`, `chore(native): …`).
