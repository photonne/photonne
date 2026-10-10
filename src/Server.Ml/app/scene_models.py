from typing import List, Optional

from pydantic import BaseModel, Field


class SceneClassifyRequest(BaseModel):
    image_path: str = Field(..., description="Absolute path to the image inside the container")
    asset_id: Optional[str] = Field(None, description="Caller-supplied asset id, echoed in response")


class ClassifiedScene(BaseModel):
    label: str = Field(..., description="Human-readable scene name (e.g. 'beach', 'kitchen')")
    class_id: int = Field(..., description="Model class index (0..364 for Places365)")
    score: float = Field(..., description="Softmax probability in [0,1]")
    rank: int = Field(..., description="1-based rank among the returned predictions")


class SceneClassifyResponse(BaseModel):
    asset_id: Optional[str] = None
    scenes: List[ClassifiedScene]
    image_size: List[int] = Field(..., description="[width, height] in pixels")
    elapsed_ms: int
