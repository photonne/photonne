using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Features.Auth;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Users;

public class UsersEndpoint : IEndpoint
{
    /// <summary>
    /// The only role the public demo lets visitors create and manage. Admins (the
    /// shared demo account among them) stay untouchable so nobody breaks the demo.
    /// </summary>
    private const string DemoManageableRole = "User";

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
            .ProducesProblem(StatusCodes.Status403Forbidden)
            .WithDescription("Creates a new user (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPut("{id:guid}", UpdateUser)
            .WithName("UpdateUser")
            .ProducesProblem(StatusCodes.Status403Forbidden)
            .WithDescription("Updates a user (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapDelete("{id:guid}", DeleteUser)
            .WithName("DeleteUser")
            .ProducesProblem(StatusCodes.Status403Forbidden)
            .WithDescription("Deletes a user (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("{id:guid}/reset-password", ResetPassword)
            .WithName("ResetPassword")
            .ProducesProblem(StatusCodes.Status403Forbidden)
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
            .ProducesProblem(StatusCodes.Status403Forbidden)
            .WithDescription("Updates the current user's own profile");

        group.MapPost("me/change-password", ChangePassword)
            .WithName("ChangePassword")
            .ProducesProblem(StatusCodes.Status403Forbidden)
            .WithDescription("Changes the current user's password");

        group.MapPost("me/delete-account", DeleteMyAccount)
            .WithName("DeleteMyAccount")
            .ProducesProblem(StatusCodes.Status403Forbidden)
            .WithDescription("Deletes the current user's own account after confirming the password");

        group.MapGet("me/rename-preview", PreviewMyRename)
            .WithName("PreviewMyRename")
            .WithDescription("Returns the impact of renaming the current user's username (assets/folders/settings to migrate)");

        group.MapGet("{id:guid}/rename-preview", PreviewUserRename)
            .WithName("PreviewUserRename")
            .WithDescription("Returns the impact of renaming a user's username (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private async Task<Ok<List<UserDto>>> GetAllUsers(
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

        return TypedResults.Ok(users);
    }

    private async Task<Results<Ok<UserDto>, NotFound>> GetUser(
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
            return TypedResults.NotFound();

        return TypedResults.Ok(user);
    }

    private async Task<Results<Ok<UserDto>, UnauthorizedHttpResult, NotFound>> GetCurrentUser(
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
        {
            return TypedResults.Unauthorized();
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
            return TypedResults.NotFound();

        return TypedResults.Ok(currentUser);
    }

    private async Task<Ok<List<ShareableUserDto>>> GetShareableUsers(
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

        return TypedResults.Ok(users);
    }

    private async Task<Results<Created<UserDto>, BadRequest<ApiError>, ProblemHttpResult>> CreateUser(
        [FromBody] CreateUserRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        [FromServices] SettingsService settingsService,
        [FromServices] IOptionsMonitor<DemoModeOptions> demoOptions,
        CancellationToken cancellationToken)
    {
        // The demo admin may create regular accounts (so visitors can try signing in
        // with one and deleting it), never another admin.
        var isDemo = demoOptions.CurrentValue.Enabled;
        if (isDemo && request.Role != null && request.Role != DemoManageableRole)
            return TypedResults.Problem(DemoModeGuardMiddleware.CreateBlockedProblem());

        if (string.IsNullOrWhiteSpace(request.Username) ||
            string.IsNullOrWhiteSpace(request.Email) ||
            string.IsNullOrWhiteSpace(request.Password))
        {
            return TypedResults.BadRequest(new ApiError("Username, email and password are required", "invalid_request"));
        }

        // Validar formato del username (chars compatibles con filesystem)
        var usernameValidation = UserStorageService.ValidateUsername(request.Username);
        if (!usernameValidation.IsValid)
        {
            return TypedResults.BadRequest(new ApiError(usernameValidation.Error!, "invalid_username"));
        }

        // Validar contraseña
        var passwordValidation = authService.ValidatePassword(request.Password);
        if (!passwordValidation.IsValid)
        {
            return TypedResults.BadRequest(new ApiError(passwordValidation.ErrorMessage!, "invalid_password"));
        }

        if (await dbContext.Users.AnyAsync(u => u.Username == request.Username || u.Email == request.Email, cancellationToken))
        {
            return TypedResults.BadRequest(new ApiError("Username or email already exists", "user_already_exists"));
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
            Role = isDemo ? DemoManageableRole : request.Role ?? defaultRole,
            IsActive = request.IsActive ?? defaultActive.Equals("true", StringComparison.OrdinalIgnoreCase),
            StorageQuotaBytes = request.StorageQuotaBytes ?? defaultQuotaBytes,
            CreatedAt = DateTime.UtcNow
        };

        dbContext.Users.Add(user);
        await dbContext.SaveChangesAsync(cancellationToken);

        return TypedResults.Created($"/api/users/{user.Id}", new UserDto
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

    private async Task<Results<Ok<UserDto>, NotFound, BadRequest<ApiError>, ProblemHttpResult>> UpdateUser(
        Guid id,
        [FromBody] UpdateUserRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] UserStorageService userStorage,
        [FromServices] IOptionsMonitor<DemoModeOptions> demoOptions,
        CancellationToken cancellationToken)
    {
        var user = await dbContext.Users.FindAsync(new object[] { id }, cancellationToken);
        if (user == null)
            return TypedResults.NotFound();

        if (demoOptions.CurrentValue.Enabled
            && (user.Role != DemoManageableRole || (request.Role != null && request.Role != DemoManageableRole)))
            return TypedResults.Problem(DemoModeGuardMiddleware.CreateBlockedProblem());

        if (user.IsPrimaryAdmin)
        {
            if (request.Role != null && request.Role != "Admin")
                return TypedResults.BadRequest(new ApiError("No se puede cambiar el rol del administrador principal.", "primary_admin_protected"));
            if (request.IsActive.HasValue && !request.IsActive.Value)
                return TypedResults.BadRequest(new ApiError("No se puede desactivar el administrador principal.", "primary_admin_protected"));
        }

        // Username rename: triggers a storage migration (carpeta física + Asset.FullPath +
        // Folder.Path + Setting.Value). Validate, run the migration, and only then continue
        // with the rest of the field updates so we never commit a half-migrated state.
        if (!string.IsNullOrEmpty(request.Username) && request.Username != user.Username)
        {
            var usernameValidation = UserStorageService.ValidateUsername(request.Username);
            if (!usernameValidation.IsValid)
                return TypedResults.BadRequest(new ApiError(usernameValidation.Error!, "invalid_username"));

            var renameResult = await userStorage.RenameAsync(id, request.Username, cancellationToken);
            if (!renameResult.Succeeded)
                return TypedResults.BadRequest(new ApiError(renameResult.ErrorMessage ?? "No se pudo renombrar el usuario", "rename_failed"));

            // RenameAsync already persisted Username + path rewrites; reload the
            // entity so subsequent edits in this method work against fresh data.
            user = await dbContext.Users.FindAsync(new object[] { id }, cancellationToken) ?? user;
        }

        if (!string.IsNullOrEmpty(request.Email) && request.Email != user.Email)
        {
            if (await dbContext.Users.AnyAsync(u => u.Email == request.Email && u.Id != id, cancellationToken))
                return TypedResults.BadRequest(new ApiError("Email already exists", "email_already_exists"));
            user.Email = request.Email;
        }

        if (request.FirstName != null) user.FirstName = request.FirstName;
        if (request.LastName != null) user.LastName = request.LastName;
        if (request.Role != null) user.Role = request.Role;
        if (request.IsActive.HasValue) user.IsActive = request.IsActive.Value;
        if (request.StorageQuotaBytes.HasValue) user.StorageQuotaBytes = request.StorageQuotaBytes == -1 ? null : request.StorageQuotaBytes;

        await dbContext.SaveChangesAsync(cancellationToken);

        return TypedResults.Ok(new UserDto
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

    private async Task<Results<NoContent, NotFound, BadRequest<ApiError>, ProblemHttpResult>> DeleteUser(
        Guid id,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IOptionsMonitor<DemoModeOptions> demoOptions,
        CancellationToken cancellationToken)
    {
        var user = await dbContext.Users.FindAsync(new object[] { id }, cancellationToken);
        if (user == null)
            return TypedResults.NotFound();

        if (demoOptions.CurrentValue.Enabled && user.Role != DemoManageableRole)
            return TypedResults.Problem(DemoModeGuardMiddleware.CreateBlockedProblem());

        if (user.IsPrimaryAdmin)
            return TypedResults.BadRequest(new ApiError("El administrador principal del sistema no puede ser eliminado.", "primary_admin_protected"));

        await RemoveUserAsync(dbContext, user, cancellationToken);

        return TypedResults.NoContent();
    }

    /// <summary>
    /// Self-service account deletion, required by the app stores for any app where
    /// accounts can be created. Same scope as the admin delete: the user's rows go
    /// away (their assets stay on disk, ownerless), and the password confirms intent.
    /// </summary>
    private async Task<Results<NoContent, UnauthorizedHttpResult, NotFound, BadRequest<ApiError>, ProblemHttpResult>> DeleteMyAccount(
        [FromBody] DeleteAccountRequest request,
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        [FromServices] IOptionsMonitor<DemoModeOptions> demoOptions,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
        if (dbUser == null)
            return TypedResults.NotFound();

        // The shared demo admin must survive for the next visitor; accounts created
        // inside the demo can delete themselves.
        if (demoOptions.CurrentValue.Enabled && dbUser.Role != DemoManageableRole)
            return TypedResults.Problem(DemoModeGuardMiddleware.CreateBlockedProblem());

        if (dbUser.IsPrimaryAdmin)
            return TypedResults.BadRequest(new ApiError("El administrador principal no puede eliminar su cuenta. Transfiere antes ese rol a otro administrador.", "primary_admin_protected"));

        if (string.IsNullOrEmpty(request.Password) || !authService.VerifyPassword(request.Password, dbUser.PasswordHash))
            return TypedResults.BadRequest(new ApiError("La contraseña no es correcta", "invalid_password"));

        await RemoveUserAsync(dbContext, dbUser, cancellationToken);

        return TypedResults.NoContent();
    }

    /// <summary>
    /// Deletes a user row. Owned albums are removed first because their FK is
    /// <c>Restrict</c> (everything else cascades or is nulled by the database).
    /// </summary>
    private static async Task RemoveUserAsync(ApplicationDbContext dbContext, User user, CancellationToken cancellationToken)
    {
        await using var transaction = await dbContext.Database.BeginTransactionAsync(cancellationToken);
        await dbContext.Albums
            .Where(a => a.OwnerId == user.Id)
            .ExecuteDeleteAsync(cancellationToken);
        dbContext.Users.Remove(user);
        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
    }

    private async Task<Results<Ok<UserMessageResponse>, BadRequest<ApiError>, NotFound, ProblemHttpResult>> ResetPassword(
        Guid id,
        [FromBody] ResetPasswordRequest request,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        [FromServices] IOptionsMonitor<DemoModeOptions> demoOptions,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.NewPassword))
        {
            return TypedResults.BadRequest(new ApiError("New password is required", "password_required"));
        }

        // Validar contraseña
        var passwordValidation = authService.ValidatePassword(request.NewPassword);
        if (!passwordValidation.IsValid)
        {
            return TypedResults.BadRequest(new ApiError(passwordValidation.ErrorMessage!, "invalid_password"));
        }

        var user = await dbContext.Users.FindAsync(new object[] { id }, cancellationToken);
        if (user == null)
            return TypedResults.NotFound();

        if (demoOptions.CurrentValue.Enabled && user.Role != DemoManageableRole)
            return TypedResults.Problem(DemoModeGuardMiddleware.CreateBlockedProblem());

        user.PasswordHash = authService.HashPassword(request.NewPassword);
        await dbContext.SaveChangesAsync(cancellationToken);

        return TypedResults.Ok(new UserMessageResponse("Password reset successfully"));
    }

    /// <summary>
    /// Transfers the <c>IsPrimaryAdmin</c> flag from the calling user (current
    /// primary admin) to <paramref name="id"/>. The destination must be an
    /// active admin. Used when the original primary admin needs to step down
    /// — without this endpoint they would be stuck since the primary flag
    /// blocks delete/demote/deactivate.
    /// </summary>
    private async Task<Results<Ok<PromoteToPrimaryAdminResponse>, UnauthorizedHttpResult, ForbidHttpResult, BadRequest<ApiError>, NotFound<ApiError>>> PromoteToPrimaryAdmin(
        Guid id,
        ClaimsPrincipal caller,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var callerIdClaim = caller.FindFirst(ClaimTypes.NameIdentifier);
        if (callerIdClaim == null || !Guid.TryParse(callerIdClaim.Value, out var callerId))
            return TypedResults.Unauthorized();

        var currentPrimary = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == callerId, cancellationToken);
        if (currentPrimary == null)
            return TypedResults.Unauthorized();

        if (!currentPrimary.IsPrimaryAdmin)
            return TypedResults.Forbid();

        if (currentPrimary.Id == id)
            return TypedResults.BadRequest(new ApiError("Ya eres el administrador principal.", "already_primary_admin"));

        var target = await dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == id, cancellationToken);
        if (target == null)
            return TypedResults.NotFound(new ApiError("Usuario no encontrado.", "user_not_found"));

        if (target.Role != "Admin")
            return TypedResults.BadRequest(new ApiError("El usuario destino debe tener el rol Admin.", "target_not_admin"));

        if (!target.IsActive)
            return TypedResults.BadRequest(new ApiError("El usuario destino debe estar activo.", "target_inactive"));

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

        return TypedResults.Ok(new PromoteToPrimaryAdminResponse(
            $"'{target.Username}' es ahora el administrador principal.",
            currentPrimary.Id,
            target.Id));
    }

    private async Task<Results<Ok<StorageInfoDto>, UnauthorizedHttpResult, NotFound>> GetStorageInfo(
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
        if (dbUser == null)
            return TypedResults.NotFound();

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

        return TypedResults.Ok(new StorageInfoDto
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

    private async Task<Results<Ok<UserDto>, UnauthorizedHttpResult, NotFound, ProblemHttpResult, BadRequest<ApiError>>> UpdateProfile(
        [FromBody] UpdateProfileRequest request,
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] UserStorageService userStorage,
        [FromServices] IOptionsMonitor<DemoModeOptions> demoOptions,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
        if (dbUser == null)
            return TypedResults.NotFound();

        // Renaming the shared demo account (or changing its email) would lock every
        // other visitor out of the published credentials until the next reset.
        if (demoOptions.CurrentValue.Enabled && dbUser.Role != DemoManageableRole)
            return TypedResults.Problem(DemoModeGuardMiddleware.CreateBlockedProblem());

        if (!string.IsNullOrWhiteSpace(request.Username) && request.Username.Trim() != dbUser.Username)
        {
            var newUsername = request.Username.Trim();
            var validation = UserStorageService.ValidateUsername(newUsername);
            if (!validation.IsValid)
                return TypedResults.BadRequest(new ApiError(validation.Error!, "invalid_username"));

            var renameResult = await userStorage.RenameAsync(userId, newUsername, cancellationToken);
            if (!renameResult.Succeeded)
                return TypedResults.BadRequest(new ApiError(renameResult.ErrorMessage ?? "No se pudo renombrar el usuario", "rename_failed"));

            dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken) ?? dbUser;
        }

        if (!string.IsNullOrWhiteSpace(request.Email) && request.Email != dbUser.Email)
        {
            if (await dbContext.Users.AnyAsync(u => u.Email == request.Email && u.Id != userId, cancellationToken))
                return TypedResults.BadRequest(new ApiError("El email ya está en uso", "email_already_exists"));
            dbUser.Email = request.Email.Trim();
        }

        if (request.FirstName != null) dbUser.FirstName = request.FirstName.Trim();
        if (request.LastName != null) dbUser.LastName = request.LastName.Trim();

        await dbContext.SaveChangesAsync(cancellationToken);

        return TypedResults.Ok(new UserDto
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

    private async Task<Results<Ok<UserMessageResponse>, BadRequest<ApiError>, UnauthorizedHttpResult, NotFound, ProblemHttpResult>> ChangePassword(
        [FromBody] ChangePasswordRequest request,
        ClaimsPrincipal user,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IAuthService authService,
        [FromServices] IOptionsMonitor<DemoModeOptions> demoOptions,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.CurrentPassword) || string.IsNullOrWhiteSpace(request.NewPassword))
            return TypedResults.BadRequest(new ApiError("Todos los campos son obligatorios", "invalid_request"));

        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        var dbUser = await dbContext.Users.FindAsync(new object[] { userId }, cancellationToken);
        if (dbUser == null)
            return TypedResults.NotFound();

        // Same as the profile: the demo account's password is published on the login page.
        if (demoOptions.CurrentValue.Enabled && dbUser.Role != DemoManageableRole)
            return TypedResults.Problem(DemoModeGuardMiddleware.CreateBlockedProblem());

        if (!authService.VerifyPassword(request.CurrentPassword, dbUser.PasswordHash))
            return TypedResults.BadRequest(new ApiError("La contraseña actual no es correcta", "invalid_current_password"));

        var validation = authService.ValidatePassword(request.NewPassword);
        if (!validation.IsValid)
            return TypedResults.BadRequest(new ApiError(validation.ErrorMessage!, "invalid_password"));

        dbUser.PasswordHash = authService.HashPassword(request.NewPassword);
        await dbContext.SaveChangesAsync(cancellationToken);

        return TypedResults.Ok(new UserMessageResponse("Contraseña cambiada correctamente"));
    }

    private async Task<Results<Ok<RenamePreviewDto>, BadRequest<ApiError>, NotFound<ApiError>, UnauthorizedHttpResult>> PreviewMyRename(
        [FromQuery] string newUsername,
        ClaimsPrincipal user,
        [FromServices] UserStorageService userStorage,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            return TypedResults.Unauthorized();

        // Results<...> doesn't widen implicitly, so re-wrap the inner result.
        return (await BuildRenamePreviewResultAsync(userStorage, userId, newUsername, cancellationToken)).Result switch
        {
            Ok<RenamePreviewDto> ok => ok,
            BadRequest<ApiError> badRequest => badRequest,
            NotFound<ApiError> notFound => notFound,
            var other => throw new InvalidOperationException($"Unexpected result {other.GetType().Name}")
        };
    }

    private async Task<Results<Ok<RenamePreviewDto>, BadRequest<ApiError>, NotFound<ApiError>>> PreviewUserRename(
        Guid id,
        [FromQuery] string newUsername,
        [FromServices] UserStorageService userStorage,
        CancellationToken cancellationToken)
    {
        return await BuildRenamePreviewResultAsync(userStorage, id, newUsername, cancellationToken);
    }

    private static async Task<Results<Ok<RenamePreviewDto>, BadRequest<ApiError>, NotFound<ApiError>>> BuildRenamePreviewResultAsync(
        UserStorageService userStorage,
        Guid userId,
        string newUsername,
        CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(newUsername))
            return TypedResults.BadRequest(new ApiError("newUsername es obligatorio", "new_username_required"));

        try
        {
            var preview = await userStorage.PreviewRenameAsync(userId, newUsername.Trim(), ct);
            return TypedResults.Ok(new RenamePreviewDto
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
                FoldersToUpdate = preview.FoldersToUpdate
            });
        }
        catch (InvalidOperationException)
        {
            // PreviewRenameAsync throws this only for an unknown user id; answer with
            // its text instead of forwarding whatever message the exception carries.
            return TypedResults.NotFound(new ApiError("Usuario no encontrado", "user_not_found"));
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

public class DeleteAccountRequest
{
    public string Password { get; set; } = string.Empty;
}

public class ChangePasswordRequest
{
    public string CurrentPassword { get; set; } = string.Empty;
    public string NewPassword { get; set; } = string.Empty;
}

public sealed record UserMessageResponse(string Message);

public sealed record PromoteToPrimaryAdminResponse(
    string Message,
    Guid PreviousPrimaryUserId,
    Guid NewPrimaryUserId);
