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

    /// <summary>Two people in eight photos together is already someone they share
    /// a life with; in three, it's a party they both attended. It was 15 while
    /// pairs competed for a slot in the feed — now each person's page shows their
    /// own, so a quieter pair still belongs there.</summary>
    private const int MinTogether = 8;

    /// <summary>
    /// Pair counting is quadratic, so the candidate set is capped rather than the
    /// pairs: the most-photographed people are the ones whose pairings mean
    /// anything, and 30 of them is 435 possible pairs — folded in memory over a
    /// set bounded by their faces, a bound that holds however the library grows.
    /// </summary>
    private const int MaxPeopleConsidered = 30;

    /// <summary>
    /// Strongest pairs kept per person ("Personas con más fotos juntas" on their
    /// page). It used to be five for the whole library, which left most people
    /// with none: one very photographed couple used up every slot.
    /// </summary>
    private const int MaxPairsPerPerson = 5;

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

        // Strongest first; a pair stays while neither of the two already has its
        // MaxPairsPerPerson, so everyone keeps their own closest people.
        var perPerson = new Dictionary<Guid, int>();
        var pairs = new List<(Guid A, Guid B)>();
        foreach (var (pair, _) in counts
                     .Where(kv => kv.Value >= MinTogether)
                     .OrderByDescending(kv => kv.Value)
                     .Select(kv => (kv.Key, kv.Value)))
        {
            if (perPerson.GetValueOrDefault(pair.A) >= MaxPairsPerPerson ||
                perPerson.GetValueOrDefault(pair.B) >= MaxPairsPerPerson) continue;
            pairs.Add(pair);
            perPerson[pair.A] = perPerson.GetValueOrDefault(pair.A) + 1;
            perPerson[pair.B] = perPerson.GetValueOrDefault(pair.B) + 1;
        }

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
                dedupeKey: PeopleMemoryKeys.Together(a, b),
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
