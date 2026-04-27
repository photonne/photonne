import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from .config import settings
from .detector import detector
from .image_loader import ImageLoadError, load_bgr
from .models import DetectRequest, DetectResponse, HealthResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("photonne.faces")


@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("Loading InsightFace model=%s providers=%s", settings.model_name, settings.providers)
    detector.load()
    log.info("Model loaded")
    yield


app = FastAPI(title="Photonne Faces", version="0.1.0", lifespan=lifespan)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    status = "ready" if detector.is_loaded else "loading"
    return HealthResponse(
        status=status,
        model=settings.model_name,
        providers=[p.strip() for p in settings.providers.split(",") if p.strip()],
    )


@app.post("/v1/faces/detect", response_model=DetectResponse)
def detect(req: DetectRequest) -> DetectResponse:
    try:
        img = load_bgr(req.image_path)
    except ImageLoadError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    faces, elapsed_ms = detector.detect(img)
    h, w = img.shape[:2]
    return DetectResponse(
        asset_id=req.asset_id,
        faces=faces,
        image_size=[w, h],
        elapsed_ms=elapsed_ms,
    )
