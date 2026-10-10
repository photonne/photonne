using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class Person
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid OwnerId { get; set; }
    public User Owner { get; set; } = null!;

    [MaxLength(200)]
    public string? Name { get; set; }

    public Guid? CoverFaceId { get; set; }
    public Face? CoverFace { get; set; }

    public int FaceCount { get; set; }

    public bool IsHidden { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<Face> Faces { get; set; } = new List<Face>();
}
