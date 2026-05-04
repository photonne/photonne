namespace Photonne.Client.Web.Services;

public interface IDatabaseBackupService
{
    Task<BackupFileResult> ExportAsync(bool includeMl);
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
    bool IncludesMlData);
