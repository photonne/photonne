"""One log line per inference call, with the number the access log lacks.

uvicorn's access log says a request happened and what it answered; it doesn't
say how long it took, and for this service that is the whole question. "The ML
calls take a long time" cannot be told apart from "the API takes a long time
between calls" by reading lines that carry no duration — and those are two
different problems with two different fixes.

So the access log is off (see the Dockerfile CMD) and this middleware writes
the line instead: route, asset, which file was read (a thumbnail or the
original — the original of a 24 MP photo costs OCR twenty times more), status
and wall time. `/health` is only logged when it fails; a healthy probe every
thirty seconds is the noise that buried everything else.
"""
from __future__ import annotations

import json
import logging
import os
import time
from typing import Any, Optional

from fastapi import FastAPI, Request

log = logging.getLogger("photonne.ml.calls")


def describe_call(
    method: str,
    path: str,
    status: int,
    elapsed_ms: int,
    asset_id: Optional[str] = None,
    image_path: Optional[str] = None,
) -> str:
    """The line itself, kept pure so it can be tested without an app."""
    parts = [f"{method} {path} -> {status} in {elapsed_ms} ms"]
    if asset_id:
        parts.append(f"asset={asset_id}")
    if image_path:
        parts.append(f"file={_short_path(image_path)}")
    return " ".join(parts)


def _short_path(image_path: str) -> str:
    """`<parent>/<name>`: enough to tell `…/large.jpg` from `…/IMG_0042.HEIC`
    without pasting the whole volume path on every line."""
    normalized = image_path.replace("\\", "/").rstrip("/")
    parent, _, name = normalized.rpartition("/")
    if not parent:
        return name
    return f"{os.path.basename(parent)}/{name}"


def _request_fields(body: bytes) -> tuple[Optional[str], Optional[str]]:
    if not body:
        return None, None
    try:
        payload: Any = json.loads(body)
    except ValueError:
        return None, None
    if not isinstance(payload, dict):
        return None, None
    asset_id = payload.get("asset_id")
    image_path = payload.get("image_path")
    return (
        str(asset_id) if asset_id else None,
        str(image_path) if image_path else None,
    )


def install(app: FastAPI) -> None:
    @app.middleware("http")
    async def log_call(request: Request, call_next):  # type: ignore[no-untyped-def]
        path = request.url.path
        if not path.startswith("/v1/") and path != "/health":
            return await call_next(request)

        asset_id: Optional[str] = None
        image_path: Optional[str] = None
        if request.method == "POST":
            # Starlette caches the body on the request, so reading it here
            # doesn't take it away from the endpoint.
            asset_id, image_path = _request_fields(await request.body())

        start = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception:
            elapsed_ms = int((time.perf_counter() - start) * 1000)
            log.error(describe_call(request.method, path, 500, elapsed_ms, asset_id, image_path))
            raise

        elapsed_ms = int((time.perf_counter() - start) * 1000)
        status = response.status_code
        if path == "/health" and status == 200:
            return response
        level = logging.INFO if status < 400 else logging.WARNING
        log.log(level, describe_call(request.method, path, status, elapsed_ms, asset_id, image_path))
        return response
