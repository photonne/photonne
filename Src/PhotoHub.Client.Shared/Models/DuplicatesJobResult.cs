namespace PhotoHub.Client.Shared.Models;

public class DuplicatesProgressUpdate
{
    public string Message { get; set; } = string.Empty;
    public double Percentage { get; set; }
    public DuplicatesJobStatistics? Statistics { get; set; }
    public bool IsCompleted { get; set; }
    /// <summary>Physical scan only: a duplicate group found during scanning.</summary>
    public PhysicalDuplicateGroup? FoundGroup { get; set; }
}

public class DuplicatesJobStatistics
{
    public int TotalAssets { get; set; }
    public int DuplicateGroups { get; set; }
    public int DuplicateAssets { get; set; }
    public int Removed { get; set; }
    public long BytesReclaimed { get; set; }
    /// <summary>Physical scan only: files found on disk not yet in the database.</summary>
    public int UnindexedFiles { get; set; }
}

public class PhysicalDuplicateGroup
{
    public string Hash { get; set; } = string.Empty;
    public List<PhysicalDuplicateFile> Files { get; set; } = new();
}

public class PhysicalDuplicateFile
{
    public string PhysicalPath { get; set; } = string.Empty;
    public string VirtualPath { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public string Directory { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public DateTime ModifiedDate { get; set; }
    public bool IsIndexed { get; set; }
    public Guid? AssetId { get; set; }
}

public class PhysicalFileDeleteRequest
{
    public string PhysicalPath { get; set; } = string.Empty;
    public Guid? AssetId { get; set; }
}

public class PhysicalDeleteResult
{
    public int Deleted { get; set; }
    public List<string> Errors { get; set; } = new();
}
