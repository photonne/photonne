using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Models;
using PhotoHub.Server.Api.Shared.Services;

namespace PhotoHub.Server.Api.Shared.Services;

public class UserInitializationService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly IAuthService _authService;
    private readonly IConfiguration _configuration;

    public UserInitializationService(
        ApplicationDbContext dbContext,
        IAuthService authService,
        IConfiguration configuration)
    {
        _dbContext = dbContext;
        _authService = authService;
        _configuration = configuration;
    }

    public async Task InitializeAdminUserAsync(CancellationToken cancellationToken = default)
    {
        var adminUsername = _configuration["AdminUser:Username"];
        var adminEmail = _configuration["AdminUser:Email"];
        var adminPassword = _configuration["AdminUser:Password"];

        // Si no hay configuración, no crear usuario admin
        if (string.IsNullOrWhiteSpace(adminUsername) ||
            string.IsNullOrWhiteSpace(adminEmail) ||
            string.IsNullOrWhiteSpace(adminPassword))
        {
            Console.WriteLine("[INFO] No se configuró usuario administrador inicial. Saltando inicialización.");
            await EnsurePrimaryAdminExistsAsync(cancellationToken);
            return;
        }

        // Verificar si ya existe un usuario con ese username o email
        var existingUser = await _dbContext.Users
            .FirstOrDefaultAsync(u => u.Username == adminUsername || u.Email == adminEmail, cancellationToken);

        if (existingUser != null)
        {
            Console.WriteLine($"[INFO] Usuario administrador '{adminUsername}' ya existe. Saltando inicialización.");
            // Asegurarse de que esté marcado como admin principal si no lo está
            if (!existingUser.IsPrimaryAdmin)
            {
                existingUser.IsPrimaryAdmin = true;
                await _dbContext.SaveChangesAsync(cancellationToken);
                Console.WriteLine($"[INFO] Usuario '{adminUsername}' marcado como admin principal.");
            }
            return;
        }

        // Crear usuario administrador
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

        Console.WriteLine($"[INFO] Usuario administrador '{adminUsername}' creado exitosamente como admin principal.");
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
