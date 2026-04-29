import os
from dataclasses import dataclass, field


def _bool_env(name: str, default: str) -> bool:
    return os.getenv(name, default).lower() not in {"false", "0", "no"}


@dataclass(frozen=True)
class FaceSettings:
    # Legacy env var names (MODEL_NAME, DET_SIZE, ...) still work so existing
    # docker-compose / .env files keep operating after the service rename.
    enabled: bool = _bool_env("FACE_ENABLED", "true")
    model_name: str = os.getenv("FACE_MODEL_NAME", os.getenv("MODEL_NAME", "buffalo_l"))
    model_root: str = os.getenv("INSIGHTFACE_HOME", "/app/models")
    det_size: int = int(os.getenv("FACE_DET_SIZE", os.getenv("DET_SIZE", "640")))
    min_face_size: int = int(os.getenv("FACE_MIN_SIZE", os.getenv("MIN_FACE_SIZE", "40")))
    min_det_score: float = float(os.getenv("FACE_MIN_DET_SCORE", os.getenv("MIN_DET_SCORE", "0.5")))
    max_faces: int = int(os.getenv("FACE_MAX", os.getenv("MAX_FACES", "50")))


@dataclass(frozen=True)
class ObjectSettings:
    enabled: bool = _bool_env("OBJECT_ENABLED", "true")
    # YOLOv8-format ONNX model. The default yolov8n.onnx (~12 MB, COCO-80) is
    # auto-downloaded into model_dir on first launch when missing.
    model_path: str = os.getenv("OBJECT_MODEL_PATH", "/app/models/yolov8n.onnx")
    model_dir: str = os.getenv("OBJECT_MODEL_DIR", "/app/models")
    # Optional fallback when the model file is missing from the persisted
    # volume (e.g. someone wiped it). The Docker image already bakes
    # yolov8n.onnx and seeds it on first launch, so by default we don't fetch
    # anything at runtime. Set to one or more comma-separated URLs to enable
    # auto-download.
    model_url: str = os.getenv("OBJECT_MODEL_URL", "")
    det_size: int = int(os.getenv("OBJECT_DET_SIZE", "640"))
    min_score: float = float(os.getenv("OBJECT_MIN_SCORE", "0.25"))
    iou_threshold: float = float(os.getenv("OBJECT_IOU", "0.45"))
    max_objects: int = int(os.getenv("OBJECT_MAX", "100"))


@dataclass(frozen=True)
class SceneSettings:
    enabled: bool = _bool_env("SCENE_ENABLED", "true")
    # Places365 ResNet18 ONNX. The default ~45 MB file is built and baked into
    # the image at Dockerfile time and seeded into model_dir on first launch.
    model_path: str = os.getenv("SCENE_MODEL_PATH", "/app/models/scene_classifier.onnx")
    model_dir: str = os.getenv("SCENE_MODEL_DIR", "/app/models")
    # Optional fallback when the model file is missing from the volume. Empty
    # by default because the entrypoint already seeds the baked copy.
    model_url: str = os.getenv("SCENE_MODEL_URL", "")
    det_size: int = int(os.getenv("SCENE_DET_SIZE", "224"))
    # Number of top predictions to return / persist. The Places365 taxonomy is
    # fine-grained, so the rank-1 prediction is often a sibling of the "right"
    # answer (e.g. "ocean" vs "beach" vs "coast"); keeping the top 3 lets the
    # search facet recover most assets without flooding the database.
    top_k: int = int(os.getenv("SCENE_TOP_K", "3"))
    # Drop predictions below this softmax probability (rank ≥ 2 only — rank 1
    # is always emitted so the model's best guess is always visible).
    min_score: float = float(os.getenv("SCENE_MIN_SCORE", "0.15"))


@dataclass(frozen=True)
class TextSettings:
    enabled: bool = _bool_env("TEXT_ENABLED", "true")
    # Optional overrides for the three ONNX models RapidOCR loads. When empty
    # the engine falls back to the weights bundled with the rapidocr_onnxruntime
    # wheel (~10 MB det + ~10 MB rec + ~1 MB cls), which is the default path.
    det_model_path: str = os.getenv("TEXT_DET_MODEL_PATH", "")
    rec_model_path: str = os.getenv("TEXT_REC_MODEL_PATH", "")
    cls_model_path: str = os.getenv("TEXT_CLS_MODEL_PATH", "")
    # Drop recognized lines below this confidence so noisy detections on
    # textures (bark, foliage, brick) don't pollute the search index.
    min_score: float = float(os.getenv("TEXT_MIN_SCORE", "0.5"))
    # Hard cap of recognized lines per image. Long documents are rare in a
    # photo library and indexing 500+ lines per asset would balloon the table.
    max_lines: int = int(os.getenv("TEXT_MAX_LINES", "200"))


@dataclass(frozen=True)
class Settings:
    # Comma-separated list of ONNX execution providers in priority order.
    providers: str = os.getenv("ONNX_PROVIDERS", "CPUExecutionProvider")
    face: FaceSettings = field(default_factory=FaceSettings)
    obj: ObjectSettings = field(default_factory=ObjectSettings)
    scene: SceneSettings = field(default_factory=SceneSettings)
    text: TextSettings = field(default_factory=TextSettings)


settings = Settings()
