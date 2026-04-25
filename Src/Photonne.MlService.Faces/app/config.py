import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    model_name: str = os.getenv("MODEL_NAME", "buffalo_l")
    model_root: str = os.getenv("INSIGHTFACE_HOME", "/app/models")
    det_size: int = int(os.getenv("DET_SIZE", "640"))
    min_face_size: int = int(os.getenv("MIN_FACE_SIZE", "40"))
    min_det_score: float = float(os.getenv("MIN_DET_SCORE", "0.5"))
    max_faces: int = int(os.getenv("MAX_FACES", "50"))
    # Comma-separated list of ONNX execution providers in priority order.
    providers: str = os.getenv("ONNX_PROVIDERS", "CPUExecutionProvider")


settings = Settings()
