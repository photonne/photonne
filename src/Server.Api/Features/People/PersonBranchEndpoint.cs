using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.People;

/// <param name="PersonId">De quién nace, o null para que nazca por su cuenta.</param>
public record SetPersonBranchRequest(Guid? PersonId);

/// <summary>
/// Guarda de la estela de quién nace una persona en la constelación: la
/// sobrina, de la hermana.
/// </summary>
public class SetPersonBranchEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPut("/api/people/{id:guid}/branch", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<Results<NoContent, UnauthorizedHttpResult, BadRequest<ApiError>, NotFound>> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        [FromBody] SetPersonBranchRequest body,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return TypedResults.Unauthorized();

        var p = await db.People.FirstOrDefaultAsync(x => x.Id == id && x.OwnerId == userId, ct);
        if (p == null) return TypedResults.NotFound();

        if (body.PersonId is Guid from)
        {
            if (from == id)
                return TypedResults.BadRequest(new ApiError("A person cannot branch from itself", "branch_same_person"));
            if (!await db.People.AnyAsync(x => x.Id == from && x.OwnerId == userId, ct))
                return TypedResults.BadRequest(new ApiError("The person to branch from does not exist", "branch_person_not_found"));

            var branches = await db.People
                .Where(x => x.OwnerId == userId && x.BranchFromPersonId != null)
                .ToDictionaryAsync(x => x.Id, x => x.BranchFromPersonId!.Value, ct);
            if (PersonBranches.Reaches(branches, from, to: id))
                return TypedResults.BadRequest(new ApiError("That person already branches from this one", "branch_cycle"));
        }

        p.BranchFromPersonId = body.PersonId;
        p.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return TypedResults.NoContent();
    }
}
