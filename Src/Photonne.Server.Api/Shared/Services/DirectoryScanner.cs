using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class DirectoryScanner
{
    /// <summary>
    /// Recursively scans a directory and returns all media files (images and videos).
    /// Ignores hidden files and unsupported formats. Kept for callers that only
    /// care about indexable media; delegates to <see cref="ScanDirectoryWithUnsupportedAsync"/>.
    /// </summary>
    public async Task<IEnumerable<ScannedFile>> ScanDirectoryAsync(string directoryPath, CancellationToken cancellationToken = default)
    {
        var result = await ScanDirectoryWithUnsupportedAsync(directoryPath, cancellationToken);
        return result.Allowed;
    }

    /// <summary>
    /// Recursively scans a directory and returns BOTH the indexable media files
    /// and the unsupported files (anything whose extension isn't a known image /
    /// video). The unsupported list is what feeds the "Otros archivos" catalogue;
    /// previously these were silently dropped.
    /// </summary>
    public async Task<ScanResult> ScanDirectoryWithUnsupportedAsync(string directoryPath, CancellationToken cancellationToken = default)
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
        var unsupported = new List<ScannedFile>();

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
                    var isAllowed = MediaFileTypes.IsSupported(normalizedExtension);
                    
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
                            
                            var assetType = MediaFileTypes.Classify(normalizedExtension);
                            
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
                        // Not an indexable media file — catalogue it as unsupported
                        // (was previously discarded) so the user can still see it.
                        try
                        {
                            var fileInfo = new FileInfo(filePath);
                            if (fileInfo.Exists)
                            {
                                var createdUtc = fileInfo.CreationTimeUtc;
                                var modifiedUtc = fileInfo.LastWriteTimeUtc;
                                var effectiveCreatedUtc = createdUtc > modifiedUtc ? modifiedUtc : createdUtc;

                                unsupported.Add(new ScannedFile
                                {
                                    FileName = fileInfo.Name,
                                    FullPath = fileInfo.FullName,
                                    FileSize = fileInfo.Length,
                                    FileCreatedAt = effectiveCreatedUtc,
                                    FileModifiedAt = modifiedUtc,
                                    Extension = normalizedExtension,
                                    AssetType = AssetType.Image // unused for unsupported
                                });
                            }
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"[WARNING] Could not catalogue unsupported file {filePath}: {ex.Message}");
                        }
                    }
                }
                
                Console.WriteLine($"[DEBUG] DirectoryScanner summary for {directoryPath}:");
                Console.WriteLine($"[DEBUG]   Total files found: {totalFilesFound}");
                Console.WriteLine($"[DEBUG]   Allowed files: {allowedFilesFound}");
                Console.WriteLine($"[DEBUG]   Rejected files: {rejectedFiles} (unsupported catalogued: {unsupported.Count})");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[ERROR] Error scanning directory {directoryPath}: {ex.Message}");
            }
        }, cancellationToken);

        return new ScanResult(files, unsupported);
    }

    private static bool IsInBinFolder(string filePath)
    {
        var normalized = filePath.Replace('\\', '/');
        return normalized.Contains("/_trash/", StringComparison.OrdinalIgnoreCase);
    }
}

/// <summary>
/// Outcome of a directory scan: indexable media (<see cref="Allowed"/>) and the
/// non-media files catalogued separately (<see cref="Unsupported"/>).
/// </summary>
public sealed record ScanResult(
    IReadOnlyList<ScannedFile> Allowed,
    IReadOnlyList<ScannedFile> Unsupported);

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

