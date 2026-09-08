namespace Photonne.Client.Web.Models;

/// <summary>Grupo de duplicados exactos del usuario (mismo checksum).</summary>
public class UserDuplicateGroup
{
    public string Hash { get; set; } = string.Empty;
    public long TotalSize { get; set; }
    public List<TimelineItem> Assets { get; set; } = new();
}
