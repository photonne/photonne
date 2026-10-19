import os
from dataclasses import dataclass, field


def _bool_env(name: str, default: str) -> bool:
    return os.getenv(name, default).lower() not in {"false", "0", "no"}


# Comma-separated list of ONNX execution providers in priority order. Each task
# reads its own <TASK>_ONNX_PROVIDERS and falls back to the global ONNX_PROVIDERS
# (which itself defaults to CPU). This lets an operator — or the admin panel via
# the ML /v1/config endpoint — put, say, faces on the GPU while keeping OCR on
# CPU, so no single task's VRAM spike takes the whole GPU out of memory.
_GLOBAL_PROVIDERS = os.getenv("ONNX_PROVIDERS", "CPUExecutionProvider")


def _providers_for(task_env: str) -> str:
    return os.getenv(task_env, _GLOBAL_PROVIDERS)


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
    providers: str = _providers_for("FACE_ONNX_PROVIDERS")


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
    providers: str = _providers_for("OBJECT_ONNX_PROVIDERS")


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
    providers: str = _providers_for("SCENE_ONNX_PROVIDERS")


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
    providers: str = _providers_for("TEXT_ONNX_PROVIDERS")


@dataclass(frozen=True)
class EmbeddingSettings:
    # Multilingual CLIP (M-CLIP ViT-B/32). Image and text encoders share the
    # same 512-dim vector space so cosine similarity between query text and
    # image embeddings is meaningful across the 50+ languages the text tower
    # was distilled on.
    enabled: bool = _bool_env("EMBEDDING_ENABLED", "true")
    image_model_path: str = os.getenv("EMBEDDING_IMAGE_MODEL_PATH", "/app/models/clip_image.onnx")
    text_model_path: str = os.getenv("EMBEDDING_TEXT_MODEL_PATH", "/app/models/clip_text.onnx")
    text_tokenizer_path: str = os.getenv("EMBEDDING_TEXT_TOKENIZER_PATH", "/app/models/clip_tokenizer.json")
    model_dir: str = os.getenv("EMBEDDING_MODEL_DIR", "/app/models")
    # Optional runtime fallback URLs (one or more comma-separated). Same
    # pattern as OBJECT_MODEL_URL / SCENE_MODEL_URL: only consulted when the
    # corresponding file is missing on disk — typically because the build
    # couldn't reach HuggingFace and never baked the file into the image.
    image_model_url: str = os.getenv("EMBEDDING_IMAGE_MODEL_URL", "")
    text_model_url: str = os.getenv("EMBEDDING_TEXT_MODEL_URL", "")
    text_tokenizer_url: str = os.getenv("EMBEDDING_TEXT_TOKENIZER_URL", "")
    image_size: int = int(os.getenv("EMBEDDING_IMAGE_SIZE", "224"))
    text_max_length: int = int(os.getenv("EMBEDDING_TEXT_MAX_LENGTH", "64"))
    # Stable identifier persisted alongside each embedding row so a future
    # model swap re-embeds via the backfill instead of mixing vector spaces.
    model_version: str = os.getenv("EMBEDDING_MODEL_VERSION", "mclip-vit-b32-v1")
    providers: str = _providers_for("EMBEDDING_ONNX_PROVIDERS")


@dataclass(frozen=True)
class VoiceSettings:
    # Silero VAD v5 (~2 MB ONNX): when someone is speaking in a video's audio,
    # so the video summaries don't cut sentences in half. Baked into the image
    # and seeded like the other models; the URL is only a fallback for a
    # missing file.
    enabled: bool = _bool_env("VOICE_ENABLED", "true")
    model_path: str = os.getenv("VOICE_MODEL_PATH", "/app/models/silero_vad.onnx")
    model_url: str = os.getenv(
        "VOICE_MODEL_URL",
        "https://raw.githubusercontent.com/snakers4/silero-vad/v5.1.2/src/silero_vad/data/silero_vad.onnx",
    )
    # Longest stretch of audio read per request: summaries only look at the
    # first 20 minutes of a video anyway.
    max_seconds: float = float(os.getenv("VOICE_MAX_SECONDS", "1200"))
    providers: str = _providers_for("VOICE_ONNX_PROVIDERS")


@dataclass(frozen=True)
class Settings:
    # Global ONNX execution-provider list (priority order). Kept as the fallback
    # for the per-task <TASK>_ONNX_PROVIDERS on each sub-settings above.
    providers: str = _GLOBAL_PROVIDERS
    face: FaceSettings = field(default_factory=FaceSettings)
    obj: ObjectSettings = field(default_factory=ObjectSettings)
    scene: SceneSettings = field(default_factory=SceneSettings)
    text: TextSettings = field(default_factory=TextSettings)
    embedding: EmbeddingSettings = field(default_factory=EmbeddingSettings)
    voice: VoiceSettings = field(default_factory=VoiceSettings)


settings = Settings()
