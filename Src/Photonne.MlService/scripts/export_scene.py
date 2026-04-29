"""Build-time export of the Places365 ResNet18 scene classifier to ONNX.

Run inside the Docker `yolo-export` stage where torch + torchvision are
already installed. Downloads the upstream PyTorch checkpoint from CSAIL Vision
(or a configured mirror), loads it into a torchvision ResNet18 with 365
output classes, and exports the model to /export/scene_classifier.onnx.

The script is intentionally tolerant: if every download URL fails, it exits
non-zero so the Dockerfile's ``|| echo`` clause logs a warning instead of
aborting the whole image build. The runtime container then surfaces a 503 on
``/v1/scenes/classify`` until the operator provides a model file.
"""

from __future__ import annotations

import os
import sys
import urllib.error
import urllib.request

import torch
import torch.nn as nn
import torchvision.models as models


# Comma-separated list, in priority order. Override at build time with
# `--build-arg PLACES365_URLS=...` (would need a corresponding ARG in the
# Dockerfile if you want to expose it). The HuggingFace mirror is a community
# re-host of the same .pth.tar file.
_DEFAULT_URLS = ",".join([
    "http://places2.csail.mit.edu/models_places365/resnet18_places365.pth.tar",
    "https://huggingface.co/spaces/onnx/onnx-export/resolve/main/resnet18_places365.pth.tar",
])
_OUT = "/export/scene_classifier.onnx"
_TMP_PTH = "/tmp/resnet18_places365.pth.tar"


def _download(urls: list[str]) -> str:
    last: Exception | None = None
    for url in urls:
        if not url.strip():
            continue
        try:
            print(f"[scene-export] downloading {url}", flush=True)
            req = urllib.request.Request(url.strip(), headers={"User-Agent": "photonne-ml-build/0.2"})
            with urllib.request.urlopen(req, timeout=180) as resp, open(_TMP_PTH, "wb") as f:
                while True:
                    chunk = resp.read(64 * 1024)
                    if not chunk:
                        break
                    f.write(chunk)
            size = os.path.getsize(_TMP_PTH)
            if size < 10 * 1024 * 1024:
                raise RuntimeError(f"file too small ({size} bytes), not a valid checkpoint")
            print(f"[scene-export] downloaded {size} bytes", flush=True)
            return _TMP_PTH
        except (urllib.error.URLError, urllib.error.HTTPError, OSError, RuntimeError) as e:
            print(f"[scene-export] mirror failed: {e}", flush=True)
            last = e
            try:
                os.remove(_TMP_PTH)
            except OSError:
                pass
    raise RuntimeError(f"all mirrors failed; last error: {last}")


def main() -> int:
    urls = os.getenv("PLACES365_URLS", _DEFAULT_URLS).split(",")
    try:
        pth = _download(urls)
    except Exception as e:
        print(f"[scene-export] FATAL: {e}", file=sys.stderr, flush=True)
        return 1

    print("[scene-export] loading checkpoint", flush=True)
    # Places365 finetuned ResNet18 has the standard architecture but a 365-way
    # classifier head. weights_only=False because the checkpoint predates the
    # PyTorch 2.6 default — it's a trusted upstream file we're mirroring.
    checkpoint = torch.load(pth, map_location="cpu", weights_only=False)
    state_dict = checkpoint.get("state_dict", checkpoint)
    # Strip the `module.` prefix added by nn.DataParallel during training.
    cleaned = {k.replace("module.", ""): v for k, v in state_dict.items()}

    model = models.resnet18(num_classes=365)
    missing, unexpected = model.load_state_dict(cleaned, strict=False)
    if unexpected:
        print(f"[scene-export] WARN unexpected keys: {unexpected}", flush=True)
    if missing:
        print(f"[scene-export] WARN missing keys: {missing}", flush=True)
    model.eval()

    print(f"[scene-export] exporting to {_OUT}", flush=True)
    dummy = torch.randn(1, 3, 224, 224)
    torch.onnx.export(
        model,
        dummy,
        _OUT,
        opset_version=12,
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
    )
    out_size = os.path.getsize(_OUT)
    print(f"[scene-export] done — {_OUT} ({out_size} bytes)", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
