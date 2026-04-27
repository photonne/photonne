from typing import List, Optional
from pydantic import BaseModel, Field


class DetectRequest(BaseModel):
    image_path: str = Field(..., description="Absolute path to the image inside the container")
    asset_id: Optional[str] = Field(None, description="Caller-supplied asset id, echoed in response")


class DetectedFace(BaseModel):
    bbox: List[float] = Field(..., description="[x, y, w, h] normalized to [0,1] over image dimensions")
    det_score: float
    embedding: List[float] = Field(..., description="ArcFace 512-d L2-normalized embedding")
    landmarks_5: Optional[List[List[float]]] = Field(
        None, description="5 facial landmarks (eyes, nose, mouth corners) in absolute pixel coords"
    )


class DetectResponse(BaseModel):
    asset_id: Optional[str] = None
    faces: List[DetectedFace]
    image_size: List[int] = Field(..., description="[width, height] in pixels")
    elapsed_ms: int


class HealthResponse(BaseModel):
    status: str
    model: str
    providers: List[str]
