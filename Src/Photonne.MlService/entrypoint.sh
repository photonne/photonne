#!/bin/sh
# Seed baked-in ONNX models into the persisted ml_models volume on first
# launch (fresh install) or after upgrading from an image that didn't ship
# them. User-supplied models at the same paths always win because we never
# overwrite existing files here.
set -e

TARGET_DIR=/app/models
mkdir -p "$TARGET_DIR"

seed_model() {
    src="$1"
    dst="$2"
    label="$3"
    if [ -f "$src" ] && [ ! -f "$dst" ]; then
        cp "$src" "$dst"
        echo "[entrypoint] Seeded $label into $dst"
    fi
}

seed_model /opt/ml-models/yolov8n.onnx "$TARGET_DIR/yolov8n.onnx" "YOLOv8 ONNX"
seed_model /opt/ml-models/scene_classifier.onnx "$TARGET_DIR/scene_classifier.onnx" "Places365 ResNet18 ONNX"

exec "$@"
