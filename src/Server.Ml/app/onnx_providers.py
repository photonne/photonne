"""ONNX Runtime execution-provider helpers.

Central place that turns a provider spec string (e.g.
``"CUDAExecutionProvider,CPUExecutionProvider"``) into the
``(providers, provider_options)`` pair ONNX Runtime — and InsightFace — expect,
attaching memory-frugal CUDA options so the ~10 models this service loads can
coexist on a single GPU.

Why the CUDA options matter: ORT's CUDA EP defaults to an EXHAUSTIVE cuDNN
convolution-algorithm search (which reserves a large scratch workspace) and a
next-power-of-two arena that grabs VRAM aggressively and never gives it back.
With every task (faces + objects + scenes + embeddings + OCR) resident on one
GPU that overflows into CUDA "out of memory" / cuDNN "internal error" the first
time a fresh model reserves its workspace. HEURISTIC search + a
same-as-requested arena keep each session's footprint small enough to share the
card. ``CUDA_GPU_MEM_LIMIT_MB`` optionally hard-caps a session's arena for the
truly tight cards.
"""
import os
from typing import Dict, List, Tuple

CUDA_PROVIDER = "CUDAExecutionProvider"


def _cuda_options() -> Dict[str, object]:
    opts: Dict[str, object] = {
        # Grow the arena by exactly what each allocation needs instead of the
        # default next-power-of-two doubling — critical when many sessions share
        # one GPU, otherwise the first few models balloon and starve the rest.
        "arena_extend_strategy": "kSameAsRequested",
        # Skip the exhaustive conv-algo benchmark (and its big scratch buffer);
        # HEURISTIC picks a good algorithm without the VRAM spike that trips the
        # cudnnFindConvolutionForwardAlgorithmEx OOM.
        "cudnn_conv_algo_search": "HEURISTIC",
        "do_copy_in_default_stream": True,
    }
    limit_mb = os.getenv("CUDA_GPU_MEM_LIMIT_MB", "").strip()
    if limit_mb.isdigit() and int(limit_mb) > 0:
        opts["gpu_mem_limit"] = int(limit_mb) * 1024 * 1024
    return opts


def parse_providers(spec: str) -> List[str]:
    """Split a comma-separated provider spec into an ordered name list."""
    return [p.strip() for p in (spec or "").split(",") if p.strip()]


def build_providers(spec: str) -> Tuple[List[str], List[Dict[str, object]]]:
    """Return ``(providers, provider_options)`` for ``ort.InferenceSession``.

    ``provider_options`` is a list parallel to ``providers``: the CUDA provider
    gets the memory-frugal options above, every other provider an empty dict.
    Falls back to CPU when the spec is empty so a mis-set env never yields an
    empty provider list (which ORT rejects).
    """
    names = parse_providers(spec) or ["CPUExecutionProvider"]
    opts = [_cuda_options() if n == CUDA_PROVIDER else {} for n in names]
    return names, opts


def uses_cuda(spec: str) -> bool:
    return CUDA_PROVIDER in parse_providers(spec)
