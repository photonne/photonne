using System.Globalization;
using System.Linq.Expressions;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// A recurring subject worth a row of its own. <see cref="GroupTitle"/> heads the
/// row and carries no year; <see cref="TitleFor"/> titles one card inside it,
/// because themes are scoped per year — see <see cref="ThemedMemoryGenerator"/>.
/// </summary>
internal sealed record MemoryTheme(
    string Key,
    string GroupTitle,
    Func<int, string> TitleFor,
    Expression<Func<Asset, bool>> Match);

/// <summary>
/// Base for memories built from a fixed catalogue of subjects the ML pipeline can
/// recognise — beaches, snow, pets. One memory per (theme, year).
///
/// Per year, and not all-time, on purpose: "Días de playa" holding fifteen years
/// of beaches isn't a memory, it's a smart album — and the app already has those.
/// A year gives the card a window, a story and a place in the ranking.
/// </summary>
internal abstract class ThemedMemoryGenerator : IMemoryGenerator
{
    public abstract MemoryKind Kind { get; }

    protected abstract IReadOnlyList<MemoryTheme> Themes { get; }

    /// <summary>Below this the theme didn't really happen that year.</summary>
    protected virtual int MinAssets => 12;

    /// <summary>Older than this and the feed turns into a wall of near-identical
    /// year cards.</summary>
    protected virtual int MaxYearsBack => 10;

    /// <summary>Include the running year: unlike an anniversary, "días de playa"
    /// this summer is a perfectly good memory in November.</summary>
    protected virtual bool IncludeCurrentYear => true;

    public async Task<IReadOnlyList<MemoryDraft>> GenerateAsync(MemoryContext ctx, CancellationToken ct)
    {
        var today = ctx.LocalToday;
        var earliestYear = today.Year - MaxYearsBack;
        var latestYear = IncludeCurrentYear ? today.Year : today.Year - 1;

        var drafts = new List<MemoryDraft>();

        foreach (var theme in Themes)
        {
            ct.ThrowIfCancellationRequested();

            var matching = ctx.Scope
                .Where(theme.Match)
                .Where(a => a.CapturedAt.Year >= earliestYear && a.CapturedAt.Year <= latestYear);

            // One grouped round-trip to find the qualifying years, rather than a
            // probe per (theme, year) — with a dozen themes over a decade that
            // would be 120 queries a user, every night.
            var years = await matching
                .GroupBy(a => a.CapturedAt.Year)
                .Where(g => g.Count() >= MinAssets)
                .Select(g => g.Key)
                .ToListAsync(ct);

            foreach (var year in years)
            {
                ct.ThrowIfCancellationRequested();

                var candidates = await MemoryCandidates.LoadAsync(
                    matching.Where(a => a.CapturedAt.Year == year), ctx.UserId, ctx.Db, ct);

                if (candidates.Count < MinAssets) continue;

                // The dedupe key is the row's key plus the year, derived rather
                // than spelled out again: the card's row and the card's identity
                // cannot drift apart. A key that shifts by one character deletes
                // every themed memory and resurrects every dismissed one.
                var themeKey = $"{DedupePrefix}:{theme.Key}";

                drafts.Add(candidates.ToDraft(
                    Kind,
                    dedupeKey: $"{themeKey}:{year:D4}",
                    themeKey: themeKey,
                    groupTitle: theme.GroupTitle,
                    title: theme.TitleFor(year),
                    subtitle: MemoryTitles.PhotoCount(candidates.Count),
                    // The row already says "Días de playa"; the card only has to
                    // say which year it is.
                    cardLabel: year.ToString(CultureInfo.InvariantCulture)));
            }
        }

        return drafts;
    }

    /// <summary>Namespaces the dedupe key so two themed generators can never
    /// collide on a shared theme key.</summary>
    protected abstract string DedupePrefix { get; }
}
