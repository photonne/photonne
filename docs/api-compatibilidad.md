# Compatibilidad de la API

Las apps de Android, iOS y escritorio se actualizan cuando el usuario quiere, y el servidor se despliega en cada push. Durante semanas conviven servidores nuevos con apps antiguas, así que la API es un **contrato público**. Este documento fija qué se puede cambiar y cómo.

El contrato es el documento OpenAPI que se guarda en [`src/Server.Api/openapi/v1.json`](../src/Server.Api/openapi/v1.json).

## Regla general: solo cambios que añaden

| Cambio | ¿Compatible? |
|---|---|
| Endpoint nuevo | Sí |
| Campo nuevo en una respuesta | Sí. Las apps ignoran lo que no conocen |
| Parámetro o campo de petición nuevo **opcional** | Sí, con un valor por defecto que mantenga el comportamiento actual |
| Código de error nuevo en `ApiError.code` | Sí. El texto de `error` sigue llegando |
| Quitar o renombrar un campo de respuesta | **No** |
| Cambiar el tipo de un campo, o que pase a admitir `null` | **No**. Kotlin falla al deserializar |
| Valor nuevo en un enum de respuesta | **No**. Las apps antiguas no saben qué es |
| Parámetro o campo de petición nuevo **obligatorio** | **No** |
| Quitar un endpoint o cambiar su ruta, método o código de éxito | **No** |

### Cómo hacer un cambio que no es compatible

1. **Mejor, no hacerlo.** Añade lo nuevo al lado de lo viejo (un campo `fooV2`, un endpoint nuevo). Retira lo viejo cuando la versión mínima de cliente ya no lo use.
2. **Si no hay alternativa:** en la misma PR sube `PhotonneMinClientVersion` en [`Directory.Build.props`](../Directory.Build.props) a la versión que ya entiende el cambio. Las apps más antiguas avisarán al usuario de que tiene que actualizar.
3. **Para un rediseño completo de un área:** usa un prefijo `/api/v2/...` y mantén `/api/...` mientras haya apps en uso que lo necesiten.

## Versiones mínimas

En `Directory.Build.props`, y se suben a mano:

- **`PhotonneMinClientVersion`**: la app más antigua que este servidor atiende bien. Se publica en `GET /api/version` (`minClientVersion`). Las apps la comparan con su propia versión y muestran el aviso de actualizar la app.
- **`PhotonneMinServerVersion`**: el servidor más antiguo que necesita la app. Va compilado en la app. Súbelo cuando la app empiece a usar un endpoint o campo nuevo, y avisará de que hay que actualizar el servidor.

## Lo que comprueba la CI

El workflow [`api-contract.yml`](../.github/workflows/api-contract.yml) se ejecuta en cada PR que toca el servidor:

- **`snapshot`**: comprueba que `openapi/v1.json` coincide con lo que genera el código. Si cambias un endpoint, regenéralo con `UPDATE_OPENAPI_SNAPSHOT=1 dotnet test tests/Server.Api.Tests --filter OpenApiContractTests` y súbelo en la misma PR. Este job también exige que todos los endpoints declaren autorización y estén tipados.
- **`breaking`**: compara el contrato de la PR con el de la rama base usando [oasdiff](https://github.com/oasdiff/oasdiff). Si encuentra un cambio incompatible, falla a no ser que la PR suba `PhotonneMinClientVersion`. Por defecto oasdiff considera "info" quitar un campo opcional de una respuesta; aquí se trata como error (ver [`.github/oasdiff-severity.txt`](../.github/oasdiff-severity.txt)), porque los esquemas que también se usan en peticiones dejan sus campos como opcionales y las apps los leen igualmente.

Si oasdiff marca algo que en realidad no cambia lo que viaja por la red (una corrección del documento), la excepción se anota, revisada, en [`.github/oasdiff-ignore.txt`](../.github/oasdiff-ignore.txt).

Para probar `breaking` en local:

```sh
git show origin/main:src/Server.Api/openapi/v1.json > /tmp/base.json
docker run --rm -v /tmp:/base -v "$PWD":/head tufin/oasdiff:v1.32.1 breaking \
  /base/base.json /head/src/Server.Api/openapi/v1.json \
  --severity-levels /head/.github/oasdiff-severity.txt \
  --err-ignore /head/.github/oasdiff-ignore.txt --fail-on ERR
```
