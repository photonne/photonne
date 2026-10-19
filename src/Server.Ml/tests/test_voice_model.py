"""Silero VAD on real audio: speech synthesised with ffmpeg's flite is found,
a tone and silence are not. Skipped unless the model file and an ffmpeg with
flite are available (the image bakes the model; set VOICE_MODEL_PATH locally)."""

import os
import shutil
import subprocess

import pytest

from app.config import settings
from app.voice_detector import VoiceDetector, read_wav

pytestmark = pytest.mark.skipif(
    not os.path.isfile(settings.voice.model_path) or shutil.which("ffmpeg") is None,
    reason="needs the Silero model at VOICE_MODEL_PATH and ffmpeg",
)


def render(tmp_path, name, *lavfi):
    """Concatenates lavfi audio sources into a 16 kHz mono WAV."""
    out = tmp_path / name
    args = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    for source in lavfi:
        args += ["-f", "lavfi", "-i", source]
    n = len(lavfi)
    graph = "".join(f"[{i}:a]aresample=16000,aformat=channel_layouts=mono[a{i}];" for i in range(n))
    graph += "".join(f"[a{i}]" for i in range(n)) + f"concat=n={n}:v=0:a=1[out]"
    args += ["-filter_complex", graph, "-map", "[out]", "-c:a", "pcm_s16le", "-ar", "16000", "-ac", "1", str(out)]
    if subprocess.run(args, capture_output=True).returncode != 0:
        pytest.skip("ffmpeg without flite")
    return str(out)


def test_finds_the_spoken_part(tmp_path):
    path = render(
        tmp_path, "speech.wav",
        "anullsrc=r=16000:cl=mono:d=3",
        "flite=text='Happy birthday to you, we are all here together tonight':voice=slt",
        "sine=f=440:d=3:sample_rate=16000",
    )
    detector = VoiceDetector()
    detector.load()

    segments, _ = detector.detect(read_wav(path, 60))

    assert segments, "speech should be found"
    first_start = segments[0][0]
    last_end = segments[-1][1]
    assert 2.5 <= first_start <= 3.5
    # Nothing in the trailing tone.
    total = len(read_wav(path, 60)) / 16000
    assert last_end <= total - 2.5
