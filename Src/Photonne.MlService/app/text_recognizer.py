import logging
import os
import time
from typing import List, Optional, Tuple

import numpy as np

from .config import settings
from .text_models import RecognizedTextLine

log = logging.getLogger("photonne.ml.text")


class TextRecognizer:
    """RapidOCR (PaddleOCR exported to ONNX) text recognizer.

    Wraps the rapidocr_onnxruntime engine so the rest of the service stays
    ONNX-only. The engine itself runs three models (DBNet detector + angle
    classifier + CRNN recognizer); this class hides that pipeline behind the
    same load()/recognize() shape used by ObjectDetector and SceneClassifier.

    Custom model paths can be supplied via TEXT_DET_MODEL_PATH /
    TEXT_REC_MODEL_PATH / TEXT_CLS_MODEL_PATH; otherwise rapidocr's bundled
    defaults (downloaded at pip-install time inside the wheel) are used.
    """

    def __init__(self) -> None:
        self._engine = None  # type: ignore[assignment]
        self._load_error: Optional[str] = None

    def load(self) -> None:
        if not settings.text.enabled:
            log.info("Text recognition disabled by config")
            return

        try:
            # Imported lazily so a missing optional dependency doesn't crash
            # the whole service at import time — only the /v1/text/detect
            # endpoint goes 503.
            from rapidocr_onnxruntime import RapidOCR

            kwargs = {}
            # rapidocr accepts model overrides via kwargs; only pass the ones
            # the operator actually configured so we don't fight its defaults.
            if settings.text.det_model_path and os.path.isfile(settings.text.det_model_path):
                kwargs["det_model_path"] = settings.text.det_model_path
            if settings.text.rec_model_path and os.path.isfile(settings.text.rec_model_path):
                kwargs["rec_model_path"] = settings.text.rec_model_path
            if settings.text.cls_model_path and os.path.isfile(settings.text.cls_model_path):
                kwargs["cls_model_path"] = settings.text.cls_model_path

            self._engine = RapidOCR(**kwargs)
            self._load_error = None
            log.info(
                "Text recognizer loaded: det=%s rec=%s cls=%s min_score=%.2f max_lines=%d",
                kwargs.get("det_model_path", "<bundled>"),
                kwargs.get("rec_model_path", "<bundled>"),
                kwargs.get("cls_model_path", "<bundled>"),
                settings.text.min_score,
                settings.text.max_lines,
            )
        except Exception as e:
            self._load_error = f"{type(e).__name__}: {e}"
            raise

    @property
    def is_loaded(self) -> bool:
        return self._engine is not None

    @property
    def load_error(self) -> Optional[str]:
        return self._load_error

    def recognize(self, image_bgr: np.ndarray) -> Tuple[List[RecognizedTextLine], str, int]:
        if self._engine is None:
            raise RuntimeError("TextRecognizer not loaded")

        start = time.perf_counter()
        h, w = image_bgr.shape[:2]

        # rapidocr's __call__ returns (result, elapsed) where result is a list
        # of [polygon, text, score] entries — or None if nothing was detected.
        result, _ = self._engine(image_bgr)
        if not result:
            return [], "", int((time.perf_counter() - start) * 1000)

        # Sort by reading order: top-to-bottom, then left-to-right within a
        # row. Using the centroid of the polygon avoids surprises with skewed
        # boxes; the row threshold is a fraction of image height to be
        # resolution-independent.
        items = []
        for entry in result:
            poly, text, score = entry[0], entry[1], float(entry[2])
            text = (text or "").strip()
            if not text:
                continue
            if score < settings.text.min_score:
                continue
            poly_arr = np.asarray(poly, dtype=np.float32).reshape(-1, 2)
            x_min = float(np.min(poly_arr[:, 0]))
            y_min = float(np.min(poly_arr[:, 1]))
            x_max = float(np.max(poly_arr[:, 0]))
            y_max = float(np.max(poly_arr[:, 1]))
            cy = (y_min + y_max) / 2.0
            cx = (x_min + x_max) / 2.0
            items.append((cy, cx, x_min, y_min, x_max, y_max, text, score))

        if not items:
            return [], "", int((time.perf_counter() - start) * 1000)

        row_threshold = max(8.0, h * 0.02)
        items.sort(key=lambda it: (it[0], it[1]))
        # Bucket items into visual rows so we can left-to-right sort within
        # each row — otherwise a slightly-lower line on the right would jump
        # ahead of a higher line on the left.
        rows: List[List[tuple]] = []
        for it in items:
            cy = it[0]
            placed = False
            for row in rows:
                if abs(row[0][0] - cy) <= row_threshold:
                    row.append(it)
                    placed = True
                    break
            if not placed:
                rows.append([it])
        rows.sort(key=lambda r: np.mean([it[0] for it in r]))
        ordered = [it for row in rows for it in sorted(row, key=lambda x: x[1])]

        lines: List[RecognizedTextLine] = []
        for idx, (_, _, x_min, y_min, x_max, y_max, text, score) in enumerate(
            ordered[: settings.text.max_lines]
        ):
            box_w = max(0.0, x_max - x_min)
            box_h = max(0.0, y_max - y_min)
            if box_w <= 1.0 or box_h <= 1.0:
                continue
            lines.append(
                RecognizedTextLine(
                    text=text,
                    confidence=score,
                    bbox=[
                        max(0.0, min(1.0, x_min / w)),
                        max(0.0, min(1.0, y_min / h)),
                        max(0.0, min(1.0, box_w / w)),
                        max(0.0, min(1.0, box_h / h)),
                    ],
                    line_index=idx,
                )
            )

        full_text = "\n".join(l.text for l in lines)
        elapsed_ms = int((time.perf_counter() - start) * 1000)
        return lines, full_text, elapsed_ms


recognizer = TextRecognizer()
