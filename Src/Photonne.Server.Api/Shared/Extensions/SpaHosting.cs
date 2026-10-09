using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.StaticFiles;
using Microsoft.EntityFrameworkCore;
using Microsoft.Net.Http.Headers;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Features.Share;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Shared.Extensions;

/// <summary>
/// Serves the web client (Src/Photonne.Client.SPA, built into wwwroot): its
/// static files with the right caching, index.html for every app route, and
/// link previews for public share links.
/// </summary>
public static partial class SpaHosting
{
    private const string IndexFile = "index.html";

    /// <summary>
    /// CSP sources for the inline scripts of index.html (the theme bootstrap
    /// and SvelteKit's start-up), so script-src needs no 'unsafe-inline'.
    /// They change with every build, hence computed from the file served.
    /// </summary>
    public static IReadOnlyList<string> InlineScriptHashes(IWebHostEnvironment environment)
    {
        var html = ReadIndex(environment);
        if (html is null) return [];
        return InlineScript().Matches(html)
            .Select(match => $"'sha256-{Convert.ToBase64String(SHA256.HashData(Encoding.UTF8.GetBytes(match.Groups[1].Value)))}'")
            .Distinct()
            .ToList();
    }

    public static WebApplication UseSpaStaticFiles(this WebApplication app)
    {
        app.UseStaticFiles(new StaticFileOptions { OnPrepareResponse = SetCacheHeaders });
        return app;
    }

    /// <summary>
    /// After every other endpoint: an unknown /api path is a 404 for the
    /// API, a public share link gets its preview, anything else is an app
    /// route and gets index.html.
    /// </summary>
    public static WebApplication MapSpaFallback(this WebApplication app)
    {
        app.MapFallback("/api/{**path}", () =>
                TypedResults.NotFound(new ApiError("Endpoint not found", "endpoint_not_found")))
            .AllowAnonymous()
            .ExcludeFromDescription();

        app.MapGet("/share/{token}", ShareIndex)
            .AllowAnonymous()
            .ExcludeFromDescription();

        app.MapFallbackToFile(IndexFile, new StaticFileOptions { OnPrepareResponse = SetCacheHeaders });
        return app;
    }

    private static void SetCacheHeaders(StaticFileResponseContext context)
    {
        // Build output under /_app/immutable has a content hash in its name;
        // everything else (index.html, the service worker, the manifest…)
        // must be revalidated so a new release is picked up.
        var immutable = context.Context.Request.Path.StartsWithSegments("/_app/immutable");
        context.Context.Response.Headers[HeaderNames.CacheControl] = immutable
            ? "public, max-age=31536000, immutable"
            : "no-cache";
    }

    /// <summary>
    /// index.html with Open Graph tags, so a link pasted into a chat shows the
    /// album's name and cover. Only for links anyone can open as they are: no
    /// password, not expired, views left. Reading it doesn't count a view.
    /// </summary>
    private static async Task<IResult> ShareIndex(
        string token,
        HttpContext context,
        IWebHostEnvironment environment,
        ApplicationDbContext dbContext,
        SmartAlbumResolver smartResolver,
        CancellationToken ct)
    {
        var html = ReadIndex(environment);
        if (html is null) return TypedResults.NotFound();

        var link = await dbContext.SharedLinks
            .AsNoTracking()
            .Include(l => l.Album)
            .FirstOrDefaultAsync(l => l.Token == token && l.Album != null, ct);

        var open = link is not null
                   && link.PasswordHash is null
                   && (link.ExpiresAt is null || link.ExpiresAt > DateTime.UtcNow)
                   && (link.MaxViews is null || link.ViewCount < link.MaxViews);

        // The cover: the album's first photo, manual or smart (the page shows the same).
        Guid? coverId = null;
        if (open)
        {
            var assets = await SharedAlbumAssets.QueryAsync(dbContext, smartResolver, link!.Album!, ct);
            coverId = await assets.Select(a => (Guid?)a.Id).FirstOrDefaultAsync(ct);
        }

        if (open)
        {
            var origin = $"{context.Request.Scheme}://{context.Request.Host}";
            var tags = new StringBuilder();
            tags.Append(Meta("og:type", "website"));
            tags.Append(Meta("og:site_name", "Photonne"));
            tags.Append(Meta("og:title", link!.Album!.Name));
            tags.Append(Meta("og:url", $"{origin}/share/{Uri.EscapeDataString(token)}"));
            if (!string.IsNullOrWhiteSpace(link.Album.Description))
                tags.Append(Meta("og:description", link.Album.Description));
            if (coverId is { } cover)
            {
                tags.Append(Meta("og:image",
                    $"{origin}/api/share/{Uri.EscapeDataString(token)}/asset/{cover}/thumbnail?size=Large"));
                tags.Append("<meta name=\"twitter:card\" content=\"summary_large_image\" />");
            }
            html = html.Replace("</head>", $"{tags}</head>", StringComparison.Ordinal);
            html = TitleTag().Replace(html, $"<title>{WebUtility.HtmlEncode(link.Album.Name)} · Photonne</title>", 1);
        }

        context.Response.Headers[HeaderNames.CacheControl] = "no-cache";
        return TypedResults.Content(html, "text/html; charset=utf-8");
    }

    private static string Meta(string property, string content) =>
        $"<meta property=\"{property}\" content=\"{WebUtility.HtmlEncode(content)}\" />";

    private static string? ReadIndex(IWebHostEnvironment environment)
    {
        var file = environment.WebRootFileProvider.GetFileInfo(IndexFile);
        if (!file.Exists) return null;
        using var reader = new StreamReader(file.CreateReadStream());
        return reader.ReadToEnd();
    }

    [GeneratedRegex(@"<script>(.*?)</script>", RegexOptions.Singleline)]
    private static partial Regex InlineScript();

    [GeneratedRegex(@"<title>.*?</title>", RegexOptions.Singleline)]
    private static partial Regex TitleTag();
}
