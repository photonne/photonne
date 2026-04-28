#!/bin/sh
# Seed the YOLOv8 ONNX model into the persisted ml_models volume on first
# launch (fresh install) or after upgrading from an image that didn't ship
# the model (where the existing volume is non-empty but lacks yolov8n.onnx).
# A user-supplied model at the same path always wins because we never
# overwrite an existing file here.
set -e

BAKED_MODEL=/opt/ml-models/yolov8n.onnx
TARGET_DIR=/app/models
TARGET_MODEL="$TARGET_DIR/yolov8n.onnx"

if [ -f "$BAKED_MODEL" ] && [ ! -f "$TARGET_MODEL" ]; then
    mkdir -p "$TARGET_DIR"
    cp "$BAKED_MODEL" "$TARGET_MODEL"
    echo "[entrypoint] Seeded YOLOv8 ONNX into $TARGET_MODEL"
fi

exec "$@"
