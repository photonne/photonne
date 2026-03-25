FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS base
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libgdiplus \
    libc6-dev \
    && rm -rf /var/lib/apt/lists/*

USER $APP_UID
WORKDIR /app
EXPOSE 8080

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS publish
ARG BUILD_CONFIGURATION=Release
WORKDIR /src
COPY ["Src/PhotoHub.Server.Api/PhotoHub.Server.Api.csproj", "Src/PhotoHub.Server.Api/"]
COPY ["Src/PhotoHub.Client.Web/PhotoHub.Client.Web.csproj", "Src/PhotoHub.Client.Web/"]
RUN dotnet restore "Src/PhotoHub.Server.Api/PhotoHub.Server.Api.csproj"
COPY . .
WORKDIR "/src/Src/PhotoHub.Server.Api"
RUN dotnet publish "PhotoHub.Server.Api.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false /p:PublishReadyToRun=false -m:1

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "PhotoHub.Server.Api.dll"]
