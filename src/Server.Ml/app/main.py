import logging
import threading
from contextlib import asynccontextmanager
from typing import Any, Callable, Dict, List, Tuple

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from .config import settings
from .embedding_image import embedder as image_embedder
from .embedding_models import EmbedImageRequest, EmbedTextRequest, EmbeddingResponse
from .embedding_text import embedder as text_embedder
from . import errors
from .face_detector import detector as face_detector
from .face_models import DetectRequest as FaceDetectRequest
from .face_models import DetectResponse as FaceDetectResponse
from .image_loader import ImageLoadError, load_bgr
from .object_detector import detector as object_detector
from .object_models import ObjectDetectRequest, ObjectDetectResponse
from . import request_log
from .scene_classifier import classifier as scene_classifier
from .scene_models import SceneClassifyRequest, SceneClassifyResponse
from .text_models import TextDetectRequest, TextDetectResponse
from .text_recognizer import recognizer as text_recognizer

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("photonne.ml")


@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.face.enabled:
        log.info("Loading face detector model=%s providers=%s", settings.face.model_name, settings.face.providers)
        face_detector.load()
        log.info("Face detector loaded")
    else:
        log.info("Face detector disabled by config")

    if settings.obj.enabled:
        log.info("Loading object detector model=%s providers=%s", settings.obj.model_path, settings.obj.providers)
        try:
            object_detector.load()
            log.info("Object detector loaded")
        except Exception:
            # Object detection runs alongside face detection; a missing or
            # incompatible YOLO model should not take the whole service down.
            # The /v1/objects/detect endpoint will respond 503 instead.
            log.exception("Object detector failed to load; the endpoint will return 503")
    else:
        log.info("Object detector disabled by config")

    if settings.scene.enabled:
        log.info("Loading scene classifier model=%s providers=%s", settings.scene.model_path, settings.scene.providers)
        try:
            scene_classifier.load()
            log.info("Scene classifier loaded")
        except Exception:
            # Same isolation rationale as object detection: a missing/broken
            # Places365 model must not take faces or objects down with it.
            log.exception("Scene classifier failed to load; the endpoint will return 503")
    else:
        log.info("Scene classifier disabled by config")

    if settings.text.enabled:
        log.info("Loading text recognizer providers=%s", settings.text.providers)
        try:
            text_recognizer.load()
            log.info("Text recognizer loaded")
        except Exception:
            # Same isolation: a missing rapidocr dependency or model file
            # must not take the rest of the service down.
            log.exception("Text recognizer failed to load; the endpoint will return 503")
    else:
        log.info("Text recognizer disabled by config")

    if settings.embedding.enabled:
        log.info(
            "Loading CLIP embedder image=%s text=%s providers=%s",
            settings.embedding.image_model_path,
            settings.embedding.text_model_path,
            settings.embedding.providers,
        )
        try:
            image_embedder.load()
            text_embedder.load()
            log.info("CLIP embedders loaded")
        except Exception:
            # Same isolation as the other capabilities: a missing CLIP model
            # only knocks /v1/embeddings/* offline (returns 503) but leaves
            # face/object/scene/text endpoints usable.
            log.exception("CLIP embedders failed to load; the endpoint will return 503")
    else:
        log.info("CLIP embedders disabled by config")

    yield


app = FastAPI(title="Photonne ML Service", version="0.2.0", lifespan=lifespan)
errors.install_handlers(app)
request_log.install(app)


@app.get("/health")
def health() -> Dict[str, Any]:
    providers = [p.strip() for p in settings.providers.split(",") if p.strip()]
    components: Dict[str, Any] = {
        "faces": {
            "enabled": settings.face.enabled,
            "loaded": face_detector.is_loaded,
            "model": settings.face.model_name,
            "providers": face_detector.providers,
        },
        "objects": {
            "enabled": settings.obj.enabled,
            "loaded": object_detector.is_loaded,
            "model": settings.obj.model_path,
            "providers": object_detector.providers,
        },
        "scenes": {
            "enabled": settings.scene.enabled,
            "loaded": scene_classifier.is_loaded,
            "model": settings.scene.model_path,
            "providers": scene_classifier.providers,
        },
        "text": {
            "enabled": settings.text.enabled,
            "loaded": text_recognizer.is_loaded,
            "model": "rapidocr",
            "providers": text_recognizer.providers,
        },
        "embeddings": {
            "enabled": settings.embedding.enabled,
            "loaded": image_embedder.is_loaded and text_embedder.is_loaded,
            "model": settings.embedding.model_version,
            "providers": image_embedder.providers,
        },
    }
    if object_detector.load_error:
        components["objects"]["error"] = object_detector.load_error
    if scene_classifier.load_error:
        components["scenes"]["error"] = scene_classifier.load_error
    if text_recognizer.load_error:
        components["text"]["error"] = text_recognizer.load_error
    if image_embedder.load_error or text_embedder.load_error:
        components["embeddings"]["error"] = (
            image_embedder.load_error or text_embedder.load_error
        )
    enabled = [c for c, v in components.items() if v["enabled"]]
    all_loaded = all(components[c]["loaded"] for c in enabled) if enabled else True
    return {
        "status": "ready" if all_loaded else "loading",
        "providers": providers,
        "components": components,
    }


class ProviderConfigRequest(BaseModel):
    # task: faces | objects | scenes | text | embeddings (singular aliases too).
    task: str
    # ONNX provider spec, e.g. "CUDAExecutionProvider,CPUExecutionProvider".
    # Empty / "auto" / "default" reloads the task on its configured env default.
    providers: str = ""


# Serialize reloads so two concurrent admin saves can't rebuild the same task's
# session at once. Inference itself stays lock-free: load() builds the new
# session fully before swapping the attribute, and the GIL makes that swap
# atomic, so an in-flight request keeps using whichever session it referenced.
_reload_lock = threading.Lock()

# task alias -> (loaders to reload, effective-providers getter)
_TASK_LOADERS: Dict[str, Tuple[List[Any], Callable[[], str]]] = {
    "faces": ([face_detector], lambda: face_detector.providers),
    "face": ([face_detector], lambda: face_detector.providers),
    "objects": ([object_detector], lambda: object_detector.providers),
    "object": ([object_detector], lambda: object_detector.providers),
    "scenes": ([scene_classifier], lambda: scene_classifier.providers),
    "scene": ([scene_classifier], lambda: scene_classifier.providers),
    "text": ([text_recognizer], lambda: text_recognizer.providers),
    "embeddings": ([image_embedder, text_embedder], lambda: image_embedder.providers),
    "embedding": ([image_embedder, text_embedder], lambda: image_embedder.providers),
}


@app.post("/v1/config")
def set_provider_config(req: ProviderConfigRequest) -> Dict[str, Any]:
    """Reload one ML task on a different execution provider at runtime.

    Lets the admin panel move a task between GPU and CPU without restarting the
    container. A failed reload leaves the previous session running and surfaces
    the cause as a 500 so the caller can revert the setting.
    """
    key = req.task.strip().lower()
    entry = _TASK_LOADERS.get(key)
    if entry is None:
        raise HTTPException(status_code=400, detail=f"unknown task '{req.task}'")
    loaders, providers_getter = entry
    reset = req.providers.strip().lower() in ("", "auto", "default")
    target = None if reset else req.providers.strip()

    with _reload_lock:
        try:
            for loader in loaders:
                loader.load(target)
        except Exception as e:
            raise HTTPException(
                status_code=500,
                detail=(
                    f"failed to reload '{key}' on providers="
                    f"{target or 'default'}: {type(e).__name__}: {e}"
                ),
            ) from e

    return {
        "task": key,
        "providers": providers_getter(),
        "loaded": all(loader.is_loaded for loader in loaders),
    }


@app.post("/v1/faces/detect", response_model=FaceDetectResponse)
def detect_faces(req: FaceDetectRequest) -> FaceDetectResponse:
    if not settings.face.enabled:
        raise errors.capability_disabled("el reconocimiento facial")

    try:
        img = load_bgr(req.image_path)
    except ImageLoadError as e:
        raise errors.image_unreadable(str(e)) from e

    try:
        faces, elapsed_ms = face_detector.detect(img)
    except Exception as e:
        log.exception("Face detection failed for asset %s", req.asset_id)
        raise errors.inference_failed("reconocimiento facial", e) from e

    h, w = img.shape[:2]
    return FaceDetectResponse(
        asset_id=req.asset_id,
        faces=faces,
        image_size=[w, h],
        elapsed_ms=elapsed_ms,
    )


@app.post("/v1/objects/detect", response_model=ObjectDetectResponse)
def detect_objects(req: ObjectDetectRequest) -> ObjectDetectResponse:
    if not settings.obj.enabled:
        raise errors.capability_disabled("la detección de objetos")
    if not object_detector.is_loaded:
        raise errors.model_not_loaded("detección de objetos", object_detector.load_error)

    try:
        img = load_bgr(req.image_path)
    except ImageLoadError as e:
        raise errors.image_unreadable(str(e)) from e

    try:
        objects, elapsed_ms = object_detector.detect(img)
    except Exception as e:
        log.exception("Object detection failed for asset %s", req.asset_id)
        raise errors.inference_failed("detección de objetos", e) from e

    h, w = img.shape[:2]
    return ObjectDetectResponse(
        asset_id=req.asset_id,
        objects=objects,
        image_size=[w, h],
        elapsed_ms=elapsed_ms,
    )


@app.post("/v1/scenes/classify", response_model=SceneClassifyResponse)
def classify_scenes(req: SceneClassifyRequest) -> SceneClassifyResponse:
    if not settings.scene.enabled:
        raise errors.capability_disabled("la clasificación de escenas")
    if not scene_classifier.is_loaded:
        raise errors.model_not_loaded("clasificación de escenas", scene_classifier.load_error)

    try:
        img = load_bgr(req.image_path)
    except ImageLoadError as e:
        raise errors.image_unreadable(str(e)) from e

    try:
        scenes, elapsed_ms = scene_classifier.classify(img)
    except Exception as e:
        log.exception("Scene classification failed for asset %s", req.asset_id)
        raise errors.inference_failed("clasificación de escenas", e) from e

    h, w = img.shape[:2]
    return SceneClassifyResponse(
        asset_id=req.asset_id,
        scenes=scenes,
        image_size=[w, h],
        elapsed_ms=elapsed_ms,
    )


@app.post("/v1/text/detect", response_model=TextDetectResponse)
def detect_text(req: TextDetectRequest) -> TextDetectResponse:
    if not settings.text.enabled:
        raise errors.capability_disabled("el reconocimiento de texto")
    if not text_recognizer.is_loaded:
        raise errors.model_not_loaded("reconocimiento de texto", text_recognizer.load_error)

    try:
        img = load_bgr(req.image_path)
    except ImageLoadError as e:
        raise errors.image_unreadable(str(e)) from e

    try:
        lines, full_text, elapsed_ms = text_recognizer.recognize(img)
    except Exception as e:
        log.exception("Text recognition failed for asset %s", req.asset_id)
        raise errors.inference_failed("reconocimiento de texto", e) from e

    h, w = img.shape[:2]
    return TextDetectResponse(
        asset_id=req.asset_id,
        lines=lines,
        full_text=full_text,
        image_size=[w, h],
        elapsed_ms=elapsed_ms,
    )


@app.post("/v1/embeddings/image", response_model=EmbeddingResponse)
def embed_image(req: EmbedImageRequest) -> EmbeddingResponse:
    if not settings.embedding.enabled:
        raise errors.capability_disabled("el cálculo de embeddings de imagen")
    if not image_embedder.is_loaded:
        raise errors.model_not_loaded("embeddings de imagen", image_embedder.load_error)

    try:
        img = load_bgr(req.image_path)
    except ImageLoadError as e:
        raise errors.image_unreadable(str(e)) from e

    try:
        vector, elapsed_ms = image_embedder.encode(img)
    except Exception as e:
        log.exception("Image embedding failed for asset %s", req.asset_id)
        raise errors.inference_failed("embeddings de imagen", e) from e

    return EmbeddingResponse(
        asset_id=req.asset_id,
        embedding=vector,
        dim=len(vector),
        model=settings.embedding.model_version,
        elapsed_ms=elapsed_ms,
    )


@app.post("/v1/embeddings/text", response_model=EmbeddingResponse)
def embed_text(req: EmbedTextRequest) -> EmbeddingResponse:
    if not settings.embedding.enabled:
        raise errors.capability_disabled("el cálculo de embeddings de texto")
    if not text_embedder.is_loaded:
        raise errors.model_not_loaded("embeddings de texto", text_embedder.load_error)

    try:
        vector, elapsed_ms = text_embedder.encode(req.text)
    except Exception as e:
        log.exception("Text embedding failed")
        raise errors.inference_failed("embeddings de texto", e) from e

    return EmbeddingResponse(
        asset_id=None,
        embedding=vector,
        dim=len(vector),
        model=settings.embedding.model_version,
        elapsed_ms=elapsed_ms,
    )
