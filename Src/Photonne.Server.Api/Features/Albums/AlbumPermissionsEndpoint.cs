using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Albums;

public class AlbumPermissionsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/albums/{albumId:guid}/permissions")
            .WithTags("Albums")
            .RequireAuthorization();

        group.MapGet("", GetAlbumPermissions)
            .WithName("GetAlbumPermissions")
            .WithDescription("Gets all permissions for an album");

        group.MapPost("", SetAlbumPermission)
            .WithName("SetAlbumPermission")
            .WithDescription("Sets or updates album permissions for a user");

        group.MapDelete("{userId:guid}", RemoveAlbumPermission)
            .WithName("RemoveAlbumPermission")
            .WithDescription("Removes album permission for a user");
    }

    private async Task<IResult> GetAlbumPermissions(
        Guid albumId,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var currentUserId))
        {
            return Results.Unauthorized();
        }

        // Verificar que el usuario tenga acceso al álbum
        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .ThenInclude(p => p.User)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return Results.NotFound(new { error = "Album not found" });
        }

        // Verificar permisos: debe ser el propietario o tener CanManagePermissions
        var hasAccess = album.OwnerId == currentUserId ||
            album.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return Results.Forbid();
        }

        var permissions = album.Permissions.Select(p => new AlbumPermissionDto
        {
            Id = p.Id,
            UserId = p.UserId,
            Username = p.User.Username,
            Email = p.User.Email,
            CanView = p.CanView,
            CanEdit = p.CanEdit,
            CanDelete = p.CanDelete,
            CanManagePermissions = p.CanManagePermissions,
            GrantedAt = p.GrantedAt,
            GrantedByUserId = p.GrantedByUserId
        }).ToList();

        return Results.Ok(permissions);
    }

    private async Task<IResult> SetAlbumPermission(
        Guid albumId,
        [FromBody] SetAlbumPermissionRequest request,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var currentUserId))
        {
            return Results.Unauthorized();
        }

        // Validar que el álbum existe
        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return Results.NotFound(new { error = "Album not found" });
        }

        // Verificar permisos: debe ser el propietario o tener CanManagePermissions
        var hasAccess = album.OwnerId == currentUserId ||
            album.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return Results.Forbid();
        }

        // Validar que el usuario existe
        var targetUser = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == request.UserId, cancellationToken);

        if (targetUser == null)
        {
            return Results.NotFound(new { error = $"User with ID {request.UserId} not found" });
        }

        // No permitir modificar permisos del propietario
        if (request.UserId == album.OwnerId)
        {
            return Results.BadRequest(new { error = "Cannot modify permissions for the album owner" });
        }

        // Buscar permiso existente
        var existingPermission = await dbContext.AlbumPermissions
            .FirstOrDefaultAsync(
                p => p.AlbumId == albumId && p.UserId == request.UserId,
                cancellationToken);

        AlbumPermission permission;

        if (existingPermission != null)
        {
            // Actualizar permiso existente
            existingPermission.CanView = request.CanView;
            existingPermission.CanEdit = request.CanEdit;
            existingPermission.CanDelete = request.CanDelete;
            existingPermission.CanManagePermissions = request.CanManagePermissions;
            existingPermission.GrantedByUserId = currentUserId;
            existingPermission.GrantedAt = DateTime.UtcNow;
            permission = existingPermission;
        }
        else
        {
            // Crear nuevo permiso
            permission = new AlbumPermission
            {
                AlbumId = albumId,
                UserId = request.UserId,
                CanView = request.CanView,
                CanEdit = request.CanEdit,
                CanDelete = request.CanDelete,
                CanManagePermissions = request.CanManagePermissions,
                GrantedByUserId = currentUserId,
                GrantedAt = DateTime.UtcNow
            };
            dbContext.AlbumPermissions.Add(permission);
        }

        await dbContext.SaveChangesAsync(cancellationToken);

        // Cargar datos relacionados para la respuesta
        await dbContext.Entry(permission)
            .Reference(p => p.User)
            .LoadAsync(cancellationToken);

        var response = new AlbumPermissionDto
        {
            Id = permission.Id,
            UserId = permission.UserId,
            Username = permission.User.Username,
            Email = permission.User.Email,
            CanView = permission.CanView,
            CanEdit = permission.CanEdit,
            CanDelete = permission.CanDelete,
            CanManagePermissions = permission.CanManagePermissions,
            GrantedAt = permission.GrantedAt,
            GrantedByUserId = permission.GrantedByUserId
        };

        return Results.Ok(response);
    }

    private async Task<IResult> RemoveAlbumPermission(
        Guid albumId,
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

        // Validar que el álbum existe
        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return Results.NotFound(new { error = "Album not found" });
        }

        // Verificar permisos: debe ser el propietario o tener CanManagePermissions
        var hasAccess = album.OwnerId == currentUserId ||
            album.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return Results.Forbid();
        }

        // No permitir eliminar permisos del propietario
        if (userId == album.OwnerId)
        {
            return Results.BadRequest(new { error = "Cannot remove permissions for the album owner" });
        }

        var permission = await dbContext.AlbumPermissions
            .FirstOrDefaultAsync(
                p => p.AlbumId == albumId && p.UserId == userId,
                cancellationToken);

        if (permission == null)
        {
            return Results.NotFound(new { error = "Permission not found" });
        }

        dbContext.AlbumPermissions.Remove(permission);
        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.NoContent();
    }
}

public class SetAlbumPermissionRequest
{
    public Guid UserId { get; set; }
    public bool CanView { get; set; }
    public bool CanEdit { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
}

public class AlbumPermissionDto
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public bool CanView { get; set; }
    public bool CanEdit { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
    public DateTime GrantedAt { get; set; }
    public Guid? GrantedByUserId { get; set; }
}
