"""Runtime download helper for CLIP model files.

Used as a last-resort fallback when the Docker build couldn't bake the ONNX
files (e.g. HuggingFace unreachable from the build host). Mirrors the pattern
already used in object_detector.py / scene_classifier.py: only consulted when
the file is missing on disk; user-supplied files always win.
"""
from __future__ import annotations

import logging
import os
import urllib.error
import urllib.request
from typing import Optional

log = logging.getLogger("photonne.ml.embeddings.download")


def ensure_file(path: str, urls: str, label: str, min_size_bytes: int = 1024) -> None:
    """If ``path`` is missing, download from the first reachable URL in
    ``urls`` (comma-separated). Raises :class:`RuntimeError` when the file is
    missing AND no URL succeeds — the caller surfaces this as a 503."""
    if os.path.isfile(path) and os.path.getsize(path) > 0:
        return

    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)

    candidates = [u.strip() for u in urls.split(",") if u.strip()]
    if not candidates:
        raise RuntimeError(
            f"{label} not found at {path} and no fallback URL configured. "
            f"Either rebuild the ML image so the build-time export bakes the "
            f"file in, drop it manually at {path}, or set the corresponding "
            f"EMBEDDING_*_URL env var to a working URL."
        )

    last_err: Optional[Exception] = None
    for url in candidates:
        tmp = path + ".part"
        try:
            log.info("Downloading %s from %s -> %s", label, url, path)
            req = urllib.request.Request(url, headers={"User-Agent": "photonne-ml/0.2"})
            with urllib.request.urlopen(req, timeout=300) as resp, open(tmp, "wb") as f:
                while True:
                    chunk = resp.read(64 * 1024)
                    if not chunk:
                        break
                    f.write(chunk)
            size = os.path.getsize(tmp)
            if size < min_size_bytes:
                raise RuntimeError(f"downloaded file is suspiciously small ({size} bytes)")
            os.replace(tmp, path)
            log.info("%s downloaded: %s (%d bytes)", label, path, size)
            return
        except (urllib.error.URLError, urllib.error.HTTPError, OSError, RuntimeError) as e:
            last_err = e
            log.warning("%s download from %s failed: %s", label, url, e)
            if os.path.exists(tmp):
                try:
                    os.remove(tmp)
                except OSError:
                    pass

    raise RuntimeError(
        f"Could not download {label} from any of {len(candidates)} URL(s); "
        f"last error: {last_err}. Workaround: drop the file at {path} or fix the URL."
    )
