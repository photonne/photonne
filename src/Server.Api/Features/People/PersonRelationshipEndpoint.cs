using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.People;

/// <param name="Category">Código de <see cref="PersonRelationship.Categories"/>, o null para quitar la relación.</param>
/// <param name="Label">Parentesco concreto en texto libre ("Madre"). Opcional; se ignora sin categoría.</param>
public record SetPersonRelationshipRequest(string? Category, string? Label = null);

/// <summary>Guarda qué es una persona para el usuario: familia, pareja, amistad…</summary>
public class SetPersonRelationshipEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPut("/api/people/{id:guid}/relationship", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<Results<NoContent, UnauthorizedHttpResult, BadRequest<ApiError>, NotFound>> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        [FromBody] SetPersonRelationshipRequest body,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return TypedResults.Unauthorized();

        var category = string.IsNullOrWhiteSpace(body.Category) ? null : body.Category.Trim().ToLowerInvariant();
        if (category != null && !PersonRelationship.Categories.Contains(category))
            return TypedResults.BadRequest(new ApiError("Unknown relationship category", "invalid_relationship_category"));

        var label = category == null || string.IsNullOrWhiteSpace(body.Label) ? null : body.Label.Trim();
        if (label is { Length: > PersonRelationship.MaxLabelLength })
            return TypedResults.BadRequest(new ApiError("Relationship label is too long", "relationship_label_too_long"));

        var p = await db.People.FirstOrDefaultAsync(x => x.Id == id && x.OwnerId == userId, ct);
        if (p == null) return TypedResults.NotFound();

        p.RelationshipCategory = category;
        p.RelationshipLabel = label;
        p.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return TypedResults.NoContent();
    }
}
