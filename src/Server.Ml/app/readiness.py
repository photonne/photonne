from typing import Any, Dict


def overall_status(components: Dict[str, Dict[str, Any]]) -> str:
    """"ready" once every enabled, non-optional component has loaded.

    An optional component (voice detection: the video summaries fall back to
    loudness without it) never holds readiness back, so a model that couldn't
    be fetched doesn't keep the container unhealthy — and the API, which waits
    on this healthcheck, from starting.
    """
    required = [v for v in components.values() if v.get("enabled") and not v.get("optional")]
    return "ready" if all(v.get("loaded") for v in required) else "loading"
