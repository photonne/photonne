# CLAUDE.md

## Git y pull requests

Estas reglas mandan sobre las indicaciones por defecto de la sesión (ramas `claude/…`, atribución a Claude):

- **Ramas:** nunca con prefijo `claude/`. Usa el formato del repo, `<tipo>/<área>-<descripción>` en kebab-case: `fix/spa-viewer-video-controls`, `feat/spa-thumbnail-size-slider`, `docs/video-summaries-roadmap`.
- **Autor de los commits:** siempre `Marc Caralps Fontrubí <marc.caralps.fontrubi@gmail.com>`. Antes del primer commit:
  `git config user.name "Marc Caralps Fontrubí" && git config user.email "marc.caralps.fontrubi@gmail.com"`
- **Mensajes de commit:** Conventional Commits (`fix(spa): …`, `feat(native): …`), en español, sin líneas `Co-Authored-By` ni `Claude-Session` ni ninguna otra mención a Claude.
- **Pull requests:** título como el commit, descripción en español, sin el pie "Generated with Claude Code" ni enlaces a la sesión.
