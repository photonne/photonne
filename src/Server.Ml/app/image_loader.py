import os
import cv2
import numpy as np


class ImageLoadError(Exception):
    pass


def load_bgr(path: str) -> np.ndarray:
    """Load an image from disk as BGR (OpenCV convention used by InsightFace)."""
    if not path:
        raise ImageLoadError("empty path")
    if not os.path.isfile(path):
        raise ImageLoadError(f"file not found: {path}")
    img = cv2.imread(path, cv2.IMREAD_COLOR)
    if img is None:
        raise ImageLoadError(f"cv2 failed to decode: {path}")
    return img
