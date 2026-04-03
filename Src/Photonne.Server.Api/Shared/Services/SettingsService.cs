using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class SettingsService
{
    private readonly ApplicationDbContext _dbContext;
    private readonly IConfiguration _configuration;
    public const string AssetsPathKey = "AssetsPath";
    private static readonly Guid GlobalUserId = Guid.Empty;

    public SettingsService(ApplicationDbContext dbContext, IConfiguration configuration)
    {
        _dbContext = dbContext;
        _configuration = configuration;
    }

    public async Task<string> GetSettingAsync(string key, Guid userId, string defaultValue = "")
    {
        var setting = await _dbContext.Settings
            .FirstOrDefaultAsync(s => s.UserId == userId && s.Key == key);

        if (setting == null && userId != GlobalUserId)
        {
            setting = await _dbContext.Settings
                .FirstOrDefaultAsync(s => s.UserId == GlobalUserId && s.Key == key);
        }

        return setting?.Value ?? defaultValue;
    }

    public async Task SetSettingAsync(string key, string value, Guid userId)
    {
        var setting = await _dbContext.Settings
            .FirstOrDefaultAsync(s => s.UserId == userId && s.Key == key);
        if (setting == null)
        {
            setting = new Setting
            {
                UserId = userId,
                Key = key,
                Value = value,
                UpdatedAt = DateTime.UtcNow
            };
            _dbContext.Settings.Add(setting);
        }
        else
        {
            setting.Value = value;
            setting.UpdatedAt = DateTime.UtcNow;
            _dbContext.Settings.Update(setting);
        }
        await _dbContext.SaveChangesAsync();
    }

    public async Task<string> GetAssetsPathAsync(Guid userId)
    {
        var path = await GetSettingAsync(AssetsPathKey, userId);
        
        if (string.IsNullOrEmpty(path))
        {
            path = Environment.GetEnvironmentVariable("ASSETS_PATH") 
                   ?? Path.Combine(Directory.GetCurrentDirectory(), "assets");

            // Use the container path if running in Docker
            if (Directory.Exists("/assets"))
            {
                path = "/assets";
            }
        }
        
        return path;
    }

    /// <summary>
    /// Obtiene la ruta interna del NAS donde se almacenan los assets sincronizados.
    /// Esta ruta siempre viene de la variable de entorno ASSETS_PATH, appsettings.json o un valor por defecto,
    /// independientemente de la configuración del usuario en Settings.
    /// </summary>
    public string GetInternalAssetsPath()
    {
        // 1. Intentar leer de variable de entorno (tiene prioridad)
        var path = Environment.GetEnvironmentVariable("ASSETS_PATH");
        
        // 2. Si no existe, leer de appsettings.json
        if (string.IsNullOrEmpty(path))
        {
            path = _configuration["ASSETS_PATH"];
        }
        
        // 3. Si aún no existe, usar valor por defecto
        if (string.IsNullOrEmpty(path))
        {
            path = Path.Combine(Directory.GetCurrentDirectory(), "assets");
        }

        // Use the container path if running in Docker
        if (Directory.Exists("/assets"))
        {
            path = "/assets";
        }
        
        return path;
    }

    public async Task<string> ResolvePhysicalPathAsync(string dbPath)
    {
        if (string.IsNullOrEmpty(dbPath)) return string.Empty;

        if (dbPath.StartsWith("/assets/", StringComparison.OrdinalIgnoreCase))
        {
            // Usar la ruta interna del NAS para resolver rutas virtuales
            var assetsPath = GetInternalAssetsPath();
            var relativePath = dbPath.Substring("/assets/".Length);
            return Path.Combine(assetsPath, relativePath.Replace('/', Path.DirectorySeparatorChar));
        }

        return dbPath;
    }

    public async Task<string> VirtualizePathAsync(string physicalPath)
    {
        if (string.IsNullOrEmpty(physicalPath)) return string.Empty;

        // Usar la ruta interna del NAS para virtualizar rutas
        var managedLibraryPath = GetInternalAssetsPath();
        var normalizedPhysicalPath = Path.GetFullPath(physicalPath);
        var normalizedLibraryPath = Path.GetFullPath(managedLibraryPath);

        if (normalizedPhysicalPath.StartsWith(normalizedLibraryPath, StringComparison.OrdinalIgnoreCase))
        {
            var relativePath = Path.GetRelativePath(normalizedLibraryPath, normalizedPhysicalPath).Replace('\\', '/');
            return "/assets/" + relativePath.TrimStart('/');
        }

        return physicalPath;
    }
}
