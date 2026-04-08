using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class DirectoryScanner
{
    private static readonly HashSet<string> ImageExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".gif", ".webp", ".heic", ".heif"
    };
    
    private static readonly HashSet<string> VideoExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".webm", ".m4v", ".3gp", ".mpeg", ".mpg", ".3g2", ".3gpp", ".amv", ".asf", ".f4v", ".m2v", ".mp2", ".mpe", ".mpv", ".ogv", ".qt", ".vob"
    };
    
    private static readonly HashSet<string> AllowedExtensions = new(
        ImageExtensions.Concat(VideoExtensions), 
        StringComparer.OrdinalIgnoreCase);

    /// <summary>
    /// Recursively scans a directory and returns all media files (images and videos)
    /// Ignores hidden files and unsupported formats
    /// </summary>
    public async Task<IEnumerable<ScannedFile>> ScanDirectoryAsync(string directoryPath, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(directoryPath))
        {
            throw new ArgumentException("Directory path cannot be empty.", nameof(directoryPath));
        }

        if (!Directory.Exists(directoryPath))
        {
            throw new DirectoryNotFoundException($"Directory '{directoryPath}' does not exist.");
        }

        var files = new List<ScannedFile>();

        await Task.Run(() =>
        {
            try 
            {
                var enumerationOptions = new EnumerationOptions
                {
                    RecurseSubdirectories = true,
                    IgnoreInaccessible = true,
                    // Solo saltar archivos realmente ocultos del sistema, no archivos con otros atributos
                    AttributesToSkip = FileAttributes.Hidden | FileAttributes.System,
                    MatchType = MatchType.Simple
                };

                var allFiles = Directory.EnumerateFiles(directoryPath, "*.*", enumerationOptions);
                int totalFilesFound = 0;
                int allowedFilesFound = 0;
                int rejectedFiles = 0;

                foreach (var filePath in allFiles)
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    totalFilesFound++;

                    if (IsInBinFolder(filePath))
                    {
                        rejectedFiles++;
                        continue;
                    }

                    var extension = Path.GetExtension(filePath);
                    
                    // Normalizar extensión: asegurar que tenga el punto y esté en minúsculas para comparación
                    var normalizedExtension = string.IsNullOrEmpty(extension) 
                        ? string.Empty 
                        : extension.ToLowerInvariant();
                    
                    // Si la extensión no tiene punto, agregarlo
                    if (!string.IsNullOrEmpty(normalizedExtension) && !normalizedExtension.StartsWith("."))
                    {
                        normalizedExtension = "." + normalizedExtension;
                    }
                    
                    // Verificar si está permitida (comparación case-insensitive)
                    var isAllowed = AllowedExtensions.Contains(normalizedExtension);
                    
                    // Log detallado para archivos específicos
                    if (normalizedExtension.Equals(".jpg", StringComparison.OrdinalIgnoreCase) || 
                        normalizedExtension.Equals(".jpeg", StringComparison.OrdinalIgnoreCase) ||
                        normalizedExtension.Equals(".heic", StringComparison.OrdinalIgnoreCase) || 
                        normalizedExtension.Equals(".mov", StringComparison.OrdinalIgnoreCase))
                    {
                        Console.WriteLine($"[DEBUG] File: {Path.GetFileName(filePath)}, Extension: '{extension}' (normalized: '{normalizedExtension}'), IsAllowed: {isAllowed}");
                    }
                    
                    if (isAllowed)
                    {
                        try
                        {
                            var fileInfo = new FileInfo(filePath);
                            
                            // Verificar que el archivo existe y es accesible
                            if (!fileInfo.Exists)
                            {
                                Console.WriteLine($"[WARNING] FileInfo says file doesn't exist: {filePath}");
                                rejectedFiles++;
                                continue;
                            }
                            
                            var assetType = ImageExtensions.Contains(normalizedExtension) ? AssetType.Image : AssetType.Video;
                            
                            var createdUtc = fileInfo.CreationTimeUtc;
                            var modifiedUtc = fileInfo.LastWriteTimeUtc;
                            var effectiveCreatedUtc = createdUtc > modifiedUtc ? modifiedUtc : createdUtc;
                            
                            files.Add(new ScannedFile
                            {
                                FileName = fileInfo.Name,
                                FullPath = fileInfo.FullName,
                                FileSize = fileInfo.Length,
                                FileCreatedAt = effectiveCreatedUtc,
                                FileModifiedAt = modifiedUtc,
                                Extension = normalizedExtension, // Usar la extensión normalizada
                                AssetType = assetType
                            });
                            allowedFilesFound++;
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[ERROR] Error processing file {filePath}: {ex.Message}");
                            rejectedFiles++;
                        }
                    }
                    else
                    {
                        rejectedFiles++;
                        // Log archivos rechazados para debugging
                        if (normalizedExtension.Equals(".jpg", StringComparison.OrdinalIgnoreCase) || 
                            normalizedExtension.Equals(".jpeg", StringComparison.OrdinalIgnoreCase) ||
                            normalizedExtension.Equals(".heic", StringComparison.OrdinalIgnoreCase) || 
                            normalizedExtension.Equals(".mov", StringComparison.OrdinalIgnoreCase))
                        {
                            Console.WriteLine($"[WARNING] File rejected - Extension not in allowed list: {filePath}, Extension: '{extension}' (normalized: '{normalizedExtension}')");
                            Console.WriteLine($"[WARNING] Allowed extensions include: {string.Join(", ", AllowedExtensions.Take(10))}...");
                        }
                    }
                }
                
                Console.WriteLine($"[DEBUG] DirectoryScanner summary for {directoryPath}:");
                Console.WriteLine($"[DEBUG]   Total files found: {totalFilesFound}");
                Console.WriteLine($"[DEBUG]   Allowed files: {allowedFilesFound}");
                Console.WriteLine($"[DEBUG]   Rejected files: {rejectedFiles}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Error scanning directory {directoryPath}: {ex.Message}");
            }
        }, cancellationToken);

        return files;
    }

    private static bool IsInBinFolder(string filePath)
    {
        var normalized = filePath.Replace('\\', '/');
        return normalized.Contains("/_trash/", StringComparison.OrdinalIgnoreCase);
    }
}

/// <summary>
/// Represents a scanned media file from the filesystem
/// </summary>
public class ScannedFile
{
    public string FileName { get; set; } = string.Empty;
    public string FullPath { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public DateTime FileCreatedAt { get; set; }
    public DateTime FileModifiedAt { get; set; }
    public string Extension { get; set; } = string.Empty;
    public AssetType AssetType { get; set; }
}

