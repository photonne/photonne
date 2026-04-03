namespace Photonne.Client.Web.Models;

public class UserDuplicateGroup
{
    public string Hash { get; set; } = string.Empty;
    public long TotalSize { get; set; }
    public List<TimelineItem> Assets { get; set; } = new();

    public string TotalSizeFormatted => FormatFileSize(TotalSize);
    public long WastedBytes => Assets.Count > 1 ? Assets.Skip(1).Sum(a => a.FileSize) : 0;
    public string WastedFormatted => FormatFileSize(WastedBytes);

    private static string FormatFileSize(long bytes)
    {
        string[] sizes = { "B", "KB", "MB", "GB" };
        double len = bytes;
        int order = 0;
        while (len >= 1024 && order < sizes.Length - 1) { order++; len /= 1024; }
        return $"{len:0.##} {sizes[order]}";
    }
}
