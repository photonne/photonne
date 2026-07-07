# Git hooks del repo

Hooks versionados que se activan con:

```sh
./scripts/setup-hooks.sh   # una vez por clon (fija core.hooksPath -> .githooks)
```

`core.hooksPath` es configuración local de git y **no** viaja con el clon, por
eso hay que ejecutar el script una vez en cada máquina/clon nuevo.

## `post-commit` — auto-bump de versión

En cada commit incrementa la versión de la app derivándola del tipo del
Conventional Commit y la **pliega en ese mismo commit** (vía `--amend`):

| Commit                          | Bump   |
| ------------------------------- | ------ |
| `feat: ...` / `feat(scope): ...`| minor  |
| `<tipo>!: ...` o footer `BREAKING CHANGE:` | major |
| `fix: ...` y cualquier otro tipo| patch  |

Actualiza:

- `Src/Directory.Build.props` `<Version>` — fuente de verdad, cascada a
  servidor .NET, cliente web, Android (`versionName`/`versionCode`), Desktop y
  la constante `PhotonneVersion`.
- `Src/Photonne.Client.Native/iosApp/iosApp.xcodeproj/project.pbxproj` —
  `MARKETING_VERSION` (= versión semver) y `CURRENT_PROJECT_VERSION`
  (= `major*10000 + minor*100 + patch`, igual que el `versionCode` de Android),
  en las configs Debug y Release.

No re-bumpea en `--amend` manual, merges, ni rebase/cherry-pick en curso, ni
cuando el commit ya trae un cambio de versión hecho a mano.
