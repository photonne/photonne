using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
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

    private async Task<Results<Ok<List<AlbumPermissionDto>>, UnauthorizedHttpResult, NotFound<ApiError>, ForbidHttpResult>> GetAlbumPermissions(
        Guid albumId,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var currentUserId))
        {
            return TypedResults.Unauthorized();
        }

        // Verificar que el usuario tenga acceso al álbum
        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .ThenInclude(p => p.User)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError("Album not found", "album_not_found"));
        }

        // Verificar permisos: debe ser el propietario o tener CanManagePermissions
        var hasAccess = album.OwnerId == currentUserId ||
            album.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return TypedResults.Forbid();
        }

        var permissions = album.Permissions.Select(p => new AlbumPermissionDto
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

        return TypedResults.Ok(permissions);
    }

    private async Task<Results<Ok<AlbumPermissionDto>, UnauthorizedHttpResult, NotFound<ApiError>, ForbidHttpResult, BadRequest<ApiError>>> SetAlbumPermission(
        Guid albumId,
        [FromBody] SetAlbumPermissionRequest request,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var currentUserId))
        {
            return TypedResults.Unauthorized();
        }

        // Validar que el álbum existe
        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError("Album not found", "album_not_found"));
        }

        // Verificar permisos: debe ser el propietario o tener CanManagePermissions
        var hasAccess = album.OwnerId == currentUserId ||
            album.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return TypedResults.Forbid();
        }

        // Validar que el usuario existe
        var targetUser = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == request.UserId, cancellationToken);

        if (targetUser == null)
        {
            return TypedResults.NotFound(new ApiError($"User with ID {request.UserId} not found", "user_not_found"));
        }

        // No permitir modificar permisos del propietario
        if (request.UserId == album.OwnerId)
        {
            return TypedResults.BadRequest(new ApiError("Cannot modify permissions for the album owner", "owner_permissions_immutable"));
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
            // Crear nuevo permiso
            permission = new AlbumPermission
            {
                AlbumId = albumId,
                UserId = request.UserId,
                CanRead = request.CanRead,
                CanWrite = request.CanWrite,
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
            CanRead = permission.CanRead,
            CanWrite = permission.CanWrite,
            CanDelete = permission.CanDelete,
            CanManagePermissions = permission.CanManagePermissions,
            GrantedAt = permission.GrantedAt,
            GrantedByUserId = permission.GrantedByUserId
        };

        return TypedResults.Ok(response);
    }

    private async Task<Results<NoContent, UnauthorizedHttpResult, NotFound<ApiError>, ForbidHttpResult, BadRequest<ApiError>>> RemoveAlbumPermission(
        Guid albumId,
        Guid userId,
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var currentUserId))
        {
            return TypedResults.Unauthorized();
        }

        // Validar que el álbum existe
        var album = await dbContext.Albums
            .Include(a => a.Permissions)
            .FirstOrDefaultAsync(a => a.Id == albumId, cancellationToken);

        if (album == null)
        {
            return TypedResults.NotFound(new ApiError("Album not found", "album_not_found"));
        }

        // Verificar permisos: debe ser el propietario o tener CanManagePermissions
        var hasAccess = album.OwnerId == currentUserId ||
            album.Permissions.Any(p => p.UserId == currentUserId && p.CanManagePermissions);

        if (!hasAccess)
        {
            return TypedResults.Forbid();
        }

        // No permitir eliminar permisos del propietario
        if (userId == album.OwnerId)
        {
            return TypedResults.BadRequest(new ApiError("Cannot remove permissions for the album owner", "owner_permissions_immutable"));
        }

        var permission = await dbContext.AlbumPermissions
            .FirstOrDefaultAsync(
                p => p.AlbumId == albumId && p.UserId == userId,
                cancellationToken);

        if (permission == null)
        {
            return TypedResults.NotFound(new ApiError("Permission not found", "permission_not_found"));
        }

        dbContext.AlbumPermissions.Remove(permission);
        await dbContext.SaveChangesAsync(cancellationToken);

        return TypedResults.NoContent();
    }
}

public class SetAlbumPermissionRequest
{
    public Guid UserId { get; set; }
    public bool CanRead { get; set; }
    public bool CanWrite { get; set; }
    public bool CanDelete { get; set; }
    public bool CanManagePermissions { get; set; }
}

public class AlbumPermissionDto
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
