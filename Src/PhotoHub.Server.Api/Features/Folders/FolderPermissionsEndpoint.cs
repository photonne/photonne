using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Server.Api.Shared.Models;
using Scalar.AspNetCore;

namespace PhotoHub.Server.Api.Features.Folders;

public class FolderPermissionsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/folders/{folderId:guid}/permissions")
            .WithTags("Folders")
            .RequireAuthorization();

        group.MapGet("", GetFolderPermissions)
            .WithName("GetFolderPermissions")
            .WithDescription("Gets all permissions for a folder");

        group.MapPost("", SetFolderPermission)
            .WithName("SetFolderPermission")
            .WithDescription("Sets or updates folder permissions for a user");

        group.MapDelete("{userId:guid}", RemoveFolderPermission)
            .WithName("RemoveFolderPermission")
            .WithDescription("Removes folder permission for a user");
    }

    private async Task<IResult> GetFolderPermissions(
        Guid folderId,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var currentUserId))
        {
            return Results.Unauthorized();
        }

        var isAdmin = user.IsInRole("Admin");

        // Validate folder exists and user has access to manage it
        var folder = await dbContext.Folders
            .Include(f => f.Permissions)
            .ThenInclude(p => p.User)
            .FirstOrDefaultAsync(f => f.Id == folderId, cancellationToken);

        if (folder == null)
        {
            return Results.NotFound(new { error = "Folder not found" });
        }

        // Must be admin or have CanManagePermissions
        var hasAccess = isAdmin || 
            folder.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return Results.Forbid();
        }

        var permissions = folder.Permissions
            .Where(p =>
            {
                // Excluir la entrada del propietario: en carpetas personales el dueño se identifica
                // por la ruta; en carpetas compartidas, el creador tiene un permiso auto-concedido.
                var isOwnerEntry = folder.Path.Contains($"/users/{p.UserId}") ||
                    (folder.Path.StartsWith("/assets/shared", StringComparison.OrdinalIgnoreCase) &&
                     p.GrantedByUserId == p.UserId);
                return !isOwnerEntry;
            })
            .Select(p => new FolderPermissionDto
            {
                Id = p.Id,
                UserId = p.UserId,
                Username = p.User.Username,
                Email = p.User.Email,
                CanRead = p.CanRead,
                CanWrite = p.CanWrite,
                CanDelete = p.CanDelete,
                CanManagePermissions = p.CanManagePermissions,
                GrantedAt = p.GrantedAt,
                GrantedByUserId = p.GrantedByUserId
            }).ToList();

        return Results.Ok(permissions);
    }

    private async Task<IResult> SetFolderPermission(
        Guid folderId,
        [FromBody] SetFolderPermissionRequest request,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var currentUserId))
        {
            return Results.Unauthorized();
        }

        var isAdmin = user.IsInRole("Admin");

        // Validate folder exists
        var folder = await dbContext.Folders
            .Include(f => f.Permissions)
            .FirstOrDefaultAsync(f => f.Id == folderId, cancellationToken);

        if (folder == null)
        {
            return Results.NotFound(new { error = "Folder not found" });
        }

        // Must be admin or have CanManagePermissions
        var isOwner = folder.Path.Contains($"/users/{currentUserId}");
        var hasAccess = isAdmin || isOwner ||
            folder.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return Results.Forbid();
        }

        // No permitir modificar permisos del propietario
        if (request.UserId == currentUserId && isOwner)
        {
            return Results.BadRequest(new { error = "No se pueden modificar los permisos del propietario de la carpeta." });
        }

        // Validate target user exists
        var targetUser = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == request.UserId, cancellationToken);

        if (targetUser == null)
        {
            return Results.NotFound(new { error = $"User with ID {request.UserId} not found" });
        }

        // Check if permission already exists
        var existingPermission = await dbContext.FolderPermissions
            .FirstOrDefaultAsync(
                p => p.FolderId == folderId && p.UserId == request.UserId,
                cancellationToken);

        FolderPermission permission;

        if (existingPermission != null)
        {
            // Update existing permission
            existingPermission.CanRead = request.CanRead;
            existingPermission.CanWrite = request.CanWrite;
            existingPermission.CanDelete = request.CanDelete;
            existingPermission.CanManagePermissions = request.CanManagePermissions;
            existingPermission.GrantedByUserId = currentUserId;
            existingPermission.GrantedAt = DateTime.UtcNow;
            permission = existingPermission;
        }
        else
        {
            // Create new permission
            permission = new FolderPermission
            {
                FolderId = folderId,
                UserId = request.UserId,
                CanRead = request.CanRead,
                CanWrite = request.CanWrite,
                CanDelete = request.CanDelete,
                CanManagePermissions = request.CanManagePermissions,
                GrantedByUserId = currentUserId,
                GrantedAt = DateTime.UtcNow
            };
            dbContext.FolderPermissions.Add(permission);
        }

        await dbContext.SaveChangesAsync(cancellationToken);

        // Load related data for response
        await dbContext.Entry(permission)
            .Reference(p => p.User)
            .LoadAsync(cancellationToken);

        var response = new FolderPermissionDto
        {
            Id = permission.Id,
            UserId = permission.UserId,
            Username = permission.User.Username,
            Email = permission.User.Email,
            CanRead = permission.CanRead,
            CanWrite = permission.CanWrite,
            CanDelete = permission.CanDelete,
            CanManagePermissions = permission.CanManagePermissions,
            GrantedAt = permission.GrantedAt,
            GrantedByUserId = permission.GrantedByUserId
        };

        return Results.Ok(response);
    }

    private async Task<IResult> RemoveFolderPermission(
        Guid folderId,
        Guid userId,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var currentUserId))
        {
            return Results.Unauthorized();
        }

        var isAdmin = user.IsInRole("Admin");

        // Validate folder exists
        var folder = await dbContext.Folders
            .Include(f => f.Permissions)
            .FirstOrDefaultAsync(f => f.Id == folderId, cancellationToken);

        if (folder == null)
        {
            return Results.NotFound(new { error = "Folder not found" });
        }

        // Must be admin or have CanManagePermissions
        var isOwner = folder.Path.Contains($"/users/{currentUserId}");
        var hasAccess = isAdmin || isOwner ||
            folder.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return Results.Forbid();
        }

        // No permitir eliminar permisos del propietario
        if (userId == currentUserId && isOwner)
        {
            return Results.BadRequest(new { error = "No se pueden eliminar los permisos del propietario de la carpeta." });
        }

        var permission = await dbContext.FolderPermissions
            .FirstOrDefaultAsync(
                p => p.FolderId == folderId && p.UserId == userId,
                cancellationToken);

        if (permission == null)
        {
            return Results.NotFound(new { error = "Permission not found" });
        }

        dbContext.FolderPermissions.Remove(permission);
        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.NoContent();
    }
}

public class SetFolderPermissionRequest
{
    public Guid UserId { get; set; }
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
}

public class FolderPermissionDto
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    public DateTime GrantedAt { get; set; }
    public Guid? GrantedByUserId { get; set; }
}
