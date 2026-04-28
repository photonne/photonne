import logging
from contextlib import asynccontextmanager
from typing import Any, Dict

from fastapi import FastAPI, HTTPException

from .config import settings
from .face_detector import detector as face_detector
from .face_models import DetectRequest as FaceDetectRequest
from .face_models import DetectResponse as FaceDetectResponse
from .image_loader import ImageLoadError, load_bgr
from .object_detector import detector as object_detector
from .object_models import ObjectDetectRequest, ObjectDetectResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("photonne.ml")


@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.face.enabled:
        log.info("Loading face detector model=%s providers=%s", settings.face.model_name, settings.providers)
        face_detector.load()
        log.info("Face detector loaded")
    else:
        log.info("Face detector disabled by config")

    if settings.obj.enabled:
        log.info("Loading object detector model=%s providers=%s", settings.obj.model_path, settings.providers)
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

    yield


app = FastAPI(title="Photonne ML Service", version="0.2.0", lifespan=lifespan)


@app.get("/health")
def health() -> Dict[str, Any]:
    providers = [p.strip() for p in settings.providers.split(",") if p.strip()]
    components: Dict[str, Any] = {
        "faces": {
            "enabled": settings.face.enabled,
            "loaded": face_detector.is_loaded,
            "model": settings.face.model_name,
        },
        "objects": {
            "enabled": settings.obj.enabled,
            "loaded": object_detector.is_loaded,
            "model": settings.obj.model_path,
        },
    }
    if object_detector.load_error:
        components["objects"]["error"] = object_detector.load_error
    enabled = [c for c, v in components.items() if v["enabled"]]
    all_loaded = all(components[c]["loaded"] for c in enabled) if enabled else True
    return {
        "status": "ready" if all_loaded else "loading",
        "providers": providers,
        "components": components,
    }


@app.post("/v1/faces/detect", response_model=FaceDetectResponse)
def detect_faces(req: FaceDetectRequest) -> FaceDetectResponse:
    if not settings.face.enabled:
        raise HTTPException(status_code=503, detail="face detection disabled")

    try:
        img = load_bgr(req.image_path)
    except ImageLoadError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    faces, elapsed_ms = face_detector.detect(img)
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
        raise HTTPException(status_code=503, detail="object detection disabled")
    if not object_detector.is_loaded:
        detail = (
            f"object detector not loaded: {object_detector.load_error}"
            if object_detector.load_error
            else "object detector not loaded"
        )
        raise HTTPException(status_code=503, detail=detail)

    try:
        img = load_bgr(req.image_path)
    except ImageLoadError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    objects, elapsed_ms = object_detector.detect(img)
    h, w = img.shape[:2]
    return ObjectDetectResponse(
        asset_id=req.asset_id,
        objects=objects,
        image_size=[w, h],
        elapsed_ms=elapsed_ms,
    )
