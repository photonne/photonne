using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Memories;

public class MemoriesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/memories", Handle)
            .WithName("GetMemories")
            .WithTags("Assets")
            .WithDescription("Returns assets from the same day in previous years")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AllowedFolderCache allowedFolders,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        [FromQuery] bool? test,
        CancellationToken ct)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (!Guid.TryParse(userIdClaim?.Value, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        // "On this day" is a LOCAL-calendar concept: capture dates are stored as
        // the photo's local wall-clock, so compare against the user's local
        // today (from the configured timezone), not UtcNow — otherwise photos
        // near midnight, and the whole first offset-hours of the local day, fall
        // onto the wrong day.
        var tz = await MetadataTimeZone.ResolveAsync(settingsService, ct);
        var today = MetadataTimeZone.LocalNow(tz);
        var allowedIds = await allowedFolders.GetAllowedFolderIdsAsync(
            dbContext, userId, $"/assets/users/{username}", ct);

        // Visibility, projection and tag hydration are the timeline's — a memory
        // is a timeline item that happens to be N years old, so it must obey the
        // same rules. Inlining them here is what let archived assets and the .mov
        // half of Live Photos leak into memories while the timeline hid both.
        var visible = TimelineQuery.VisibleAssets(dbContext, allowedIds);

        if (test == true)
        {
            var testItems = await visible
                .OrderBy(_ => EF.Functions.Random())
                .Take(15)
                .Select(TimelineProjection.ToResponse)
                .ToListAsync(ct);

            await TimelineQuery.HydrateTagsAsync(dbContext, testItems, ct);
            return Results.Ok(testItems);
        }

        var items = await visible
            .Where(a =>
                a.CapturedAt.Month == today.Month &&
                a.CapturedAt.Day == today.Day &&
                a.CapturedAt.Year < today.Year)
            .OrderByDescending(a => a.CapturedAt)
            .Take(100)
            .Select(TimelineProjection.ToResponse)
            .ToListAsync(ct);

        await TimelineQuery.HydrateTagsAsync(dbContext, items, ct);
        return Results.Ok(items);
    }
}
