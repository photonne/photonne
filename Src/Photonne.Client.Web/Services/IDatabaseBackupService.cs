namespace Photonne.Client.Web.Services;

public enum BackupLevel
{
    Config,
    Essential,
    Full,
}

public interface IDatabaseBackupService
{
    Task<BackupFileResult> ExportAsync(BackupLevel level);
    Task<DatabaseRestoreResult> RestoreAsync(Stream fileStream, string fileName);
}

public record BackupFileResult(byte[] Bytes, string FileName);
public record DatabaseRestoreResult(bool Success, string Message, DatabaseRestoreStats? Stats);
public record DatabaseRestoreStats(
    int Users,
    int Assets,
    int Albums,
    int Folders,
    int ExternalLibraries,
    int People,
    int Faces,
    int Embeddings,
    int OcrLines,
    bool IncludesConfig,
    bool IncludesLibrary,
    bool IncludesMlData);
