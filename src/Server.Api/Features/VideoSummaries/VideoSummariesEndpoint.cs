using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.VideoSummaries;

/// <summary>
/// Vídeos resumen bajo demanda: el usuario pide el resumen de un año o de un
/// álbum, el servidor lo monta en segundo plano (<see cref="VideoSummaryWorker"/>)
/// y lo deja en su biblioteca, en la carpeta «Recuerdos», como un vídeo más.
/// </summary>
public class VideoSummariesEndpoint : IEndpoint
{
    /// <summary>Summaries one user may have queued or rendering at once.</summary>
    internal const int MaxQueuedPerUser = 3;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/video-summaries")
            .WithTags("VideoSummaries")
            .RequireAuthorization();

        group.MapPost("", Request)
            .WithName("RequestVideoSummary")
            .WithDescription("Requests (or re-renders) the summary video of a year or an album")
            .Produces<ApiError>(StatusCodes.Status400BadRequest)
            .Produces(StatusCodes.Status404NotFound);

        group.MapGet("", List)
            .WithName("GetVideoSummaries")
            .WithDescription("Lists the current user's summary videos, newest request first");

        group.MapGet("{summaryId:guid}", Get)
            .WithName("GetVideoSummary")
            .WithDescription("Gets one summary video request and its progress");

        group.MapGet("years", Years)
            .WithName("GetVideoSummaryYears")
            .WithDescription("Years with material for a summary, with how many videos and photos each has");
    }

    private static async Task<Results<Accepted<VideoSummaryResponse>, Ok<VideoSummaryResponse>, BadRequest<ApiError>, NotFound, UnauthorizedHttpResult>> Request(
        [FromBody] RequestVideoSummaryRequest? request,
        [FromServices] ApplicationDbContext db,
        [FromServices] VideoSummarySources sources,
        [FromServices] VideoSummaryQueue queue,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();
        if (request == null)
            return TypedResults.BadRequest(new ApiError("Falta la petición.", "invalid_request"));

        IQueryable<Asset> material;
        string sourceKey;
        string title;
        VideoSummarySource source;
        switch (request.Source?.Trim().ToLowerInvariant())
        {
            case "year":
                if (request.Year is not { } year || year < 1900 || year > DateTime.UtcNow.Year + 1)
                    return TypedResults.BadRequest(new ApiError("Año no válido.", "invalid_year"));
                source = VideoSummarySource.Year;
                sourceKey = $"year:{year}";
                title = $"Resumen {year}";
                material = await sources.ForYearAsync(userId, year, ct);
                break;

            case "album":
                if (request.AlbumId is not { } albumId)
                    return TypedResults.BadRequest(new ApiError("Falta el álbum.", "invalid_album"));
                var album = await sources.GetReadableAlbumAsync(userId, albumId, ct);
                var albumMaterial = album == null ? null : await sources.ForAlbumAsync(userId, albumId, ct);
                if (album == null || albumMaterial == null) return TypedResults.NotFound();
                source = VideoSummarySource.Album;
                sourceKey = $"album:{albumId:D}";
                title = $"Resumen · {album.Name}";
                if (title.Length > 200) title = title[..200];
                material = albumMaterial;
                break;

            default:
                return TypedResults.BadRequest(new ApiError("Origen no válido: usa «year» o «album».", "invalid_source"));
        }

        var counts = await material
            .GroupBy(a => a.Type)
            .Select(g => new { Type = g.Key, Count = g.Count() })
            .ToListAsync(ct);
        var videos = counts.Where(c => c.Type == AssetType.Video).Sum(c => c.Count);
        var photos = counts.Where(c => c.Type == AssetType.Image).Sum(c => c.Count);
        var usable = videos + (request.IncludePhotos ? photos : 0);
        if (usable < 2)
            return TypedResults.BadRequest(new ApiError(
                request.IncludePhotos || photos == 0
                    ? "No hay suficientes vídeos ni fotos para montar un resumen."
                    : "No hay suficientes vídeos para montar un resumen. Prueba a incluir las fotos.",
                "not_enough_media"));

        var summary = await db.VideoSummaries
            .FirstOrDefaultAsync(s => s.OwnerId == userId && s.SourceKey == sourceKey, ct);

        // Already queued or rendering: asking again just returns it.
        if (summary is { Status: VideoSummaryStatus.Pending or VideoSummaryStatus.Running })
            return TypedResults.Ok(VideoSummaryResponse.From(summary));

        // Rendering is one at a time for the whole server; one user can't
        // queue up a whole decade ahead of everyone else.
        var queued = await db.VideoSummaries.CountAsync(s => s.OwnerId == userId
            && (s.Status == VideoSummaryStatus.Pending || s.Status == VideoSummaryStatus.Running), ct);
        if (queued >= MaxQueuedPerUser)
            return TypedResults.BadRequest(new ApiError(
                "Ya tienes varios vídeos resumen en preparación. Espera a que terminen.",
                "summary_queue_full"));

        if (summary == null)
        {
            summary = new VideoSummary
            {
                OwnerId = userId,
                Source = source,
                SourceKey = sourceKey,
                Year = source == VideoSummarySource.Year ? request.Year : null,
                AlbumId = source == VideoSummarySource.Album ? request.AlbumId : null,
            };
            db.VideoSummaries.Add(summary);
        }

        summary.Title = title;
        summary.IncludePhotos = request.IncludePhotos;
        summary.Status = VideoSummaryStatus.Pending;
        summary.Progress = 0;
        summary.Error = null;
        summary.RequestedAt = DateTime.UtcNow;
        summary.CompletedAt = null;
        await db.SaveChangesAsync(ct);

        queue.Enqueue(summary.Id);
        return TypedResults.Accepted($"/api/video-summaries/{summary.Id}", VideoSummaryResponse.From(summary));
    }

    private static async Task<Results<Ok<List<VideoSummaryResponse>>, UnauthorizedHttpResult>> List(
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var summaries = await db.VideoSummaries
            .AsNoTracking()
            .Include(s => s.Asset)
            .Where(s => s.OwnerId == userId)
            .OrderByDescending(s => s.RequestedAt)
            .ToListAsync(ct);
        return TypedResults.Ok(summaries.Select(VideoSummaryResponse.From).ToList());
    }

    private static async Task<Results<Ok<VideoSummaryResponse>, NotFound, UnauthorizedHttpResult>> Get(
        [FromRoute] Guid summaryId,
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var summary = await db.VideoSummaries
            .AsNoTracking()
            .Include(s => s.Asset)
            .FirstOrDefaultAsync(s => s.Id == summaryId && s.OwnerId == userId, ct);
        return summary == null ? TypedResults.NotFound() : TypedResults.Ok(VideoSummaryResponse.From(summary));
    }

    private static async Task<Results<Ok<List<VideoSummaryYearResponse>>, UnauthorizedHttpResult>> Years(
        [FromServices] ApplicationDbContext db,
        [FromServices] Shared.Authorization.AssetVisibilityService visibility,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        // Same set as VideoSummarySources.ForYearAsync, without the year filter.
        var scope = await visibility.GetScopeAsync(userId, ct);
        var rows = await db.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null && !a.IsArchived && !a.IsFileMissing
                     && !a.Tags.Any(t => t.TagType == AssetTagType.MotionPhotoPart))
            .Where(scope.DiscoveryPredicate())
            .Where(a => (a.Type == AssetType.Video || a.Type == AssetType.Image)
                     && !db.VideoSummaries.Any(s => s.AssetId == a.Id))
            .GroupBy(a => new { a.CapturedAt.Year, a.Type })
            .Select(g => new { g.Key.Year, g.Key.Type, Count = g.Count() })
            .ToListAsync(ct);

        var years = rows
            .GroupBy(r => r.Year)
            .Select(g => new VideoSummaryYearResponse(
                g.Key,
                g.Where(r => r.Type == AssetType.Video).Sum(r => r.Count),
                g.Where(r => r.Type == AssetType.Image).Sum(r => r.Count)))
            .Where(y => y.Videos > 0)
            .OrderByDescending(y => y.Year)
            .ToList();
        return TypedResults.Ok(years);
    }
}

/// <param name="Source">"year" or "album".</param>
public sealed record RequestVideoSummaryRequest(string? Source, int? Year, Guid? AlbumId, bool IncludePhotos);

public sealed record VideoSummaryYearResponse(int Year, int Videos, int Photos);

/// <param name="Source">"year" or "album".</param>
/// <param name="Status">"pending", "running", "completed" or "failed".</param>
/// <param name="Progress">0..1.</param>
/// <param name="AssetId">The rendered video, once completed.</param>
public sealed record VideoSummaryResponse(
    Guid Id,
    string Source,
    int? Year,
    Guid? AlbumId,
    string Title,
    bool IncludePhotos,
    string Status,
    double Progress,
    string? Error,
    Guid? AssetId,
    int ClipCount,
    DateTime RequestedAt,
    DateTime? CompletedAt)
{
    public static VideoSummaryResponse From(VideoSummary s) => new(
        s.Id,
        s.Source == VideoSummarySource.Year ? "year" : "album",
        s.Year,
        s.AlbumId,
        s.Title,
        s.IncludePhotos,
        s.Status.ToString().ToLowerInvariant(),
        s.Progress,
        s.Error,
        // A video sent to the trash is no longer something to open.
        s.Asset is { DeletedAt: not null } ? null : s.AssetId,
        s.ClipCount,
        s.RequestedAt,
        s.CompletedAt);
}
