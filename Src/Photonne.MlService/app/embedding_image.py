"""CLIP image encoder backed by ONNX Runtime.

The runtime ships M-CLIP's image tower (an OpenAI ViT-B/32 export to ONNX)
baked into the image at build time. The encoder produces 512-dim embeddings
that share a vector space with the multilingual text encoder, so cosine
similarity between query text and image embeddings is meaningful across the
languages the text tower supports.
"""
from __future__ import annotations

import logging
import os
import time
from typing import List, Optional, Tuple

import cv2
import numpy as np
import onnxruntime as ort

from .config import settings
from .embedding_download import ensure_file

log = logging.getLogger("photonne.ml.embeddings.image")


# OpenAI CLIP normalization (different from ImageNet defaults — these are the
# stats used during CLIP pretraining and inherited by every CLIP-family
# checkpoint, including M-CLIP's image tower).
_CLIP_MEAN = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
_CLIP_STD = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)


class ImageEmbedder:
    def __init__(self) -> None:
        self._session: ort.InferenceSession | None = None
        self._input_name: str = ""
        self._input_size: int = settings.embedding.image_size
        self._load_error: Optional[str] = None

    def load(self) -> None:
        if not settings.embedding.enabled:
            log.info("Image embedding disabled by config")
            return

        try:
            path = settings.embedding.image_model_path
            ensure_file(
                path,
                settings.embedding.image_model_url,
                label="CLIP image-tower ONNX",
                # Image tower is ~150 MB; reject anything tiny as obviously wrong.
                min_size_bytes=10 * 1024 * 1024,
            )

            providers = [p.strip() for p in settings.providers.split(",") if p.strip()]
            self._session = ort.InferenceSession(path, providers=providers)
            self._input_name = self._session.get_inputs()[0].name
            shape = self._session.get_inputs()[0].shape
            if isinstance(shape[2], int) and shape[2] > 0:
                self._input_size = int(shape[2])
            self._load_error = None
            log.info(
                "Image embedder loaded: model=%s providers=%s input=%dx%d",
                path, providers, self._input_size, self._input_size,
            )
        except Exception as e:
            self._load_error = f"{type(e).__name__}: {e}"
            raise

    @property
    def is_loaded(self) -> bool:
        return self._session is not None

    @property
    def load_error(self) -> Optional[str]:
        return self._load_error

    def encode(self, image_bgr: np.ndarray) -> Tuple[List[float], int]:
        if self._session is None:
            raise RuntimeError("ImageEmbedder not loaded")

        start = time.perf_counter()
        tensor = _preprocess(image_bgr, self._input_size)
        outputs = self._session.run(None, {self._input_name: tensor})[0]
        # Output shape is [1, dim]; squeeze the batch dim.
        vec = np.asarray(outputs, dtype=np.float32).reshape(-1)
        # L2 normalize so the .NET side can use cosine distance directly.
        norm = float(np.linalg.norm(vec))
        if norm > 0:
            vec = vec / norm
        elapsed_ms = int((time.perf_counter() - start) * 1000)
        return vec.tolist(), elapsed_ms


def _preprocess(image_bgr: np.ndarray, size: int) -> np.ndarray:
    """CLIP's standard preprocessing: shorter-side resize to `size`, center-crop
    to size×size, BGR→RGB, normalize with CLIP stats, NCHW float32."""
    h, w = image_bgr.shape[:2]
    scale = size / min(h, w)
    new_w, new_h = int(round(w * scale)), int(round(h * scale))
    resized = cv2.resize(image_bgr, (new_w, new_h), interpolation=cv2.INTER_LINEAR)

    left = max(0, (new_w - size) // 2)
    top = max(0, (new_h - size) // 2)
    cropped = resized[top:top + size, left:left + size]
    if cropped.shape[0] != size or cropped.shape[1] != size:
        canvas = np.zeros((size, size, 3), dtype=cropped.dtype)
        canvas[: cropped.shape[0], : cropped.shape[1]] = cropped
        cropped = canvas

    rgb = cv2.cvtColor(cropped, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    rgb = (rgb - _CLIP_MEAN) / _CLIP_STD
    tensor = np.transpose(rgb, (2, 0, 1))[None, ...].astype(np.float32)
    return np.ascontiguousarray(tensor)


embedder = ImageEmbedder()
