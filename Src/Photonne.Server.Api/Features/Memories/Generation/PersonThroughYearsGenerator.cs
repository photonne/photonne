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

            var candidates = await MemoryCandidates.LoadAsync(theirs, ctx.UserId, ctx.Db, ct);
            if (candidates.Count < MinAssets) continue;

            drafts.Add(candidates.ToDraft(
                Kind,
                // Keyed on the person, not the year span: the span grows every
                // time they appear again, and a key that moves would orphan the
                // row and resurrect a dismissed card.
                dedupeKey: $"person:{person.Id}:years",
                title: $"{person.Name} a lo largo de los años",
                subtitle: $"{years.Min()} – {years.Max()}"));
        }

        return drafts;
    }
}
