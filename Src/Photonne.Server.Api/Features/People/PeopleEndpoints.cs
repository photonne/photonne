using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.FaceRecognition;

namespace Photonne.Server.Api.Features.People;

public record PersonDto(
    Guid Id,
    string? Name,
    Guid? CoverFaceId,
    int FaceCount,
    bool IsHidden,
    DateTime CreatedAt,
    DateTime UpdatedAt);

public record RenamePersonRequest(string? Name);

public record AssignFaceRequest(Guid? PersonId, string? NewPersonName);

/// <summary>Lists, edits, merges and hides face clusters (Persons) owned by the current user.</summary>
public class ListPeopleEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/people", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromQuery] bool includeHidden,
        [FromQuery] int? limit,
        [FromQuery] int? offset,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var q = db.People.Where(p => p.OwnerId == userId);
        if (!includeHidden) q = q.Where(p => !p.IsHidden);

        var total = await q.CountAsync(ct);
        var page = await q
            .OrderByDescending(p => p.FaceCount)
            .ThenByDescending(p => p.UpdatedAt)
            .Skip(offset ?? 0)
            .Take(Math.Clamp(limit ?? 50, 1, 200))
            .Select(p => new PersonDto(p.Id, p.Name, p.CoverFaceId, p.FaceCount, p.IsHidden, p.CreatedAt, p.UpdatedAt))
            .ToListAsync(ct);

        return Results.Ok(new { total, items = page });
    }

    internal static bool TryGetUserId(ClaimsPrincipal user, out Guid id)
    {
        id = Guid.Empty;
        var c = user.FindFirst(ClaimTypes.NameIdentifier);
        return c != null && Guid.TryParse(c.Value, out id);
    }
}

public class GetPersonEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/people/{id:guid}", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var p = await db.People
            .Where(x => x.Id == id && x.OwnerId == userId)
            .Select(x => new PersonDto(x.Id, x.Name, x.CoverFaceId, x.FaceCount, x.IsHidden, x.CreatedAt, x.UpdatedAt))
            .FirstOrDefaultAsync(ct);

        return p == null ? Results.NotFound() : Results.Ok(p);
    }
}

public class RenamePersonEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPatch("/api/people/{id:guid}", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        [FromBody] RenamePersonRequest body,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var p = await db.People.FirstOrDefaultAsync(x => x.Id == id && x.OwnerId == userId, ct);
        if (p == null) return Results.NotFound();

        p.Name = string.IsNullOrWhiteSpace(body.Name) ? null : body.Name.Trim();
        p.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return Results.NoContent();
    }
}

public class HidePersonEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/people/{id:guid}/hide", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();
        var p = await db.People.FirstOrDefaultAsync(x => x.Id == id && x.OwnerId == userId, ct);
        if (p == null) return Results.NotFound();
        p.IsHidden = true;
        p.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return Results.NoContent();
    }
}

public class MergePeopleEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/people/{id:guid}/merge/{otherId:guid}", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] FaceClusteringService clustering,
        Guid id,
        Guid otherId,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();
        if (id == otherId) return Results.BadRequest(new { error = "Cannot merge a person with itself" });

        var pair = await db.People.Where(p => p.OwnerId == userId && (p.Id == id || p.Id == otherId)).ToListAsync(ct);
        if (pair.Count != 2) return Results.NotFound();

        await clustering.MergeAsync(userId, targetPersonId: id, sourcePersonId: otherId, ct);
        return Results.NoContent();
    }
}

public class SetCoverFaceEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/people/{id:guid}/cover/{faceId:guid}", Handle)
            .WithTags("People")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        Guid faceId,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var person = await db.People.FirstOrDefaultAsync(p => p.Id == id && p.OwnerId == userId, ct);
        if (person == null) return Results.NotFound();

        var face = await db.Faces.Include(f => f.Asset)
            .FirstOrDefaultAsync(f => f.Id == faceId && f.PersonId == id && f.Asset.OwnerId == userId, ct);
        if (face == null) return Results.NotFound();

        person.CoverFaceId = faceId;
        person.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return Results.NoContent();
    }
}
