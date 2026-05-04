"""Multilingual CLIP text encoder backed by ONNX Runtime.

Embeds free-form text into the same 512-dim vector space as the image tower
in :mod:`embedding_image`. Built on M-CLIP's distilled XLM-Roberta encoder
(50+ languages including English and Spanish), which is the smallest off-the-
shelf option that gives Spanish queries first-class quality without
translating client-side.
"""
from __future__ import annotations

import logging
import os
import time
from typing import List, Optional, Tuple

import numpy as np
import onnxruntime as ort

from .config import settings
from .embedding_download import ensure_file

log = logging.getLogger("photonne.ml.embeddings.text")


class TextEmbedder:
    def __init__(self) -> None:
        self._session: ort.InferenceSession | None = None
        self._tokenizer = None  # huggingface tokenizers.Tokenizer
        self._input_names: List[str] = []
        self._max_length: int = settings.embedding.text_max_length
        self._load_error: Optional[str] = None

    def load(self) -> None:
        if not settings.embedding.enabled:
            log.info("Text embedding disabled by config")
            return

        try:
            model_path = settings.embedding.text_model_path
            tok_path = settings.embedding.text_tokenizer_path

            ensure_file(
                model_path,
                settings.embedding.text_model_url,
                label="CLIP text-tower ONNX",
                # Text tower is ~250 MB.
                min_size_bytes=10 * 1024 * 1024,
            )
            ensure_file(
                tok_path,
                settings.embedding.text_tokenizer_url,
                label="CLIP tokenizer JSON",
                # Tokenizer JSON is ~5 MB.
                min_size_bytes=64 * 1024,
            )

            # tokenizers is a small native dep already used by transformers; we
            # depend on it directly to avoid pulling all of transformers into
            # the runtime image.
            from tokenizers import Tokenizer  # type: ignore
            self._tokenizer = Tokenizer.from_file(tok_path)
            self._tokenizer.enable_truncation(max_length=self._max_length)
            self._tokenizer.enable_padding(length=self._max_length)

            providers = [p.strip() for p in settings.providers.split(",") if p.strip()]
            self._session = ort.InferenceSession(model_path, providers=providers)
            self._input_names = [i.name for i in self._session.get_inputs()]
            self._load_error = None
            log.info(
                "Text embedder loaded: model=%s tokenizer=%s providers=%s max_length=%d inputs=%s",
                model_path, tok_path, providers, self._max_length, self._input_names,
            )
        except Exception as e:
            self._load_error = f"{type(e).__name__}: {e}"
            raise

    @property
    def is_loaded(self) -> bool:
        return self._session is not None and self._tokenizer is not None

    @property
    def load_error(self) -> Optional[str]:
        return self._load_error

    def encode(self, text: str) -> Tuple[List[float], int]:
        if self._session is None or self._tokenizer is None:
            raise RuntimeError("TextEmbedder not loaded")

        start = time.perf_counter()
        encoded = self._tokenizer.encode(text)
        ids = np.asarray([encoded.ids], dtype=np.int64)
        mask = np.asarray([encoded.attention_mask], dtype=np.int64)

        feeds = {}
        for name in self._input_names:
            if name in ("input_ids", "input"):
                feeds[name] = ids
            elif name in ("attention_mask", "mask"):
                feeds[name] = mask
        # Fallback for exports with positional input names.
        if not feeds:
            feeds[self._input_names[0]] = ids
            if len(self._input_names) > 1:
                feeds[self._input_names[1]] = mask

        outputs = self._session.run(None, feeds)[0]
        vec = np.asarray(outputs, dtype=np.float32).reshape(-1)
        norm = float(np.linalg.norm(vec))
        if norm > 0:
            vec = vec / norm
        elapsed_ms = int((time.perf_counter() - start) * 1000)
        return vec.tolist(), elapsed_ms


embedder = TextEmbedder()
