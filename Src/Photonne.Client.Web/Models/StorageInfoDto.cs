namespace Photonne.Client.Web.Models;

public class StorageInfoDto
{
    public long UsedBytes { get; set; }
    public long? QuotaBytes { get; set; }

    public double UsedPercent => QuotaBytes.HasValue && QuotaBytes > 0
        ? Math.Min(100.0, (double)UsedBytes / QuotaBytes.Value * 100.0)
        : 0;

    public string UsedFormatted => FormatBytes(UsedBytes);
    public string QuotaFormatted => QuotaBytes.HasValue ? FormatBytes(QuotaBytes.Value) : "";

    private static string FormatBytes(long bytes)
    {
        string[] sizes = ["B", "KB", "MB", "GB", "TB"];
        double len = bytes;
        int order = 0;
        while (len >= 1024 && order < sizes.Length - 1) { order++; len /= 1024; }
        return $"{len:0.#} {sizes[order]}";
    }
}
