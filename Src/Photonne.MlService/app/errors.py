"""Structured errors for every /v1 endpoint.

The API records what a failed enrichment said so an operator can decide whether
to retry it. A free-text `detail` doesn't support that decision: "face detection
disabled", "cannot read image" and a CUDA crash all arrive as prose, and the
worst of the three — an inference that raised — arrived as FastAPI's default
"Internal Server Error" with nothing at all, because the inference calls were
not wrapped. The cause stayed in this container's log, which is exactly where
the person looking at the admin screen cannot see it.

So every failure leaves here as:

    {"error": {"code": ..., "transient": ..., "message": ..., "detail": ...},
     "detail": "<message>"}

`code` is a stable token the API maps to a failure kind; `transient` says
whether trying again later, with nothing changed, could plausibly work.
`detail` is kept at the top level as well so older API builds — which read the
body as a blob of text — still show something useful.
"""

from typing import Any, Dict, Optional

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

# ── Codes ────────────────────────────────────────────────────────────────────
# Kept short and stable: the API switches on them, and a rename is a wire break.

CAPABILITY_DISABLED = "capability_disabled"
MODEL_NOT_LOADED = "model_not_loaded"
IMAGE_UNREADABLE = "image_unreadable"
BAD_REQUEST = "bad_request"
INFERENCE_FAILED = "inference_failed"
INTERNAL = "internal"


class MlError(Exception):
    """A failure with a code, so the caller doesn't have to parse prose."""

    def __init__(
        self,
        status_code: int,
        code: str,
        message: str,
        transient: bool,
        detail: Optional[str] = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        self.transient = transient
        self.detail = detail

    def body(self) -> Dict[str, Any]:
        return {
            "error": {
                "code": self.code,
                "transient": self.transient,
                "message": self.message,
                "detail": self.detail,
            },
            # Back-compat: the API used to read whatever text the body held.
            "detail": self.message,
        }


def capability_disabled(capability: str) -> MlError:
    """Switched off by configuration. Not transient: retrying changes nothing
    until a human turns it back on."""
    return MlError(
        status_code=503,
        code=CAPABILITY_DISABLED,
        message=f"{capability} está desactivado en el servicio de ML",
        transient=False,
    )


def model_not_loaded(capability: str, load_error: Optional[str]) -> MlError:
    """The model failed to load at startup. Retrying inside the same container
    is pointless — it needs a fix and a restart — but it is not the asset's
    fault, so it must not be filed against the photo."""
    return MlError(
        status_code=503,
        code=MODEL_NOT_LOADED,
        message=f"el modelo de {capability} no está cargado",
        transient=False,
        detail=load_error,
    )


def image_unreadable(reason: str) -> MlError:
    """The file can't be decoded. The same bytes will fail the same way, so
    this one really is the asset's problem."""
    return MlError(
        status_code=400,
        code=IMAGE_UNREADABLE,
        message="no se ha podido leer la imagen",
        transient=False,
        detail=reason,
    )


def inference_failed(capability: str, exc: BaseException) -> MlError:
    """The model ran and raised. Reported as transient because a single OOM
    under load looks identical here to a broken driver — but the detail carries
    the real exception, which is what tells those two apart when the same line
    shows up on ten thousand assets."""
    return MlError(
        status_code=500,
        code=INFERENCE_FAILED,
        message=f"la inferencia de {capability} ha fallado",
        transient=True,
        detail=f"{type(exc).__name__}: {exc}",
    )


def install_handlers(app) -> None:
    """Route every error through the structured shape, including the ones
    raised as plain HTTPException and the ones nobody saw coming."""

    @app.exception_handler(MlError)
    async def _ml_error(_: Request, exc: MlError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=exc.body())

    @app.exception_handler(StarletteHTTPException)
    async def _http_error(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        # Anything still raising HTTPException (the /v1/config endpoint, or a
        # 404 from the router) is wrapped rather than left in the old shape, so
        # the API only has one thing to parse. 5xx is assumed transient.
        status = exc.status_code
        return JSONResponse(
            status_code=status,
            content=MlError(
                status_code=status,
                code=INTERNAL if status >= 500 else BAD_REQUEST,
                message=str(exc.detail),
                transient=status >= 500 or status in (408, 429),
            ).body(),
            headers=getattr(exc, "headers", None),
        )

    @app.exception_handler(Exception)
    async def _unhandled(_: Request, exc: Exception) -> JSONResponse:
        # Without this an inference crash reached the API as an empty 500.
        log_detail = f"{type(exc).__name__}: {exc}"
        return JSONResponse(
            status_code=500,
            content=MlError(
                status_code=500,
                code=INTERNAL,
                message="error interno del servicio de ML",
                transient=True,
                detail=log_detail,
            ).body(),
        )
