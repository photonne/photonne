from typing import List, Optional

from pydantic import BaseModel, Field


class TextDetectRequest(BaseModel):
    image_path: str = Field(..., description="Absolute path to the image inside the container")
    asset_id: Optional[str] = Field(None, description="Caller-supplied asset id, echoed in response")


class RecognizedTextLine(BaseModel):
    text: str = Field(..., description="Recognized text content of the line")
    confidence: float = Field(..., description="Recognition confidence in [0,1]")
    # Axis-aligned bounding box of the line in normalized [0,1] coordinates
    # (x, y, w, h). The underlying detector returns a quad polygon; we collapse
    # it to the enclosing rectangle so the storage shape matches DetectedObjects.
    bbox: List[float] = Field(..., description="[x, y, w, h] normalized to [0,1] over image dimensions")
    line_index: int = Field(..., description="0-based reading order (top-to-bottom, left-to-right)")


class TextDetectResponse(BaseModel):
    asset_id: Optional[str] = None
    lines: List[RecognizedTextLine]
    full_text: str = Field(..., description="All lines joined with '\\n' in reading order")
    image_size: List[int] = Field(..., description="[width, height] in pixels")
    elapsed_ms: int
