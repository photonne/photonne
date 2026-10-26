using System.Security.Cryptography;
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
            await WarnAboutPlaceholderPasswordsAsync(cancellationToken);
            return;
        }

        var adminUsername = _configuration["AdminUser:Username"];
        var adminEmail = _configuration["AdminUser:Email"];
        var adminPassword = _configuration["AdminUser:Password"];

        if (string.IsNullOrWhiteSpace(adminUsername) ||
            string.IsNullOrWhiteSpace(adminEmail))
        {
            Console.WriteLine("[INIT] No hay administradores y no se configuró AdminUser:* — saltando.");
            return;
        }

        // Without a password, or with one published in this repo, the admin gets
        // a random one that is printed once: anyone could guess "admin123".
        var generatedPassword = false;
        if (string.IsNullOrWhiteSpace(adminPassword) || IsPlaceholderPassword(adminPassword))
        {
            adminPassword = GeneratePassword();
            generatedPassword = true;
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
        if (generatedPassword)
        {
            Console.WriteLine(
                $"[INIT] ADMIN_PASSWORD no estaba definida o era un valor de ejemplo. Contraseña generada para '{adminUsername}': {adminPassword}");
            Console.WriteLine("[INIT] Cámbiala en Ajustes → Seguridad después de entrar; no se volverá a mostrar.");
        }
    }

    /// <summary>
    /// Passwords that have shipped as defaults or examples in docker-compose,
    /// appsettings, .env.example and the README.
    /// </summary>
    internal static readonly string[] PlaceholderPasswords =
    [
        "admin123",
        "cambia_esto_por_password_seguro",
        "contraseña-segura",
    ];

    internal static bool IsPlaceholderPassword(string password) =>
        PlaceholderPasswords.Contains(password.Trim(), StringComparer.Ordinal);

    /// <summary>
    /// Four dash-separated groups of five, without look-alikes (0/O, 1/l/I) so
    /// it can be typed from the log. Redrawn until it meets the password policy
    /// (upper, lower, digit; the dashes are the special character).
    /// </summary>
    internal static string GeneratePassword()
    {
        const string alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        while (true)
        {
            var groups = Enumerable.Range(0, 4).Select(_ => RandomNumberGenerator.GetString(alphabet, 5));
            var password = string.Join('-', groups);
            if (password.Any(char.IsUpper) && password.Any(char.IsLower) && password.Any(char.IsDigit))
                return password;
        }
    }

    /// <summary>
    /// Existing installs keep their admin, so a placeholder password stays until
    /// someone changes it: say so on every boot.
    /// </summary>
    private async Task WarnAboutPlaceholderPasswordsAsync(CancellationToken cancellationToken)
    {
        var admins = await _dbContext.Users
            .Where(u => u.Role == "Admin" && u.IsActive)
            .Select(u => new { u.Username, u.PasswordHash })
            .ToListAsync(cancellationToken);

        foreach (var admin in admins)
        {
            if (PlaceholderPasswords.Any(p => _authService.VerifyPassword(p, admin.PasswordHash)))
            {
                Console.WriteLine(
                    $"[WARN] El administrador '{admin.Username}' usa una contraseña de ejemplo pública. Cámbiala en Ajustes → Seguridad.");
            }
        }
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
