using System.Reflection;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Admin;

public class VersionEndpoint : IEndpoint
{
    private const string LatestReleaseUrl = "https://api.github.com/repos/photonne/photonne/releases/latest";
    private const string LatestReleaseCacheKey = "version:latest-release";

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("version", GetVersion)
            .WithName("GetVersion")
            .WithDescription("Gets the current application version and checks for updates on GitHub");

        // Endpoint público: solo devuelve la versión actual (sin info de
        // updates, que sigue siendo admin) y la app más antigua que atiende.
        // Lo usan los clientes para incluir la versión del servidor en los
        // reportes de error y para avisar si servidor y app no son compatibles.
        app.MapGet("/api/version", GetPublicVersion)
            .WithTags("Version")
            .WithName("GetPublicVersion")
            .WithDescription("Returns the current server version. Public, no auth required.")
            .AllowAnonymous();

        // Última release publicada, para que los clientes que se instalan a
        // mano (escritorio) sepan si hay un instalador más nuevo. No va en
        // /api/version porque ese lo usa la sonda del login y no debe esperar
        // a GitHub; y pide sesión para que nadie use el servidor de proxy.
        app.MapGet("/api/version/latest-release", GetLatestRelease)
            .WithTags("Version")
            .WithName("GetLatestRelease")
            .WithDescription("Returns the latest published release of Photonne on GitHub.")
            .RequireAuthorization();
    }

    private static Ok<PublicVersionResponse> GetPublicVersion()
    {
        return TypedResults.Ok(new PublicVersionResponse
        {
            Version = ResolveCurrentVersion(),
            MinClientVersion = ResolveMinClientVersion()
        });
    }

    private static string? ResolveMinClientVersion() =>
        Assembly.GetExecutingAssembly()
            .GetCustomAttributes<AssemblyMetadataAttribute>()
            .FirstOrDefault(a => a.Key == "PhotonneMinClientVersion")
            ?.Value is { Length: > 0 } min ? min : null;

    private static string ResolveCurrentVersion()
    {
        var raw = Assembly.GetExecutingAssembly()
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()
            ?.InformationalVersion
            ?? typeof(VersionEndpoint).Assembly
                .GetName().Version?.ToString(3)
            ?? "desconocida";

        var plusIdx = raw.IndexOf('+', StringComparison.Ordinal);
        return plusIdx >= 0 ? raw[..plusIdx] : raw;
    }

    private static async Task<Ok<LatestReleaseResponse>> GetLatestRelease(
        [FromServices] IMemoryCache cache,
        [FromServices] IHttpClientFactory httpClientFactory,
        CancellationToken ct)
    {
        var check = await CheckLatestReleaseAsync(cache, httpClientFactory, refresh: false, ct);
        return TypedResults.Ok(new LatestReleaseResponse
        {
            LatestVersion = check.Release?.Version,
            ReleaseUrl = check.Release?.HtmlUrl
        });
    }

    private static async Task<Ok<VersionInfoResponse>> GetVersion(
        [FromServices] IMemoryCache cache,
        [FromServices] IHttpClientFactory httpClientFactory,
        [FromQuery] bool? refresh,
        CancellationToken ct)
    {
        var currentVersion = ResolveCurrentVersion();
        var check = await CheckLatestReleaseAsync(cache, httpClientFactory, refresh == true, ct);
        var release = check.Release;

        // Tres estados, no dos: un servidor desplegado desde main antes de
        // que su release exista (o una imagen local) va POR DELANTE de la
        // última release, y eso no es lo mismo que "al día".
        bool hasUpdate = false, isAhead = false;
        if (release is not null && Version.TryParse(currentVersion, out var cur) && Version.TryParse(release.Version, out var latest))
        {
            hasUpdate = latest > cur;
            isAhead = latest < cur;
        }

        return TypedResults.Ok(new VersionInfoResponse
        {
            CurrentVersion = currentVersion,
            LatestVersion = release?.Version,
            LatestReleaseUrl = release?.HtmlUrl,
            ReleaseNotes = release?.Body,
            PublishedAt = release?.PublishedAt,
            HasUpdate = hasUpdate,
            IsAhead = isAhead,
            CheckError = check.Error,
            CheckedAt = check.CheckedAt
        });
    }

    /// <summary>
    /// Consulta la última release de GitHub, cacheada una hora. Compartida por
    /// el endpoint de admin y el de clientes para no gastar el límite de 60
    /// peticiones/hora sin autenticar. Un repo sin releases no es un error.
    /// </summary>
    private static async Task<ReleaseCheck> CheckLatestReleaseAsync(
        IMemoryCache cache, IHttpClientFactory httpClientFactory, bool refresh, CancellationToken ct)
    {
        if (!refresh && cache.TryGetValue(LatestReleaseCacheKey, out ReleaseCheck? cached) && cached is not null)
            return cached;

        ReleaseCheck check;
        try
        {
            var client = httpClientFactory.CreateClient("github");
            using var response = await client.GetAsync(LatestReleaseUrl, ct);

            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync(ct);
                var release = JsonSerializer.Deserialize<GitHubRelease>(json);
                check = new ReleaseCheck(
                    release?.TagName is null
                        ? null
                        : new LatestRelease(release.TagName.TrimStart('v'), release.HtmlUrl, release.Body, release.PublishedAt),
                    null, DateTimeOffset.UtcNow);
            }
            else if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
            {
                check = new ReleaseCheck(null, null, DateTimeOffset.UtcNow);
            }
            else
            {
                check = new ReleaseCheck(null,
                    $"Error al consultar GitHub: {(int)response.StatusCode} {response.ReasonPhrase}",
                    DateTimeOffset.UtcNow);
            }
        }
        catch (Exception ex) when (ex is not OperationCanceledException || !ct.IsCancellationRequested)
        {
            check = new ReleaseCheck(null, $"No se pudo conectar con GitHub: {ex.Message}", DateTimeOffset.UtcNow);
        }

        cache.Set(LatestReleaseCacheKey, check, TimeSpan.FromHours(1));
        return check;
    }

    private sealed record LatestRelease(string Version, string? HtmlUrl, string? Body, DateTimeOffset? PublishedAt);

    private sealed record ReleaseCheck(LatestRelease? Release, string? Error, DateTimeOffset CheckedAt);

    private sealed record GitHubRelease(
        [property: JsonPropertyName("tag_name")] string? TagName,
        [property: JsonPropertyName("html_url")] string? HtmlUrl,
        [property: JsonPropertyName("body")] string? Body,
        [property: JsonPropertyName("published_at")] DateTimeOffset? PublishedAt);
}

public sealed record PublicVersionResponse
{
    public string Version { get; init; } = "";

    /// <summary>
    /// La app nativa más antigua que este servidor atiende bien; una más
    /// antigua avisa de que hay que actualizarla. Nulo si no se fijó.
    /// </summary>
    public string? MinClientVersion { get; init; }
}

public sealed record LatestReleaseResponse
{
    public string? LatestVersion { get; init; }
    public string? ReleaseUrl { get; init; }
}

public sealed record VersionInfoResponse
{
    public string CurrentVersion { get; init; } = "";
    public string? LatestVersion { get; init; }
    public string? LatestReleaseUrl { get; init; }
    public string? ReleaseNotes { get; init; }
    public DateTimeOffset? PublishedAt { get; init; }
    public bool HasUpdate { get; init; }
    /// <summary>La versión instalada es más nueva que la última release publicada.</summary>
    public bool IsAhead { get; init; }
    public string? CheckError { get; init; }
    public DateTimeOffset CheckedAt { get; init; }
}
