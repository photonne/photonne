using System.Net.Http.Headers;
using System.Reflection;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Admin;

public class VersionEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("version", GetVersion)
            .WithName("GetVersion")
            .WithDescription("Gets the current application version and checks for updates on GitHub");
    }

    private static async Task<IResult> GetVersion(
        [FromServices] IMemoryCache cache,
        [FromServices] IHttpClientFactory httpClientFactory,
        CancellationToken ct)
    {
        const string cacheKey = "admin:version";
        if (cache.TryGetValue(cacheKey, out VersionInfoResponse? cached))
            return Results.Ok(cached);

        var currentVersion = Assembly.GetExecutingAssembly()
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()
            ?.InformationalVersion
            ?? typeof(VersionEndpoint).Assembly
                .GetName().Version?.ToString(3)
            ?? "desconocida";

        // Strip build metadata (e.g. "1.0.0+abc123")
        var plusIdx = currentVersion.IndexOf('+', StringComparison.Ordinal);
        if (plusIdx >= 0)
            currentVersion = currentVersion[..plusIdx];

        string? latestVersion = null;
        string? latestReleaseUrl = null;
        string? releaseNotes = null;
        DateTimeOffset? publishedAt = null;
        string? checkError = null;

        try
        {
            var client = httpClientFactory.CreateClient("github");
            using var response = await client.GetAsync(
                "https://api.github.com/repos/marccafo/Photonne/releases/latest", ct);

            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync(ct);
                var release = JsonSerializer.Deserialize<GitHubRelease>(json);
                if (release is not null)
                {
                    latestVersion = release.TagName?.TrimStart('v');
                    latestReleaseUrl = release.HtmlUrl;
                    releaseNotes = release.Body;
                    publishedAt = release.PublishedAt;
                }
            }
            else if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
            {
                checkError = "No se encontraron releases publicadas en GitHub.";
            }
            else
            {
                checkError = $"Error al consultar GitHub: {(int)response.StatusCode} {response.ReasonPhrase}";
            }
        }
        catch (Exception ex)
        {
            checkError = $"No se pudo conectar con GitHub: {ex.Message}";
        }

        bool hasUpdate = false;
        if (latestVersion is not null && Version.TryParse(currentVersion, out var cur) && Version.TryParse(latestVersion, out var latest))
            hasUpdate = latest > cur;

        var result = new VersionInfoResponse
        {
            CurrentVersion = currentVersion,
            LatestVersion = latestVersion,
            LatestReleaseUrl = latestReleaseUrl,
            ReleaseNotes = releaseNotes,
            PublishedAt = publishedAt,
            HasUpdate = hasUpdate,
            CheckError = checkError,
            CheckedAt = DateTimeOffset.UtcNow
        };

        cache.Set(cacheKey, result, TimeSpan.FromHours(1));
        return Results.Ok(result);
    }

    private sealed record GitHubRelease(
        [property: JsonPropertyName("tag_name")] string? TagName,
        [property: JsonPropertyName("html_url")] string? HtmlUrl,
        [property: JsonPropertyName("body")] string? Body,
        [property: JsonPropertyName("published_at")] DateTimeOffset? PublishedAt);
}

public sealed record VersionInfoResponse
{
    public string CurrentVersion { get; init; } = "";
    public string? LatestVersion { get; init; }
    public string? LatestReleaseUrl { get; init; }
    public string? ReleaseNotes { get; init; }
    public DateTimeOffset? PublishedAt { get; init; }
    public bool HasUpdate { get; init; }
    public string? CheckError { get; init; }
    public DateTimeOffset CheckedAt { get; init; }
}
