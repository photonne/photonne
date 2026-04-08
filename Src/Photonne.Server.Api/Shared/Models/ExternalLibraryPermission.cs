namespace Photonne.Server.Api.Shared.Models;

public class ExternalLibraryPermission
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid ExternalLibraryId { get; set; }
    public ExternalLibrary ExternalLibrary { get; set; } = null!;

    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    public bool CanRead { get; set; }

    public DateTime GrantedAt { get; set; } = DateTime.UtcNow;

    public Guid? GrantedByUserId { get; set; }
    public User? GrantedByUser { get; set; }
}
