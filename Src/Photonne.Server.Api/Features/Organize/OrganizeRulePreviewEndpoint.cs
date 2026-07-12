using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Organize;

/// <summary>
/// Dry-run of a condition rule scoped to the "Para organizar" inbox: how many
/// (and a sample of which) MobileBackup-pending assets match, without moving
/// anything. Powers the live preview in the "Mover por condiciones" screen.
/// Reuses <see cref="SmartAlbumResolver.ResolveWithinAsync"/> over
/// <see cref="OrganizeQuery.Pending"/> so the preview equals what the move
/// touches — and, like the rest of the Organize feature, never leaks assets that
/// have already been filed out of MobileBackup.
/// </summary>
public class OrganizeRulePreviewEndpoint : IEndpoint
{
    private const int DefaultSample = 24;
    private const int MaxSample = 60;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/organize/rule/preview", Handle)
            .WithName("PreviewOrganizeRule")
            .WithTags("Assets")
            .WithDescription("Resolve a condition rule within the MobileBackup inbox and return the match count plus a sample")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SmartAlbumResolver resolver,
        ClaimsPrincipal user,
        [FromBody] OrganizeRulePreviewRequest request,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        if (request.Rule is null)
            return Results.BadRequest(new { error = "A rule is required." });

        IQueryable<Shared.Models.Asset> query;
        try
        {
            query = await resolver.ResolveWithinAsync(
                request.Rule, OrganizeQuery.Pending(dbContext, username), userId, cancellationToken);
        }
        catch (SmartRuleException ex)
        {
            return Results.BadRequest(new { error = ex.Message });
        }

        var sampleSize = request.SampleSize is > 0
            ? Math.Min(request.SampleSize.Value, MaxSample)
            : DefaultSample;

        var count = await query.CountAsync(cancellationToken);
        var sample = await query
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .ThenBy(a => a.Id)
            .Take(sampleSize)
            .Select(a => a.Id)
            .ToListAsync(cancellationToken);

        return Results.Ok(new OrganizeRulePreviewResponse { Count = count, SampleAssetIds = sample });
    }

    public class OrganizeRulePreviewRequest
    {
        public SmartRuleNode? Rule { get; set; }
        public int? SampleSize { get; set; }
    }

    public class OrganizeRulePreviewResponse
    {
        public int Count { get; set; }
        public List<Guid> SampleAssetIds { get; set; } = new();
    }
}
