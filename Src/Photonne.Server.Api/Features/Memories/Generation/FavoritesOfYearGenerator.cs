using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// "Tus favoritas de 2023". IsFavorite is the only signal in the whole model
/// where the user told us, deliberately, that a photo matters — there are no
/// ratings, views or shares — so a year of them is the highest-confidence
/// memory we can assemble without a single classifier being involved.
/// </summary>
internal sealed class FavoritesOfYearGenerator : IMemoryGenerator
{
    private const int MinAssets = 10;

    /// <summary>How far back to look. Beyond this the feed fills with a wall of
    /// year cards that all look alike.</summary>
    private const int MaxYearsBack = 10;

    public MemoryKind Kind => MemoryKind.FavoritesOfYear;

    public async Task<IReadOnlyList<MemoryDraft>> GenerateAsync(MemoryContext ctx, CancellationToken ct)
    {
        var today = ctx.LocalToday;
        var earliestYear = today.Year - MaxYearsBack;

        var favorites = ctx.Scope.Where(AssetConditions.IsFavorite(true));

        var years = await favorites
            .Where(a => a.CapturedAt.Year < today.Year && a.CapturedAt.Year >= earliestYear)
            .GroupBy(a => a.CapturedAt.Year)
            .Where(g => g.Count() >= MinAssets)
            .Select(g => g.Key)
            .ToListAsync(ct);

        var drafts = new List<MemoryDraft>();
        foreach (var year in years)
        {
            ct.ThrowIfCancellationRequested();

            // CapturedBetweenLocal, not CapturedBetween: the bounds are wall-clock
            // like the column, and the UTC-converting sibling would slide the year
            // boundary by the host's offset (see AssetConditions).
            var query = favorites.Where(AssetConditions.CapturedBetweenLocal(
                new DateTime(year, 1, 1),
                new DateTime(year, 12, 31)));

            var candidates = await MemoryCandidates.LoadAsync(query, ctx.UserId, ctx.Db, ct);
            if (candidates.Count < MinAssets) continue;

            drafts.Add(candidates.ToDraft(
                Kind,
                dedupeKey: $"favyear:{year:D4}",
                title: $"Tus favoritas de {year}",
                subtitle: MemoryTitles.PhotoCount(candidates.Count)));
        }

        return drafts;
    }
}
