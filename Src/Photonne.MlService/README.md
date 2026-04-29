# Photonne ML Service

FastAPI microservice that hosts the ML capabilities consumed by the Photonne
API. Each capability is opt-in and runs on the same ONNX Runtime stack.

| Capability | Endpoint | Model | Purpose |
|---|---|---|---|
| Face detection + embeddings | `POST /v1/faces/detect` | InsightFace `buffalo_l` (RetinaFace + ArcFace) | Bounding boxes and 512-d embeddings used for face clustering. |
| Object detection | `POST /v1/objects/detect` | YOLOv8 ONNX (`yolov8n` by default, COCO-80) | Bounding boxes and class labels for tagging and search. |
| Scene classification | `POST /v1/scenes/classify` | Places365 ResNet18 ONNX | Top-K scene labels (e.g. "beach", "kitchen", "forest path") used as search facets. |

Both endpoints accept `{"image_path": "/data/assets/...", "asset_id": "<guid>"}`
and the API process reads images directly from the shared volume — pixel bytes
are never sent over HTTP.

`GET /health` reports per-component readiness and the active ONNX providers.

## Local run

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

First boot downloads the InsightFace bundle (~280 MB) into `$INSIGHTFACE_HOME`.
The YOLOv8 ONNX (~12 MB) is **baked into the Docker image** at build time and
the entrypoint seeds it into `$OBJECT_MODEL_DIR` on first launch — the runtime
container does not need internet access for object detection. Outside Docker
provide your own ONNX file at `OBJECT_MODEL_PATH` or set `OBJECT_MODEL_URL` to
a working source.

## Configuration

Environment variables (see `app/config.py`):

### Shared
| Var | Default | Notes |
|---|---|---|
| `ONNX_PROVIDERS` | `CPUExecutionProvider` | Comma-separated. Use `CUDAExecutionProvider,CPUExecutionProvider` for GPU |

### Face detection
| Var | Default | Notes |
|---|---|---|
| `FACE_ENABLED` | `true` | Disable to skip loading InsightFace |
| `FACE_MODEL_NAME` (legacy: `MODEL_NAME`) | `buffalo_l` | InsightFace model bundle |
| `FACE_DET_SIZE` (legacy: `DET_SIZE`) | `640` | Detector input size |
| `FACE_MIN_SIZE` (legacy: `MIN_FACE_SIZE`) | `40` | Skip faces smaller than this (pixels) |
| `FACE_MIN_DET_SCORE` (legacy: `MIN_DET_SCORE`) | `0.5` | Detection confidence threshold |
| `FACE_MAX` (legacy: `MAX_FACES`) | `50` | Hard cap per image |

### Object detection
| Var | Default | Notes |
|---|---|---|
| `OBJECT_ENABLED` | `true` | Disable to skip loading the YOLO model |
| `OBJECT_MODEL_PATH` | `/app/models/yolov8n.onnx` | Path to a YOLOv8-format ONNX file. Seeded from the image on first launch. |
| `OBJECT_MODEL_DIR` | `/app/models` | Where the model lives (must be writable). |
| `OBJECT_MODEL_URL` | _(empty)_ | Optional comma-separated fallback URL list when the file is missing. Empty by default; the image already ships the model. |
| `OBJECT_DET_SIZE` | `640` | Network input size (square) |
| `OBJECT_MIN_SCORE` | `0.25` | Per-class confidence threshold |
| `OBJECT_IOU` | `0.45` | IoU threshold for greedy NMS |
| `OBJECT_MAX` | `100` | Hard cap of detections per image |

### Scene classification
| Var | Default | Notes |
|---|---|---|
| `SCENE_ENABLED` | `true` | Disable to skip loading the Places365 model |
| `SCENE_MODEL_PATH` | `/app/models/scene_classifier.onnx` | Path to a Places365-compatible ResNet ONNX. Seeded from the image on first launch. |
| `SCENE_MODEL_DIR` | `/app/models` | Where the model lives (must be writable). |
| `SCENE_MODEL_URL` | _(empty)_ | Optional comma-separated fallback URL list when the file is missing. Empty by default; the image already ships the model. |
| `SCENE_DET_SIZE` | `224` | Network input size (square) |
| `SCENE_TOP_K` | `3` | Number of top predictions returned per image |
| `SCENE_MIN_SCORE` | `0.15` | Drop predictions below this softmax probability (rank ≥ 2 only — rank 1 is always emitted). |
