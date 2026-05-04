"""Host-side CLIP export — generates the 3 ONNX/JSON files that the ML
service expects, writing them to a directory you choose.

When the in-Docker build can't reach HuggingFace (or its ONNX export crashes),
run this on your host machine instead. The output goes straight to the
photonne ml_models volume's mount path so the running container picks it up
on the next start.

Quick start (Windows, with Python 3.11+ installed):

    python -m venv .venv-clip
    .venv-clip\\Scripts\\activate
    pip install --extra-index-url https://download.pytorch.org/whl/cpu \
        torch==2.4.1+cpu torchvision==0.19.1+cpu \
        transformers==4.45.2 sentence-transformers==3.1.1 tokenizers==0.20.0
    python Src/Photonne.MlService/scripts/export_clip_host.py --out-dir <volume-path>

Then `docker compose restart photonne-ml`.

To find the volume path, run `docker volume inspect photonne_ml_models` and
copy the "Mountpoint" field. Or use a bind mount in docker-compose.override.yml
to make this trivial.
"""
from __future__ import annotations

import argparse
import os
import sys


def _export_image_tower(out_path: str) -> None:
    print(f"[clip-export] loading image tower (openai/clip-vit-base-patch32)", flush=True)
    import torch
    from transformers import CLIPVisionModelWithProjection

    model = CLIPVisionModelWithProjection.from_pretrained("openai/clip-vit-base-patch32")
    model.eval()

    class ImageWrapper(torch.nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, pixel_values):
            return self.m(pixel_values=pixel_values).image_embeds

    wrapped = ImageWrapper(model)
    dummy = torch.randn(1, 3, 224, 224)

    print(f"[clip-export] exporting image tower to {out_path}", flush=True)
    torch.onnx.export(
        wrapped,
        dummy,
        out_path,
        opset_version=14,
        input_names=["pixel_values"],
        output_names=["image_embeds"],
        dynamic_axes={"pixel_values": {0: "batch"}, "image_embeds": {0: "batch"}},
    )
    print(f"[clip-export] image tower done ({os.path.getsize(out_path)} bytes)", flush=True)


def _export_text_tower(model_out: str, tokenizer_out: str) -> None:
    """Robust text-tower export.

    The sentence-transformers Sequential pipeline uses dict-based forwards
    that ONNX traces poorly. We bypass that by reaching into the underlying
    XLM-Roberta model + the Dense projection and re-implementing the mean
    pooling inline — same math, ONNX-friendly graph.
    """
    print(f"[clip-export] loading text tower (sentence-transformers/clip-ViT-B-32-multilingual-v1)", flush=True)
    import torch
    from sentence_transformers import SentenceTransformer

    st = SentenceTransformer("sentence-transformers/clip-ViT-B-32-multilingual-v1")
    st.eval()

    transformer_module = st[0]
    auto_model = transformer_module.auto_model
    tokenizer = transformer_module.tokenizer

    # Optional Dense projection (last module). If absent, the pooled output
    # is already the alignment vector.
    dense_linear = None
    dense_activation = torch.nn.Identity()
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

            # Mean pooling with attention mask — matches sentence-transformers
            # Pooling.forward when pooling_mode_mean_tokens=True (the default
            # for clip-ViT-B-32-multilingual-v1).
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

    print(f"[clip-export] exporting text tower to {model_out}", flush=True)
    torch.onnx.export(
        wrapped,
        (dummy_ids, dummy_mask),
        model_out,
        opset_version=14,
        input_names=["input_ids", "attention_mask"],
        output_names=["text_embeds"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "text_embeds": {0: "batch"},
        },
    )
    print(f"[clip-export] text tower done ({os.path.getsize(model_out)} bytes)", flush=True)

    backend = getattr(tokenizer, "backend_tokenizer", None)
    if backend is None:
        raise RuntimeError(
            "Expected a fast tokenizer with .backend_tokenizer; got "
            f"{type(tokenizer).__name__}. Make sure you installed `tokenizers` "
            "and that sentence-transformers picked the fast XLM-R tokenizer."
        )
    backend.save(tokenizer_out)
    print(f"[clip-export] tokenizer saved to {tokenizer_out}", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out-dir",
        required=True,
        help="Target directory. Should be the host path of the photonne_ml_models volume.",
    )
    args = parser.parse_args()

    out_dir = os.path.abspath(args.out_dir)
    os.makedirs(out_dir, exist_ok=True)

    image_path = os.path.join(out_dir, "clip_image.onnx")
    text_path = os.path.join(out_dir, "clip_text.onnx")
    tokenizer_path = os.path.join(out_dir, "clip_tokenizer.json")

    try:
        _export_image_tower(image_path)
        _export_text_tower(text_path, tokenizer_path)
    except Exception as e:
        print(f"[clip-export] FATAL: {type(e).__name__}: {e}", file=sys.stderr, flush=True)
        return 1

    print(f"\n[clip-export] DONE. Files written to {out_dir}:", flush=True)
    for p in (image_path, text_path, tokenizer_path):
        print(f"  {os.path.basename(p)}: {os.path.getsize(p):,} bytes", flush=True)
    print("\nRestart the ML container so it picks them up:", flush=True)
    print("  docker compose restart photonne-ml", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
