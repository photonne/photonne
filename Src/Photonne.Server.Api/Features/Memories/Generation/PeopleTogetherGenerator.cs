using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// "Martina y Joan" — two named people who keep turning up in the same frame.
///
/// Co-occurrence is a relationship signal nothing else in the model captures:
/// the people you photograph together are the people who matter together.
/// </summary>
internal sealed class PeopleTogetherGenerator : IMemoryGenerator
{
    private const int MinFaceCount = 20;

    /// <summary>Two people in fifteen photos together is a relationship. In three,
    /// it's a party they both attended.</summary>
    private const int MinTogether = 15;

    /// <summary>
    /// Pair counting is quadratic, so the candidate set is capped rather than the
    /// pairs: the most-photographed people are the ones whose pairings mean
    /// anything, and 12 of them is 66 possible pairs — a bound that holds however
    /// many faces the library grows.
    /// </summary>
    private const int MaxPeopleConsidered = 12;

    /// <summary>Even with good data, the tail of pairs is noise. Keep the strongest.</summary>
    private const int MaxPairs = 5;

    public MemoryKind Kind => MemoryKind.PeopleTogether;

    public async Task<IReadOnlyList<MemoryDraft>> GenerateAsync(MemoryContext ctx, CancellationToken ct)
    {
        var people = await ctx.Db.People
            .AsNoTracking()
            .Where(p => p.OwnerId == ctx.UserId
                     && p.Name != null
                     && !p.IsHidden
                     && p.FaceCount >= MinFaceCount)
            .OrderByDescending(p => p.FaceCount)
            .Take(MaxPeopleConsidered)
            .Select(p => new { p.Id, Name = p.Name! })
            .ToListAsync(ct);

        if (people.Count < 2) return [];

        var names = people.ToDictionary(p => p.Id, p => p.Name);
        var personIds = people.Select(p => p.Id).ToList();
        var scopeIds = ctx.Scope.Select(a => a.Id);

        // Which of these people appear on which visible asset — one round-trip.
        // Counting the pairs in SQL would need a self-join EF can't express
        // cleanly; this set is bounded by (12 people x their faces), so folding it
        // in memory is both simpler and cheap.
        var rows = await ctx.Db.UserFaceAssignments
            .AsNoTracking()
            .Where(uf => uf.UserId == ctx.UserId
                      && uf.PersonId != null
                      && !uf.IsRejected
                      && personIds.Contains(uf.PersonId!.Value)
                      && scopeIds.Contains(uf.Face.AssetId))
            .Select(uf => new { uf.Face.AssetId, PersonId = uf.PersonId!.Value })
            .Distinct()
            .ToListAsync(ct);

        var counts = new Dictionary<(Guid A, Guid B), int>();
        foreach (var group in rows.GroupBy(r => r.AssetId))
        {
            var present = group.Select(r => r.PersonId).Distinct().OrderBy(id => id).ToList();
            for (var i = 0; i < present.Count; i++)
                for (var j = i + 1; j < present.Count; j++)
                {
                    // Ordered by construction, so (A,B) and (B,A) can't both exist.
                    var key = (present[i], present[j]);
                    counts[key] = counts.GetValueOrDefault(key) + 1;
                }
        }

        var pairs = counts
            .Where(kv => kv.Value >= MinTogether)
            .OrderByDescending(kv => kv.Value)
            .Take(MaxPairs)
            .Select(kv => kv.Key)
            .ToList();

        var drafts = new List<MemoryDraft>();

        foreach (var (a, b) in pairs)
        {
            ct.ThrowIfCancellationRequested();

            var together = ctx.Scope
                .Where(AssetConditions.HasPerson(ctx.Db, ctx.UserId, a))
                .Where(AssetConditions.HasPerson(ctx.Db, ctx.UserId, b));

            var candidates = await MemoryCandidates.LoadAsync(together, ctx.UserId, ctx.Db, ct);
            if (candidates.Count < MinTogether) continue;

            drafts.Add(candidates.ToDraft(
                Kind,
                // The ids are already ordered, so the key is stable no matter
                // which way round the pair came out of the fold.
                dedupeKey: $"together:{a}:{b}",
                // Same row as PersonThroughYears — see the note there.
                themeKey: "people",
                groupTitle: "Personas",
                title: $"{names[a]} y {names[b]}",
                subtitle: MemoryTitles.PhotoCount(candidates.Count),
                // "Martina y Joan" is already the short label.
                cardLabel: null));
        }

        return drafts;
    }
}
