using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.FaceRecognition;

namespace Photonne.Server.Api.Features.People;

public record FaceDto(
    Guid Id,
    Guid AssetId,
    Guid? PersonId,
    float BoundingBoxX,
    float BoundingBoxY,
    float BoundingBoxW,
    float BoundingBoxH,
    float Confidence,
    bool IsManuallyAssigned,
    bool IsRejected);

/// <summary>Returns all detected faces for an asset (current user must own it).</summary>
public class ListFacesForAssetEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/{id:guid}/faces", Handle)
            .WithTags("Faces")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var asset = await db.Assets.AsNoTracking()
            .FirstOrDefaultAsync(a => a.Id == id && a.OwnerId == userId, ct);
        if (asset == null) return Results.NotFound();

        var faces = await db.Faces.AsNoTracking()
            .Where(f => f.AssetId == id && !f.IsRejected)
            .Select(f => new FaceDto(
                f.Id, f.AssetId, f.PersonId,
                f.BoundingBoxX, f.BoundingBoxY, f.BoundingBoxW, f.BoundingBoxH,
                f.Confidence, f.IsManuallyAssigned, f.IsRejected))
            .ToListAsync(ct);

        return Results.Ok(faces);
    }
}

/// <summary>Manually assigns a face to a person (existing or new). Marks the face as manual so clustering ignores it.</summary>
public class AssignFaceEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/faces/{id:guid}/assign", Handle)
            .WithTags("Faces")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] FaceClusteringService clustering,
        Guid id,
        [FromBody] AssignFaceRequest body,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var face = await db.Faces.Include(f => f.Asset)
            .FirstOrDefaultAsync(f => f.Id == id && f.Asset.OwnerId == userId, ct);
        if (face == null) return Results.NotFound();

        Guid? targetPersonId = body.PersonId;

        if (targetPersonId == null && !string.IsNullOrWhiteSpace(body.NewPersonName))
        {
            var newPerson = new Person
            {
                OwnerId = userId,
                Name = body.NewPersonName.Trim(),
                CoverFaceId = face.Id,
                FaceCount = 0,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow,
            };
            db.People.Add(newPerson);
            await db.SaveChangesAsync(ct);
            targetPersonId = newPerson.Id;
        }
        else if (targetPersonId != null)
        {
            var p = await db.People.FirstOrDefaultAsync(p => p.Id == targetPersonId && p.OwnerId == userId, ct);
            if (p == null) return Results.NotFound();
        }
        else
        {
            return Results.BadRequest(new { error = "Provide PersonId or NewPersonName" });
        }

        face.PersonId = targetPersonId;
        face.IsManuallyAssigned = true;
        face.IsRejected = false;
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsAsync(userId, ct);

        return Results.Ok(new { face.Id, face.PersonId });
    }
}

/// <summary>Rejects a face (false positive). Soft-delete: kept in DB to avoid re-detection on rerun.</summary>
public class RejectFaceEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapDelete("/api/faces/{id:guid}", Handle)
            .WithTags("Faces")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] FaceClusteringService clustering,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var face = await db.Faces.Include(f => f.Asset)
            .FirstOrDefaultAsync(f => f.Id == id && f.Asset.OwnerId == userId, ct);
        if (face == null) return Results.NotFound();

        face.IsRejected = true;
        face.PersonId = null;
        face.IsManuallyAssigned = true; // prevents reassignment by clustering
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsAsync(userId, ct);
        return Results.NoContent();
    }
}

/// <summary>Detaches a face from its current Person without rejecting it. The face
/// stays valid (not a false positive) but becomes orphan; IsManuallyAssigned is set
/// so the online clustering won't immediately reattach it. Use this when a face was
/// auto-assigned to the wrong Person and the user wants to leave it unlabeled.</summary>
public class UnassignFaceEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/faces/{id:guid}/unassign", Handle)
            .WithTags("Faces")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] FaceClusteringService clustering,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var face = await db.Faces.Include(f => f.Asset)
            .FirstOrDefaultAsync(f => f.Id == id && f.Asset.OwnerId == userId, ct);
        if (face == null) return Results.NotFound();

        face.PersonId = null;
        face.IsManuallyAssigned = true;
        face.IsRejected = false;
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsAsync(userId, ct);
        return Results.NoContent();
    }
}
