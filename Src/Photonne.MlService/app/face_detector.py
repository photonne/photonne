import time
from typing import List, Optional, Tuple

import numpy as np
from insightface.app import FaceAnalysis

from .config import settings
from .face_models import DetectedFace
from .onnx_providers import build_providers, uses_cuda


class FaceDetector:
    """Wraps InsightFace's FaceAnalysis (RetinaFace + ArcFace) with lazy load."""

    def __init__(self) -> None:
        self._app: FaceAnalysis | None = None
        # Effective provider spec of the live models; swappable at runtime via
        # /v1/config -> load(providers=...).
        self._spec: str = settings.face.providers

    def load(self, providers: Optional[str] = None) -> None:
        spec = providers if providers is not None else settings.face.providers
        names, opts = build_providers(spec)
        app = FaceAnalysis(
            name=settings.face.model_name,
            root=settings.face.model_root,
            providers=names,
            provider_options=opts,
        )
        # ctx_id selects the compute device InsightFace prepares its models on:
        # >= 0 is a CUDA device index, < 0 is CPU. Match it to the provider spec
        # so a CPU-only task doesn't try to prime a GPU it isn't using.
        ctx_id = 0 if uses_cuda(spec) else -1
        app.prepare(ctx_id=ctx_id, det_size=(settings.face.det_size, settings.face.det_size))
        self._app = app
        self._spec = spec

    @property
    def is_loaded(self) -> bool:
        return self._app is not None

    @property
    def providers(self) -> str:
        return self._spec

    def detect(self, image_bgr: np.ndarray) -> Tuple[List[DetectedFace], int]:
        if self._app is None:
            raise RuntimeError("FaceDetector not loaded")

        start = time.perf_counter()
        h, w = image_bgr.shape[:2]
        faces = self._app.get(image_bgr)
        results: List[DetectedFace] = []

        for f in faces:
            if float(f.det_score) < settings.face.min_det_score:
                continue

            x1, y1, x2, y2 = (float(v) for v in f.bbox)
            face_w = max(0.0, x2 - x1)
            face_h = max(0.0, y2 - y1)
            if min(face_w, face_h) < settings.face.min_face_size:
                continue

            # Normalize bbox to [0,1] for storage independent of image resolution.
            nx = max(0.0, min(1.0, x1 / w))
            ny = max(0.0, min(1.0, y1 / h))
            nw = max(0.0, min(1.0, face_w / w))
            nh = max(0.0, min(1.0, face_h / h))

            emb = np.asarray(f.normed_embedding, dtype=np.float32)
            landmarks = None
            if getattr(f, "kps", None) is not None:
                landmarks = [[float(p[0]), float(p[1])] for p in f.kps]

            results.append(
                DetectedFace(
                    bbox=[nx, ny, nw, nh],
                    det_score=float(f.det_score),
                    embedding=emb.tolist(),
                    landmarks_5=landmarks,
                )
            )

            if len(results) >= settings.face.max_faces:
                break

        elapsed_ms = int((time.perf_counter() - start) * 1000)
        return results, elapsed_ms


detector = FaceDetector()
