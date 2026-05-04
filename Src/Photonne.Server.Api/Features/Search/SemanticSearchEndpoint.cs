using System.Collections.Concurrent;
using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Options;
using Pgvector;
using Pgvector.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Embeddings;

namespace Photonne.Server.Api.Features.Search;

/// <summary>
/// CLIP-based semantic search. The user's natural-language query is encoded
/// into a 512-dim vector by the Python service (multilingual — English,
/// Spanish, etc.) and ranked against precomputed image embeddings using
/// pgvector's cosine distance operator.
///
/// Visibility: results are filtered by <see cref="AssetVisibilityService"/>
/// before the ranking, so a user can never see assets they don't own or have
/// shared access to. Filtering before ORDER BY does narrow HNSW recall, but
/// the typical visibility scope contains thousands of assets which is well
/// above the index's effective working set.
/// </summary>
public class SemanticSearchEndpoint : IEndpoint
{
    // Small LRU on encoded text vectors. Search queries repeat a lot
    // ("playa", "perro") so caching saves a HTTP round-trip + ~50ms of CPU.
    // Keyed by (modelVersion, normalized text). Memory is negligible (a few
    // KB per entry, 256-cap default).
    private static readonly MemoryCache TextEmbeddingCache = new(new MemoryCacheOptions
    {
        SizeLimit = 256
    });

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/search/semantic", Handle)
            .WithName("SemanticSearchAssets")
            .WithTags("Assets")
            .WithDescription("Multilingual CLIP-based natural-language asset search")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AssetVisibilityService visibility,
        [FromServices] IEmbeddingClient embeddingClient,
        [FromServices] IOptions<EmbeddingOptions> embeddingOptions,
        ClaimsPrincipal user,
        [FromQuery] string? q,
        [FromQuery] int? limit,
        CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(q))
            return Results.Ok(new SemanticSearchResponse());

        var query = q.Trim();
        if (query.Length is < 2 or > 200)
            return Results.BadRequest(new { error = "query must be 2-200 chars" });

        var effectiveLimit = limit is > 0 ? Math.Min(limit.Value, 200) : 50;

        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();

        var options = embeddingOptions.Value;
        var maxDistance = options.MaxCosineDistance;

        // Encode the query (with a tiny LRU in front to amortize repeats).
        var cacheKey = (options.ModelVersion, query.ToLowerInvariant());
        if (!TextEmbeddingCache.TryGetValue(cacheKey, out Vector? queryVector) || queryVector == null)
        {
            EmbeddingResponseDto encoded;
            try
            {
                encoded = await embeddingClient.EmbedTextAsync(query, ct);
            }
            catch (Exception ex)
            {
                // The embedding service can be 503 if the operator hasn't
                // dropped CLIP models in /app/models. Surface it cleanly.
                return Results.Problem(
                    title: "Semantic search unavailable",
                    detail: ex.Message,
                    statusCode: StatusCodes.Status503ServiceUnavailable);
            }

            queryVector = new Vector(encoded.Embedding);
            TextEmbeddingCache.Set(cacheKey, queryVector, new MemoryCacheEntryOptions
            {
                Size = 1,
                AbsoluteExpirationRelativeToNow = TimeSpan.FromHours(1),
            });
        }

        var isAdmin = user.IsInRole("Admin");

        // Build the EF query: visible assets joined with their embedding,
        // ordered by cosine distance to the query vector. The HNSW index is
        // used automatically because the ORDER BY uses the same operator
        // class (vector_cosine_ops) the index was built with.
        IQueryable<Asset> assets = dbContext.Assets
            .Include(a => a.Exif)
            .Include(a => a.Thumbnails)
            .Include(a => a.Tags)
            .Include(a => a.UserTags)
                .ThenInclude(ut => ut.UserTag)
            .Where(a => a.DeletedAt == null && !a.IsArchived && a.Embedding != null);

        if (!isAdmin)
        {
            var scope = await visibility.GetScopeAsync(userId, ct);
            assets = assets.Where(scope.AssetPredicate());
        }

        var modelVersion = options.ModelVersion;
        var capturedVector = queryVector;
        var results = await assets
            .Where(a => a.Embedding!.ModelVersion == modelVersion)
            .OrderBy(a => a.Embedding!.Embedding.CosineDistance(capturedVector))
            .Select(a => new
            {
                Asset = a,
                Distance = a.Embedding!.Embedding.CosineDistance(capturedVector),
            })
            .Take(effectiveLimit)
            .ToListAsync(ct);

        var items = results
            .Where(r => r.Distance <= maxDistance)
            .Select(r => new SemanticSearchItem
            {
                Score = (float)Math.Max(0, 1 - r.Distance),
                Asset = ToTimelineDto(r.Asset),
            })
            .ToList();

        return Results.Ok(new SemanticSearchResponse { Items = items });
    }

    private static TimelineResponse ToTimelineDto(Asset a) => new()
    {
        Id = a.Id,
        FileName = a.FileName,
        FullPath = a.FullPath,
        FileSize = a.FileSize,
        FileCreatedAt = a.FileCreatedAt,
        FileModifiedAt = a.FileModifiedAt,
        Extension = a.Extension,
        ScannedAt = a.ScannedAt,
        Type = a.Type.ToString(),
        Checksum = a.Checksum,
        HasExif = a.Exif != null,
        HasThumbnails = a.Thumbnails.Any(),
        SyncStatus = AssetSyncStatus.Synced,
        Width = a.Exif?.Width,
        Height = a.Exif?.Height,
        Tags = a.Tags.Select(t => t.TagType.ToString())
            .Concat(a.UserTags.Select(ut => ut.UserTag.Name))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(t => t)
            .ToList(),
        IsFavorite = a.IsFavorite,
        IsFileMissing = a.IsFileMissing,
        IsReadOnly = a.ExternalLibraryId.HasValue,
    };

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(claim?.Value, out userId);
    }
}

public class SemanticSearchItem
{
    // Cosine similarity in [0, 1]. Higher = more relevant. The frontend
    // only uses this for debug overlays, never for filtering — we already
    // truncate at MaxCosineDistance server-side.
    public float Score { get; set; }
    public TimelineResponse Asset { get; set; } = new();
}

public class SemanticSearchResponse
{
    public List<SemanticSearchItem> Items { get; set; } = new();
}
