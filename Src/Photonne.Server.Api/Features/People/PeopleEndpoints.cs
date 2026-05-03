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
    DateTime UpdatedAt,
    int PendingSuggestionsCount);

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
        [FromServices] FaceClusteringService clustering,
        [FromServices] ILoggerFactory loggerFactory,
        [FromQuery] bool? includeHidden,
        [FromQuery] int? limit,
        [FromQuery] int? offset,
        [FromQuery] string? search,
        [FromQuery] string? sort,
        [FromQuery] string? sortDir,
        [FromQuery] bool? unnamedFirst,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId)) return Results.Unauthorized();

        // Lazy per-user clustering: users with shared-only access (e.g. an
        // external library someone else owns) never get a Person row from
        // detection-time hooks, which only run for the asset owner. The
        // documented contract is that opening /people triggers an "ensure up
        // to date" pass for the requesting user — online-attaching newly
        // visible faces and running a cooldown-guarded batch when there are
        // enough orphans. Done here (not in callers) so every consumer of
        // /api/people benefits without duplicating the call. Failures are
        // logged and swallowed: the user should still see whatever Persons
        // already exist rather than getting a 500.
        try
        {
            await clustering.EnsureUpToDateForUserAsync(userId, ct);
        }
        catch (Exception ex) when (!ct.IsCancellationRequested)
        {
            loggerFactory.CreateLogger<ListPeopleEndpoint>()
                .LogWarning(ex, "Lazy clustering pass for user {UserId} failed; returning current People snapshot", userId);
        }

        // Nullable bool params: client only sends them when true, so a missing
        // value must be treated as false (Minimal API would otherwise 400/500
        // on a missing required bool).
        var q = db.People.Where(p => p.OwnerId == userId);
        if (includeHidden != true) q = q.Where(p => !p.IsHidden);

        if (!string.IsNullOrWhiteSpace(search))
        {
            var s = search.Trim();
            // EF.Functions.ILike is the case-insensitive Contains in Npgsql.
            q = q.Where(p => p.Name != null && EF.Functions.ILike(p.Name, $"%{s}%"));
        }

        var total = await q.CountAsync(ct);

        // Ordering: optional "unnamed first" pre-sort puts Persons without a name
        // at the top so the user is nudged to label them. The body sort keys are
        // "name" or "facecount"; default mirrors the previous behavior (faces desc).
        IOrderedQueryable<Person> ordered = unnamedFirst == true
            ? q.OrderByDescending(p => p.Name == null)
            : q.OrderBy(p => 0); // identity placeholder

        var asc = string.Equals(sortDir, "asc", StringComparison.OrdinalIgnoreCase);
        var key = sort?.ToLowerInvariant();
        ordered = key switch
        {
            "name" => asc
                ? ordered.ThenBy(p => p.Name ?? string.Empty)
                : ordered.ThenByDescending(p => p.Name ?? string.Empty),
            "facecount" => asc
                ? ordered.ThenBy(p => p.FaceCount)
                : ordered.ThenByDescending(p => p.FaceCount),
            _ => ordered.ThenByDescending(p => p.FaceCount).ThenByDescending(p => p.UpdatedAt),
        };

        var page = await ordered
            .Skip(offset ?? 0)
            .Take(Math.Clamp(limit ?? 50, 1, 200))
            .Select(p => new PersonDto(
                p.Id, p.Name, p.CoverFaceId, p.FaceCount, p.IsHidden, p.CreatedAt, p.UpdatedAt,
                db.UserFaceAssignments.Count(uf => uf.UserId == userId
                                    && uf.SuggestedPersonId == p.Id
                                    && uf.PersonId == null
                                    && !uf.IsRejected
                                    && uf.Face.Asset.DeletedAt == null
                                    && !uf.Face.Asset.IsFileMissing)))
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
            .Select(x => new PersonDto(
                x.Id, x.Name, x.CoverFaceId, x.FaceCount, x.IsHidden, x.CreatedAt, x.UpdatedAt,
                db.UserFaceAssignments.Count(uf => uf.UserId == userId
                                    && uf.SuggestedPersonId == x.Id
                                    && uf.PersonId == null
                                    && !uf.IsRejected
                                    && uf.Face.Asset.DeletedAt == null
                                    && !uf.Face.Asset.IsFileMissing)))
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

public class UnhidePersonEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/people/{id:guid}/unhide", Handle)
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
        p.IsHidden = false;
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

        // The cover must be a face the current user has confirmed for this
        // Person — UserFaceAssignment rather than Face.PersonId is the source
        // of truth for "this user thinks face F is person P".
        var assignment = await db.UserFaceAssignments
            .FirstOrDefaultAsync(uf => uf.FaceId == faceId
                                       && uf.UserId == userId
                                       && uf.PersonId == id
                                       && !uf.IsRejected, ct);
        if (assignment == null) return Results.NotFound();

        person.CoverFaceId = faceId;
        person.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return Results.NoContent();
    }
}
