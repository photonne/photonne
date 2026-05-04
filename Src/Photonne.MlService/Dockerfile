# ─── Stage 1: export yolov8n.pt -> yolov8n.onnx + Places365 ResNet18 -> ONNX ──
# Ultralytics distributes only .pt weights via their CDN; the .onnx files we
# actually consume at runtime are generated here at build time. Doing the
# export in a dedicated stage means torch + ultralytics never reach the
# runtime image.
FROM python:3.11-slim AS yolo-export

ENV PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# CPU-only torch keeps the build stage light (~200 MB vs the CUDA build's ~2 GB).
# Everything goes in a single `pip install` so the resolver sees the +cpu pin
# at the same time as ultralytics/torchvision and doesn't silently upgrade
# torch from PyPI. onnxscript is required by torch>=2.6's torch.onnx.export
# even when the legacy (non-dynamo) path is selected.
RUN pip install \
        --extra-index-url https://download.pytorch.org/whl/cpu \
        torch==2.4.1+cpu \
        torchvision==0.19.1+cpu \
        ultralytics==8.3.0 \
        onnx==1.16.2 \
        onnxslim==0.1.34 \
        onnxscript==0.1.0 \
        onnxruntime==1.19.2 \
        transformers==4.45.2 \
        sentence-transformers==3.1.1 \
        tokenizers==0.20.0

WORKDIR /export
RUN python -c "from ultralytics import YOLO; YOLO('yolov8n.pt').export(format='onnx', dynamic=False, simplify=True, opset=12)"
# Result: /export/yolov8n.pt (auto-downloaded) + /export/yolov8n.onnx (exported).

# Places365 ResNet18: download the official .pth.tar checkpoint from CSAIL
# Vision and export to ONNX. Two mirrors are tried because the primary host
# (places2.csail.mit.edu) occasionally rate-limits build runners. If both
# fail the build still succeeds (with no scene_classifier.onnx baked in) and
# the runtime endpoint returns 503 until SCENE_MODEL_URL is set.
COPY scripts/export_scene.py /export/export_scene.py
# `|| true` keeps the build green if every mirror is unreachable; the runtime
# container then serves 503 on /v1/scenes/classify until SCENE_MODEL_URL is
# pointed at a working file or one is mounted at /app/models/scene_classifier.onnx.
RUN python /export/export_scene.py || echo "[build] WARNING: scene_classifier.onnx export failed; runtime endpoint will be unavailable"

# M-CLIP (ViT-B/32 multilingual) export. Same pattern as the scene classifier:
# tolerant of network failures so the image build still succeeds; the runtime
# returns 503 on /v1/embeddings/* until the operator drops models manually.
COPY scripts/export_clip.py /export/export_clip.py
RUN python /export/export_clip.py || echo "[build] WARNING: clip export failed; semantic search endpoint will be unavailable"

# Visibility checkpoint — the wildcard COPYs in stage 2 silently swallow a
# missing file, so without this echo the operator only finds out at runtime.
# Logged here so any `docker build` shows the bake status before the build
# layers are sealed. STRICT_CLIP_BAKE=1 (build arg) flips the warning into a
# hard failure for CI: any image you push to a registry should always have
# CLIP baked in.
ARG STRICT_CLIP_BAKE=0
RUN echo "[build] /export bake status:" \
    && ls -la /export/yolov8n.onnx /export/scene_classifier.onnx /export/clip_image.onnx /export/clip_text.onnx /export/clip_tokenizer.json 2>&1 || true \
    && for f in clip_image.onnx clip_text.onnx clip_tokenizer.json; do \
         if [ ! -s "/export/$f" ]; then \
           echo "[build] WARNING: /export/$f is missing or empty"; \
           if [ "$STRICT_CLIP_BAKE" = "1" ]; then echo "[build] STRICT_CLIP_BAKE=1, failing build"; exit 1; fi; \
         fi; \
       done


# ─── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    INSIGHTFACE_HOME=/app/models \
    OBJECT_MODEL_PATH=/app/models/yolov8n.onnx \
    SCENE_MODEL_PATH=/app/models/scene_classifier.onnx \
    EMBEDDING_IMAGE_MODEL_PATH=/app/models/clip_image.onnx \
    EMBEDDING_TEXT_MODEL_PATH=/app/models/clip_text.onnx \
    EMBEDDING_TEXT_TOKENIZER_PATH=/app/models/clip_tokenizer.json \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       libglib2.0-0 \
       libgl1 \
       libgomp1 \
       curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       build-essential \
       python3-dev \
    && pip install --no-cache-dir -r requirements.txt \
    && apt-get purge -y --auto-remove build-essential python3-dev \
    && rm -rf /var/lib/apt/lists/*

# Bake the YOLOv8 ONNX into a separate path (NOT /app/models, which is the
# user-managed volume mount-point). The entrypoint copies it into the volume
# only when the volume doesn't already contain a model — preserving any
# user-supplied override.
COPY --from=yolo-export /export/yolov8n.onnx /opt/ml-models/yolov8n.onnx
# Same approach for the Places365 scene classifier. Wildcard so the COPY
# silently no-ops when the export step in stage 1 was unable to fetch the
# upstream weights — letting the rest of the runtime image build cleanly.
COPY --from=yolo-export /export/scene_classifier.onn[x] /opt/ml-models/scene_classifier.onnx
# M-CLIP image + text encoders and tokenizer. Wildcards keep the build green
# if export_clip.py failed (e.g. HuggingFace unreachable from the build host).
COPY --from=yolo-export /export/clip_image.onn[x] /opt/ml-models/clip_image.onnx
COPY --from=yolo-export /export/clip_text.onn[x] /opt/ml-models/clip_text.onnx
COPY --from=yolo-export /export/clip_tokenizer.jso[n] /opt/ml-models/clip_tokenizer.json

COPY app /app/app
COPY entrypoint.sh /usr/local/bin/photonne-ml-entrypoint.sh
# Strip a possible BOM and CRLF line endings so the shebang resolves on Linux
# even when the file was checked out on Windows with autocrlf=true. The
# .gitattributes covers fresh clones; this is the belt-and-braces.
RUN sed -i '1s/^\xEF\xBB\xBF//' /usr/local/bin/photonne-ml-entrypoint.sh \
 && sed -i 's/\r$//' /usr/local/bin/photonne-ml-entrypoint.sh \
 && chmod +x /usr/local/bin/photonne-ml-entrypoint.sh

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -fsS http://localhost:8000/health | grep -q '"status":"ready"' || exit 1

ENTRYPOINT ["/usr/local/bin/photonne-ml-entrypoint.sh"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
