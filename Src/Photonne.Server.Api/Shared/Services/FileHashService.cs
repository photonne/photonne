using System.Security.Cryptography;

namespace Photonne.Server.Api.Shared.Services;

public class FileHashService
{
    /// <summary>
    /// Calculates SHA256 hash of a file
    /// </summary>
    public async Task<string> CalculateFileHashAsync(string filePath, CancellationToken cancellationToken = default)
    {
        using var sha256 = SHA256.Create();
        await using var fileStream = File.OpenRead(filePath);
        
        var hashBytes = await sha256.ComputeHashAsync(fileStream, cancellationToken);
        return Convert.ToHexString(hashBytes).ToLowerInvariant();
    }
    
    /// <summary>
    /// Quick heuristic check using file size and modification time
    /// Returns true if file might have changed (size or mtime differs)
    /// </summary>
    public bool HasFileChanged(string filePath, long? existingFileSize, DateTime? existingModifiedDate)
    {
        if (!File.Exists(filePath))
            return true;
            
        var fileInfo = new FileInfo(filePath);
        
        if (existingFileSize.HasValue && fileInfo.Length != existingFileSize.Value)
            return true;
            
        if (existingModifiedDate.HasValue)
        {
            var existingMtime = existingModifiedDate.Value;
            var currentMtime = fileInfo.LastWriteTimeUtc;
            
            // Allow 1 second tolerance for filesystem timestamp precision
            if (Math.Abs((currentMtime - existingMtime).TotalSeconds) > 1)
                return true;
        }
        
        return false;
    }
}

