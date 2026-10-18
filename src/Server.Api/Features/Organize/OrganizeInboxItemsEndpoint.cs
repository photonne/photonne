using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Organize;

public class OrganizeInboxItemsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class OrganizeInboxItemsResponse
{
    public List<TimelineResponse> Items { get; set; } = new();
}

/// <summary>
/// The inbox items among the given ids, newest first: what a proposed batch
/// (which travels as ids) still has to file. Backs the batch view, where the
/// batch is shown on its own before going back to the whole inbox; ids that
/// are no longer pending (moved, set aside, someone else's) are left out.
/// </summary>
public class OrganizeInboxItemsEndpoint : IEndpoint
{
    /// <summary>A month batch tops out well below this; it only bounds the IN list.</summary>
    private const int MaxIds = 5000;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/organize/inbox/items", Handle)
            .WithName("GetOrganizeInboxItems")
            .WithTags("Assets")
            .WithDescription("Returns the pending inbox items among the given asset ids, newest first")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<OrganizeInboxItemsResponse>, UnauthorizedHttpResult>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        [FromBody] OrganizeInboxItemsRequest request,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out _))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

        var ids = request.AssetIds.Distinct().Take(MaxIds).ToList();
        if (ids.Count == 0) return TypedResults.Ok(new OrganizeInboxItemsResponse());

        var items = await OrganizeQuery.Pending(dbContext, username)
            .Where(a => ids.Contains(a.Id))
            .OrderByDescending(a => a.CapturedAt)
            .ThenByDescending(a => a.FileModifiedAt)
            .Select(TimelineProjection.ToResponse)
            .ToListAsync(cancellationToken);

        await TimelineQuery.HydrateTagsAsync(dbContext, items, cancellationToken);

        return TypedResults.Ok(new OrganizeInboxItemsResponse { Items = items });
    }
}
