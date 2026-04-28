from app.face_models import DetectRequest, DetectedFace, DetectResponse
from app.object_models import DetectedObject, ObjectDetectRequest, ObjectDetectResponse


def test_detect_request_minimum_payload():
    req = DetectRequest(image_path="/data/assets/x.jpg")
    assert req.image_path == "/data/assets/x.jpg"
    assert req.asset_id is None


def test_detected_face_roundtrip():
    f = DetectedFace(
        bbox=[0.1, 0.2, 0.3, 0.4],
        det_score=0.95,
        embedding=[0.0] * 512,
    )
    payload = DetectResponse(
        asset_id="abc",
        faces=[f],
        image_size=[800, 600],
        elapsed_ms=42,
    )
    j = payload.model_dump()
    assert j["asset_id"] == "abc"
    assert len(j["faces"][0]["embedding"]) == 512
    assert j["image_size"] == [800, 600]


def test_object_detect_request_minimum_payload():
    req = ObjectDetectRequest(image_path="/data/assets/x.jpg")
    assert req.image_path == "/data/assets/x.jpg"
    assert req.asset_id is None


def test_detected_object_roundtrip():
    obj = DetectedObject(label="dog", class_id=16, score=0.92, bbox=[0.1, 0.2, 0.3, 0.4])
    payload = ObjectDetectResponse(
        asset_id="abc",
        objects=[obj],
        image_size=[800, 600],
        elapsed_ms=42,
    )
    j = payload.model_dump()
    assert j["asset_id"] == "abc"
    assert j["objects"][0]["label"] == "dog"
    assert j["objects"][0]["class_id"] == 16
    assert j["image_size"] == [800, 600]
