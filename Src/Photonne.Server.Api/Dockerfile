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
COPY ["Src/Photonne.Server.Api/Photonne.Server.Api.csproj", "Src/Photonne.Server.Api/"]
COPY ["Src/Photonne.Client.Web/Photonne.Client.Web.csproj", "Src/Photonne.Client.Web/"]
RUN dotnet restore "Src/Photonne.Server.Api/Photonne.Server.Api.csproj"
COPY . .
WORKDIR "/src/Src/Photonne.Server.Api"
RUN dotnet publish "Photonne.Server.Api.csproj" -c $BUILD_CONFIGURATION -o /app/publish /p:UseAppHost=false /p:PublishReadyToRun=false -m:1

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Photonne.Server.Api.dll"]
