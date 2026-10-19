"""Voice activity detection with Silero VAD (ONNX).

Answers one question for the video summaries: when is someone speaking? A cut
placed inside one of these stretches cuts a sentence in half. Loudness alone
can't tell a voice from wind, music or traffic; this model can.

The API extracts the audio track as 16 kHz mono 16-bit WAV into the shared
volume and passes the path, the same way images are passed: no audio bytes
travel over HTTP, and decoding containers (MP4, MOV) stays in the API's ffmpeg.
"""

import logging
import os
import time
import wave
from typing import List, Optional, Tuple

import numpy as np
import onnxruntime as ort

from .config import settings
from .embedding_download import ensure_file
from .onnx_providers import build_providers

log = logging.getLogger("photonne.ml.voice")

SAMPLE_RATE = 16000
# Silero VAD v5 at 16 kHz: 512 new samples per step (32 ms), plus the last 64
# samples of the previous step as context.
CHUNK = 512
CONTEXT = 64


class AudioReadError(Exception):
    pass


def read_wav(path: str, max_seconds: float) -> np.ndarray:
    """Samples of a 16 kHz mono 16-bit PCM WAV as float32 in [-1, 1]."""
    try:
        with wave.open(path, "rb") as w:
            if w.getframerate() != SAMPLE_RATE or w.getnchannels() != 1 or w.getsampwidth() != 2:
                raise AudioReadError(
                    f"expected 16 kHz mono 16-bit PCM, got {w.getframerate()} Hz, "
                    f"{w.getnchannels()} channel(s), {8 * w.getsampwidth()}-bit"
                )
            frames = min(w.getnframes(), int(max_seconds * SAMPLE_RATE))
            raw = w.readframes(frames)
    except (OSError, EOFError, wave.Error) as e:
        raise AudioReadError(str(e)) from e
    return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0


def speech_segments(
    probabilities: np.ndarray,
    step_seconds: float = CHUNK / SAMPLE_RATE,
    threshold: float = 0.5,
    min_speech: float = 0.25,
    min_silence: float = 0.35,
    pad: float = 0.1,
    duration: Optional[float] = None,
) -> List[Tuple[float, float]]:
    """Turns per-step speech probabilities into [start, end] stretches.

    Hysteresis like Silero's own helper: speech starts above ``threshold`` and
    only ends after ``min_silence`` below ``threshold - 0.15``, so the pauses
    between words don't split a sentence. Stretches shorter than
    ``min_speech`` are dropped (a cough, a clap), and each is padded a little
    so its first and last syllables aren't clipped.
    """
    end_threshold = threshold - 0.15
    total = duration if duration is not None else len(probabilities) * step_seconds
    segments: List[Tuple[float, float]] = []
    start: Optional[float] = None
    quiet_since: Optional[float] = None

    for i, p in enumerate(probabilities):
        t = i * step_seconds
        if start is None:
            if p >= threshold:
                start = t
                quiet_since = None
            continue
        if p < end_threshold:
            if quiet_since is None:
                quiet_since = t
            elif t - quiet_since >= min_silence:
                segments.append((start, quiet_since))
                start = None
                quiet_since = None
        else:
            quiet_since = None

    if start is not None:
        segments.append((start, quiet_since if quiet_since is not None else total))

    padded: List[Tuple[float, float]] = []
    for s, e in segments:
        if e - s < min_speech:
            continue
        s, e = max(0.0, s - pad), min(total, e + pad)
        if padded and s <= padded[-1][1]:
            padded[-1] = (padded[-1][0], e)
        else:
            padded.append((s, e))
    return [(round(s, 3), round(e, 3)) for s, e in padded]


class VoiceDetector:
    def __init__(self) -> None:
        self._session: Optional[ort.InferenceSession] = None
        self._load_error: Optional[str] = None
        self._spec: str = settings.voice.providers

    def load(self, providers: Optional[str] = None) -> None:
        if not settings.voice.enabled:
            log.info("Voice detection disabled by config")
            return
        spec = providers if providers is not None else settings.voice.providers
        try:
            path = settings.voice.model_path
            if not os.path.isfile(path):
                os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
                ensure_file(path, settings.voice.model_url, "Silero VAD", min_size_bytes=100_000)
            names, opts = build_providers(spec)
            # Tiny recurrent model: one thread per call is faster than the
            # default thread pool's overhead.
            so = ort.SessionOptions()
            so.intra_op_num_threads = 1
            so.inter_op_num_threads = 1
            self._session = ort.InferenceSession(path, sess_options=so, providers=names, provider_options=opts)
            self._spec = spec
            self._load_error = None
            log.info("Voice detector loaded: model=%s providers=%s", path, names)
        except Exception as e:
            self._load_error = f"{type(e).__name__}: {e}"
            raise

    @property
    def is_loaded(self) -> bool:
        return self._session is not None

    @property
    def load_error(self) -> Optional[str]:
        return self._load_error

    @property
    def providers(self) -> str:
        return self._spec

    def probabilities(self, samples: np.ndarray) -> np.ndarray:
        """Speech probability for each 32 ms step of ``samples``."""
        if self._session is None:
            raise RuntimeError("VoiceDetector not loaded")
        steps = len(samples) // CHUNK
        state = np.zeros((2, 1, 128), dtype=np.float32)
        context = np.zeros((1, CONTEXT), dtype=np.float32)
        sr = np.array(SAMPLE_RATE, dtype=np.int64)
        out = np.empty(steps, dtype=np.float32)
        for i in range(steps):
            chunk = samples[i * CHUNK:(i + 1) * CHUNK].reshape(1, CHUNK)
            x = np.concatenate([context, chunk], axis=1)
            prob, state = self._session.run(None, {"input": x, "state": state, "sr": sr})
            out[i] = float(prob[0][0])
            context = chunk[:, -CONTEXT:]
        return out

    def detect(self, samples: np.ndarray) -> Tuple[List[Tuple[float, float]], int]:
        start = time.perf_counter()
        probs = self.probabilities(samples)
        segments = speech_segments(probs, duration=len(samples) / SAMPLE_RATE)
        return segments, int((time.perf_counter() - start) * 1000)


detector = VoiceDetector()
