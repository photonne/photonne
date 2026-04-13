using System.Text;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class SettingsService
{
    private readonly ApplicationDbContext _dbContext;
    public const string AssetsPathKey = "AssetsPath";
    public const string InternalAssetsPath = "/data/assets";
    private static readonly Guid GlobalUserId = Guid.Empty;

    public SettingsService(ApplicationDbContext dbContext)
    {
        _dbContext = dbContext;
    }

    public async Task<string> GetSettingAsync(string key, Guid userId, string defaultValue = "")
    {
        var setting = await _dbContext.Settings
            .FirstOrDefaultAsync(s => s.OwnerId == userId && s.Key == key);

        if (setting == null && userId != GlobalUserId)
        {
            setting = await _dbContext.Settings
                .FirstOrDefaultAsync(s => s.OwnerId == GlobalUserId && s.Key == key);
        }

        return setting?.Value ?? defaultValue;
    }

    public async Task SetSettingAsync(string key, string value, Guid userId)
    {
        var setting = await _dbContext.Settings
            .FirstOrDefaultAsync(s => s.OwnerId == userId && s.Key == key);
        if (setting == null)
        {
            setting = new Setting
            {
                OwnerId = userId,
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
            path = GetInternalAssetsPath();
        }

        return path;
    }

    public string GetInternalAssetsPath() => InternalAssetsPath;

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

        // Para rutas externas, comprobar si el archivo existe; si no, probar
        // con la normalización Unicode opuesta (NFC ↔ NFD) para cubrir
        // diferencias entre cómo el filesystem y la BD almacenan acentos.
        if (File.Exists(dbPath))
            return dbPath;

        var nfcPath = dbPath.Normalize(NormalizationForm.FormC);
        if (nfcPath != dbPath && File.Exists(nfcPath))
            return nfcPath;

        var nfdPath = dbPath.Normalize(NormalizationForm.FormD);
        if (nfdPath != dbPath && File.Exists(nfdPath))
            return nfdPath;

        // Devolver la ruta original; el caller se encargará del 404
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
