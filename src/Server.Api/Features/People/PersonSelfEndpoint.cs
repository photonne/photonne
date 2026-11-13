using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.People;

public record SetPersonSelfRequest(bool IsSelf);

/// <summary>
/// Marca (o desmarca) una persona como el propio usuario. Solo puede haber
/// una: marcar otra desmarca la anterior.
/// </summary>
public class SetPersonSelfEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPut("/api/people/{id:guid}/self", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<Results<NoContent, UnauthorizedHttpResult, NotFound>> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        [FromBody] SetPersonSelfRequest body,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return TypedResults.Unauthorized();

        var p = await db.People.FirstOrDefaultAsync(x => x.Id == id && x.OwnerId == userId, ct);
        if (p == null) return TypedResults.NotFound();

        if (body.IsSelf)
        {
            var others = await db.People
                .Where(x => x.OwnerId == userId && x.IsSelf && x.Id != id)
                .ToListAsync(ct);
            foreach (var other in others)
            {
                other.IsSelf = false;
                other.UpdatedAt = DateTime.UtcNow;
            }
        }

        p.IsSelf = body.IsSelf;
        p.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return TypedResults.NoContent();
    }
}
