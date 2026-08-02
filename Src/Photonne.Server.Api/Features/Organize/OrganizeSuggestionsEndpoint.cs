using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Organize;

public class OrganizeSuggestionResponse
{
    /// <summary>trip | person | scene | month — drives the icon, not the behaviour.</summary>
    public string Kind { get; set; } = string.Empty;

    /// <summary>Stable within a response; the client uses it as a list key.</summary>
    public string Key { get; set; } = string.Empty;

    public string Title { get; set; } = string.Empty;

    /// <summary>Capture span, already formatted as "yyyy-MM" bounds for the client.</summary>
    public string? From { get; set; }
    public string? To { get; set; }

    public int Count { get; set; }
    public Guid? CoverAssetId { get; set; }

    /// <summary>
    /// The batch itself. Shipped in full so the client can review, edit and move
    /// it with the endpoints that already exist, instead of re-resolving a rule
    /// server-side and risking a different set than the one reviewed.
    /// </summary>
    public List<Guid> AssetIds { get; set; } = new();
}

/// <summary>
/// Proposes ready-made batches out of the "Para organizar" inbox.
///
/// The inbox used to be a flat chronological wall: the app already knew how to
/// group these photos — the condition builder queries exactly these signals —
/// yet made the user invent the groups by hand, selecting them out of the
/// scroll. This turns that around: the server does the clustering it is already
/// equipped for, and the screen becomes a short list of decisions instead of an
/// infinite list of photos.
///
/// Generators run in order of how specific their signal is, and each one only
/// sees what the previous ones left behind, so a photo is proposed once:
///
///   1. <b>Trip</b> — a place with enough photos inside a narrow window. The
///      window is what separates a trip from home: everyone's home city has
///      hundreds of photos spread over years, and offering "Barcelona · 4 años"
///      as a batch would be noise. Past <see cref="TripMaxSpanDays"/> a place is
///      treated as home and falls through to the month buckets.
///   2. <b>Person</b> — someone who appears often in what's left.
///   3. <b>Scene</b> — the top scene label, when the classifier was confident.
///   4. <b>Month</b> — the remainder, so nothing is invisible.
/// </summary>
public class OrganizeSuggestionsEndpoint : IEndpoint
{
    /// <summary>Past this, a place is where you live, not somewhere you went.</summary>
    private const int TripMaxSpanDays = 45;

    /// <summary>Below this a batch isn't worth a row of its own.</summary>
    private const int MinBatchSize = 8;

    /// <summary>Scene labels below this are guesses, not groupings.</summary>
    private const float MinSceneConfidence = 0.55f;

    /// <summary>Keeps the response bounded; the rest stays in the flat grid.</summary>
    private const int MaxSuggestions = 12;
    private const int MaxAssetsPerSuggestion = 2000;

    /// <summary>
    /// How far back the clustering looks. Grouping is done in memory, so an
    /// unbounded inbox would mean an unbounded working set; the newest slice is
    /// also where the useful batches are, and anything older is still reachable
    /// through the flat grid.
    /// </summary>
    private const int MaxScanned = 20000;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/organize/suggestions", Handle)
            .WithName("GetOrganizeSuggestions")
            .WithTags("Assets")
            .WithDescription("Proposes ready-made batches from the unorganized inbox")
            .RequireAuthorization();
    }

    private sealed record Pending(Guid Id, DateTime CapturedAt, Guid? PlaceId, string? PlaceName);

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out _))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        var pendingQuery = OrganizeQuery.Pending(dbContext, username);
        // Subquery, not a materialized id list: passing tens of thousands of
        // Guids back as parameters would blow past what the driver will send.
        var pendingIds = pendingQuery.Select(a => a.Id);

        var pending = await pendingQuery
            .OrderByDescending(a => a.CapturedAt)
            .Take(MaxScanned)
            .Select(a => new Pending(
                a.Id,
                a.CapturedAt,
                a.Exif != null ? a.Exif.PlaceId : null,
                a.Exif != null && a.Exif.Place != null ? a.Exif.Place.Name : null))
            .ToListAsync(cancellationToken);

        if (pending.Count == 0) return Results.Ok(new List<OrganizeSuggestionResponse>());

        var byId = pending.ToDictionary(p => p.Id);
        var claimed = new HashSet<Guid>();
        var suggestions = new List<OrganizeSuggestionResponse>();

        // ---- 1. Trips ----
        foreach (var place in pending
                     .Where(p => p.PlaceId != null)
                     .GroupBy(p => p.PlaceId!.Value)
                     .Select(g => new
                     {
                         Name = g.First().PlaceName,
                         Items = g.OrderByDescending(p => p.CapturedAt).ToList(),
                     })
                     .Where(g => g.Items.Count >= MinBatchSize)
                     .Where(g => (g.Items.First().CapturedAt - g.Items.Last().CapturedAt).TotalDays
                                 <= TripMaxSpanDays)
                     // Tightest window first: the more concentrated the dates,
                     // the more the batch reads as one outing.
                     .OrderBy(g => (g.Items.First().CapturedAt - g.Items.Last().CapturedAt).TotalDays)
                     .ThenByDescending(g => g.Items.Count))
        {
            if (suggestions.Count >= MaxSuggestions) break;
            if (string.IsNullOrWhiteSpace(place.Name)) continue;
            Add(suggestions, claimed, "trip", $"trip:{place.Name}", place.Name!, place.Items);
        }

        // ---- 2. People ----
        var faceOwners = await dbContext.Faces
            .AsNoTracking()
            .Where(f => f.PersonId != null && f.Person!.IsHidden == false)
            .Where(f => pendingIds.Contains(f.AssetId))
            .Select(f => new { f.AssetId, PersonId = f.PersonId!.Value, Name = f.Person!.Name })
            .ToListAsync(cancellationToken);

        foreach (var person in faceOwners
                     .GroupBy(f => f.PersonId)
                     .Select(g => new
                     {
                         Name = g.First().Name,
                         // Distinct: several faces of the same person in one photo
                         // must not inflate the batch.
                         Ids = g.Select(f => f.AssetId).Distinct().ToList(),
                     })
                     .OrderByDescending(g => g.Ids.Count))
        {
            if (suggestions.Count >= MaxSuggestions) break;
            if (string.IsNullOrWhiteSpace(person.Name)) continue;
            var items = person.Ids.Where(id => !claimed.Contains(id) && byId.ContainsKey(id))
                .Select(id => byId[id]).OrderByDescending(p => p.CapturedAt).ToList();
            if (items.Count < MinBatchSize) continue;
            Add(suggestions, claimed, "person", $"person:{person.Name}", person.Name!, items);
        }

        // ---- 3. Scenes ----
        var scenes = await dbContext.Set<Shared.Models.AssetClassifiedScene>()
            .AsNoTracking()
            .Where(s => s.Rank == 1 && s.Confidence >= MinSceneConfidence)
            .Where(s => pendingIds.Contains(s.AssetId))
            .Select(s => new { s.AssetId, s.Label })
            .ToListAsync(cancellationToken);

        foreach (var scene in scenes
                     .GroupBy(s => s.Label)
                     .OrderByDescending(g => g.Count()))
        {
            if (suggestions.Count >= MaxSuggestions) break;
            var items = scene.Select(s => s.AssetId).Distinct()
                .Where(id => !claimed.Contains(id) && byId.ContainsKey(id))
                .Select(id => byId[id]).OrderByDescending(p => p.CapturedAt).ToList();
            if (items.Count < MinBatchSize) continue;
            Add(suggestions, claimed, "scene", $"scene:{scene.Key}", scene.Key, items);
        }

        // ---- 4. Whatever is left, by month ----
        foreach (var month in pending
                     .Where(p => !claimed.Contains(p.Id))
                     .GroupBy(p => new { p.CapturedAt.Year, p.CapturedAt.Month })
                     .OrderByDescending(g => g.Key.Year).ThenByDescending(g => g.Key.Month))
        {
            if (suggestions.Count >= MaxSuggestions) break;
            var items = month.OrderByDescending(p => p.CapturedAt).ToList();
            if (items.Count < MinBatchSize) continue;
            var key = $"{month.Key.Year:D4}-{month.Key.Month:D2}";
            Add(suggestions, claimed, "month", $"month:{key}", key, items);
        }

        return Results.Ok(suggestions);
    }

    private static void Add(
        List<OrganizeSuggestionResponse> into,
        HashSet<Guid> claimed,
        string kind,
        string key,
        string title,
        List<Pending> items)
    {
        var batch = items.Take(MaxAssetsPerSuggestion).ToList();
        foreach (var item in batch) claimed.Add(item.Id);
        into.Add(new OrganizeSuggestionResponse
        {
            Kind = kind,
            Key = key,
            Title = title,
            // Items arrive newest-first, so the span runs last → first.
            From = batch.Last().CapturedAt.ToString("yyyy-MM"),
            To = batch.First().CapturedAt.ToString("yyyy-MM"),
            Count = batch.Count,
            CoverAssetId = batch.First().Id,
            AssetIds = batch.Select(b => b.Id).ToList(),
        });
    }
}
