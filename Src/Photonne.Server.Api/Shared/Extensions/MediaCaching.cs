using Microsoft.Net.Http.Headers;

namespace Photonne.Server.Api.Shared.Extensions;

/// <summary>
/// HTTP caching for media responses (thumbnails, originals, motion clips).
///
/// Two modes, picked by the URL:
///   * Versioned — the client appends <c>?v=…</c> (a value that changes when the
///     bytes do: <c>thumbnailsGeneratedAt</c> for thumbnails, the checksum for an
///     original). The response is then immutable for a year: the browser never
///     asks again, and a regenerated thumbnail arrives under a new URL.
///   * Unversioned — older clients. The browser may reuse its copy for a day and
///     then revalidates with the ETag, which costs a 304 instead of the bytes.
///
/// Always <c>private</c>: these are a user's photos, never for a shared cache.
/// The ETag is derived from the file on disk (size + mtime + variant), so a
/// rewrite of the same path still changes it.
/// </summary>
public static class MediaCaching
{
    public const string VersionQueryKey = "v";

    private const string Versioned = "private, max-age=31536000, immutable";
    private const string Unversioned = "private, max-age=86400";

    public static EntityTagHeaderValue ETagFor(FileInfo file, string variant = "")
        => new($"\"{file.Length:x}-{file.LastWriteTimeUtc.Ticks:x}{(variant.Length > 0 ? "-" + variant : "")}\"");

    public static void ApplyCacheControl(HttpContext context)
    {
        var versioned = !string.IsNullOrEmpty(context.Request.Query[VersionQueryKey]);
        context.Response.Headers.CacheControl = versioned ? Versioned : Unversioned;
    }

    /// <summary>
    /// True when the client already holds this representation. Lets an endpoint
    /// skip expensive work (a RAW/HEIC → JPEG render) before answering 304;
    /// <see cref="Results.File(string, string?, string?, DateTimeOffset?, EntityTagHeaderValue?, bool)"/>
    /// already does this for plain files.
    /// </summary>
    public static bool IsNotModified(HttpRequest request, EntityTagHeaderValue etag)
    {
        var ifNoneMatch = request.GetTypedHeaders().IfNoneMatch;
        return ifNoneMatch.Any(candidate => candidate.Equals(EntityTagHeaderValue.Any) || candidate.Compare(etag, useStrongComparison: false));
    }

    public static IResult NotModified(HttpContext context, EntityTagHeaderValue etag)
    {
        context.Response.Headers.ETag = etag.ToString();
        return Results.StatusCode(StatusCodes.Status304NotModified);
    }
}
