namespace Photonne.Client.Web.Models;

public class TrashStatsResponse
{
    public int TotalItems { get; set; }
    public long TotalBytes { get; set; }
    public int ExpiredItems { get; set; }
    public int RetentionDays { get; set; }
    public int MaxQuotaMb { get; set; }
    public int OverQuotaUsers { get; set; }
    public long OverQuotaBytes { get; set; }
}

public class TrashCleanupResult
{
    public bool Success { get; set; }
    public string Message { get; set; } = string.Empty;
    public int Deleted { get; set; }
}

/// <summary>
/// Un elemento de la papelera de carpetas compartidas: un asset que un usuario
/// eliminó desde una carpeta compartida y que los administradores (o quienes
/// gestionan la carpeta de origen) pueden restaurar o eliminar permanentemente.
/// </summary>
public class SharedTrashItem
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string FullPath { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public string Type { get; set; } = string.Empty;
    public string Extension { get; set; } = string.Empty;
    public bool HasThumbnails { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public DateTime? DeletedAt { get; set; }
    public string? DeletedByUsername { get; set; }
    public string? DeletedFromPath { get; set; }
    public string? DeletedFromFolderName { get; set; }

    public string ThumbnailUrl => $"{ApiConfig.BaseUrl}/api/assets/{Id}/thumbnail?size=Small";

    public string FileSizeFormatted => FormatBytes(FileSize);

    private static string FormatBytes(long bytes)
    {
        if (bytes >= 1_073_741_824)
            return $"{bytes / 1_073_741_824.0:F1} GB";
        if (bytes >= 1_048_576)
            return $"{bytes / 1_048_576.0:F1} MB";
        if (bytes >= 1_024)
            return $"{bytes / 1_024.0:F1} KB";
        return $"{bytes} B";
    }
}

public class SharedTrashPage
{
    public List<SharedTrashItem> Items { get; set; } = new();
    public bool HasMore { get; set; }
    public DateTime? NextCursor { get; set; }
}
