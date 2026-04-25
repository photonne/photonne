# Photonne Faces

Microservice that exposes face detection and 512-d ArcFace embeddings to the
Photonne API. Built on InsightFace (`buffalo_l`: RetinaFace detector + ArcFace
recognizer) served by FastAPI.

## Endpoints

- `GET /health` — liveness/readiness; reports model + ONNX providers.
- `POST /v1/faces/detect` — body `{"image_path": "/data/assets/...", "asset_id": "<guid>"}`.

The service reads images directly from a shared volume (`:ro`); the API never
serializes pixel bytes over HTTP.

## Local run

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

First boot downloads ~280 MB of weights into `$INSIGHTFACE_HOME`. Persist that
volume in production to avoid re-downloads.

## Configuration

Environment variables (see `app/config.py`):

| Var | Default | Notes |
|---|---|---|
| `MODEL_NAME` | `buffalo_l` | InsightFace model bundle |
| `DET_SIZE` | `640` | Detector input size |
| `MIN_FACE_SIZE` | `40` | Skip faces smaller than this (pixels) |
| `MIN_DET_SCORE` | `0.5` | Detection confidence threshold |
| `MAX_FACES` | `50` | Hard cap per image |
| `ONNX_PROVIDERS` | `CPUExecutionProvider` | Comma-separated. Use `CUDAExecutionProvider,CPUExecutionProvider` for GPU |
