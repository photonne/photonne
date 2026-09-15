"""The shape the API reads to decide whether a failure is worth retrying.

These are wire-format tests. The codes and the `transient` flag are a contract
with `MlCall`/`EnrichmentFailureClassifier` on the .NET side — renaming one here
silently turns every classified failure back into "Unknown", which is the state
this whole thing exists to get out of.
"""

from app import errors


def test_body_carries_code_and_transience():
    err = errors.image_unreadable("cv2.imdecode devolvió None")
    body = err.body()

    assert err.status_code == 400
    assert body["error"]["code"] == "image_unreadable"
    assert body["error"]["transient"] is False
    assert body["error"]["detail"] == "cv2.imdecode devolvió None"


def test_body_keeps_a_top_level_detail_for_older_callers():
    # An API build that predates the structured shape reads the body as text;
    # it must not come back empty just because we added a nested object.
    body = errors.capability_disabled("el reconocimiento facial").body()

    assert body["detail"] == body["error"]["message"]
    assert "reconocimiento facial" in body["detail"]


def test_a_disabled_capability_is_not_transient():
    # 503 would be retried on status alone. Nothing changes until a human turns
    # it back on, so the verdict has to override the status.
    err = errors.capability_disabled("la detección de objetos")

    assert err.status_code == 503
    assert err.transient is False
    assert err.code == errors.CAPABILITY_DISABLED


def test_a_model_that_never_loaded_is_not_transient_and_says_why():
    err = errors.model_not_loaded("detección de objetos", "FileNotFoundError: yolov8n.onnx")

    assert err.transient is False
    assert err.code == errors.MODEL_NOT_LOADED
    assert "yolov8n.onnx" in err.body()["error"]["detail"]


def test_an_inference_crash_is_transient_and_keeps_the_exception():
    # The case that started this: the model raised and the API received an empty
    # 500. One OOM under load and a broken driver look the same from here, so it
    # stays retryable — but the text is what lets an operator tell them apart
    # when the same line appears on ten thousand assets.
    err = errors.inference_failed("reconocimiento facial", RuntimeError("CUBLAS failure 3"))

    assert err.status_code == 500
    assert err.transient is True
    assert err.code == errors.INFERENCE_FAILED
    assert err.body()["error"]["detail"] == "RuntimeError: CUBLAS failure 3"


def test_every_code_is_a_plain_token():
    # They cross the wire and get stored in a 64-char column; spaces or
    # punctuation would mean the .NET side is matching prose again.
    codes = [
        errors.CAPABILITY_DISABLED,
        errors.MODEL_NOT_LOADED,
        errors.IMAGE_UNREADABLE,
        errors.BAD_REQUEST,
        errors.INFERENCE_FAILED,
        errors.INTERNAL,
    ]

    assert len(set(codes)) == len(codes)
    for code in codes:
        assert code.replace("_", "").isalnum()
        assert code == code.lower()
        assert len(code) <= 64
