using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.VideoSummaries.Music;
using Photonne.Server.Api.Features.VideoSummaries.Rendering;
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

        group.MapGet("{summaryId:guid}/plan", GetPlan)
            .WithName("GetVideoSummaryPlan")
            .WithDescription("Gets the clips chosen for a summary, to review them before (or after) rendering")
            .Produces(StatusCodes.Status404NotFound);

        group.MapPut("{summaryId:guid}/plan", UpdatePlan)
            .WithName("UpdateVideoSummaryPlan")
            .WithDescription("Replaces the clips of a summary's plan: removed, swapped, trimmed or pinned")
            .Produces<ApiError>(StatusCodes.Status400BadRequest)
            .Produces<ApiError>(StatusCodes.Status409Conflict)
            .Produces(StatusCodes.Status404NotFound);

        group.MapGet("{summaryId:guid}/plan/alternatives", Alternatives)
            .WithName("GetVideoSummaryPlanAlternatives")
            .WithDescription("Material of the summary left out of its plan, closest in time to an asset first")
            .Produces(StatusCodes.Status404NotFound);

        group.MapPost("{summaryId:guid}/render", Render)
            .WithName("RenderVideoSummary")
            .WithDescription("Approves the summary's plan as it is and renders it")
            .Produces<ApiError>(StatusCodes.Status400BadRequest)
            .Produces<ApiError>(StatusCodes.Status409Conflict)
            .Produces(StatusCodes.Status404NotFound);

        group.MapPost("music", UploadMusic)
            .WithName("UploadVideoSummaryMusic")
            .WithDescription("Uploads a music track to lay under a summary video; pass its id as musicId")
            .DisableAntiforgery()
            .Produces<ApiError>(StatusCodes.Status400BadRequest);

        group.MapGet("years", Years)
            .WithName("GetVideoSummaryYears")
            .WithDescription("Years with material for a summary, with how many videos and photos each has");
    }

    private static async Task<Results<Accepted<VideoSummaryResponse>, Ok<VideoSummaryResponse>, BadRequest<ApiError>, NotFound, UnauthorizedHttpResult>> Request(
        [FromBody] RequestVideoSummaryRequest? request,
        [FromServices] ApplicationDbContext db,
        [FromServices] VideoSummarySources sources,
        [FromServices] VideoSummaryQueue queue,
        [FromServices] SummaryMusicStore music,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();
        if (request == null)
            return TypedResults.BadRequest(new ApiError("Falta la petición.", "invalid_request"));
        if (request.MusicId is { } requestedMusic && music.Find(userId, requestedMusic) == null)
            return TypedResults.BadRequest(new ApiError(
                "No se encuentra la música. Vuelve a subirla.", "music_not_found"));

        VideoSummaryOrientation orientation;
        switch (request.Orientation?.Trim().ToLowerInvariant())
        {
            case null or "" or "all": orientation = VideoSummaryOrientation.All; break;
            case "landscape": orientation = VideoSummaryOrientation.Landscape; break;
            case "portrait": orientation = VideoSummaryOrientation.Portrait; break;
            default:
                return TypedResults.BadRequest(new ApiError(
                    "Orientación no válida: usa «all», «landscape» o «portrait».", "invalid_orientation"));
        }

        var durationSeconds = (request.DurationMinutes ?? VideoSummary.DefaultDurationSeconds / 60) * 60;
        if (durationSeconds is < VideoSummary.MinDurationSeconds or > VideoSummary.MaxDurationSeconds)
            return TypedResults.BadRequest(new ApiError(
                "La duración debe estar entre 1 y 10 minutos.", "invalid_duration"));

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

        // A first check, by the thumbnails' shape; material with no thumbnail
        // yet gets the benefit of the doubt here and is settled when rendering.
        var shaped = material.Select(a => new
        {
            a.Type,
            Ratio = a.Thumbnails
                .Where(t => t.Size == ThumbnailSize.Small && t.Height > 0)
                .Select(t => (double?)t.Width / t.Height)
                .FirstOrDefault(),
        });
        if (orientation == VideoSummaryOrientation.Landscape)
            shaped = shaped.Where(x => x.Ratio == null || x.Ratio >= 1 - SummaryOrientation.SquareTolerance);
        else if (orientation == VideoSummaryOrientation.Portrait)
            shaped = shaped.Where(x => x.Ratio == null || x.Ratio <= 1 + SummaryOrientation.SquareTolerance);

        var counts = await shaped
            .GroupBy(a => a.Type)
            .Select(g => new { Type = g.Key, Count = g.Count() })
            .ToListAsync(ct);
        var videos = counts.Where(c => c.Type == AssetType.Video).Sum(c => c.Count);
        var photos = counts.Where(c => c.Type == AssetType.Image).Sum(c => c.Count);
        var usable = videos + (request.IncludePhotos ? photos : 0);
        if (usable < 2)
            return TypedResults.BadRequest(new ApiError(
                VideoSummaryGenerator.NotEnoughMessage(request.IncludePhotos || photos == 0, orientation),
                "not_enough_media"));

        var summary = await db.VideoSummaries
            .FirstOrDefaultAsync(s => s.OwnerId == userId && s.SourceKey == sourceKey, ct);

        // Already queued or rendering: asking again just returns it.
        if (summary is { Status: VideoSummaryStatus.Pending or VideoSummaryStatus.Running })
            return TypedResults.Ok(VideoSummaryResponse.From(summary));

        // Rendering is one at a time for the whole server; one user can't
        // queue up a whole decade ahead of everyone else.
        if (await QueueFullAsync(db, userId, ct))
            return TypedResults.BadRequest(QueueFull);

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
        summary.Orientation = orientation;
        summary.DurationSeconds = durationSeconds;
        summary.ShowTitles = request.ShowTitles ?? true;
        summary.MusicId = request.MusicId;
        summary.MusicName = request.MusicId == null ? null : MusicName(request.MusicName);
        summary.Review = request.Review ?? false;
        summary.PlanApproved = false;
        summary.Status = VideoSummaryStatus.Pending;
        summary.Progress = 0;
        summary.Error = null;
        summary.RequestedAt = DateTime.UtcNow;
        summary.CompletedAt = null;
        await db.SaveChangesAsync(ct);

        queue.Enqueue(summary.Id);
        return TypedResults.Accepted($"/api/video-summaries/{summary.Id}", VideoSummaryResponse.From(summary));
    }

    private static readonly ApiError QueueFull = new(
        "Ya tienes varios vídeos resumen en preparación. Espera a que terminen.", "summary_queue_full");

    private static async Task<bool> QueueFullAsync(ApplicationDbContext db, Guid userId, CancellationToken ct) =>
        await db.VideoSummaries.CountAsync(s => s.OwnerId == userId
            && (s.Status == VideoSummaryStatus.Pending || s.Status == VideoSummaryStatus.Running), ct)
        >= MaxQueuedPerUser;

    private static readonly ApiError Busy = new(
        "El vídeo se está preparando. Espera a que termine para cambiar los fragmentos.", "summary_busy");

    /// <summary>Shortest cut the user may leave a video at.</summary>
    internal const double MinClipSeconds = 1;

    /// <summary>Longest and shortest a photo may stay on screen.</summary>
    internal const double MinPhotoSeconds = 1;
    internal const double MaxPhotoSeconds = 10;

    /// <summary>More clips than a ten-minute summary could ever show.</summary>
    internal const int MaxPlanClips = 600;

    private static async Task<Results<Ok<VideoSummaryPlanResponse>, NotFound, UnauthorizedHttpResult>> GetPlan(
        [FromRoute] Guid summaryId,
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var summary = await db.VideoSummaries.AsNoTracking()
            .FirstOrDefaultAsync(s => s.Id == summaryId && s.OwnerId == userId, ct);
        var plan = SummaryPlanDocument.Deserialize(summary?.Plan);
        return summary == null || plan == null
            ? TypedResults.NotFound()
            : TypedResults.Ok(VideoSummaryPlanResponse.From(summary, plan));
    }

    private static async Task<Results<Ok<VideoSummaryPlanResponse>, BadRequest<ApiError>, Conflict<ApiError>, NotFound, UnauthorizedHttpResult>> UpdatePlan(
        [FromRoute] Guid summaryId,
        [FromBody] UpdateVideoSummaryPlanRequest? request,
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var summary = await db.VideoSummaries
            .FirstOrDefaultAsync(s => s.Id == summaryId && s.OwnerId == userId, ct);
        var plan = SummaryPlanDocument.Deserialize(summary?.Plan);
        if (summary == null || plan == null) return TypedResults.NotFound();
        if (summary.Status is VideoSummaryStatus.Pending or VideoSummaryStatus.Running)
            return TypedResults.Conflict(Busy);

        var requested = request?.Clips ?? new List<VideoSummaryPlanClipRequest>();
        if (requested.Count < 2)
            return TypedResults.BadRequest(new ApiError("Deja al menos dos fragmentos.", "not_enough_media"));
        if (requested.Count > MaxPlanClips)
            return TypedResults.BadRequest(new ApiError("Demasiados fragmentos.", "invalid_clip"));

        var candidates = plan.Candidates.ToDictionary(c => c.AssetId);
        var clips = new List<SummaryClip>(requested.Count);
        foreach (var clip in requested)
        {
            if (!candidates.TryGetValue(clip.AssetId, out var candidate))
                return TypedResults.BadRequest(new ApiError(
                    "Uno de los fragmentos no es de este resumen.", "invalid_clip"));
            if (!double.IsFinite(clip.Start) || !double.IsFinite(clip.Length))
                return TypedResults.BadRequest(new ApiError("Fragmento no válido.", "invalid_clip"));

            if (candidate.IsVideo)
            {
                var start = Math.Round(clip.Start, 3);
                var length = Math.Round(clip.Length, 3);
                if (start < 0 || length < MinClipSeconds || start + length > candidate.DurationSeconds + 0.05)
                    return TypedResults.BadRequest(new ApiError(
                        $"Cada fragmento de vídeo dura al menos {MinClipSeconds:0} s y no se sale del vídeo.",
                        "invalid_clip"));
                clips.Add(new SummaryClip(candidate.AssetId, true, candidate.CapturedAt, start,
                    Math.Min(length, Math.Round(candidate.DurationSeconds - start, 3))));
            }
            else
            {
                var length = Math.Round(clip.Length, 3);
                if (length is < MinPhotoSeconds or > MaxPhotoSeconds)
                    return TypedResults.BadRequest(new ApiError(
                        $"Cada foto se ve entre {MinPhotoSeconds:0} y {MaxPhotoSeconds:0} segundos.", "invalid_clip"));
                clips.Add(new SummaryClip(candidate.AssetId, false, candidate.CapturedAt, 0, length));
            }
        }

        if (clips.Sum(c => c.Length) > summary.DurationSeconds + 0.5)
            return TypedResults.BadRequest(new ApiError(
                "Los fragmentos duran más que el máximo elegido para el vídeo.", "plan_too_long"));

        // The summary plays in time order, whatever order the edit came in.
        clips = clips.OrderBy(c => c.CapturedAt).ThenBy(c => c.AssetId).ThenBy(c => c.Start).ToList();
        var edited = plan with { Clips = clips };
        summary.Plan = edited.Serialize();
        summary.PinnedAssetIds = requested.Where(c => c.Pinned).Select(c => c.AssetId).Distinct().ToList();
        summary.ClipCount = clips.Select(c => c.AssetId).Distinct().Count();
        await db.SaveChangesAsync(ct);
        return TypedResults.Ok(VideoSummaryPlanResponse.From(summary, edited));
    }

    private static async Task<Results<Ok<List<VideoSummaryPlanCandidateResponse>>, NotFound, UnauthorizedHttpResult>> Alternatives(
        [FromRoute] Guid summaryId,
        [FromQuery] Guid? near,
        [FromQuery] int? count,
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var summary = await db.VideoSummaries.AsNoTracking()
            .FirstOrDefaultAsync(s => s.Id == summaryId && s.OwnerId == userId, ct);
        var plan = SummaryPlanDocument.Deserialize(summary?.Plan);
        if (summary == null || plan == null) return TypedResults.NotFound();

        var used = plan.Clips.Select(c => c.AssetId).ToHashSet();
        var anchor = near is { } nearId
            ? plan.Candidates.FirstOrDefault(c => c.AssetId == nearId)?.CapturedAt
            : null;
        IEnumerable<SummaryCandidate> left = plan.Candidates.Where(c => !used.Contains(c.AssetId));
        if (anchor is { } at)
            left = left.OrderBy(c => Math.Abs((c.CapturedAt - at).TotalSeconds)).ThenBy(c => c.AssetId);
        var take = Math.Clamp(count ?? 24, 1, 100);

        return TypedResults.Ok(left
            .Take(take)
            .OrderBy(c => c.CapturedAt)
            .Select(c => new VideoSummaryPlanCandidateResponse(c.AssetId, c.IsVideo, c.CapturedAt, c.DurationSeconds))
            .ToList());
    }

    private static async Task<Results<Accepted<VideoSummaryResponse>, BadRequest<ApiError>, Conflict<ApiError>, NotFound, UnauthorizedHttpResult>> Render(
        [FromRoute] Guid summaryId,
        [FromServices] ApplicationDbContext db,
        [FromServices] VideoSummaryQueue queue,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var summary = await db.VideoSummaries
            .Include(s => s.Asset)
            .FirstOrDefaultAsync(s => s.Id == summaryId && s.OwnerId == userId, ct);
        if (summary == null || SummaryPlanDocument.Deserialize(summary.Plan) == null)
            return TypedResults.NotFound();
        if (summary.Status is VideoSummaryStatus.Pending or VideoSummaryStatus.Running)
            return TypedResults.Conflict(Busy);
        if (await QueueFullAsync(db, userId, ct))
            return TypedResults.BadRequest(QueueFull);

        summary.PlanApproved = true;
        summary.Status = VideoSummaryStatus.Pending;
        summary.Progress = 0;
        summary.Error = null;
        summary.RequestedAt = DateTime.UtcNow;
        summary.CompletedAt = null;
        await db.SaveChangesAsync(ct);

        queue.Enqueue(summary.Id);
        return TypedResults.Accepted($"/api/video-summaries/{summary.Id}", VideoSummaryResponse.From(summary));
    }

    private static string MusicName(string? name)
    {
        var clean = name?.Trim() ?? "";
        return clean.Length == 0 ? "Música" : clean[..Math.Min(200, clean.Length)];
    }

    private static async Task<Results<Ok<VideoSummaryMusicResponse>, BadRequest<ApiError>, UnauthorizedHttpResult>> UploadMusic(
        IFormFile file,
        [FromServices] SummaryMusicStore music,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        try
        {
            var saved = await music.SaveAsync(userId, file, ct);
            return TypedResults.Ok(new VideoSummaryMusicResponse(saved.Id, saved.Name, saved.DurationSeconds));
        }
        catch (VideoSummaryException ex)
        {
            return TypedResults.BadRequest(new ApiError(ex.Message, "invalid_music"));
        }
    }

    private static async Task<Results<Ok<List<VideoSummaryResponse>>, UnauthorizedHttpResult>> List(
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var summaries = await VideoSummaryResponse.Project(db.VideoSummaries
                .Where(s => s.OwnerId == userId)
                .OrderByDescending(s => s.RequestedAt))
            .ToListAsync(ct);
        return TypedResults.Ok(summaries);
    }

    private static async Task<Results<Ok<VideoSummaryResponse>, NotFound, UnauthorizedHttpResult>> Get(
        [FromRoute] Guid summaryId,
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
            return TypedResults.Unauthorized();

        var summary = await VideoSummaryResponse.Project(db.VideoSummaries
                .Where(s => s.Id == summaryId && s.OwnerId == userId))
            .FirstOrDefaultAsync(ct);
        return summary == null ? TypedResults.NotFound() : TypedResults.Ok(summary);
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
/// <param name="Orientation">"all" (default), "landscape" or "portrait": which
/// material goes in. A portrait summary is also cut in a portrait frame.</param>
/// <param name="DurationMinutes">Longest the summary may run, 1 to 10 (default 5).</param>
/// <param name="ShowTitles">Title card and chapter captions (default true).</param>
/// <param name="MusicId">A track uploaded with POST /api/video-summaries/music.</param>
/// <param name="MusicName">How to call that track (its file name, usually).</param>
/// <param name="Review">Stop once the clips are chosen ("planned"), to review
/// them before rendering (default false).</param>
public sealed record RequestVideoSummaryRequest(
    string? Source, int? Year, Guid? AlbumId, bool IncludePhotos,
    string? Orientation = null, int? DurationMinutes = null,
    bool? ShowTitles = null, Guid? MusicId = null, string? MusicName = null,
    bool? Review = null);

/// <param name="Start">Seconds into the video the clip starts (ignored for photos).</param>
/// <param name="Length">Seconds the clip lasts: at least 1 for a video, 1 to 10 for a photo.</param>
/// <param name="Pinned">"Include always": every new cut of this summary keeps the asset.</param>
public sealed record VideoSummaryPlanClipRequest(Guid AssetId, double Start, double Length, bool Pinned = false);

public sealed record UpdateVideoSummaryPlanRequest(List<VideoSummaryPlanClipRequest>? Clips);

/// <param name="SourceSeconds">The whole video's length (0 for photos), the
/// bound for moving the clip's start and end.</param>
public sealed record VideoSummaryPlanClipResponse(
    Guid AssetId, bool IsVideo, DateTime CapturedAt, double Start, double Length, double SourceSeconds, bool Pinned);

public sealed record VideoSummaryPlanCandidateResponse(Guid AssetId, bool IsVideo, DateTime CapturedAt, double DurationSeconds);

/// <param name="Status">The summary's status (see <see cref="VideoSummaryResponse"/>).</param>
/// <param name="TargetSeconds">The longest the clips may add up to.</param>
/// <param name="Alternatives">How much material was left out, to swap in.</param>
public sealed record VideoSummaryPlanResponse(
    Guid SummaryId,
    string Title,
    string Status,
    int TargetSeconds,
    double TotalSeconds,
    List<VideoSummaryPlanClipResponse> Clips,
    int Alternatives)
{
    public static VideoSummaryPlanResponse From(VideoSummary summary, SummaryPlanDocument plan)
    {
        var durations = plan.Candidates.ToDictionary(c => c.AssetId, c => c.DurationSeconds);
        var pinned = summary.PinnedAssetIds.ToHashSet();
        var used = plan.Clips.Select(c => c.AssetId).ToHashSet();
        return new VideoSummaryPlanResponse(
            summary.Id,
            summary.Title,
            summary.Status.ToString().ToLowerInvariant(),
            summary.DurationSeconds,
            Math.Round(plan.TotalSeconds, 3),
            plan.Clips
                .Select(c => new VideoSummaryPlanClipResponse(
                    c.AssetId, c.IsVideo, c.CapturedAt, c.Start, c.Length,
                    c.IsVideo ? durations.GetValueOrDefault(c.AssetId, c.Start + c.Length) : 0,
                    pinned.Contains(c.AssetId)))
                .ToList(),
            plan.Candidates.Count(c => !used.Contains(c.AssetId)));
    }
}

public sealed record VideoSummaryMusicResponse(Guid Id, string Name, double DurationSeconds);

public sealed record VideoSummaryYearResponse(int Year, int Videos, int Photos);

/// <param name="Source">"year" or "album".</param>
/// <param name="Orientation">"all", "landscape" or "portrait".</param>
/// <param name="Status">"pending", "running", "planned" (clips chosen, waiting
/// for the user's review), "completed" or "failed".</param>
/// <param name="Review">The request asked to review the clips before rendering.</param>
/// <param name="HasPlan">There are chosen clips to review or change.</param>
/// <param name="Progress">0..1.</param>
/// <param name="AssetId">The rendered video, once completed.</param>
public sealed record VideoSummaryResponse(
    Guid Id,
    string Source,
    int? Year,
    Guid? AlbumId,
    string Title,
    bool IncludePhotos,
    string Orientation,
    int DurationMinutes,
    bool ShowTitles,
    Guid? MusicId,
    string? MusicName,
    bool Review,
    bool HasPlan,
    string Status,
    double Progress,
    string? Error,
    Guid? AssetId,
    int ClipCount,
    DateTime RequestedAt,
    DateTime? CompletedAt)
{
    /// <summary>The responses straight from the table, without loading the
    /// plans (they can be large, and the list is polled while rendering).</summary>
    public static IQueryable<VideoSummaryResponse> Project(IQueryable<VideoSummary> summaries) =>
        summaries.Select(s => new VideoSummaryResponse(
            s.Id,
            s.Source == VideoSummarySource.Year ? "year" : "album",
            s.Year,
            s.AlbumId,
            s.Title,
            s.IncludePhotos,
            Name(s.Orientation),
            s.DurationSeconds / 60,
            s.ShowTitles,
            s.MusicId,
            s.MusicName,
            s.Review,
            s.Plan != null,
            Name(s.Status),
            s.Progress,
            s.Error,
            s.Asset != null && s.Asset.DeletedAt != null ? null : s.AssetId,
            s.ClipCount,
            s.RequestedAt,
            s.CompletedAt));

    private static string Name<T>(T value) where T : struct, Enum => value.ToString().ToLowerInvariant();

    public static VideoSummaryResponse From(VideoSummary s) => new(
        s.Id,
        s.Source == VideoSummarySource.Year ? "year" : "album",
        s.Year,
        s.AlbumId,
        s.Title,
        s.IncludePhotos,
        Name(s.Orientation),
        s.DurationSeconds / 60,
        s.ShowTitles,
        s.MusicId,
        s.MusicName,
        s.Review,
        s.Plan != null,
        Name(s.Status),
        s.Progress,
        s.Error,
        // A video sent to the trash is no longer something to open.
        s.Asset is { DeletedAt: not null } ? null : s.AssetId,
        s.ClipCount,
        s.RequestedAt,
        s.CompletedAt);
}
