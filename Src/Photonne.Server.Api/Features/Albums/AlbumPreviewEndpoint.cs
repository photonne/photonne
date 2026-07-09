using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Albums;

/// <summary>
/// Dry-run resolution of a smart-album rule WITHOUT persisting anything. Powers
/// the live "N fotos coinciden" preview in the rule editor
/// (docs/smart-albums/creation-ux.md). Resolves owner-anchored with owner ==
/// viewer == requester (you only preview your own draft), so it reuses the exact
/// same <see cref="SmartAlbumResolver"/> the saved album will use — preview
/// equals real content.
/// </summary>
public sealed class AlbumPreviewEndpoint : IEndpoint
{
    private const int DefaultSample = 24;
    private const int MaxSample = 60;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/albums/preview", Handle)
            .WithName("PreviewSmartAlbum")
            .WithTags("Albums")
            .WithDescription("Resolve a smart-album rule and return the match count plus a sample, without saving")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] SmartAlbumResolver resolver,
        ClaimsPrincipal user,
        [FromBody] PreviewRequest request,
        CancellationToken ct)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(claim?.Value, out var userId))
            return Results.Unauthorized();

        if (request.Rule is null)
            return Results.BadRequest(new { error = "A rule is required." });

        IQueryable<Shared.Models.Asset> query;
        try
        {
            query = await resolver.ResolveAsync(request.Rule, ownerId: userId, viewerId: userId, ct);
        }
        catch (SmartRuleException ex)
        {
            return Results.BadRequest(new { error = ex.Message });
        }

        var sampleSize = request.SampleSize is > 0
            ? Math.Min(request.SampleSize.Value, MaxSample)
            : DefaultSample;

        var count = await query.CountAsync(ct);
        var sample = await query
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .ThenBy(a => a.Id)
            .Take(sampleSize)
            .Select(a => a.Id)
            .ToListAsync(ct);

        return Results.Ok(new PreviewResponse { Count = count, SampleAssetIds = sample });
    }

    public sealed class PreviewRequest
    {
        public SmartRuleNode? Rule { get; set; }
        public int? SampleSize { get; set; }
    }

    public sealed class PreviewResponse
    {
        public int Count { get; set; }
        public List<Guid> SampleAssetIds { get; set; } = new();
    }
}
