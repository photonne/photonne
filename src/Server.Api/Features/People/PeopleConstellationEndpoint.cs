using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.People;

/// <summary>Fotos de una persona en un año natural.</summary>
public sealed record ConstellationYearDto(int Year, int AssetCount);

/// <summary>La vida de una persona en la biblioteca: de su primera foto a la última.</summary>
public sealed record ConstellationPersonDto(
    Guid Id,
    string Name,
    Guid? CoverFaceId,
    string? RelationshipCategory,
    string? RelationshipLabel,
    DateTime FirstCapturedAt,
    DateTime LastCapturedAt,
    int AssetCount,
    List<ConstellationYearDto> Years);

public sealed record PeopleConstellationResponse(List<ConstellationPersonDto> People);

/// <summary>
/// Datos de la constelación: por cada persona con nombre y visible, su relación
/// con el usuario, la primera y la última foto en la que sale y cuántas fotos
/// tiene cada año. Con eso el cliente dibuja una estela por persona y sabe
/// dónde se detiene.
/// </summary>
public class PeopleConstellationEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/people/constellation", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<PeopleConstellationResponse>, UnauthorizedHttpResult>> Handle(
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return TypedResults.Unauthorized();

        var people = await db.People.AsNoTracking()
            .Where(p => p.OwnerId == userId && !p.IsHidden && p.Name != null)
            .Select(p => new { p.Id, Name = p.Name!, p.CoverFaceId, p.RelationshipCategory, p.RelationshipLabel })
            .ToListAsync(ct);

        // Una foto con dos caras de la misma persona cuenta una vez: primero
        // (persona, foto) sin repetir y después se agrupa por año. El mismo
        // filtro que la ficha de la persona: caras confirmadas por este usuario
        // en fotos que siguen en la biblioteca.
        var buckets = await db.UserFaceAssignments.AsNoTracking()
            .Where(uf => uf.UserId == userId
                        && uf.PersonId != null
                        && !uf.IsRejected
                        && !uf.Person!.IsHidden
                        && uf.Person.Name != null
                        && uf.Face.Asset.DeletedAt == null
                        && !uf.Face.Asset.IsFileMissing)
            .Select(uf => new { PersonId = uf.PersonId!.Value, uf.Face.AssetId, uf.Face.Asset.CapturedAt })
            .Distinct()
            .GroupBy(x => new { x.PersonId, x.CapturedAt.Year })
            .Select(g => new
            {
                g.Key.PersonId,
                g.Key.Year,
                Count = g.Count(),
                First = g.Min(x => x.CapturedAt),
                Last = g.Max(x => x.CapturedAt)
            })
            .ToListAsync(ct);

        var byPerson = buckets.ToLookup(b => b.PersonId);

        var result = people
            .Where(p => byPerson.Contains(p.Id))
            .Select(p =>
            {
                var years = byPerson[p.Id].OrderBy(b => b.Year).ToList();
                return new ConstellationPersonDto(
                    p.Id,
                    p.Name,
                    p.CoverFaceId,
                    p.RelationshipCategory,
                    p.RelationshipLabel,
                    years.Min(b => b.First),
                    years.Max(b => b.Last),
                    years.Sum(b => b.Count),
                    years.Select(b => new ConstellationYearDto(b.Year, b.Count)).ToList());
            })
            .OrderBy(p => p.FirstCapturedAt)
            .ThenBy(p => p.Name, StringComparer.OrdinalIgnoreCase)
            .ToList();

        return TypedResults.Ok(new PeopleConstellationResponse(result));
    }
}
