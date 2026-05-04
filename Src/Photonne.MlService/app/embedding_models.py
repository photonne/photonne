from typing import List, Optional

from pydantic import BaseModel, Field


class EmbedImageRequest(BaseModel):
    image_path: str = Field(..., description="Absolute path to the image inside the container")
    asset_id: Optional[str] = Field(None, description="Caller-supplied asset id, echoed in response")


class EmbedTextRequest(BaseModel):
    # Free-form natural-language query in any language supported by the
    # multilingual text encoder. The image and text encoders share the same
    # vector space so cosine similarity between query/image embeddings is
    # meaningful.
    text: str = Field(..., min_length=1, max_length=2000)


class EmbeddingResponse(BaseModel):
    asset_id: Optional[str] = None
    # L2-normalized embedding. The .NET side stores the vector as-is in
    # pgvector and uses cosine distance (<=>) which is equivalent to
    # 1 - dot(a, b) for unit-length vectors.
    embedding: List[float] = Field(..., description="L2-normalized embedding vector")
    dim: int = Field(..., description="Vector dimensionality")
    model: str = Field(..., description="Stable identifier of the active model")
    elapsed_ms: int
