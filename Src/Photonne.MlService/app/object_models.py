from typing import List, Optional

from pydantic import BaseModel, Field


class ObjectDetectRequest(BaseModel):
    image_path: str = Field(..., description="Absolute path to the image inside the container")
    asset_id: Optional[str] = Field(None, description="Caller-supplied asset id, echoed in response")


class DetectedObject(BaseModel):
    label: str = Field(..., description="Human-readable class name (e.g. 'dog', 'car')")
    class_id: int = Field(..., description="Model class index")
    score: float = Field(..., description="Detection confidence in [0,1]")
    bbox: List[float] = Field(..., description="[x, y, w, h] normalized to [0,1] over image dimensions")


class ObjectDetectResponse(BaseModel):
    asset_id: Optional[str] = None
    objects: List[DetectedObject]
    image_size: List[int] = Field(..., description="[width, height] in pixels")
    elapsed_ms: int
