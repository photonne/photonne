"""The per-call log line replaces uvicorn's access log, so it has to carry at
least what that did (route and status) plus the two things it didn't: how long
the call took, and which file it read."""

from app import request_log


def test_line_has_route_status_and_duration():
    line = request_log.describe_call("POST", "/v1/faces/detect", 200, 87)

    assert line == "POST /v1/faces/detect -> 200 in 87 ms"


def test_line_names_the_asset_and_the_file_it_read():
    # A thumbnail and an original are the difference between 300 ms and 6 s
    # of OCR on the same photo; the line has to show which one it was.
    line = request_log.describe_call(
        "POST",
        "/v1/text/detect",
        200,
        6104,
        asset_id="3f2a",
        image_path="/data/assets/users/marc/2024/IMG_0042.HEIC",
    )

    assert "asset=3f2a" in line
    assert "file=2024/IMG_0042.HEIC" in line
    assert "/data/assets" not in line


def test_thumbnail_paths_keep_the_asset_folder():
    line = request_log.describe_call(
        "POST",
        "/v1/scenes/classify",
        200,
        18,
        image_path="/data/thumbnails/f7ba959e-2e72-4ecd-94b6-96361b3a8ab5/large.jpg",
    )

    assert "file=f7ba959e-2e72-4ecd-94b6-96361b3a8ab5/large.jpg" in line


def test_windows_separators_are_normalized():
    line = request_log.describe_call(
        "POST", "/v1/objects/detect", 200, 5, image_path="C:\\thumbs\\abc\\large.webp"
    )

    assert "file=abc/large.webp" in line


def test_request_fields_tolerate_bodies_that_are_not_ours():
    assert request_log._request_fields(b"") == (None, None)
    assert request_log._request_fields(b"not json") == (None, None)
    assert request_log._request_fields(b"[1, 2]") == (None, None)
    assert request_log._request_fields(b'{"asset_id": "a1", "image_path": "/x/y.jpg"}') == ("a1", "/x/y.jpg")
