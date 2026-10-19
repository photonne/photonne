"""Voice activity: turning per-step probabilities into speech stretches, and
reading the WAV the API extracts. The model itself is exercised by
test_voice_model.py when the ONNX file is available."""

import os
import wave

import numpy as np
import pytest

from app.voice_detector import AudioReadError, read_wav, speech_segments

STEP = 0.032


def probs(pattern):
    """Probabilities from (seconds, value) runs."""
    out = []
    for seconds, value in pattern:
        out.extend([value] * int(round(seconds / STEP)))
    return np.array(out, dtype=np.float32)


def test_a_sentence_with_short_pauses_stays_one_stretch():
    # Two seconds of silence, 3 s of talking with a 0.2 s breath, silence.
    p = probs([(2, 0.05), (1.5, 0.9), (0.2, 0.1), (1.5, 0.9), (2, 0.05)])

    segments = speech_segments(p, pad=0)

    assert len(segments) == 1
    start, end = segments[0]
    assert start == pytest.approx(2, abs=0.05)
    assert end == pytest.approx(5.2, abs=0.05)


def test_a_real_pause_splits_sentences():
    p = probs([(1, 0.9), (1, 0.05), (1, 0.9)])

    assert len(speech_segments(p, pad=0)) == 2


def test_a_cough_is_not_speech():
    p = probs([(2, 0.05), (0.1, 0.95), (2, 0.05)])

    assert speech_segments(p) == []


def test_stretches_are_padded_within_the_audio():
    p = probs([(1, 0.9), (2, 0.05)])

    start, end = speech_segments(p, pad=0.1)[0]

    assert start == 0
    assert end == pytest.approx(1.1, abs=0.05)


def test_talking_until_the_end_closes_at_the_end():
    p = probs([(1, 0.05), (2, 0.9)])

    assert speech_segments(p, pad=0, duration=3.0)[0][1] == pytest.approx(3.0)


def write_wav(path, rate=16000, channels=1, seconds=1.0):
    samples = (np.sin(np.linspace(0, 440 * 2 * np.pi * seconds, int(rate * seconds))) * 10000).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(np.repeat(samples, channels).tobytes())


def test_reads_16k_mono_and_caps_the_length(tmp_path):
    path = tmp_path / "a.wav"
    write_wav(path, seconds=3)

    samples = read_wav(str(path), max_seconds=2)

    assert samples.dtype == np.float32
    assert len(samples) == 32000
    assert np.abs(samples).max() <= 1


@pytest.mark.parametrize("rate,channels", [(44100, 1), (16000, 2)])
def test_rejects_other_formats(tmp_path, rate, channels):
    path = tmp_path / "b.wav"
    write_wav(path, rate=rate, channels=channels)

    with pytest.raises(AudioReadError):
        read_wav(str(path), max_seconds=10)


def test_rejects_a_file_that_is_not_audio(tmp_path):
    path = tmp_path / "c.wav"
    path.write_bytes(b"not a wav")

    with pytest.raises(AudioReadError):
        read_wav(str(path), max_seconds=10)
