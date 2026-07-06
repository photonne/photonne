# Photonne ML Service

FastAPI microservice that hosts the ML capabilities consumed by the Photonne
API. Each capability is opt-in and runs on the same ONNX Runtime stack.

| Capability | Endpoint | Model | Purpose |
|---|---|---|---|
| Face detection + embeddings | `POST /v1/faces/detect` | InsightFace `buffalo_l` (RetinaFace + ArcFace) | Bounding boxes and 512-d embeddings used for face clustering. |
| Object detection | `POST /v1/objects/detect` | YOLOv8 ONNX (`yolov8n` by default, COCO-80) | Bounding boxes and class labels for tagging and search. |
| Scene classification | `POST /v1/scenes/classify` | Places365 ResNet18 ONNX | Top-K scene labels (e.g. "beach", "kitchen", "forest path") used as search facets. |
| Text recognition (OCR) | `POST /v1/text/detect` | RapidOCR (PaddleOCR DBNet + CRNN, ONNX) | Per-line transcription with bbox + confidence; full-text indexed for search. |

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
| `ONNX_PROVIDERS` | `CPUExecutionProvider` | Comma-separated ORT execution providers, in priority order. See **GPU acceleration** below — the default image is CPU-only, so setting this to `CUDAExecutionProvider` alone does nothing. |
| `FACE_ONNX_PROVIDERS`, `OBJECT_ONNX_PROVIDERS`, `SCENE_ONNX_PROVIDERS`, `TEXT_ONNX_PROVIDERS`, `EMBEDDING_ONNX_PROVIDERS` | *(falls back to `ONNX_PROVIDERS`)* | Per-task provider override, so e.g. faces run on GPU while OCR stays on CPU. Also settable at runtime per task from the admin panel (persisted in the API DB, pushed to `POST /v1/config`). |
| `CUDA_GPU_MEM_LIMIT_MB` | *(unset)* | Optional hard cap (MiB) on each CUDA session's memory arena. Leave unset unless a very tight card OOMs even with the frugal defaults. |

## GPU acceleration (NVIDIA/CUDA)

The default `photonne-ml` image is CPU-only (`python:3.11-slim` + the `onnxruntime`
CPU wheel), so faces/objects/scenes/embeddings/OCR all run on CPU regardless of
`ONNX_PROVIDERS`. To use an NVIDIA GPU you need three things together:

1. **Host:** an NVIDIA driver and the [`nvidia-container-toolkit`](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html)
   (`sudo nvidia-ctk runtime configure && sudo systemctl restart docker`). Verify:
   `docker run --rm --gpus all nvidia/cuda:12.6.2-base-ubuntu22.04 nvidia-smi`.
2. **Image:** the `photonne-ml:latest-gpu` build (CUDA 12 + cuDNN 9 base +
   `onnxruntime-gpu`), built from `Dockerfile.gpu`. amd64 only.
3. **Compose:** the `docker-compose.gpu.yml` overlay, which passes the GPU
   through and sets `ONNX_PROVIDERS=CUDAExecutionProvider,CPUExecutionProvider`.

```bash
docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d
```

Confirm GPU is actually active: `curl -s localhost:8000/health | jq .providers`
should list `CUDAExecutionProvider`, and `docker logs photonne-ml` should NOT show
an ORT warning about falling back to CPU. During a (re)index `nvidia-smi` should
show the container's Python process with utilisation > 0.

All CUDA sessions this service creates use memory-frugal provider options
(`cudnn_conv_algo_search=HEURISTIC`, `arena_extend_strategy=kSameAsRequested`)
so the ~10 models can share one GPU without the exhaustive conv-search workspace
tipping the card into "CUDA out of memory". See `onnx_providers.py`.

### Per-task device selection

Each task reads its own `<TASK>_ONNX_PROVIDERS` (falling back to
`ONNX_PROVIDERS`), so you don't have to run everything on the same device. The
same choice is exposed per task in the admin panel (Face/Object/Scene/Text/
Embedding settings → *Compute device*): the API persists it and pushes it to
`POST /v1/config`, which hot-reloads that task on the new provider — no container
restart. On API startup the saved choices are reconciled back to the ML service.

> **OCR (RapidOCR) defaults to CPU even in the GPU overlay.**
> rapidocr-onnxruntime hardcodes an EXHAUSTIVE cuDNN conv-algo search and doesn't
> expose `provider_options`, so we can't shrink its VRAM footprint like the other
> models. On a GPU already hosting faces/objects/scenes/embeddings its workspace
> is the first allocation to OOM. `docker-compose.gpu.yml` therefore sets
> `TEXT_ONNX_PROVIDERS=CPUExecutionProvider`; move OCR to GPU only if the card has
> ample spare VRAM.

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

### Text recognition (OCR)
| Var | Default | Notes |
|---|---|---|
| `TEXT_ENABLED` | `true` | Disable to skip loading RapidOCR |
| `TEXT_DET_MODEL_PATH` | _(empty)_ | Optional override for the DBNet detection ONNX. Empty = use the model bundled with the rapidocr_onnxruntime wheel. |
| `TEXT_REC_MODEL_PATH` | _(empty)_ | Optional override for the CRNN recognition ONNX (swap to a different language bundle here). |
| `TEXT_CLS_MODEL_PATH` | _(empty)_ | Optional override for the orientation-classifier ONNX. |
| `TEXT_MIN_SCORE` | `0.5` | Drop recognized lines below this confidence so noisy textures don't pollute search. |
| `TEXT_MAX_LINES` | `200` | Hard cap of stored lines per image. |
