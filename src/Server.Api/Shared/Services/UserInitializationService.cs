using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class UserInitializationService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly IAuthService _authService;
    private readonly IConfiguration _configuration;
    private readonly IOptions<DemoModeOptions> _demoOptions;

    public UserInitializationService(
        ApplicationDbContext dbContext,
        IAuthService authService,
        IConfiguration configuration,
        IOptions<DemoModeOptions> demoOptions)
    {
        _dbContext = dbContext;
        _authService = authService;
        _configuration = configuration;
        _demoOptions = demoOptions;
    }

    /// <summary>
    /// One-shot bootstrap: creates the initial admin user only when the database
    /// has no admin yet. Once any admin exists — regardless of whether their
    /// username/email still match the values in <c>AdminUser:*</c> configuration —
    /// the <c>ADMIN_*</c> variables are ignored, so renaming the admin from the
    /// UI never causes a duplicate to be re-spawned on the next container start.
    /// </summary>
    public async Task InitializeAdminUserAsync(CancellationToken cancellationToken = default)
    {
        // In demo mode the whole admin account is skipped on purpose: the demo is public,
        // so leaving an admin password around (even a long one) widens the attack surface.
        // The DemoSeederService creates the shared `demo` user instead.
        if (_demoOptions.Value.Enabled)
        {
            Console.WriteLine("[INIT] Modo demo activo — omitiendo creación de usuario administrador.");
            return;
        }

        // If any admin already exists, this is not a fresh install: do not create
        // anything, just make sure at least one is flagged as primary and return.
        var anyAdminExists = await _dbContext.Users
            .AnyAsync(u => u.Role == "Admin", cancellationToken);

        if (anyAdminExists)
        {
            Console.WriteLine("[INIT] Admin existente detectado — saltando creación inicial.");
            await EnsurePrimaryAdminExistsAsync(cancellationToken);
            return;
        }

        var adminUsername = _configuration["AdminUser:Username"];
        var adminEmail = _configuration["AdminUser:Email"];
        var adminPassword = _configuration["AdminUser:Password"];

        if (string.IsNullOrWhiteSpace(adminUsername) ||
            string.IsNullOrWhiteSpace(adminEmail) ||
            string.IsNullOrWhiteSpace(adminPassword))
        {
            Console.WriteLine("[INIT] No hay administradores y no se configuró AdminUser:* — saltando.");
            return;
        }

        // Username becomes the on-disk folder name (/assets/users/{username}/...).
        // Reject characters that break the filesystem layout before persisting.
        var validation = UserStorageService.ValidateUsername(adminUsername);
        if (!validation.IsValid)
        {
            Console.WriteLine(
                $"[INIT] Username inválido para admin inicial: '{adminUsername}'. {validation.Error}. Aborto.");
            return;
        }

        Console.WriteLine(
            $"[INIT] No hay administradores en BD. Creando admin inicial '{adminUsername}' desde la configuración.");

        var adminUser = new User
        {
            Username = adminUsername,
            Email = adminEmail,
            PasswordHash = _authService.HashPassword(adminPassword),
            FirstName = _configuration["AdminUser:FirstName"] ?? "Administrador",
            LastName = _configuration["AdminUser:LastName"] ?? "Sistema",
            Role = "Admin",
            IsActive = true,
            IsEmailVerified = true,
            IsPrimaryAdmin = true,
            CreatedAt = DateTime.UtcNow
        };

        _dbContext.Users.Add(adminUser);
        await _dbContext.SaveChangesAsync(cancellationToken);

        Console.WriteLine(
            $"[INIT] Usuario administrador '{adminUsername}' creado exitosamente como admin principal.");
    }

    /// <summary>
    /// Ensures at least one admin is marked as primary.
    /// Called when no AdminUser config is provided but there may be existing admins.
    /// </summary>
    private async Task EnsurePrimaryAdminExistsAsync(CancellationToken cancellationToken)
    {
        var hasPrimary = await _dbContext.Users.AnyAsync(u => u.IsPrimaryAdmin, cancellationToken);
        if (hasPrimary) return;

        var firstAdmin = await _dbContext.Users
            .Where(u => u.Role == "Admin" && u.IsActive)
            .OrderBy(u => u.CreatedAt)
            .FirstOrDefaultAsync(cancellationToken);

        if (firstAdmin == null) return;

        firstAdmin.IsPrimaryAdmin = true;
        await _dbContext.SaveChangesAsync(cancellationToken);
        Console.WriteLine($"[INFO] Usuario '{firstAdmin.Username}' marcado como admin principal (retroactivo).");
    }
}
