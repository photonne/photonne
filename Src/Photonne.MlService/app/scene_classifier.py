import logging
import os
import time
import urllib.error
import urllib.request
from typing import List, Optional, Tuple

import cv2
import numpy as np
import onnxruntime as ort

from .config import settings
from .places365_labels import PLACES365_CLASSES
from .scene_models import ClassifiedScene

log = logging.getLogger("photonne.ml.scenes")


# Mean / std used by the upstream Places365 ResNet (PyTorch / torchvision
# defaults). The ONNX export inherits this normalization, so we have to
# replicate it here on the input side.
_IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
_IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


class SceneClassifier:
    """Places365 ResNet18 scene classifier (ONNX).

    Loads any ONNX model whose output is [batch, 365] logits in the canonical
    Places365 class order. The default ships with resnet18_places365 (~45 MB)
    but can be swapped via SCENE_MODEL_PATH for a different backbone (resnet50,
    densenet161, etc.) as long as the class list still has 365 entries.
    """

    def __init__(self) -> None:
        self._session: ort.InferenceSession | None = None
        self._input_name: str = ""
        self._input_size: int = settings.scene.det_size
        self._classes: List[str] = PLACES365_CLASSES
        # Captured at load() time so the cause is observable from /health and
        # from the 503 body, not just from the startup logs.
        self._load_error: Optional[str] = None

    def load(self) -> None:
        if not settings.scene.enabled:
            log.info("Scene classification disabled by config")
            return

        try:
            path = self._ensure_model()
            providers = [p.strip() for p in settings.providers.split(",") if p.strip()]
            self._session = ort.InferenceSession(path, providers=providers)
            self._input_name = self._session.get_inputs()[0].name
            shape = self._session.get_inputs()[0].shape
            if isinstance(shape[2], int) and shape[2] > 0:
                self._input_size = int(shape[2])
            self._load_error = None
            log.info(
                "Scene classifier loaded: model=%s providers=%s input=%dx%d classes=%d",
                path, providers, self._input_size, self._input_size, len(self._classes),
            )
        except Exception as e:
            self._load_error = f"{type(e).__name__}: {e}"
            raise

    def _ensure_model(self) -> str:
        path = settings.scene.model_path
        if os.path.isfile(path) and os.path.getsize(path) > 0:
            log.info("Scene model already present at %s (%d bytes)", path, os.path.getsize(path))
            return path

        os.makedirs(settings.scene.model_dir, exist_ok=True)
        # The Docker image bakes scene_classifier.onnx and the entrypoint seeds
        # it into the volume; SCENE_MODEL_URL is only consulted when the volume
        # is missing the model (e.g. running outside Docker, or after a wipe).
        urls = [u.strip() for u in settings.scene.model_url.split(",") if u.strip()]
        if not urls:
            raise RuntimeError(
                f"Scene model not found at {path} and SCENE_MODEL_URL is empty. "
                f"Inside Docker the entrypoint should have seeded it from the "
                f"image; verify the ml_models volume is writable. Outside Docker "
                f"either drop the file at {path} or set SCENE_MODEL_URL."
            )

        last_err: Optional[Exception] = None
        for url in urls:
            tmp = path + ".part"
            try:
                log.info("Downloading scene model from %s -> %s", url, path)
                req = urllib.request.Request(url, headers={"User-Agent": "photonne-ml/0.2"})
                with urllib.request.urlopen(req, timeout=180) as resp, open(tmp, "wb") as f:
                    while True:
                        chunk = resp.read(64 * 1024)
                        if not chunk:
                            break
                        f.write(chunk)
                size = os.path.getsize(tmp)
                if size < 1024:
                    raise RuntimeError(f"downloaded file is suspiciously small ({size} bytes)")
                os.replace(tmp, path)
                log.info("Scene model downloaded: %s (%d bytes)", path, size)
                return path
            except (urllib.error.URLError, urllib.error.HTTPError, OSError, RuntimeError) as e:
                last_err = e
                log.warning("Scene model download from %s failed: %s", url, e)
                if os.path.exists(tmp):
                    try:
                        os.remove(tmp)
                    except OSError:
                        pass

        raise RuntimeError(
            f"Could not download scene model from any of {len(urls)} URL(s); "
            f"last error: {last_err}. "
            f"Workaround: place a Places365 ONNX file at {path} (mount the volume) "
            f"or set SCENE_MODEL_URL to a working URL."
        )

    @property
    def is_loaded(self) -> bool:
        return self._session is not None

    @property
    def load_error(self) -> Optional[str]:
        return self._load_error

    def classify(self, image_bgr: np.ndarray) -> Tuple[List[ClassifiedScene], int]:
        if self._session is None:
            raise RuntimeError("SceneClassifier not loaded")

        start = time.perf_counter()
        tensor = _preprocess(image_bgr, self._input_size)

        outputs = self._session.run(None, {self._input_name: tensor})[0]
        logits = np.squeeze(outputs, axis=0)  # (num_classes,)
        probs = _softmax(logits)

        top_k = max(1, settings.scene.top_k)
        top_idx = np.argsort(probs)[::-1][:top_k]

        results: List[ClassifiedScene] = []
        for rank, idx in enumerate(top_idx, start=1):
            score = float(probs[int(idx)])
            if score < settings.scene.min_score and rank > 1:
                # Always emit at least one prediction (rank 1) so callers can
                # see the model's best guess even on ambiguous images; below
                # that, prune low-confidence noise.
                break
            cls_id = int(idx)
            label = self._classes[cls_id] if 0 <= cls_id < len(self._classes) else f"class_{cls_id}"
            results.append(
                ClassifiedScene(label=label, class_id=cls_id, score=score, rank=rank)
            )

        elapsed_ms = int((time.perf_counter() - start) * 1000)
        return results, elapsed_ms


def _preprocess(image_bgr: np.ndarray, size: int) -> np.ndarray:
    """Resize (shorter side → size+32, then center-crop to size×size), convert
    BGR→RGB, normalize with ImageNet stats and emit a contiguous NCHW float32
    tensor — i.e. the standard torchvision evaluation transform that Places365
    ResNet was trained with."""
    h, w = image_bgr.shape[:2]
    resize_target = size + 32  # 256 when size=224 — matches Resize(256) in torchvision
    scale = resize_target / min(h, w)
    new_w, new_h = int(round(w * scale)), int(round(h * scale))
    resized = cv2.resize(image_bgr, (new_w, new_h), interpolation=cv2.INTER_LINEAR)

    # Center crop
    left = max(0, (new_w - size) // 2)
    top = max(0, (new_h - size) // 2)
    cropped = resized[top:top + size, left:left + size]
    # Pad if the crop fell short (degenerate aspect ratios).
    if cropped.shape[0] != size or cropped.shape[1] != size:
        canvas = np.zeros((size, size, 3), dtype=cropped.dtype)
        canvas[: cropped.shape[0], : cropped.shape[1]] = cropped
        cropped = canvas

    rgb = cv2.cvtColor(cropped, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    rgb = (rgb - _IMAGENET_MEAN) / _IMAGENET_STD
    tensor = np.transpose(rgb, (2, 0, 1))[None, ...].astype(np.float32)
    return np.ascontiguousarray(tensor)


def _softmax(logits: np.ndarray) -> np.ndarray:
    z = logits - np.max(logits)
    e = np.exp(z)
    return e / np.sum(e)


classifier = SceneClassifier()
