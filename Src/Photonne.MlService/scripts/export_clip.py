"""Build-time export of M-CLIP (ViT-B/32 multilingual) to ONNX.

Run inside the Docker `yolo-export` build stage where torch is already
available. Downloads the upstream sentence-transformers package
``clip-ViT-B-32-multilingual-v1`` (text tower) plus the OpenAI ViT-B/32 image
tower the M-CLIP authors aligned to, then exports both to ONNX:

  /export/clip_image.onnx       — image encoder, input (1, 3, 224, 224)
  /export/clip_text.onnx        — text encoder, inputs (input_ids, attention_mask)
  /export/clip_tokenizer.json   — XLM-R tokenizer (HuggingFace tokenizers format)

The two encoders share the same 512-dim vector space, so cosine similarity
between text and image embeddings is meaningful across the 50+ languages the
text tower was distilled on (English and Spanish included).

Tolerant by design: any failure exits non-zero and the Dockerfile's ``|| echo``
clause logs a warning. The runtime container then surfaces 503s on
``/v1/embeddings/*`` until the operator drops models in ``/app/models/``.
"""
from __future__ import annotations

import os
import sys
import urllib.error
import urllib.request

import torch


_CACHE_DIR = "/tmp/clip-cache"
_OUT_DIR = "/export"

_IMAGE_OUT = os.path.join(_OUT_DIR, "clip_image.onnx")
_TEXT_OUT = os.path.join(_OUT_DIR, "clip_text.onnx")
_TOKENIZER_OUT = os.path.join(_OUT_DIR, "clip_tokenizer.json")


def _export_image_tower() -> None:
    """OpenAI CLIP ViT-B/32 image tower → ONNX. Uses HuggingFace's
    ``openai/clip-vit-base-patch32`` because the weights match the image
    side of M-CLIP's aligned space."""
    print("[clip-export] loading image tower (openai/clip-vit-base-patch32)", flush=True)
    from transformers import CLIPVisionModelWithProjection  # type: ignore

    # attn_implementation="eager" forces the textbook softmax-attention path.
    # The default "sdpa" wraps F.scaled_dot_product_attention, which torch 2.4's
    # ONNX legacy exporter mis-translates (a Python-float scale is passed where
    # a tensor is expected, raising TypeError in symbolic_opset14). The math is
    # identical; only the build-time tracing changes.
    model = CLIPVisionModelWithProjection.from_pretrained(
        "openai/clip-vit-base-patch32",
        cache_dir=_CACHE_DIR,
        attn_implementation="eager",
    )
    model.eval()

    class ImageWrapper(torch.nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, pixel_values):
            return self.m(pixel_values=pixel_values).image_embeds

    wrapped = ImageWrapper(model)
    dummy = torch.randn(1, 3, 224, 224)

    print(f"[clip-export] exporting image tower to {_IMAGE_OUT}", flush=True)
    torch.onnx.export(
        wrapped,
        dummy,
        _IMAGE_OUT,
        opset_version=14,
        input_names=["pixel_values"],
        output_names=["image_embeds"],
        dynamic_axes={"pixel_values": {0: "batch"}, "image_embeds": {0: "batch"}},
    )
    print(f"[clip-export] image tower done ({os.path.getsize(_IMAGE_OUT)} bytes)", flush=True)


def _export_text_tower() -> None:
    """M-CLIP text tower → ONNX. The tower is a distilled XLM-Roberta with an
    optional linear projection that aligns it with the OpenAI image encoder's
    vector space.

    We bypass sentence-transformers' Sequential/dict-based forward (which
    ONNX traces poorly — feature-dict mutation confuses the tracer) by
    reaching into the underlying XLM-Roberta + Dense projection and
    re-implementing the mean pooling inline. Same math, ONNX-friendly graph.
    """
    print("[clip-export] loading text tower (sentence-transformers/clip-ViT-B-32-multilingual-v1)", flush=True)
    from sentence_transformers import SentenceTransformer  # type: ignore

    st = SentenceTransformer(
        "sentence-transformers/clip-ViT-B-32-multilingual-v1",
        cache_folder=_CACHE_DIR,
        model_kwargs={"attn_implementation": "eager"},
    )
    st.eval()

    transformer_module = st[0]
    auto_model = transformer_module.auto_model
    tokenizer = transformer_module.tokenizer

    dense_linear = None
    dense_activation: torch.nn.Module = torch.nn.Identity()
    if len(st) > 2:
        dense_module = st[2]
        dense_linear = dense_module.linear
        dense_activation = getattr(dense_module, "activation_function", torch.nn.Identity())

    class CleanTextWrapper(torch.nn.Module):
        def __init__(self, auto_model, dense_linear, dense_activation):
            super().__init__()
            self.auto_model = auto_model
            self.dense_linear = dense_linear
            self.dense_activation = dense_activation

        def forward(self, input_ids, attention_mask):
            outputs = self.auto_model(
                input_ids=input_ids, attention_mask=attention_mask, return_dict=False
            )
            token_embeddings = outputs[0]
            mask = attention_mask.unsqueeze(-1).float()
            sum_emb = (token_embeddings * mask).sum(dim=1)
            sum_mask = mask.sum(dim=1).clamp(min=1e-9)
            pooled = sum_emb / sum_mask
            if self.dense_linear is not None:
                pooled = self.dense_activation(self.dense_linear(pooled))
            return pooled

    wrapped = CleanTextWrapper(auto_model, dense_linear, dense_activation).eval()
    dummy_ids = torch.zeros((1, 16), dtype=torch.long)
    dummy_mask = torch.ones((1, 16), dtype=torch.long)

    print(f"[clip-export] exporting text tower to {_TEXT_OUT}", flush=True)
    torch.onnx.export(
        wrapped,
        (dummy_ids, dummy_mask),
        _TEXT_OUT,
        opset_version=14,
        input_names=["input_ids", "attention_mask"],
        output_names=["text_embeds"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "text_embeds": {0: "batch"},
        },
    )
    print(f"[clip-export] text tower done ({os.path.getsize(_TEXT_OUT)} bytes)", flush=True)

    # Save the tokenizer in the standalone HuggingFace ``tokenizers`` JSON
    # format. This avoids dragging the full ``transformers`` package into the
    # runtime image (we only need the fast tokenizer at request time).
    backend = getattr(tokenizer, "backend_tokenizer", None)
    if backend is None:
        raise RuntimeError(
            "Expected a fast tokenizer with .backend_tokenizer; got "
            f"{type(tokenizer).__name__}. Re-pin sentence-transformers if needed."
        )
    backend.save(_TOKENIZER_OUT)
    print(f"[clip-export] tokenizer saved to {_TOKENIZER_OUT}", flush=True)


def main() -> int:
    os.makedirs(_OUT_DIR, exist_ok=True)
    os.makedirs(_CACHE_DIR, exist_ok=True)
    try:
        _export_image_tower()
        _export_text_tower()
    except (urllib.error.URLError, urllib.error.HTTPError, OSError, RuntimeError, ImportError) as e:
        print(f"[clip-export] FATAL: {e}", file=sys.stderr, flush=True)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
