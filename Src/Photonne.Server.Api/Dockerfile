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
COPY Src/Photonne.Server.Api/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
