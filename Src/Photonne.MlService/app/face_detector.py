import time
from typing import List, Tuple

import numpy as np
from insightface.app import FaceAnalysis

from .config import settings
from .face_models import DetectedFace


class FaceDetector:
    """Wraps InsightFace's FaceAnalysis (RetinaFace + ArcFace) with lazy load."""

    def __init__(self) -> None:
        self._app: FaceAnalysis | None = None

    def load(self) -> None:
        providers = [p.strip() for p in settings.providers.split(",") if p.strip()]
        app = FaceAnalysis(
            name=settings.face.model_name,
            root=settings.face.model_root,
            providers=providers,
        )
        app.prepare(ctx_id=0, det_size=(settings.face.det_size, settings.face.det_size))
        self._app = app

    @property
    def is_loaded(self) -> bool:
        return self._app is not None

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
