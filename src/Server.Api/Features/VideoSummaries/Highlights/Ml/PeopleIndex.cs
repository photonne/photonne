using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;

namespace Photonne.Server.Api.Features.VideoSummaries.Highlights;

/// <summary>
/// The people a user has named, as one reference embedding each (the mean of
/// their faces), to recognise them in a video. Per user: naming is private.
/// </summary>
public sealed class PeopleIndex
{
    /// <summary>ArcFace similarity to a person's mean face above which a face
    /// in a video is taken as them. Between the clustering threshold (0.42
    /// distance) and the suggestion one (0.55): video frames are blurrier than
    /// photos, so a little looser than clustering.</summary>
    public const double Match = 0.5;

    private const int FacesPerPerson = 40;

    public static readonly PeopleIndex Empty = new(new Dictionary<Guid, float[]>());

    private readonly IReadOnlyDictionary<Guid, float[]> _people;

    public PeopleIndex(IReadOnlyDictionary<Guid, float[]> people) => _people = people;

    public int Count => _people.Count;

    public static async Task<PeopleIndex> LoadAsync(ApplicationDbContext db, Guid userId, CancellationToken ct)
    {
        var rows = await db.UserFaceAssignments
            .AsNoTracking()
            .Where(a => a.UserId == userId && a.PersonId != null && !a.IsRejected
                     && a.Person!.Name != null && a.Person.Name != "" && !a.Person.IsHidden)
            .OrderByDescending(a => a.IsManuallyAssigned)
            .Select(a => new { PersonId = a.PersonId!.Value, a.Face.Embedding })
            .ToListAsync(ct);

        var people = rows
            .GroupBy(r => r.PersonId)
            .ToDictionary(
                g => g.Key,
                g => Vectors.Mean(g.Take(FacesPerPerson).Select(r => Vectors.Normalize(r.Embedding.ToArray())).ToList()));
        return new PeopleIndex(people);
    }

    /// <summary>Whether this face embedding is one of the user's named people.</summary>
    public bool IsNamedPerson(float[] embedding) =>
        _people.Values.Any(person => Vectors.Similarity(person, embedding) >= Match);
}
