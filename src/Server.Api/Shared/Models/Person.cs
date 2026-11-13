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

    // Qué es esta persona para el dueño. La categoría es un código de
    // PersonRelationship.Categories (se guarda como texto para poder añadir
    // categorías sin romper a las apps antiguas); la etiqueta es el parentesco
    // concreto en texto libre ("Madre", "Amigo del instituto").
    [MaxLength(PersonRelationship.MaxCategoryLength)]
    public string? RelationshipCategory { get; set; }

    [MaxLength(PersonRelationship.MaxLabelLength)]
    public string? RelationshipLabel { get; set; }

    // De la estela de quién nace esta persona en la constelación (la sobrina,
    // de la hermana). Null: nace por su cuenta.
    public Guid? BranchFromPersonId { get; set; }
    public Person? BranchFrom { get; set; }

    // La persona que es el propio dueño: en la constelación no es una estrella
    // más, sino la línea principal. Como mucho una por dueño.
    public bool IsSelf { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<Face> Faces { get; set; } = new List<Face>();
}
