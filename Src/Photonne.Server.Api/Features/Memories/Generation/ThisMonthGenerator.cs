using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// "Julio de 2019" — the same calendar month in a previous year. This is what
/// carries the section on the ~360 days a year when nothing happened to be shot
/// on today's exact date; on-this-day alone leaves the feed empty.
/// </summary>
internal sealed class ThisMonthGenerator : IMemoryGenerator
{
    /// <summary>Higher than on-this-day's: a whole month with only a handful of
    /// photos isn't a month worth retelling.</summary>
    private const int MinAssets = 8;

    public MemoryKind Kind => MemoryKind.ThisMonth;

    public async Task<IReadOnlyList<MemoryDraft>> GenerateAsync(MemoryContext ctx, CancellationToken ct)
    {
        var today = ctx.LocalToday;

        var years = await ctx.Scope
            .Where(a => a.CapturedAt.Month == today.Month
                     && a.CapturedAt.Year < today.Year)
            .GroupBy(a => a.CapturedAt.Year)
            .Where(g => g.Count() >= MinAssets)
            .Select(g => g.Key)
            .ToListAsync(ct);

        var drafts = new List<MemoryDraft>();
        foreach (var year in years)
        {
            ct.ThrowIfCancellationRequested();

            // Exclude today's date: OnThisDay already tells that story, and two
            // cards opening on the same photos reads as a bug.
            var query = ctx.Scope.Where(a => a.CapturedAt.Month == today.Month
                                          && a.CapturedAt.Year == year
                                          && a.CapturedAt.Day != today.Day);

            var candidates = await MemoryCandidates.LoadAsync(query, ctx.UserId, ctx.Db, ct);
            if (candidates.Count < MinAssets) continue;

            drafts.Add(candidates.ToDraft(
                Kind,
                dedupeKey: $"month:{year:D4}-{today.Month:D2}",
                title: MemoryTitles.MonthAndYear(year, today.Month),
                subtitle: MemoryTitles.PhotoCount(candidates.Count)));
        }

        return drafts;
    }
}
