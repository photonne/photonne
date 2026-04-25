from app.models import DetectRequest, DetectedFace, DetectResponse


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
