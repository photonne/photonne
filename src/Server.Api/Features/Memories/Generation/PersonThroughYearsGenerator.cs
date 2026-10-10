using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// "Martina a lo largo de los años" — one named person, seen across time.
///
/// The strongest memory we can build, because it rests on something the user
/// did on purpose: they picked a cluster of faces and typed a name. Nothing else
/// in the model carries that much intent. Which is also why it only ever exists
/// for the user who did the naming — identity is private
/// (<see cref="UserFaceAssignment"/>), so this generator's output is genuinely
/// per-user rather than per-user-by-convention.
/// </summary>
internal sealed class PersonThroughYearsGenerator : IMemoryGenerator
{
    /// <summary>A person with a handful of faces is usually a half-formed cluster
    /// or a passer-by, not someone you'd want a card about.</summary>
    private const int MinFaceCount = 20;

    private const int MinAssets = 15;

    /// <summary>The point is the passage of time. Someone who only appears in one
    /// summer belongs in a trip memory, not in "a lo largo de los años".</summary>
    private const int MinDistinctYears = 3;

    /// <summary>Photos per year in the summary: enough to see the year, few
    /// enough that ten years fit in one sitting.</summary>
    private const int PerYear = 8;

    /// <summary>How many of a year's newest photos are looked at to pick its
    /// PerYear. Bounds the per-year query however much was shot that year.</summary>
    private const int PoolPerYear = 120;

    public MemoryKind Kind => MemoryKind.PersonThroughYears;

    public async Task<IReadOnlyList<MemoryDraft>> GenerateAsync(MemoryContext ctx, CancellationToken ct)
    {
        var people = await ctx.Db.People
            .AsNoTracking()
            .Where(p => p.OwnerId == ctx.UserId
                     && p.Name != null
                     && !p.IsHidden
                     && p.FaceCount >= MinFaceCount)
            .Select(p => new { p.Id, p.Name })
            .ToListAsync(ct);

        var drafts = new List<MemoryDraft>();

        foreach (var person in people)
        {
            ct.ThrowIfCancellationRequested();

            var theirs = ctx.Scope.Where(AssetConditions.HasPerson(ctx.Db, ctx.UserId, person.Id));

            var years = await theirs
                .Select(a => a.CapturedAt.Year)
                .Distinct()
                .ToListAsync(ct);

            if (years.Count < MinDistinctYears) continue;
            if (await theirs.CountAsync(ct) < MinAssets) continue;

            // A summary across the years, not their newest 200: that was the same
            // grid their own page shows, and with a busy last year the older ones
            // never made it in. A few photos from every year instead, oldest first.
            var candidates = new List<MemoryCandidate>();
            foreach (var year in years.OrderBy(y => y))
            {
                var start = new DateTime(year, 1, 1);
                var end = start.AddYears(1);
                var pool = await MemoryCandidates.LoadAsync(
                    theirs.Where(a => a.CapturedAt >= start && a.CapturedAt < end),
                    ctx.UserId, ctx.Db, ct, take: PoolPerYear);
                candidates.AddRange(SampleYear(pool, PerYear));
            }

            drafts.Add(candidates.ToDraft(
                Kind,
                // Keyed on the person, not the year span: the span grows every
                // time they appear again, and a key that moves would orphan the
                // row and resurrect a dismissed card.
                dedupeKey: PeopleMemoryKeys.Years(person.Id),
                // One row for everyone, shared with PeopleTogether: "Martina" and
                // "Martina y Joan" are the same thought, and a row per kind would
                // split them for a reason only the enum cares about.
                themeKey: "people",
                groupTitle: "Personas",
                title: $"{person.Name} a lo largo de los años",
                subtitle: $"{years.Min()} – {years.Max()}",
                cardLabel: person.Name,
                chronological: true));
        }

        return drafts;
    }

    /// <summary>
    /// Up to [count] photos of one year: favourites first (the user's own pick),
    /// then the rest spread evenly over the year so one busy weekend can't fill it.
    /// </summary>
    internal static List<MemoryCandidate> SampleYear(IReadOnlyList<MemoryCandidate> pool, int count)
    {
        if (pool.Count <= count) return pool.ToList();
        var picked = pool.Where(c => c.IsFavorite)
            .OrderByDescending(c => c.HasNamedFace)
            .ThenBy(c => c.CapturedAt)
            .Take(count)
            .ToList();
        var rest = pool.Where(c => !picked.Contains(c)).OrderBy(c => c.CapturedAt).ToList();
        var missing = count - picked.Count;
        if (missing > 0 && rest.Count > 0)
        {
            // Evenly spaced indices across the year's remaining photos.
            var step = rest.Count / (double)missing;
            for (var i = 0; i < missing && i < rest.Count; i++)
                picked.Add(rest[(int)(i * step)]);
        }
        return picked;
    }
}
