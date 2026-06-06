#!/bin/sh
# Photonne API entrypoint.
#
# Self-hosted volumes routinely arrive owned by whatever uid the NAS or a
# previous deployment used, which makes the in-container `app` user unable
# to create folders (e.g. MobileBackup/<device>) and every upload fails
# with UnauthorizedAccessException. Instead of asking each user to chown
# by hand, the container starts as root, makes its own data directories
# usable, then drops privileges before running the server.
#
# Knobs (all optional):
#   PUID / PGID            run the server as this uid/gid (NAS convention,
#                          default 1654 = the image's `app` user).
#   PHOTONNE_SKIP_CHOWN=1  skip the ownership pass (for users who manage
#                          permissions themselves on very large libraries).
set -e

PUID="${PUID:-1654}"
PGID="${PGID:-1654}"
DATA_DIRS="/data/assets /data/thumbnails"

if [ "$(id -u)" = "0" ]; then
    # Remap the app user to the requested ids so files created by the
    # server match what the host/NAS expects.
    if [ "$PGID" != "$(id -g app)" ]; then
        groupmod -o -g "$PGID" app
    fi
    if [ "$PUID" != "$(id -u app)" ]; then
        usermod -o -u "$PUID" app
        chown -R app:app /home/app
    fi

    mkdir -p $DATA_DIRS

    if [ "${PHOTONNE_SKIP_CHOWN:-0}" != "1" ]; then
        # Only touch entries with the wrong owner: a no-op on healthy
        # volumes, a one-time fix on broken ones. Errors are tolerated so
        # an odd read-only mount cannot prevent startup.
        find $DATA_DIRS \( ! -user app -o ! -group app \) \
            -exec chown app:app {} + 2>/dev/null || true
    fi

    exec setpriv --reuid app --regid app --init-groups dotnet Photonne.Server.Api.dll "$@"
fi

# Container was started with an explicit non-root --user: trust the
# operator's setup and just run.
exec dotnet Photonne.Server.Api.dll "$@"
