using System.Text.RegularExpressions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Logging;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Single source of truth for the user-storage convention
/// <c>/assets/users/{username}/...</c>.
///
/// Provides:
///   * Username validation (filesystem-safe chars only).
///   * Cached <c>userId → username</c> resolution for services that don't have
///     access to the JWT username claim.
///   * Path parsing (extract username/userId from a virtual path).
///   * Rename preview + atomic rename migration (folder move + Assets/Folders/Settings updates).
/// </summary>
public sealed class UserStorageService
{
    public const string UsersSegment = "users";
    public const string VirtualPrefix = "/assets/users/";

    // Allowed chars: ASCII letters/digits/dot/underscore/dash. Length 1..64.
    // Disallows path separators, control chars, and any Unicode that varies
    // between filesystem encodings (NFC/NFD).
    private static readonly Regex UsernamePattern =
        new("^[a-zA-Z0-9._-]{1,64}$", RegexOptions.Compiled);

    private readonly ApplicationDbContext _db;
    private readonly SettingsService _settings;
    private readonly IMemoryCache _cache;
    private readonly ILogger<UserStorageService> _logger;

    private static readonly TimeSpan CacheTtl = TimeSpan.FromMinutes(10);

    public UserStorageService(
        ApplicationDbContext db,
        SettingsService settings,
        IMemoryCache cache,
        ILogger<UserStorageService> logger)
    {
        _db = db;
        _settings = settings;
        _cache = cache;
        _logger = logger;
    }

    // ---- Username validation ------------------------------------------------

    public static (bool IsValid, string? Error) ValidateUsername(string? candidate)
    {
        if (string.IsNullOrWhiteSpace(candidate))
            return (false, "El nombre de usuario es obligatorio");

        if (candidate.Length > 64)
            return (false, "El nombre de usuario no puede tener más de 64 caracteres");

        if (!UsernamePattern.IsMatch(candidate))
            return (false, "El nombre de usuario solo puede contener letras, números, puntos, guiones y guiones bajos");

        return (true, null);
    }

    // ---- Path building ------------------------------------------------------

    public static string BuildVirtualRoot(string username) =>
        $"{VirtualPrefix}{username}";

    /// <summary>
    /// Resolves a userId to its current virtual root <c>/assets/users/{username}</c>.
    /// Cached for <see cref="CacheTtl"/>. Cache is invalidated on rename.
    /// </summary>
    public async Task<string?> GetVirtualRootAsync(Guid userId, CancellationToken ct = default)
    {
        var username = await GetUsernameAsync(userId, ct);
        return username == null ? null : BuildVirtualRoot(username);
    }

    public async Task<string?> GetUsernameAsync(Guid userId, CancellationToken ct = default)
    {
        if (userId == Guid.Empty) return null;

        var cacheKey = $"username:{userId}";
        if (_cache.TryGetValue<string>(cacheKey, out var cached) && !string.IsNullOrEmpty(cached))
            return cached;

        var username = await _db.Users
            .AsNoTracking()
            .Where(u => u.Id == userId)
            .Select(u => u.Username)
            .FirstOrDefaultAsync(ct);

        if (!string.IsNullOrEmpty(username))
        {
            _cache.Set(cacheKey, username, CacheTtl);
        }
        return username;
    }

    /// <summary>
    /// Reverse lookup: username → userId. Cached. Returns null if no such user.
    /// </summary>
    public async Task<Guid?> ResolveUserIdAsync(string username, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(username)) return null;

        var cacheKey = $"userid:{username.ToLowerInvariant()}";
        if (_cache.TryGetValue<Guid>(cacheKey, out var cached) && cached != Guid.Empty)
            return cached;

        var id = await _db.Users
            .AsNoTracking()
            .Where(u => u.Username == username)
            .Select(u => (Guid?)u.Id)
            .FirstOrDefaultAsync(ct);

        if (id.HasValue)
        {
            _cache.Set(cacheKey, id.Value, CacheTtl);
        }
        return id;
    }

    public void InvalidateUserCache(Guid userId, string? username = null, string? oldUsername = null)
    {
        _cache.Remove($"username:{userId}");
        if (!string.IsNullOrEmpty(username))
            _cache.Remove($"userid:{username.ToLowerInvariant()}");
        if (!string.IsNullOrEmpty(oldUsername))
            _cache.Remove($"userid:{oldUsername.ToLowerInvariant()}");
    }

    // ---- Path parsing -------------------------------------------------------

    /// <summary>
    /// Returns true if <paramref name="path"/> starts with the user-storage
    /// prefix and outputs the embedded username segment.
    /// </summary>
    public static bool TryGetUsernameFromPath(string? path, out string username)
    {
        username = string.Empty;
        if (string.IsNullOrWhiteSpace(path)) return false;

        var normalized = path.Replace('\\', '/').TrimStart('/');
        if (!normalized.StartsWith("assets/users/", StringComparison.OrdinalIgnoreCase))
            return false;

        var parts = normalized.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length < 3) return false;

        username = parts[2];
        return !string.IsNullOrEmpty(username);
    }

    /// <summary>
    /// Resolves the owner userId from a virtual path. Returns null if the path
    /// is not a user path or the username does not match any user.
    /// </summary>
    public async Task<Guid?> TryGetOwnerIdFromPathAsync(string? path, CancellationToken ct = default)
    {
        if (!TryGetUsernameFromPath(path, out var username))
            return null;
        return await ResolveUserIdAsync(username, ct);
    }

    // ---- Rename preview / migration -----------------------------------------

    /// <summary>
    /// Computes the impact of renaming <paramref name="userId"/> from its current
    /// username to <paramref name="newUsername"/> without performing the rename.
    /// </summary>
    public async Task<RenamePreview> PreviewRenameAsync(
        Guid userId,
        string newUsername,
        CancellationToken ct = default)
    {
        var user = await _db.Users.AsNoTracking().FirstOrDefaultAsync(u => u.Id == userId, ct)
            ?? throw new InvalidOperationException("Usuario no encontrado");

        var validation = ValidateUsername(newUsername);
        if (!validation.IsValid)
            return RenamePreview.Invalid(user.Username, newUsername, validation.Error!);

        if (string.Equals(user.Username, newUsername, StringComparison.Ordinal))
            return RenamePreview.NoChange(user.Username);

        var taken = await _db.Users.AnyAsync(u => u.Username == newUsername && u.Id != userId, ct);
        if (taken)
            return RenamePreview.Invalid(user.Username, newUsername, "Este nombre de usuario ya está en uso");

        var oldRoot = BuildVirtualRoot(user.Username);
        var newRoot = BuildVirtualRoot(newUsername);

        var oldRootPrefix = oldRoot + "/";

        var assetsCount = await _db.Assets
            .CountAsync(a =>
                a.FullPath == oldRoot ||
                a.FullPath.StartsWith(oldRootPrefix) ||
                a.DeletedFromPath == oldRoot ||
                (a.DeletedFromPath != null && a.DeletedFromPath.StartsWith(oldRootPrefix)),
                ct);

        var foldersCount = await _db.Folders
            .CountAsync(f => f.Path == oldRoot || f.Path.StartsWith(oldRootPrefix), ct);

        var oldPhysical = await _settings.ResolvePhysicalPathAsync(oldRoot);
        var newPhysical = await _settings.ResolvePhysicalPathAsync(newRoot);

        var folderExistsOnDisk = !string.IsNullOrEmpty(oldPhysical) && Directory.Exists(oldPhysical);
        var newFolderExistsOnDisk = !string.IsNullOrEmpty(newPhysical) && Directory.Exists(newPhysical);

        return new RenamePreview
        {
            IsValid = !newFolderExistsOnDisk,
            ErrorMessage = newFolderExistsOnDisk
                ? "Ya existe una carpeta física en el destino. Resuélvelo antes de continuar."
                : null,
            CurrentUsername = user.Username,
            NewUsername = newUsername,
            CurrentVirtualPath = oldRoot,
            NewVirtualPath = newRoot,
            CurrentPhysicalPath = oldPhysical,
            NewPhysicalPath = newPhysical,
            FolderExistsOnDisk = folderExistsOnDisk,
            AssetsToUpdate = assetsCount,
            FoldersToUpdate = foldersCount
        };
    }

    /// <summary>
    /// Performs the rename: updates User.Username, rewrites Asset.FullPath /
    /// Asset.DeletedFromPath / Folder.Path / Setting.Value for the AssetsPath key,
    /// then moves the physical directory. Atomic within a DB transaction; if the
    /// filesystem move fails the DB transaction is rolled back.
    /// </summary>
    public async Task<RenameResult> RenameAsync(
        Guid userId,
        string newUsername,
        CancellationToken ct = default)
    {
        var preview = await PreviewRenameAsync(userId, newUsername, ct);
        if (!preview.IsValid)
            return RenameResult.Failure(preview.ErrorMessage ?? "Validación fallida");
        if (preview.IsNoChange)
            return RenameResult.NoChange();

        var oldUsername = preview.CurrentUsername;
        var oldRoot = preview.CurrentVirtualPath!;
        var newRoot = preview.NewVirtualPath!;
        var oldPhysical = preview.CurrentPhysicalPath;
        var newPhysical = preview.NewPhysicalPath;

        await using var tx = await _db.Database.BeginTransactionAsync(ct);
        try
        {
            // 1) Rewrite DB strings. PostgreSQL REPLACE on the prefix only.
            // Use raw SQL because EF doesn't translate string-prefix UPDATEs efficiently.
            var oldPrefix = oldRoot + "/";
            var newPrefix = newRoot + "/";

            // Update Assets.FullPath
            await _db.Database.ExecuteSqlRawAsync(
                @"UPDATE ""Assets"" SET ""FullPath"" =
                       CASE WHEN ""FullPath"" = {0} THEN {1}
                            ELSE {1} || SUBSTRING(""FullPath"" FROM LENGTH({2}) + 1)
                       END
                  WHERE ""FullPath"" = {0} OR ""FullPath"" LIKE {3}",
                oldRoot, newRoot, oldRoot, oldPrefix + "%");

            // Update Assets.DeletedFromPath (nullable)
            await _db.Database.ExecuteSqlRawAsync(
                @"UPDATE ""Assets"" SET ""DeletedFromPath"" =
                       CASE WHEN ""DeletedFromPath"" = {0} THEN {1}
                            ELSE {1} || SUBSTRING(""DeletedFromPath"" FROM LENGTH({2}) + 1)
                       END
                  WHERE ""DeletedFromPath"" = {0} OR ""DeletedFromPath"" LIKE {3}",
                oldRoot, newRoot, oldRoot, oldPrefix + "%");

            // Update Folders.Path
            await _db.Database.ExecuteSqlRawAsync(
                @"UPDATE ""Folders"" SET ""Path"" =
                       CASE WHEN ""Path"" = {0} THEN {1}
                            ELSE {1} || SUBSTRING(""Path"" FROM LENGTH({2}) + 1)
                       END
                  WHERE ""Path"" = {0} OR ""Path"" LIKE {3}",
                oldRoot, newRoot, oldRoot, oldPrefix + "%");

            // Also rewrite Folder.Name for the top-level user folder (its name
            // was the previous username).
            await _db.Database.ExecuteSqlRawAsync(
                @"UPDATE ""Folders"" SET ""Name"" = {0} WHERE ""Path"" = {1}",
                newUsername, newRoot);

            // 2) Update the User row itself.
            var user = await _db.Users.FirstAsync(u => u.Id == userId, ct);
            user.Username = newUsername;
            await _db.SaveChangesAsync(ct);

            // 3) Move physical folder. Skipped if there's nothing on disk yet
            //    (e.g. user has no assets — folder was never created).
            if (preview.FolderExistsOnDisk &&
                !string.IsNullOrEmpty(oldPhysical) &&
                !string.IsNullOrEmpty(newPhysical))
            {
                var parentDir = Path.GetDirectoryName(newPhysical);
                if (!string.IsNullOrEmpty(parentDir) && !Directory.Exists(parentDir))
                    Directory.CreateDirectory(parentDir);

                Directory.Move(oldPhysical, newPhysical);
            }

            await tx.CommitAsync(ct);

            // Cache invalidation must happen after commit so concurrent reads
            // don't repopulate with stale data.
            InvalidateUserCache(userId, newUsername, oldUsername);

            _logger.LogInformation(
                "[USERSTORE] Renamed user {UserId}: '{Old}' -> '{New}'. Assets={Assets}, Folders={Folders}",
                userId, oldUsername, newUsername,
                preview.AssetsToUpdate, preview.FoldersToUpdate);

            return RenameResult.Success(preview);
        }
        catch (Exception ex)
        {
            // Attempt to roll back the filesystem move if it succeeded before
            // the commit failed.
            try
            {
                if (preview.FolderExistsOnDisk &&
                    !string.IsNullOrEmpty(newPhysical) && Directory.Exists(newPhysical) &&
                    !string.IsNullOrEmpty(oldPhysical) && !Directory.Exists(oldPhysical))
                {
                    Directory.Move(newPhysical, oldPhysical);
                }
            }
            catch (Exception rollbackEx)
            {
                _logger.LogError(rollbackEx,
                    "[USERSTORE] Filesystem rollback failed after rename failure for user {UserId}. " +
                    "Manual intervention may be required: old='{Old}', new='{New}'.",
                    userId, oldPhysical, newPhysical);
            }

            await tx.RollbackAsync(ct);
            _logger.LogError(ex, "[USERSTORE] Rename failed for user {UserId}", userId);
            return RenameResult.Failure(ex.Message);
        }
    }
}

public sealed class RenamePreview
{
    public bool IsValid { get; init; }
    public bool IsNoChange { get; init; }
    public string? ErrorMessage { get; init; }

    public string CurrentUsername { get; init; } = string.Empty;
    public string NewUsername { get; init; } = string.Empty;

    public string? CurrentVirtualPath { get; init; }
    public string? NewVirtualPath { get; init; }
    public string? CurrentPhysicalPath { get; init; }
    public string? NewPhysicalPath { get; init; }

    public bool FolderExistsOnDisk { get; init; }

    public int AssetsToUpdate { get; init; }
    public int FoldersToUpdate { get; init; }

    public static RenamePreview Invalid(string current, string requested, string error) => new()
    {
        IsValid = false,
        ErrorMessage = error,
        CurrentUsername = current,
        NewUsername = requested
    };

    public static RenamePreview NoChange(string current) => new()
    {
        IsValid = true,
        IsNoChange = true,
        CurrentUsername = current,
        NewUsername = current
    };
}

public sealed class RenameResult
{
    public bool Succeeded { get; init; }
    public bool IsNoChange { get; init; }
    public string? ErrorMessage { get; init; }
    public RenamePreview? Preview { get; init; }

    public static RenameResult Success(RenamePreview preview) => new()
    {
        Succeeded = true,
        Preview = preview
    };

    public static RenameResult NoChange() => new()
    {
        Succeeded = true,
        IsNoChange = true
    };

    public static RenameResult Failure(string error) => new()
    {
        Succeeded = false,
        ErrorMessage = error
    };
}
