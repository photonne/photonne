using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
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
    bool IsRejected,
    Guid? SuggestedPersonId,
    float? SuggestedDistance);

internal static class FaceEndpointHelpers
{
    /// <summary>
    /// Loads (or creates) the current user's UserFaceAssignment for a face.
    /// The face must exist and the current user must have read access to its
    /// asset; otherwise returns <c>null</c> and the caller should 404.
    /// </summary>
    public static async Task<UserFaceAssignment?> LoadOrCreateAssignmentAsync(
        ApplicationDbContext db,
        AssetVisibilityService visibility,
        Guid faceId,
        Guid userId,
        CancellationToken ct)
    {
        var face = await db.Faces.AsNoTracking()
            .Include(f => f.Asset)
            .FirstOrDefaultAsync(f => f.Id == faceId, ct);
        if (face == null) return null;

        // Visibility check — owner shortcut is the common case.
        if (face.Asset.OwnerId != userId)
        {
            var scope = await visibility.GetScopeAsync(userId, ct);
            var folderOk = face.Asset.FolderId.HasValue && scope.AllowedFolderIds.Contains(face.Asset.FolderId.Value);
            var libOk = face.Asset.ExternalLibraryId.HasValue && scope.AllowedExternalLibraryIds.Contains(face.Asset.ExternalLibraryId.Value);
            var albumOk = scope.AlbumVisibleAssetIds.Contains(face.Asset.Id);
            if (!folderOk && !libOk && !albumOk) return null;
        }

        var assignment = await db.UserFaceAssignments
            .FirstOrDefaultAsync(uf => uf.FaceId == faceId && uf.UserId == userId, ct);
        if (assignment == null)
        {
            assignment = new UserFaceAssignment
            {
                FaceId = faceId,
                UserId = userId,
                UpdatedAt = DateTime.UtcNow,
            };
            db.UserFaceAssignments.Add(assignment);
        }
        return assignment;
    }
}

/// <summary>Returns all detected faces for an asset visible to the current
/// user. The returned identity fields (PersonId, IsManuallyAssigned, IsRejected,
/// suggestion) come from the user's <see cref="UserFaceAssignment"/> for that
/// face, not from the shared Face row — so two users seeing the same shared
/// asset get different identities, names, and suggestions.</summary>
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
        [FromServices] AssetVisibilityService visibility,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var asset = await db.Assets.AsNoTracking()
            .FirstOrDefaultAsync(a => a.Id == id && a.DeletedAt == null && !a.IsFileMissing, ct);
        if (asset == null) return Results.NotFound();

        // Visibility short-circuit: owner skips the lookup.
        if (asset.OwnerId != userId)
        {
            var scope = await visibility.GetScopeAsync(userId, ct);
            var folderOk = asset.FolderId.HasValue && scope.AllowedFolderIds.Contains(asset.FolderId.Value);
            var libOk = asset.ExternalLibraryId.HasValue && scope.AllowedExternalLibraryIds.Contains(asset.ExternalLibraryId.Value);
            var albumOk = scope.AlbumVisibleAssetIds.Contains(asset.Id);
            if (!folderOk && !libOk && !albumOk) return Results.NotFound();
        }

        // LEFT JOIN Face with the user's assignment row (if any). Faces with
        // no assignment yet show up as orphans; faces the user has rejected
        // are filtered out so the overlay doesn't keep painting them.
        var faces = await (
            from f in db.Faces.AsNoTracking()
            where f.AssetId == id
            join ufRaw in db.UserFaceAssignments.AsNoTracking().Where(x => x.UserId == userId)
                on f.Id equals ufRaw.FaceId into ufGroup
            from uf in ufGroup.DefaultIfEmpty()
            where uf == null || !uf.IsRejected
            select new FaceDto(
                f.Id, f.AssetId,
                uf == null ? null : uf.PersonId,
                f.BoundingBoxX, f.BoundingBoxY, f.BoundingBoxW, f.BoundingBoxH,
                f.Confidence,
                uf != null && uf.IsManuallyAssigned,
                uf != null && uf.IsRejected,
                uf == null ? null : uf.SuggestedPersonId,
                uf == null ? null : uf.SuggestedDistance))
            .ToListAsync(ct);

        return Results.Ok(faces);
    }
}

/// <summary>Manually assigns a face to a person (existing or new) for the
/// current user. Marks the assignment as manual so per-user clustering
/// ignores it on subsequent runs.</summary>
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
        [FromServices] AssetVisibilityService visibility,
        [FromServices] FaceClusteringService clustering,
        Guid id,
        [FromBody] AssignFaceRequest body,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var assignment = await FaceEndpointHelpers.LoadOrCreateAssignmentAsync(db, visibility, id, userId, ct);
        if (assignment == null) return Results.NotFound();

        Guid? targetPersonId = body.PersonId;

        if (targetPersonId == null && !string.IsNullOrWhiteSpace(body.NewPersonName))
        {
            var newPerson = new Person
            {
                OwnerId = userId,
                Name = body.NewPersonName.Trim(),
                CoverFaceId = id,
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

        assignment.PersonId = targetPersonId;
        assignment.IsManuallyAssigned = true;
        assignment.IsRejected = false;
        assignment.SuggestedPersonId = null;
        assignment.SuggestedDistance = null;
        assignment.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsForUserAsync(userId, ct);

        return Results.Ok(new { Id = id, PersonId = targetPersonId });
    }
}

/// <summary>Marks a face as a false positive **for the current user**. Other
/// users still see it. The flag persists across re-detection (we soft-reject
/// rather than delete the Face row, so the next ML pass doesn't re-create
/// it).</summary>
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
        [FromServices] AssetVisibilityService visibility,
        [FromServices] FaceClusteringService clustering,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var assignment = await FaceEndpointHelpers.LoadOrCreateAssignmentAsync(db, visibility, id, userId, ct);
        if (assignment == null) return Results.NotFound();

        assignment.IsRejected = true;
        assignment.PersonId = null;
        assignment.IsManuallyAssigned = true; // prevents reassignment by clustering
        assignment.SuggestedPersonId = null;
        assignment.SuggestedDistance = null;
        assignment.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsForUserAsync(userId, ct);
        await clustering.CleanupEmptyPersonsAsync(userId, ct);
        return Results.NoContent();
    }
}

/// <summary>Detaches a face from its current Person without rejecting it. The
/// face stays valid (not a false positive) but becomes orphan for the current
/// user; the manual flag prevents online clustering from reattaching it
/// immediately. Use this when a face was auto-assigned to the wrong Person
/// and the user wants to leave it unlabeled.</summary>
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
        [FromServices] AssetVisibilityService visibility,
        [FromServices] FaceClusteringService clustering,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var assignment = await FaceEndpointHelpers.LoadOrCreateAssignmentAsync(db, visibility, id, userId, ct);
        if (assignment == null) return Results.NotFound();

        assignment.PersonId = null;
        assignment.IsManuallyAssigned = true;
        assignment.IsRejected = false;
        assignment.SuggestedPersonId = null;
        assignment.SuggestedDistance = null;
        assignment.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsForUserAsync(userId, ct);
        await clustering.CleanupEmptyPersonsAsync(userId, ct);
        return Results.NoContent();
    }
}

/// <summary>Confirms the proactive suggestion stored on the user's
/// assignment: equivalent to manually assigning the face to the suggested
/// Person. 404 if the user has no pending suggestion (e.g. they dismissed it
/// concurrently).</summary>
public class AcceptFaceSuggestionEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/faces/{id:guid}/accept-suggestion", Handle)
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

        var assignment = await db.UserFaceAssignments
            .FirstOrDefaultAsync(uf => uf.FaceId == id && uf.UserId == userId, ct);
        if (assignment == null || assignment.SuggestedPersonId == null) return Results.NotFound();

        var personOk = await db.People
            .AnyAsync(p => p.Id == assignment.SuggestedPersonId && p.OwnerId == userId, ct);
        if (!personOk) return Results.NotFound();

        var personId = assignment.SuggestedPersonId.Value;
        assignment.PersonId = personId;
        assignment.IsManuallyAssigned = true;
        assignment.IsRejected = false;
        assignment.SuggestedPersonId = null;
        assignment.SuggestedDistance = null;
        assignment.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);

        await clustering.RecomputeFaceCountsForUserAsync(userId, ct);
        return Results.Ok(new { Id = id, PersonId = personId });
    }
}

/// <summary>Removes the suggestion hint from the user's assignment without
/// rejecting the face or blocking future re-suggestion. The face stays
/// orphan for the user; the next clustering pass may surface the same
/// suggestion again — by design (dismiss is non-sticky).</summary>
public class DismissFaceSuggestionEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/faces/{id:guid}/dismiss-suggestion", Handle)
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

        var assignment = await db.UserFaceAssignments
            .FirstOrDefaultAsync(uf => uf.FaceId == id && uf.UserId == userId, ct);
        if (assignment == null) return Results.NoContent();

        assignment.SuggestedPersonId = null;
        assignment.SuggestedDistance = null;
        assignment.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(ct);
        return Results.NoContent();
    }
}
