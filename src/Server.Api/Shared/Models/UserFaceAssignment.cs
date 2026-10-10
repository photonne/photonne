namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// Per-user opinion about a detected <see cref="Face"/>.
///
/// Detection (<see cref="Face"/>) is a property of the asset and is shared across
/// every user who can see the asset (own asset, shared album, shared folder,
/// shared external library). Identity, on the other hand, is private: each user
/// builds and names their own <see cref="Person"/> clusters over the faces they
/// can see. This row is "user U thinks face F belongs to person P (or maybe P,
/// or no, that's not a face)".
///
/// The legacy identity fields on <see cref="Face"/> (<c>PersonId</c>,
/// <c>IsManuallyAssigned</c>, <c>IsRejected</c>, <c>SuggestedPersonId</c>,
/// <c>SuggestedDistance</c>) are kept on disk for safety during the rollout but
/// are no longer read or written by the code; this entity is the canonical
/// store. A future migration will drop those columns once everyone has rolled
/// past this version.
/// </summary>
public class UserFaceAssignment
{
    public Guid Id { get; set; } = Guid.NewGuid();

    public Guid FaceId { get; set; }
    public Face Face { get; set; } = null!;

    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    /// <summary>Confirmed cluster. Null while the face is still orphan for this user.</summary>
    public Guid? PersonId { get; set; }
    public Person? Person { get; set; }

    /// <summary>True when the user explicitly assigned the face: clustering must never overwrite it.</summary>
    public bool IsManuallyAssigned { get; set; }

    /// <summary>Soft-rejected false positive. Excluded from clustering and counted as rejected for this user.</summary>
    public bool IsRejected { get; set; }

    /// <summary>Proactive suggestion: closest <see cref="Person"/> within
    /// [ClusteringThreshold, SuggestionThreshold). Always null when the face is
    /// assigned, manual, or rejected. Cleared on any user action that resolves
    /// the face. Non-binding hint surfaced by the FaceOverlay / suggestions UI.</summary>
    public Guid? SuggestedPersonId { get; set; }
    public Person? SuggestedPerson { get; set; }
    public float? SuggestedDistance { get; set; }

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
