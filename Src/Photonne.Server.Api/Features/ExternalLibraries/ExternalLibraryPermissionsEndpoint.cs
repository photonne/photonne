using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.ExternalLibraries;

// ─── DTOs ────────────────────────────────────────────────────────────────────

public record ExternalLibraryPermissionDto(
    Guid Id,
    Guid UserId,
    string Username,
    string Email,
    bool CanRead,
    DateTime GrantedAt,
    Guid? GrantedByUserId);

public record SetExternalLibraryPermissionRequest(Guid UserId, bool CanRead);

// ─── Endpoint ────────────────────────────────────────────────────────────────

public class ExternalLibraryPermissionsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/libraries/{libraryId:guid}/permissions")
            .WithTags("External Libraries")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("", GetPermissions)
            .WithName("GetExternalLibraryPermissions")
            .WithDescription("Lists all user permissions for an external library");

        group.MapPost("", SetPermission)
            .WithName("SetExternalLibraryPermission")
            .WithDescription("Grants or updates a user's access to an external library");

        group.MapDelete("{userId:guid}", RemovePermission)
            .WithName("RemoveExternalLibraryPermission")
            .WithDescription("Revokes a user's access to an external library");
    }

    // GET /api/libraries/{libraryId}/permissions
    private static async Task<IResult> GetPermissions(
        Guid libraryId,
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var adminId = GetUserId(user);
        if (adminId == null) return Results.Unauthorized();

        var library = await db.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == libraryId && l.OwnerId == adminId.Value, ct);

        if (library is null) return Results.NotFound();

        var permissions = await db.ExternalLibraryPermissions
            .Where(p => p.ExternalLibraryId == libraryId)
            .Include(p => p.User)
            .Select(p => new ExternalLibraryPermissionDto(
                p.Id,
                p.UserId,
                p.User.Username,
                p.User.Email,
                p.CanRead,
                p.GrantedAt,
                p.GrantedByUserId))
            .ToListAsync(ct);

        return Results.Ok(permissions);
    }

    // POST /api/libraries/{libraryId}/permissions
    private static async Task<IResult> SetPermission(
        Guid libraryId,
        [FromBody] SetExternalLibraryPermissionRequest request,
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var adminId = GetUserId(user);
        if (adminId == null) return Results.Unauthorized();

        var library = await db.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == libraryId && l.OwnerId == adminId.Value, ct);

        if (library is null) return Results.NotFound();

        var targetUser = await db.Users.FirstOrDefaultAsync(u => u.Id == request.UserId, ct);
        if (targetUser is null)
            return Results.NotFound(new { error = $"User {request.UserId} not found" });

        if (request.UserId == adminId.Value)
            return Results.BadRequest(new { error = "Cannot set permissions for the library owner" });

        var existing = await db.ExternalLibraryPermissions
            .FirstOrDefaultAsync(p => p.ExternalLibraryId == libraryId && p.UserId == request.UserId, ct);

        ExternalLibraryPermission permission;
        if (existing is not null)
        {
            existing.CanRead = request.CanRead;
            existing.GrantedByUserId = adminId.Value;
            existing.GrantedAt = DateTime.UtcNow;
            permission = existing;
        }
        else
        {
            permission = new ExternalLibraryPermission
            {
                ExternalLibraryId = libraryId,
                UserId = request.UserId,
                CanRead = request.CanRead,
                GrantedByUserId = adminId.Value,
                GrantedAt = DateTime.UtcNow
            };
            db.ExternalLibraryPermissions.Add(permission);
        }

        await db.SaveChangesAsync(ct);

        return Results.Ok(new ExternalLibraryPermissionDto(
            permission.Id,
            permission.UserId,
            targetUser.Username,
            targetUser.Email,
            permission.CanRead,
            permission.GrantedAt,
            permission.GrantedByUserId));
    }

    // DELETE /api/libraries/{libraryId}/permissions/{userId}
    private static async Task<IResult> RemovePermission(
        Guid libraryId,
        Guid userId,
        [FromServices] ApplicationDbContext db,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var adminId = GetUserId(user);
        if (adminId == null) return Results.Unauthorized();

        var library = await db.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == libraryId && l.OwnerId == adminId.Value, ct);

        if (library is null) return Results.NotFound();

        var permission = await db.ExternalLibraryPermissions
            .FirstOrDefaultAsync(p => p.ExternalLibraryId == libraryId && p.UserId == userId, ct);

        if (permission is null) return Results.NotFound();

        db.ExternalLibraryPermissions.Remove(permission);
        await db.SaveChangesAsync(ct);

        return Results.NoContent();
    }

    private static Guid? GetUserId(ClaimsPrincipal user)
    {
        var value = user.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return Guid.TryParse(value, out var id) ? id : null;
    }
}
