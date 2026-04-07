using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class Folder
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    [Required]
    [MaxLength(1000)]
    public string Path { get; set; } = string.Empty;
    
    [Required]
    [MaxLength(500)]
    public string Name { get; set; } = string.Empty;
    
    public Guid? ParentFolderId { get; set; }
    public Folder? ParentFolder { get; set; }
    public ICollection<Folder> SubFolders { get; set; } = new List<Folder>();
    
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    
    public Guid? ExternalLibraryId { get; set; }
    public ExternalLibrary? ExternalLibrary { get; set; }

    // Navigation properties
    public ICollection<FolderPermission> Permissions { get; set; } = new List<FolderPermission>();
    public ICollection<Asset> Assets { get; set; } = new List<Asset>();
}

