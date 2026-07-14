FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS base
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libgdiplus \
    libc6-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# No USER directive: the entrypoint starts as root to fix data-volume
# ownership (PUID/PGID remap + chown of /data/*), then drops to `app`
# via setpriv before launching the server.
WORKDIR /app
EXPOSE 8080

# Liveness check used by the Docker engine + compose `depends_on:
# service_healthy`. `start-period` is generous because the first boot also
# triggers EF migrations and the admin-bootstrap pass.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/health | grep -q '"status":"ready"' || exit 1

# ─── GeoNames extract ─────────────────────────────────────────────────────────
# Bakes the offline reverse-geocoding dataset (cities500: every populated place
# with 500+ inhabitants) into the image. Trimmed to the six columns the lookup
# needs — id, name, lat, lon, country, population — which turns a 30 MB dump into
# a ~3 MB gzip.
#
# Data © GeoNames, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).
# Redistributing it inside this image is allowed *because* we attribute it; the
# attribution ships in /api/version and the app's About screen. Do not remove it.
#
# Baked, not downloaded at runtime: geocoding must work on an air-gapped NAS, and
# per-photo calls to a public geocoding API would rate-limit a first index into
# oblivion — besides handing someone else's server every user's coordinates.
#
# `|| echo` keeps the build green when download.geonames.org is unreachable, the
# same tolerance the ML image's model bake uses. The runtime degrades to no place
# names at all; nothing else breaks.
FROM alpine:3.20 AS geodata
RUN apk add --no-cache curl unzip gzip
WORKDIR /opt/geo
# Column map (cities500.txt is 19 tab-separated fields):
#   $1 geonameid  $2 name  $5 lat  $6 lon  $8 feature code  $9 country  $15 population
#
# PPLX is "section of populated place" — a neighbourhood. Left in, a photo at the
# Sagrada Família resolves to "Sagrada Família" (105 m away) instead of
# "Barcelona", because the neighbourhood centroid is nearer than the city's. That
# reads as a bug in a memory title and would be absurd in a trip name. PPLQ / PPLW
# / PPLH are abandoned, destroyed and historical places: not where anyone took a
# photo this decade.
RUN set -eux; \
    if curl -fsSL --retry 3 --max-time 300 -o cities500.zip \
         https://download.geonames.org/export/dump/cities500.zip; then \
      unzip -p cities500.zip cities500.txt \
        | awk -F'\t' 'BEGIN{OFS="\t"} \
            $5 != "" && $6 != "" && $8 != "PPLX" && $8 != "PPLQ" && $8 != "PPLW" && $8 != "PPLH" \
            { print $1, $2, $5, $6, $9, $15 }' \
        | gzip -9 > cities.tsv.gz; \
      echo "[build] GeoNames baked: $(gzip -dc cities.tsv.gz | wc -l) cities, $(du -h cities.tsv.gz | cut -f1)"; \
    else \
      echo "[build] WARNING: GeoNames download failed; place names will be unavailable"; \
    fi; \
    touch cities.tsv.gz

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS publish
ARG BUILD_CONFIGURATION=Release
WORKDIR /src
COPY ["Src/Photonne.Server.Api/Photonne.Server.Api.csproj", "Src/Photonne.Server.Api/"]
COPY ["Src/Photonne.Client.Web/Photonne.Client.Web.csproj", "Src/Photonne.Client.Web/"]
RUN dotnet restore "Src/Photonne.Server.Api/Photonne.Server.Api.csproj"
COPY . .
WORKDIR "/src/Src/Photonne.Server.Api"
RUN dotnet publish "Photonne.Server.Api.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false /p:PublishReadyToRun=false -m:1

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
COPY --from=geodata /opt/geo/cities.tsv.gz /opt/geo/cities.tsv.gz
COPY Src/Photonne.Server.Api/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
