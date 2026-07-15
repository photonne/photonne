using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// "Hace N años" — the same calendar day in a previous year, one memory per
/// year. This is the kind the timeline strip shows.
/// </summary>
internal sealed class OnThisDayGenerator : IMemoryGenerator
{
    /// <summary>Below this a "memory" is a stray photo, not a day worth retelling.</summary>
    private const int MinAssets = 3;

    public MemoryKind Kind => MemoryKind.OnThisDay;

    public async Task<IReadOnlyList<MemoryDraft>> GenerateAsync(MemoryContext ctx, CancellationToken ct)
    {
        var today = ctx.LocalToday;

        var years = await ctx.Scope
            .Where(a => a.CapturedAt.Month == today.Month
                     && a.CapturedAt.Day == today.Day
                     && a.CapturedAt.Year < today.Year)
            .GroupBy(a => a.CapturedAt.Year)
            .Where(g => g.Count() >= MinAssets)
            .Select(g => g.Key)
            .ToListAsync(ct);

        var drafts = new List<MemoryDraft>();
        foreach (var year in years)
        {
            ct.ThrowIfCancellationRequested();

            var candidates = await MemoryCandidates.LoadAsync(
                ctx.Scope.Where(a => a.CapturedAt.Month == today.Month
                                  && a.CapturedAt.Day == today.Day
                                  && a.CapturedAt.Year == year),
                ctx.UserId, ctx.Db, ct);

            if (candidates.Count < MinAssets) continue;

            var yearsAgo = today.Year - year;
            drafts.Add(candidates.ToDraft(
                Kind,
                // The date, not the years-ago count: "hace 3 años" becomes "hace
                // 4 años" next January and would silently orphan the row.
                dedupeKey: $"onthisday:{year:D4}-{today.Month:D2}-{today.Day:D2}",
                themeKey: "onthisday",
                groupTitle: "Hoy",
                title: yearsAgo == 1 ? "Hace 1 año" : $"Hace {yearsAgo} años",
                subtitle: MemoryTitles.DayAndYear(new DateTime(year, today.Month, today.Day)),
                // No card label: "Hace 4 años" is already the short one.
                cardLabel: null));
        }

        return drafts;
    }
}
