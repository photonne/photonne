import logging
import os
import time
import urllib.error
import urllib.request
from typing import List, Optional, Tuple

import cv2
import numpy as np
import onnxruntime as ort

from .coco_labels import COCO_CLASSES
from .config import settings
from .object_models import DetectedObject

log = logging.getLogger("photonne.ml.objects")


class ObjectDetector:
    """YOLOv8-format ONNX object detector.

    Loads any ONNX model whose output tensor is YOLOv8's standard
    [batch, 4 + num_classes, num_anchors] (xywh-center + per-class scores) layout.
    Default ships with COCO-80 (yolov8n) but a different model + label list can
    be supplied via env vars to swap class taxonomies.
    """

    def __init__(self) -> None:
        self._session: ort.InferenceSession | None = None
        self._input_name: str = ""
        self._input_size: int = settings.obj.det_size
        self._classes: List[str] = COCO_CLASSES
        # Captured at load() time so the cause is observable from /health and
        # from the 503 body, not just from the startup logs.
        self._load_error: Optional[str] = None

    def load(self) -> None:
        if not settings.obj.enabled:
            log.info("Object detection disabled by config")
            return

        try:
            path = self._ensure_model()
            providers = [p.strip() for p in settings.providers.split(",") if p.strip()]
            self._session = ort.InferenceSession(path, providers=providers)
            self._input_name = self._session.get_inputs()[0].name
            # Honor the model's actual input height when it differs from det_size.
            shape = self._session.get_inputs()[0].shape
            if isinstance(shape[2], int) and shape[2] > 0:
                self._input_size = int(shape[2])
            self._load_error = None
            log.info(
                "Object detector loaded: model=%s providers=%s input=%dx%d classes=%d",
                path, providers, self._input_size, self._input_size, len(self._classes),
            )
        except Exception as e:
            self._load_error = f"{type(e).__name__}: {e}"
            raise

    def _ensure_model(self) -> str:
        path = settings.obj.model_path
        if os.path.isfile(path) and os.path.getsize(path) > 0:
            log.info("Object model already present at %s (%d bytes)", path, os.path.getsize(path))
            return path

        os.makedirs(settings.obj.model_dir, exist_ok=True)
        # The Docker image bakes yolov8n.onnx and the entrypoint seeds it into
        # the volume; OBJECT_MODEL_URL is only consulted when the volume is
        # missing the model (e.g. running outside Docker, or after a wipe).
        urls = [u.strip() for u in settings.obj.model_url.split(",") if u.strip()]
        if not urls:
            raise RuntimeError(
                f"Object model not found at {path} and OBJECT_MODEL_URL is empty. "
                f"Inside Docker the entrypoint should have seeded it from the "
                f"image; verify the ml_models volume is writable. Outside Docker "
                f"either drop the file at {path} or set OBJECT_MODEL_URL."
            )

        last_err: Optional[Exception] = None
        for url in urls:
            tmp = path + ".part"
            try:
                log.info("Downloading object model from %s -> %s", url, path)
                # Some hosts (HuggingFace etc.) reject the default Python UA.
                req = urllib.request.Request(url, headers={"User-Agent": "photonne-ml/0.2"})
                with urllib.request.urlopen(req, timeout=120) as resp, open(tmp, "wb") as f:
                    while True:
                        chunk = resp.read(64 * 1024)
                        if not chunk:
                            break
                        f.write(chunk)
                size = os.path.getsize(tmp)
                if size < 1024:
                    raise RuntimeError(f"downloaded file is suspiciously small ({size} bytes)")
                os.replace(tmp, path)
                log.info("Object model downloaded: %s (%d bytes)", path, size)
                return path
            except (urllib.error.URLError, urllib.error.HTTPError, OSError, RuntimeError) as e:
                last_err = e
                log.warning("Model download from %s failed: %s", url, e)
                if os.path.exists(tmp):
                    try:
                        os.remove(tmp)
                    except OSError:
                        pass

        raise RuntimeError(
            f"Could not download object model from any of {len(urls)} URL(s); "
            f"last error: {last_err}. "
            f"Workaround: place a YOLOv8 ONNX file at {path} (mount the volume) "
            f"or set OBJECT_MODEL_URL to a working URL."
        )

    @property
    def is_loaded(self) -> bool:
        return self._session is not None

    @property
    def load_error(self) -> Optional[str]:
        return self._load_error

    def detect(self, image_bgr: np.ndarray) -> Tuple[List[DetectedObject], int]:
        if self._session is None:
            raise RuntimeError("ObjectDetector not loaded")

        start = time.perf_counter()
        h, w = image_bgr.shape[:2]
        tensor, scale, pad = _letterbox(image_bgr, self._input_size)

        outputs = self._session.run(None, {self._input_name: tensor})[0]
        # YOLOv8 ONNX export shape: [1, 4 + nc, N]. Transpose to [N, 4 + nc].
        preds = np.squeeze(outputs, axis=0).T  # (N, 4+nc)
        if preds.shape[1] < 5:
            return [], int((time.perf_counter() - start) * 1000)

        boxes_xywh = preds[:, :4]
        scores = preds[:, 4:]
        class_ids = np.argmax(scores, axis=1)
        confidences = scores[np.arange(scores.shape[0]), class_ids]

        keep = confidences >= settings.obj.min_score
        boxes_xywh = boxes_xywh[keep]
        confidences = confidences[keep]
        class_ids = class_ids[keep]
        if boxes_xywh.shape[0] == 0:
            return [], int((time.perf_counter() - start) * 1000)

        # Convert xywh-center to xyxy in the letterboxed canvas, then map back.
        cx, cy, bw, bh = boxes_xywh[:, 0], boxes_xywh[:, 1], boxes_xywh[:, 2], boxes_xywh[:, 3]
        x1 = (cx - bw / 2 - pad[0]) / scale
        y1 = (cy - bh / 2 - pad[1]) / scale
        x2 = (cx + bw / 2 - pad[0]) / scale
        y2 = (cy + bh / 2 - pad[1]) / scale
        x1 = np.clip(x1, 0, w)
        y1 = np.clip(y1, 0, h)
        x2 = np.clip(x2, 0, w)
        y2 = np.clip(y2, 0, h)

        boxes_xyxy = np.stack([x1, y1, x2, y2], axis=1)
        keep_idx = _nms(boxes_xyxy, confidences, settings.obj.iou_threshold)

        results: List[DetectedObject] = []
        for i in keep_idx[: settings.obj.max_objects]:
            bx1, by1, bx2, by2 = boxes_xyxy[i]
            box_w = max(0.0, float(bx2 - bx1))
            box_h = max(0.0, float(by2 - by1))
            if box_w <= 1.0 or box_h <= 1.0:
                continue
            cls_id = int(class_ids[i])
            label = self._classes[cls_id] if 0 <= cls_id < len(self._classes) else f"class_{cls_id}"
            results.append(
                DetectedObject(
                    label=label,
                    class_id=cls_id,
                    score=float(confidences[i]),
                    bbox=[
                        max(0.0, min(1.0, float(bx1) / w)),
                        max(0.0, min(1.0, float(by1) / h)),
                        max(0.0, min(1.0, box_w / w)),
                        max(0.0, min(1.0, box_h / h)),
                    ],
                )
            )

        elapsed_ms = int((time.perf_counter() - start) * 1000)
        return results, elapsed_ms


def _letterbox(image_bgr: np.ndarray, size: int) -> Tuple[np.ndarray, float, Tuple[float, float]]:
    """Resize while preserving aspect ratio and pad to (size, size). Returns the
    NCHW float32 RGB tensor, the scale factor, and the (pad_x, pad_y) offsets
    used to map predictions back to the original image."""
    h, w = image_bgr.shape[:2]
    scale = min(size / w, size / h)
    new_w, new_h = int(round(w * scale)), int(round(h * scale))
    resized = cv2.resize(image_bgr, (new_w, new_h), interpolation=cv2.INTER_LINEAR)

    canvas = np.full((size, size, 3), 114, dtype=np.uint8)
    pad_x = (size - new_w) / 2
    pad_y = (size - new_h) / 2
    top, left = int(round(pad_y)), int(round(pad_x))
    canvas[top:top + new_h, left:left + new_w] = resized

    rgb = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB)
    tensor = rgb.astype(np.float32) / 255.0
    tensor = np.transpose(tensor, (2, 0, 1))[None, ...]
    tensor = np.ascontiguousarray(tensor)
    return tensor, scale, (pad_x, pad_y)


def _nms(boxes: np.ndarray, scores: np.ndarray, iou_threshold: float) -> List[int]:
    """Plain greedy NMS in numpy. Boxes are [N, 4] xyxy, scores are [N]."""
    if boxes.shape[0] == 0:
        return []
    x1, y1, x2, y2 = boxes[:, 0], boxes[:, 1], boxes[:, 2], boxes[:, 3]
    areas = np.maximum(0.0, x2 - x1) * np.maximum(0.0, y2 - y1)
    order = scores.argsort()[::-1]

    keep: List[int] = []
    while order.size > 0:
        i = int(order[0])
        keep.append(i)
        if order.size == 1:
            break
        rest = order[1:]
        xx1 = np.maximum(x1[i], x1[rest])
        yy1 = np.maximum(y1[i], y1[rest])
        xx2 = np.minimum(x2[i], x2[rest])
        yy2 = np.minimum(y2[i], y2[rest])
        inter = np.maximum(0.0, xx2 - xx1) * np.maximum(0.0, yy2 - yy1)
        union = areas[i] + areas[rest] - inter
        iou = np.where(union > 0, inter / union, 0.0)
        order = rest[iou < iou_threshold]
    return keep


detector = ObjectDetector()
