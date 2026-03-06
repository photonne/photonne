using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Server.Api.Shared.Models;
using PhotoHub.Server.Api.Shared.Services;

namespace PhotoHub.Server.Api.Features.SyncAsset;

public class SyncAssetEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/sync", Handle)
            .WithName("SyncAsset")
            .WithTags("Assets")
            .WithDescription("Copies a pending asset from the user's device to the internal assets directory. The file will be indexed when the scan process runs.")
            .RequireAuthorization();
    }

    private async Task<IResult> Handle(
        [FromQuery] string path,
        [FromServices] SettingsService settingsService,
        [FromServices] FileHashService hashService,
        [FromServices] ExifExtractorService exifService,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IServiceScopeFactory serviceScopeFactory,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrEmpty(path))
            return Results.BadRequest("La ruta es obligatoria");

        try
        {
            var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
            if (userIdClaim == null || !Guid.TryParse(userIdClaim.Value, out var userId))
            {
                return Results.Unauthorized();
            }

            // Validar que el archivo proviene de la ruta configurada por el usuario
            var userConfiguredPath = await settingsService.GetAssetsPathAsync(userId);
            if (!IsPathSafe(path, userConfiguredPath))
                return Results.Forbid();

            if (!File.Exists(path))
                return Results.NotFound("El archivo no existe en el disco");

            // Obtener la ruta interna del NAS (ASSETS_PATH)
            var deviceBackupVirtual = $"/assets/users/{userId}/DeviceBackup";
            var deviceBackupRoot = await settingsService.ResolvePhysicalPathAsync(deviceBackupVirtual);

            await EnsureFolderRecordAsync(dbContext, userId, deviceBackupVirtual, cancellationToken);

            if (!Directory.Exists(deviceBackupRoot))
            {
                Directory.CreateDirectory(deviceBackupRoot);
                Console.WriteLine($"[SYNC] Created device backup directory: {deviceBackupRoot}");
            }
            
            Console.WriteLine($"[SYNC] Source path: {path}");
            Console.WriteLine($"[SYNC] Target internal path: {deviceBackupRoot}");

            var currentFileInfo = new FileInfo(path);
            var fileName = currentFileInfo.Name;
            var relativePath = Path.GetRelativePath(userConfiguredPath, path);
            var targetPath = Path.Combine(deviceBackupRoot, relativePath);
            var targetDirectory = Path.GetDirectoryName(targetPath);
            if (!string.IsNullOrEmpty(targetDirectory))
            {
                Directory.CreateDirectory(targetDirectory);
            }

            // Normalizar rutas para comparación
            var normalizedPath = Path.GetFullPath(path);
            var normalizedLibraryPath = Path.GetFullPath(deviceBackupRoot);

            // Si el archivo ya está en el directorio interno, no hacer nada
            if (normalizedPath.StartsWith(normalizedLibraryPath, StringComparison.OrdinalIgnoreCase))
            {
                Console.WriteLine($"[SYNC] File is already in internal directory: {path}");
                return Results.Ok(new { 
                    message = "El archivo ya está en el directorio interno", 
                    targetPath = path 
                });
            }

            // Calcular checksum del archivo fuente para verificar duplicados
            Console.WriteLine($"[SYNC] Calculating checksum for source file: {path}");
            var sourceChecksum = await hashService.CalculateFileHashAsync(path, cancellationToken);
            Console.WriteLine($"[SYNC] Source checksum: {sourceChecksum}");

            // Verificar si ya existe un archivo con el mismo checksum en el directorio interno
            var existingAsset = await dbContext.Assets
                .FirstOrDefaultAsync(a => a.Checksum == sourceChecksum, cancellationToken);
            
            if (existingAsset != null)
            {
                // Resolver la ruta física del asset existente
                var existingPhysicalPath = await settingsService.ResolvePhysicalPathAsync(existingAsset.FullPath);
                
                if (!string.IsNullOrEmpty(existingPhysicalPath) && File.Exists(existingPhysicalPath))
                {
                    Console.WriteLine($"[SYNC] File with same checksum already exists: {existingPhysicalPath}");
                    return Results.Ok(new { 
                        message = "El archivo ya existe en el directorio interno (mismo contenido)", 
                        targetPath = existingPhysicalPath 
                    });
                }
            }

            // Si no está en BD, verificar si el archivo con el nombre ya existe y tiene el mismo checksum
            // (para evitar copiar el mismo archivo múltiples veces)
            if (File.Exists(targetPath))
            {
                try
                {
                    var existingChecksum = await hashService.CalculateFileHashAsync(targetPath, cancellationToken);
                    if (existingChecksum == sourceChecksum)
                    {
                        Console.WriteLine($"[SYNC] File with same name and checksum already exists: {targetPath}");
                        return Results.Ok(new { 
                            message = "El archivo ya existe en el directorio interno", 
                            targetPath = targetPath 
                        });
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[SYNC] Warning: Could not calculate checksum for existing file {targetPath}: {ex.Message}");
                }
            }

            // Manejar colisiones de nombres (solo si el archivo existe pero tiene diferente checksum)
            if (File.Exists(targetPath))
            {
                // Verificar si el archivo existente tiene el mismo checksum
                var existingChecksum = await hashService.CalculateFileHashAsync(targetPath, cancellationToken);
                if (existingChecksum == sourceChecksum)
                {
                    Console.WriteLine($"[SYNC] File with same name and checksum already exists: {targetPath}");
                    return Results.Ok(new { 
                        message = "El archivo ya existe en el directorio interno", 
                        targetPath = targetPath 
                    });
                }
                // Si tiene diferente checksum, crear con nombre único
                targetPath = Path.Combine(deviceBackupRoot, $"{Guid.NewGuid()}_{fileName}");
            }

            // Preservar fechas originales (priorizar EXIF DateTimeOriginal si existe)
            var originalCreation = currentFileInfo.CreationTimeUtc;
            var originalLastWrite = currentFileInfo.LastWriteTimeUtc;
            try
            {
                var exif = await exifService.ExtractExifAsync(path, cancellationToken);
                if (exif?.DateTimeOriginal != null)
                {
                    originalCreation = exif.DateTimeOriginal.Value;
                    originalLastWrite = exif.DateTimeOriginal.Value;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[SYNC] Warning: No se pudo extraer EXIF de {path}: {ex.Message}");
            }

            // Copiar el archivo
            Console.WriteLine($"[SYNC] Copying file from {path} to {targetPath}");
            File.Copy(path, targetPath, overwrite: false);
            Console.WriteLine($"[SYNC] File copied successfully");

            // Aplicar metadatos de tiempo
            File.SetCreationTimeUtc(targetPath, originalCreation);
            File.SetLastWriteTimeUtc(targetPath, originalLastWrite);

            // Verificar que el archivo se copió correctamente
            if (!File.Exists(targetPath))
            {
                throw new Exception($"Error: El archivo no se copió correctamente a {targetPath}");
            }

            Console.WriteLine($"[SYNC] File verified at target path: {targetPath}");

            // Lanzar indexación en segundo plano
            var capturedTargetPath = targetPath;
            var capturedUserId = userId;
            _ = Task.Run(async () =>
            {
                using var scope = serviceScopeFactory.CreateScope();
                var svc = scope.ServiceProvider.GetRequiredService<PhotoHub.Server.Api.Shared.Services.AssetIndexingService>();
                await svc.IndexFileAsync(capturedTargetPath, capturedUserId, CancellationToken.None);
            });

            return Results.Ok(new {
                message = "Archivo sincronizado. Indexando en segundo plano...",
                targetPath = await settingsService.VirtualizePathAsync(targetPath)
            });
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[SYNC ERROR] Error al sincronizar asset: {ex.Message}");
            Console.WriteLine($"[SYNC ERROR] Stack trace: {ex.StackTrace}");
            return Results.Problem($"Error al sincronizar el asset: {ex.Message}");
        }
    }

    private bool IsPathSafe(string path, string assetsPath)
    {
        var fullPath = Path.GetFullPath(path);
        var fullAssetsPath = Path.GetFullPath(assetsPath);
        return fullPath.StartsWith(fullAssetsPath, StringComparison.OrdinalIgnoreCase);
    }

    private static async Task EnsureFolderRecordAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string folderPath,
        CancellationToken cancellationToken)
    {
        var normalizedPath = folderPath.Replace('\\', '/').TrimEnd('/');
        if (string.IsNullOrEmpty(normalizedPath))
        {
            return;
        }

        var existing = await dbContext.Folders
            .FirstOrDefaultAsync(f => f.Path == normalizedPath, cancellationToken);
        if (existing != null)
        {
            return;
        }

        var parentPath = Path.GetDirectoryName(normalizedPath)?.Replace('\\', '/').TrimEnd('/');
        Folder? parentFolder = null;
        if (!string.IsNullOrEmpty(parentPath))
        {
            parentFolder = await dbContext.Folders
                .FirstOrDefaultAsync(f => f.Path == parentPath, cancellationToken);

            if (parentFolder == null)
            {
                parentFolder = new Folder
                {
                    Path = parentPath,
                    Name = Path.GetFileName(parentPath),
                    ParentFolderId = null
                };
                dbContext.Folders.Add(parentFolder);
                await dbContext.SaveChangesAsync(cancellationToken);

                await EnsureFolderPermissionAsync(dbContext, userId, parentFolder.Id, cancellationToken);
            }
        }

        var folder = new Folder
        {
            Path = normalizedPath,
            Name = Path.GetFileName(normalizedPath),
            ParentFolderId = parentFolder?.Id
        };

        dbContext.Folders.Add(folder);
        await dbContext.SaveChangesAsync(cancellationToken);

        await EnsureFolderPermissionAsync(dbContext, userId, folder.Id, cancellationToken);
    }

    private static async Task EnsureFolderPermissionAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        Guid folderId,
        CancellationToken cancellationToken)
    {
        var exists = await dbContext.FolderPermissions
            .AnyAsync(p => p.UserId == userId && p.FolderId == folderId, cancellationToken);
        if (exists)
        {
            return;
        }

        dbContext.FolderPermissions.Add(new FolderPermission
        {
            UserId = userId,
            FolderId = folderId,
            CanRead = true,
            CanWrite = true,
            CanDelete = true,
            CanManagePermissions = true,
            GrantedByUserId = userId
        });
        await dbContext.SaveChangesAsync(cancellationToken);
    }
}
