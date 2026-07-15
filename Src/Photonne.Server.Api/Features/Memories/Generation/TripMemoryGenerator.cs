using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Turns the <see cref="Trip"/> rows found by TripDetectionService into memories.
/// The only generator that reads a fact instead of deriving one: trip boundaries
/// and names are detected separately and nightly, so that adding photos to an old
/// holiday refines the trip in place rather than making a second one appear.
/// </summary>
internal sealed class TripMemoryGenerator : IMemoryGenerator
{
    /// <summary>A trip whose photos are all gone (deleted, archived) is not a
    /// memory worth surfacing, whatever the Trip row still says.</summary>
    private const int MinAssets = 10;

    public MemoryKind Kind => MemoryKind.Trip;

    public async Task<IReadOnlyList<MemoryDraft>> GenerateAsync(MemoryContext ctx, CancellationToken ct)
    {
        var trips = await ctx.Db.Trips
            .AsNoTracking()
            .Where(t => t.OwnerId == ctx.UserId)
            .OrderByDescending(t => t.WindowStart)
            .ToListAsync(ct);

        var drafts = new List<MemoryDraft>();
        foreach (var trip in trips)
        {
            ct.ThrowIfCancellationRequested();

            // Everything shot inside the window, not just the geolocated photos the
            // trip was detected from. GPS coverage is a minority of most libraries,
            // and the camera shots from the same week are unmistakably part of the
            // same trip — the window is the evidence, the fix was only the clue.
            var query = ctx.Scope.Where(AssetConditions.CapturedBetweenLocal(
                trip.WindowStart, trip.WindowEnd));

            var candidates = await MemoryCandidates.LoadAsync(query, ctx.UserId, ctx.Db, ct);
            if (candidates.Count < MinAssets) continue;

            drafts.Add(candidates.ToDraft(
                Kind,
                dedupeKey: $"trip:{trip.Id}",
                themeKey: "trips",
                groupTitle: "Viajes",
                title: trip.Title,
                subtitle: MemoryTitles.MonthAndYear(trip.WindowStart.Year, trip.WindowStart.Month),
                // The trip's name is the label — "Roma" needs no shortening.
                cardLabel: null));
        }

        return drafts;
    }
}
