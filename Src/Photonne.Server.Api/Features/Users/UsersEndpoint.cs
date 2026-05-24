using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Auth;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Users;

public class UsersEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/users")
            .WithTags("Users")
            .RequireAuthorization();

        group.MapGet("", GetAllUsers)
            .WithName("GetAllUsers")
            .WithDescription("Gets all users (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("{id:guid}", GetUser)
            .WithName("GetUser")
            .WithDescription("Gets a user by ID (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("me", GetCurrentUser)
            .WithName("GetCurrentUser")
            .WithDescription("Gets the current authenticated user");

        group.MapGet("shareable", GetShareableUsers)
            .WithName("GetShareableUsers")
            .WithDescription("Gets users for sharing");

        group.MapPost("", CreateUser)
            .WithName("CreateUser")
            .WithDescription("Creates a new user (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPut("{id:guid}", UpdateUser)
            .WithName("UpdateUser")
            .WithDescription("Updates a user (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapDelete("{id:guid}", DeleteUser)
            .WithName("DeleteUser")
            .WithDescription("Deletes a user (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("{id:guid}/reset-password", ResetPassword)
            .WithName("ResetPassword")
            .WithDescription("Resets a user's password (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("{id:guid}/promote-to-primary", PromoteToPrimaryAdmin)
            .WithName("PromoteToPrimaryAdmin")
            .WithDescription("Transfers the primary-admin flag from the current primary admin to another admin user")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("me/storage", GetStorageInfo)
            .WithName("GetStorageInfo")
            .WithDescription("Gets storage usage and quota for the current user");

        group.MapPut("me", UpdateProfile)
            .WithName("UpdateProfile")
            .WithDescription("Updates the current user's own profile");

        group.MapPost("me/change-password", ChangePassword)
            .WithName("ChangePassword")
            .WithDescription("Changes the current user's password");

        group.MapGet("me/rename-preview", PreviewMyRename)
            .WithName("PreviewMyRename")
            .WithDescription("Returns the impact of renaming the current user's username (assets/folders/settings to migrate)");

        group.MapGet("{id:guid}/rename-preview", PreviewUserRename)
            .WithName("PreviewUserRename")
            .WithDescription("Returns the impact of renaming a user's username (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private async Task<IResult> GetAllUsers(
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var users = await dbContext.Users
            .Select(u => new UserDto
            {
                Id = u.Id,
                Username = u.Username,
                Email = u.Email,
                Role = u.Role,
                FirstName = u.FirstName,
                LastName = u.LastName,
                IsActive = u.IsActive,
                IsPrimaryAdmin = u.IsPrimaryAdmin,
                CreatedAt = u.CreatedAt,
                LastLoginAt = u.LastLoginAt,
                StorageQuotaBytes = u.StorageQuotaBytes
            })
            .ToListAsync(cancellationToken);

        return Results.Ok(users);
    }

    private async Task<IResult> GetUser(
        Guid id,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var user = await dbContext.Users
            .Select(u => new UserDto
            {
                Id = u.Id,
                Username = u.Username,
                Email = u.Email,
                Role = u.Role,
                FirstName = u.FirstName,
                LastName = u.LastName,
                IsActive = u.IsActive,
                IsPrimaryAdmin = u.IsPrimaryAdmin,
                CreatedAt = u.CreatedAt,
                LastLoginAt = u.LastLoginAt,
                StorageQuotaBytes = u.StorageQuotaBytes
            })
            .FirstOrDefaultAsync(u => u.Id == id, cancellationToken);

        if (user == null)
            return Results.NotFound();

        return Results.Ok(user);
    }

    private async Task<IResult> GetCurrentUser(
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return Results.Unauthorized();
        }

        var currentUser = await dbContext.Users
            .Select(u => new UserDto
            {
                Id = u.Id,
                Username = u.Username,
                Email = u.Email,
                Role = u.Role,
                FirstName = u.FirstName,
                LastName = u.LastName,
                IsActive = u.IsActive,
                IsPrimaryAdmin = u.IsPrimaryAdmin,
                CreatedAt = u.CreatedAt,
                LastLoginAt = u.LastLoginAt,
                StorageQuotaBytes = u.StorageQuotaBytes
            })
            .FirstOrDefaultAsync(u => u.Id == userId, cancellationToken);

        if (currentUser == null)
            return Results.NotFound();

        return Results.Ok(currentUser);
    }

    private async Task<IResult> GetShareableUsers(
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var users = await dbContext.Users
            .Where(u => u.IsActive)
            .Select(u => new ShareableUserDto
            {
                Id = u.Id,
                Username = u.Username,
                Email = u.Email
            })
            .ToListAsync(cancellationToken);

        return Results.Ok(users);
    }

    private async Task<IResult> CreateUser(
        [FromBody] CreateUserRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        [FromServices] SettingsService settingsService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Username) ||
            string.IsNullOrWhiteSpace(request.Email) ||
            string.IsNullOrWhiteSpace(request.Password))
        {
            return Results.BadRequest(new { error = "Username, email and password are required" });
        }

        // Validar formato del username (chars compatibles con filesystem)
        var usernameValidation = UserStorageService.ValidateUsername(request.Username);
        if (!usernameValidation.IsValid)
        {
            return Results.BadRequest(new { error = usernameValidation.Error });
        }

        // Validar contraseña
        var passwordValidation = authService.ValidatePassword(request.Password);
        if (!passwordValidation.IsValid)
        {
            return Results.BadRequest(new { error = passwordValidation.ErrorMessage });
        }

        if (await dbContext.Users.AnyAsync(u => u.Username == request.Username || u.Email == request.Email, cancellationToken))
        {
            return Results.BadRequest(new { error = "Username or email already exists" });
        }

        // Read global defaults; explicit request values always take precedence
        var defaultRole     = await settingsService.GetSettingAsync("UserSettings.DefaultRole",     Guid.Empty, "User");
        var defaultActive   = await settingsService.GetSettingAsync("UserSettings.DefaultIsActive", Guid.Empty, "true");
        var defaultQuotaGb  = await settingsService.GetSettingAsync("UserSettings.DefaultStorageQuotaGb", Guid.Empty, "0");

        long? defaultQuotaBytes = int.TryParse(defaultQuotaGb, out var gb) && gb > 0
            ? (long)gb * 1_073_741_824L
            : null;

        var user = new User
        {
            Username = request.Username,
            Email = request.Email,
            PasswordHash = authService.HashPassword(request.Password),
            FirstName = request.FirstName,
            LastName = request.LastName,
            Role = request.Role ?? defaultRole,
            IsActive = request.IsActive ?? defaultActive.Equals("true", StringComparison.OrdinalIgnoreCase),
            StorageQuotaBytes = request.StorageQuotaBytes ?? defaultQuotaBytes,
            CreatedAt = DateTime.UtcNow
        };

        dbContext.Users.Add(user);
        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.Created($"/api/users/{user.Id}", new UserDto
        {
            Id = user.Id,
            Username = user.Username,
            Email = user.Email,
            Role = user.Role,
            FirstName = user.FirstName,
            LastName = user.LastName,
            IsActive = user.IsActive,
            IsPrimaryAdmin = user.IsPrimaryAdmin,
            CreatedAt = user.CreatedAt,
            LastLoginAt = user.LastLoginAt,
            StorageQuotaBytes = user.StorageQuotaBytes
        });
    }

    private async Task<IResult> UpdateUser(
        Guid id,
        [FromBody] UpdateUserRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] UserStorageService userStorage,
        CancellationToken cancellationToken)
    {
        var user = await dbContext.Users.FindAsync(new object[] { id }, cancellationToken);
        if (user == null)
            return Results.NotFound();

        if (user.IsPrimaryAdmin)
        {
            if (request.Role != null && request.Role != "Admin")
                return Results.BadRequest(new { error = "No se puede cambiar el rol del administrador principal." });
            if (request.IsActive.HasValue && !request.IsActive.Value)
                return Results.BadRequest(new { error = "No se puede desactivar el administrador principal." });
        }

        // Username rename: triggers a storage migration (carpeta física + Asset.FullPath +
        // Folder.Path + Setting.Value). Validate, run the migration, and only then continue
        // with the rest of the field updates so we never commit a half-migrated state.
        if (!string.IsNullOrEmpty(request.Username) && request.Username != user.Username)
        {
            var usernameValidation = UserStorageService.ValidateUsername(request.Username);
            if (!usernameValidation.IsValid)
                return Results.BadRequest(new { error = usernameValidation.Error });

            var renameResult = await userStorage.RenameAsync(id, request.Username, cancellationToken);
            if (!renameResult.Succeeded)
                return Results.BadRequest(new { error = renameResult.ErrorMessage ?? "No se pudo renombrar el usuario" });

            // RenameAsync already persisted Username + path rewrites; reload the
            // entity so subsequent edits in this method work against fresh data.
            user = await dbContext.Users.FindAsync(new object[] { id }, cancellationToken) ?? user;
        }

        if (!string.IsNullOrEmpty(request.Email) && request.Email != user.Email)
        {
            if (await dbContext.Users.AnyAsync(u => u.Email == request.Email && u.Id != id, cancellationToken))
                return Results.BadRequest(new { error = "Email already exists" });
            user.Email = request.Email;
        }

        if (request.FirstName != null) user.FirstName = request.FirstName;
        if (request.LastName != null) user.LastName = request.LastName;
        if (request.Role != null) user.Role = request.Role;
        if (request.IsActive.HasValue) user.IsActive = request.IsActive.Value;
        if (request.StorageQuotaBytes.HasValue) user.StorageQuotaBytes = request.StorageQuotaBytes == -1 ? null : request.StorageQuotaBytes;

        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.Ok(new UserDto
        {
            Id = user.Id,
            Username = user.Username,
            Email = user.Email,
            Role = user.Role,
            FirstName = user.FirstName,
            LastName = user.LastName,
            IsActive = user.IsActive,
            IsPrimaryAdmin = user.IsPrimaryAdmin,
            CreatedAt = user.CreatedAt,
            LastLoginAt = user.LastLoginAt,
            StorageQuotaBytes = user.StorageQuotaBytes
        });
    }

    private async Task<IResult> DeleteUser(
        Guid id,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var user = await dbContext.Users.FindAsync(new object[] { id }, cancellationToken);
        if (user == null)
            return Results.NotFound();

        if (user.IsPrimaryAdmin)
            return Results.BadRequest(new { error = "El administrador principal del sistema no puede ser eliminado." });

        dbContext.Users.Remove(user);
        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.NoContent();
    }

    private async Task<IResult> ResetPassword(
        Guid id,
        [FromBody] ResetPasswordRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.NewPassword))
        {
            return Results.BadRequest(new { error = "New password is required" });
        }

        // Validar contraseña
        var passwordValidation = authService.ValidatePassword(request.NewPassword);
        if (!passwordValidation.IsValid)
        {
            return Results.BadRequest(new { error = passwordValidation.ErrorMessage });
        }

        var user = await dbContext.Users.FindAsync(new object[] { id }, cancellationToken);
        if (user == null)
            return Results.NotFound();

        user.PasswordHash = authService.HashPassword(request.NewPassword);
        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.Ok(new { message = "Password reset successfully" });
    }

    /// <summary>
    /// Transfers the <c>IsPrimaryAdmin</c> flag from the calling user (current
    /// primary admin) to <paramref name="id"/>. The destination must be an
    /// active admin. Used when the original primary admin needs to step down
    /// — without this endpoint they would be stuck since the primary flag
    /// blocks delete/demote/deactivate.
    /// </summary>
    private async Task<IResult> PromoteToPrimaryAdmin(
        Guid id,
        ClaimsPrincipal caller,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var callerIdClaim = caller.FindFirst(ClaimTypes.NameIdentifier);
        if (callerIdClaim == null || !Guid.TryParse(callerIdClaim.Value, out var callerId))
            return Results.Unauthorized();

        var currentPrimary = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == callerId, cancellationToken);
        if (currentPrimary == null)
            return Results.Unauthorized();

        if (!currentPrimary.IsPrimaryAdmin)
            return Results.Forbid();

        if (currentPrimary.Id == id)
            return Results.BadRequest(new { error = "Ya eres el administrador principal." });

        var target = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == id, cancellationToken);
        if (target == null)
            return Results.NotFound(new { error = "Usuario no encontrado." });

        if (target.Role != "Admin")
            return Results.BadRequest(new { error = "El usuario destino debe tener el rol Admin." });

        if (!target.IsActive)
            return Results.BadRequest(new { error = "El usuario destino debe estar activo." });

        // Both flag flips inside a single transaction so we never end up with
        // zero primary admins (or two) if anything fails between the writes.
        await using var tx = await dbContext.Database.BeginTransactionAsync(cancellationToken);
        try
        {
            currentPrimary.IsPrimaryAdmin = false;
            target.IsPrimaryAdmin = true;
            await dbContext.SaveChangesAsync(cancellationToken);
            await tx.CommitAsync(cancellationToken);
        }
        catch
        {
            await tx.RollbackAsync(cancellationToken);
            throw;
        }

        return Results.Ok(new
        {
            message = $"'{target.Username}' es ahora el administrador principal.",
            previousPrimaryUserId = currentPrimary.Id,
            newPrimaryUserId = target.Id
        });
    }

    private async Task<IResult> GetStorageInfo(
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
        if (dbUser == null)
            return Results.NotFound();

        // Group by (type, library) so we can return both the personal subset
        // (ExternalLibraryId == null) and per-library usage in one query.
        var breakdown = await dbContext.Assets
            .AsNoTracking()
            .Where(a => a.OwnerId == userId && a.DeletedAt == null)
            .GroupBy(a => new { a.Type, a.ExternalLibraryId })
            .Select(g => new
            {
                Type = g.Key.Type,
                LibraryId = g.Key.ExternalLibraryId,
                Count = g.Count(),
                Bytes = g.Sum(a => (long?)a.FileSize) ?? 0L
            })
            .ToListAsync(cancellationToken);

        var libraryIds = breakdown
            .Where(b => b.LibraryId.HasValue)
            .Select(b => b.LibraryId!.Value)
            .Distinct()
            .ToList();

        var libraryNames = libraryIds.Count == 0
            ? new Dictionary<Guid, string>()
            : await dbContext.ExternalLibraries
                .AsNoTracking()
                .Where(l => libraryIds.Contains(l.Id))
                .Select(l => new { l.Id, l.Name })
                .ToDictionaryAsync(l => l.Id, l => l.Name, cancellationToken);

        int CountOf(Guid? libId, AssetType type) =>
            breakdown.FirstOrDefault(b => b.LibraryId == libId && b.Type == type)?.Count ?? 0;
        long BytesOf(Guid? libId, AssetType type) =>
            breakdown.FirstOrDefault(b => b.LibraryId == libId && b.Type == type)?.Bytes ?? 0L;

        var libraries = libraryIds
            .Select(id => new StorageLibraryUsage
            {
                Id = id,
                Name = libraryNames.TryGetValue(id, out var name) ? name : id.ToString(),
                Photos = CountOf(id, AssetType.Image),
                Videos = CountOf(id, AssetType.Video),
                PhotoBytes = BytesOf(id, AssetType.Image),
                VideoBytes = BytesOf(id, AssetType.Video)
            })
            .OrderByDescending(l => l.PhotoBytes + l.VideoBytes)
            .ToList();

        return Results.Ok(new StorageInfoDto
        {
            UsedBytes = breakdown.Sum(b => b.Bytes),
            QuotaBytes = dbUser.StorageQuotaBytes,
            Photos = breakdown.Where(b => b.Type == AssetType.Image).Sum(b => b.Count),
            Videos = breakdown.Where(b => b.Type == AssetType.Video).Sum(b => b.Count),
            PhotoBytes = breakdown.Where(b => b.Type == AssetType.Image).Sum(b => b.Bytes),
            VideoBytes = breakdown.Where(b => b.Type == AssetType.Video).Sum(b => b.Bytes),
            PersonalPhotos = CountOf(null, AssetType.Image),
            PersonalVideos = CountOf(null, AssetType.Video),
            PersonalPhotoBytes = BytesOf(null, AssetType.Image),
            PersonalVideoBytes = BytesOf(null, AssetType.Video),
            Libraries = libraries
        });
    }

    private async Task<IResult> UpdateProfile(
        [FromBody] UpdateProfileRequest request,
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] UserStorageService userStorage,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
        if (dbUser == null)
            return Results.NotFound();

        if (!string.IsNullOrWhiteSpace(request.Username) && request.Username.Trim() != dbUser.Username)
        {
            var newUsername = request.Username.Trim();
            var validation = UserStorageService.ValidateUsername(newUsername);
            if (!validation.IsValid)
                return Results.BadRequest(new { error = validation.Error });

            var renameResult = await userStorage.RenameAsync(userId, newUsername, cancellationToken);
            if (!renameResult.Succeeded)
                return Results.BadRequest(new { error = renameResult.ErrorMessage ?? "No se pudo renombrar el usuario" });

            dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken) ?? dbUser;
        }

        if (!string.IsNullOrWhiteSpace(request.Email) && request.Email != dbUser.Email)
        {
            if (await dbContext.Users.AnyAsync(u => u.Email == request.Email && u.Id != userId, cancellationToken))
                return Results.BadRequest(new { error = "El email ya está en uso" });
            dbUser.Email = request.Email.Trim();
        }

        if (request.FirstName != null) dbUser.FirstName = request.FirstName.Trim();
        if (request.LastName != null) dbUser.LastName = request.LastName.Trim();

        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.Ok(new UserDto
        {
            Id = dbUser.Id,
            Username = dbUser.Username,
            Email = dbUser.Email,
            Role = dbUser.Role,
            FirstName = dbUser.FirstName,
            LastName = dbUser.LastName,
            IsActive = dbUser.IsActive,
            IsPrimaryAdmin = dbUser.IsPrimaryAdmin,
            CreatedAt = dbUser.CreatedAt,
            LastLoginAt = dbUser.LastLoginAt,
            StorageQuotaBytes = dbUser.StorageQuotaBytes
        });
    }

    private async Task<IResult> ChangePassword(
        [FromBody] ChangePasswordRequest request,
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.CurrentPassword) || string.IsNullOrWhiteSpace(request.NewPassword))
            return Results.BadRequest(new { error = "Todos los campos son obligatorios" });

        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
        if (dbUser == null)
            return Results.NotFound();

        if (!authService.VerifyPassword(request.CurrentPassword, dbUser.PasswordHash))
            return Results.BadRequest(new { error = "La contraseña actual no es correcta" });

        var validation = authService.ValidatePassword(request.NewPassword);
        if (!validation.IsValid)
            return Results.BadRequest(new { error = validation.ErrorMessage });

        dbUser.PasswordHash = authService.HashPassword(request.NewPassword);
        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.Ok(new { message = "Contraseña cambiada correctamente" });
    }

    private async Task<IResult> PreviewMyRename(
        [FromQuery] string newUsername,
        ClaimsPrincipal user,
        [FromServices] UserStorageService userStorage,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return Results.Unauthorized();

        return await BuildRenamePreviewResultAsync(userStorage, userId, newUsername, cancellationToken);
    }

    private async Task<IResult> PreviewUserRename(
        Guid id,
        [FromQuery] string newUsername,
        [FromServices] UserStorageService userStorage,
        CancellationToken cancellationToken)
    {
        return await BuildRenamePreviewResultAsync(userStorage, id, newUsername, cancellationToken);
    }

    private static async Task<IResult> BuildRenamePreviewResultAsync(
        UserStorageService userStorage,
        Guid userId,
        string newUsername,
        CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(newUsername))
            return Results.BadRequest(new { error = "newUsername es obligatorio" });

        try
        {
            var preview = await userStorage.PreviewRenameAsync(userId, newUsername.Trim(), ct);
            return Results.Ok(new RenamePreviewDto
            {
                IsValid = preview.IsValid,
                IsNoChange = preview.IsNoChange,
                ErrorMessage = preview.ErrorMessage,
                CurrentUsername = preview.CurrentUsername,
                NewUsername = preview.NewUsername,
                CurrentVirtualPath = preview.CurrentVirtualPath,
                NewVirtualPath = preview.NewVirtualPath,
                CurrentPhysicalPath = preview.CurrentPhysicalPath,
                NewPhysicalPath = preview.NewPhysicalPath,
                FolderExistsOnDisk = preview.FolderExistsOnDisk,
                AssetsToUpdate = preview.AssetsToUpdate,
                FoldersToUpdate = preview.FoldersToUpdate,
                SettingsToUpdate = preview.SettingsToUpdate
            });
        }
        catch (InvalidOperationException ex)
        {
            return Results.NotFound(new { error = ex.Message });
        }
    }
}

public class RenamePreviewDto
{
    public bool IsValid { get; set; }
    public bool IsNoChange { get; set; }
    public string? ErrorMessage { get; set; }

    public string CurrentUsername { get; set; } = string.Empty;
    public string NewUsername { get; set; } = string.Empty;

    public string? CurrentVirtualPath { get; set; }
    public string? NewVirtualPath { get; set; }
    public string? CurrentPhysicalPath { get; set; }
    public string? NewPhysicalPath { get; set; }

    public bool FolderExistsOnDisk { get; set; }

    public int AssetsToUpdate { get; set; }
    public int FoldersToUpdate { get; set; }
    public int SettingsToUpdate { get; set; }
}

public class CreateUserRequest
{
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
    public string? FirstName { get; set; }
    public string? LastName { get; set; }
    public string? Role { get; set; }
    public bool? IsActive { get; set; }
    /// <summary>Storage quota in bytes. Null uses the global default from UserSettings.</summary>
    public long? StorageQuotaBytes { get; set; }
}

public class UpdateUserRequest
{
    public string? Username { get; set; }
    public string? Email { get; set; }
    public string? FirstName { get; set; }
    public string? LastName { get; set; }
    public string? Role { get; set; }
    public bool? IsActive { get; set; }
    /// <summary>Storage quota in bytes. Pass -1 to remove the quota (unlimited).</summary>
    public long? StorageQuotaBytes { get; set; }
}

public class StorageInfoDto
{
    public long UsedBytes { get; set; }
    public long? QuotaBytes { get; set; }
    public int Photos { get; set; }
    public int Videos { get; set; }
    public long PhotoBytes { get; set; }
    public long VideoBytes { get; set; }
    public int PersonalPhotos { get; set; }
    public int PersonalVideos { get; set; }
    public long PersonalPhotoBytes { get; set; }
    public long PersonalVideoBytes { get; set; }
    public List<StorageLibraryUsage> Libraries { get; set; } = new();
}

public class StorageLibraryUsage
{
    public Guid Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public int Photos { get; set; }
    public int Videos { get; set; }
    public long PhotoBytes { get; set; }
    public long VideoBytes { get; set; }
}

public class ResetPasswordRequest
{
    public string NewPassword { get; set; } = string.Empty;
}

public class ShareableUserDto
{
    public Guid Id { get; set; }
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
}

public class UpdateProfileRequest
{
    public string? Username { get; set; }
    public string? Email { get; set; }
    public string? FirstName { get; set; }
    public string? LastName { get; set; }
}

public class ChangePasswordRequest
{
    public string CurrentPassword { get; set; } = string.Empty;
    public string NewPassword { get; set; } = string.Empty;
}
