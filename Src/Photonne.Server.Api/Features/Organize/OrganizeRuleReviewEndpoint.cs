using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Folders;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Organize;

/// <summary>
/// Full breakdown of a condition rule scoped to the "Para organizar" inbox: every
/// matching asset id, grouped by capture year (newest year first; ids within a
/// year by capture date desc). Powers the "Revisar" grid that shows all the
/// thumbnails about to move, grouped by the Year subfolder they'll land in. Uses
/// the same resolver as the preview/move so what you review is what moves.
/// </summary>
public class OrganizeRuleReviewEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/organize/rule/review", Handle)
            .WithName("ReviewOrganizeRule")
            .WithTags("Assets")
            .WithDescription("Resolve a condition rule within the inbox and return every matching id grouped by capture year")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SmartAlbumResolver resolver,
        ClaimsPrincipal user,
        [FromBody] OrganizeRuleReviewRequest request,
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

        var rows = await query
            .OrderByDescending(a => a.CapturedAt)
            .ThenBy(a => a.Id)
            .Select(a => new { a.Id, Year = a.CapturedAt.Year })
            .ToListAsync(cancellationToken);

        var groups = rows
            .GroupBy(r => r.Year)
            .OrderByDescending(g => g.Key)
            .Select(g => new YearGroup(g.Key, g.Select(r => r.Id).ToList()))
            .ToList();

        return Results.Ok(new OrganizeRuleReviewResponse { Groups = groups });
    }

    public class OrganizeRuleReviewRequest
    {
        public SmartRuleNode? Rule { get; set; }
    }

    public class OrganizeRuleReviewResponse
    {
        public IReadOnlyList<YearGroup> Groups { get; set; } = [];
    }
}
