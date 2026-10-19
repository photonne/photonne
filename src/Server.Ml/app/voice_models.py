from typing import List, Optional

from pydantic import BaseModel, Field


class VoiceDetectRequest(BaseModel):
    audio_path: str = Field(
        ...,
        description="Absolute path inside the container to a 16 kHz mono 16-bit PCM WAV",
    )
    asset_id: Optional[str] = Field(None, description="Caller-supplied asset id, echoed in response")


class VoiceDetectResponse(BaseModel):
    asset_id: Optional[str] = None
    segments: List[List[float]] = Field(
        ..., description="Stretches with someone speaking, as [start, end] in seconds"
    )
    duration: float = Field(..., description="Length of the audio read, in seconds")
    elapsed_ms: int
