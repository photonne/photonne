using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Features.Memories.Generation;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Memories;

/// <summary>
/// The generator's contract against a real database: what becomes a memory,
/// what a re-run does to one that already exists, and who gets to see it.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class MemoryGenerationTests : IntegrationTestBase
{
    public MemoryGenerationTests(PhotonneApiFactory factory) : base(factory) { }

    /// <summary>
    /// "Today" exactly as the generator computes it — via the configured metadata
    /// timezone, not the test host's clock. Deriving it any other way makes these
    /// tests fail on a machine in the wrong zone for reasons that have nothing to
    /// do with the code under test.
    /// </summary>
    private async Task<DateTime> LocalTodayAsync()
    {
        using var scope = Factory.Services.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
        var tz = await MetadataTimeZone.ResolveAsync(settings);
        return MetadataTimeZone.LocalNow(tz).Date;
    }

    private async Task<MemoryGenerationResult> GenerateAsync(Guid userId)
    {
        using var scope = Factory.Services.CreateScope();
        var generation = scope.ServiceProvider.GetRequiredService<MemoryGenerationService>();
        return await generation.RunForUserAsync(userId, CancellationToken.None);
    }

    private async Task<Guid> CreateFolderAsync(TestUser owner) =>
        await WithDbContextAsync(async db =>
        {
            var path = $"/assets/users/{owner.Username}";
            var folder = new Folder { Path = path, Name = owner.Username };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    /// <summary>A folder under /assets/shared — the only kind that can be opted
    /// out of the user's own surfaces.</summary>
    private async Task<Guid> CreateSharedFolderAsync(string name) =>
        await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = $"/assets/shared/{name}", Name = name };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    /// <summary>Marks [folderId] as one the user only administers, the same way
    /// the folders endpoint does — including dropping the derived cache, which is
    /// the half that makes the toggle take effect now rather than in 30s.</summary>
    private async Task ExcludeFromDiscoveryAsync(TestUser user, Guid folderId)
    {
        await WithDbContextAsync(async db =>
        {
            db.Settings.Add(new Setting
            {
                OwnerId = user.Id,
                Key = AllowedFolderCache.ExcludedFoldersSettingKey,
                Value = JsonSerializer.Serialize(new[] { folderId }),
            });
            await db.SaveChangesAsync();
        });

        using var scope = Factory.Services.CreateScope();
        scope.ServiceProvider.GetRequiredService<AllowedFolderCache>().Invalidate(user.Id);
    }

    private async Task CreateAssetsAsync(
        TestUser owner,
        Guid folderId,
        DateTime capturedAt,
        int count,
        bool isFavorite = false,
        bool isArchived = false,
        bool isMotionPhotoPart = false)
    {
        await WithDbContextAsync(async db =>
        {
            for (var i = 0; i < count; i++)
            {
                var name = $"{Guid.NewGuid():N}.jpg";
                var asset = new Asset
                {
                    FileName = name,
                    FullPath = $"/assets/users/{owner.Username}/{name}",
                    FileSize = 3,
                    Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                    Type = AssetType.Image,
                    Extension = "jpg",
                    FileCreatedAt = capturedAt,
                    FileModifiedAt = capturedAt,
                    CapturedAt = capturedAt.AddMinutes(i),
                    OwnerId = owner.Id,
                    FolderId = folderId,
                    IsFavorite = isFavorite,
                    IsArchived = isArchived,
                };
                db.Assets.Add(asset);
                if (isMotionPhotoPart)
                    db.AssetTags.Add(new AssetTag { Asset = asset, TagType = AssetTagType.MotionPhotoPart });
            }
            await db.SaveChangesAsync();
        });
    }

    [Fact]
    public async Task OnThisDay_CreatesOneMemoryPerYear_AboveTheThreshold()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 4);
        await CreateAssetsAsync(user, folder, today.AddYears(-3), count: 5);
        // Two photos is a stray, not a story — must not become a memory.
        await CreateAssetsAsync(user, folder, today.AddYears(-5), count: 2);

        await GenerateAsync(user.Id);

        var memories = await WithDbContextAsync(async db => await db.Memories
            .Where(m => m.OwnerId == user.Id && m.Kind == MemoryKind.OnThisDay)
            .OrderBy(m => m.WindowStart)
            .ToListAsync());

        Assert.Equal(2, memories.Count);
        Assert.Equal(today.AddYears(-3).Year, memories[0].WindowStart.Year);
        Assert.Equal(today.AddYears(-1).Year, memories[1].WindowStart.Year);
        Assert.Equal("Hace 1 año", memories[1].Title);
        Assert.Equal("Hace 3 años", memories[0].Title);
    }

    [Fact]
    public async Task OnThisDay_IncludesAPhotoTakenLateAtNight()
    {
        // 23:50 local is the classic off-by-one-day victim: read the column in the
        // wrong frame and this photo lands on tomorrow, disappearing from the
        // memory it belongs to.
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, folder, today.AddYears(-2).AddHours(23).AddMinutes(50), count: 3);

        await GenerateAsync(user.Id);

        var memory = await WithDbContextAsync(async db => await db.Memories
            .SingleOrDefaultAsync(m => m.OwnerId == user.Id && m.Kind == MemoryKind.OnThisDay));

        Assert.NotNull(memory);
        Assert.Equal(3, memory!.AssetCount);
        Assert.Equal(today.AddYears(-2).Day, memory.WindowStart.Day);
    }

    [Fact]
    public async Task Generation_SkipsArchivedAndMotionPhotoParts()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();
        var when = today.AddYears(-1);

        await CreateAssetsAsync(user, folder, when, count: 3);
        await CreateAssetsAsync(user, folder, when, count: 4, isArchived: true);
        await CreateAssetsAsync(user, folder, when, count: 4, isMotionPhotoPart: true);

        await GenerateAsync(user.Id);

        var memory = await WithDbContextAsync(async db => await db.Memories
            .SingleAsync(m => m.OwnerId == user.Id && m.Kind == MemoryKind.OnThisDay));

        // Only the three visible ones — the timeline hides the other eight, so a
        // memory over them would be showing photos the user can't find anywhere.
        Assert.Equal(3, memory.AssetCount);
    }

    [Fact]
    public async Task Rerunning_UpsertsInPlace_KeepingIdentity()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 3);

        var first = await GenerateAsync(user.Id);
        var before = await WithDbContextAsync(async db => await db.Memories
            .AsNoTracking()
            .SingleAsync(m => m.OwnerId == user.Id && m.Kind == MemoryKind.OnThisDay));

        // A new photo lands on that same day, then the nightly pass runs again.
        await CreateAssetsAsync(user, folder, today.AddYears(-1).AddHours(2), count: 2);
        var second = await GenerateAsync(user.Id);

        var after = await WithDbContextAsync(async db => await db.Memories
            .AsNoTracking()
            .Where(m => m.OwnerId == user.Id && m.Kind == MemoryKind.OnThisDay)
            .ToListAsync());

        Assert.Equal(1, first.Created);
        Assert.Equal(0, second.Created);
        Assert.Equal(1, second.Updated);

        // Still exactly one row — this is the whole point of the dedupe key.
        var row = Assert.Single(after);
        Assert.Equal(before.Id, row.Id);
        // FirstGeneratedAt must survive, or the client reads a refreshed memory
        // as brand new every single night.
        Assert.Equal(before.FirstGeneratedAt, row.FirstGeneratedAt);
        Assert.True(row.LastGeneratedAt >= before.LastGeneratedAt);
        Assert.Equal(5, row.AssetCount);
    }

    [Fact]
    public async Task Regeneration_RemovesAMemoryWhoseAssetsAreGone()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 3);
        await GenerateAsync(user.Id);

        // The user trashes the lot; the memory now points at nothing.
        await WithDbContextAsync(async db =>
        {
            await db.Assets
                .Where(a => a.OwnerId == user.Id)
                .ExecuteUpdateAsync(s => s.SetProperty(a => a.DeletedAt, DateTime.UtcNow));
        });

        var result = await GenerateAsync(user.Id);

        var remaining = await WithDbContextAsync(async db => await db.Memories
            .CountAsync(m => m.OwnerId == user.Id));

        Assert.Equal(1, result.Removed);
        Assert.Equal(0, remaining);
    }

    [Fact]
    public async Task Cover_PrefersAFavorite()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();
        var when = today.AddYears(-1);

        await CreateAssetsAsync(user, folder, when.AddHours(10), count: 4);
        // Earlier the same day, so recency alone would never pick it: only the
        // favourite flag can lift it to cover.
        await CreateAssetsAsync(user, folder, when.AddHours(8), count: 1, isFavorite: true);

        await GenerateAsync(user.Id);

        var memory = await WithDbContextAsync(async db => await db.Memories
            .SingleAsync(m => m.OwnerId == user.Id && m.Kind == MemoryKind.OnThisDay));

        var coverIsFavorite = await WithDbContextAsync(async db => await db.Assets
            .Where(a => a.Id == memory.CoverAssetId)
            .Select(a => a.IsFavorite)
            .SingleAsync());

        Assert.True(coverIsFavorite);
    }

    [Fact]
    public async Task Feed_NeverLeaksAnotherUsersMemories()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var today = await LocalTodayAsync();

        var aliceFolder = await CreateFolderAsync(alice);
        await CreateAssetsAsync(alice, aliceFolder, today.AddYears(-1), count: 3);

        await GenerateAsync(alice.Id);
        await GenerateAsync(bob.Id);

        var aliceFeed = await aliceClient.GetFromJsonAsync<List<FeedItem>>("/api/memories");
        var bobFeed = await bobClient.GetFromJsonAsync<List<FeedItem>>("/api/memories");

        Assert.Single(aliceFeed!);
        Assert.Empty(bobFeed!);

        // And the detail route must not become the back door the feed isn't.
        var stolen = await bobClient.GetAsync($"/api/memories/{aliceFeed![0].Id}");
        Assert.Equal(HttpStatusCode.NotFound, stolen.StatusCode);
    }

    [Fact]
    public async Task Detail_ReturnsAssetsWithTheCoverFirst()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 4);
        await GenerateAsync(user.Id);

        var feed = await client.GetFromJsonAsync<List<FeedItem>>("/api/memories");
        var detail = await client.GetFromJsonAsync<DetailItem>($"/api/memories/{feed![0].Id}");

        Assert.Equal(4, detail!.Assets.Count);
        // The client opens the viewer at index 0 and morphs from the cover
        // thumbnail; if these disagree the transition jumps to the wrong photo.
        Assert.Equal(detail.CoverAssetId, detail.Assets[0].Id);
    }

    [Fact]
    public async Task Feed_RejectsAnUnknownKind()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var response = await client.GetAsync("/api/memories?kind=Nonsense");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    /// <summary>
    /// A shared folder the user only administers must not come back as a memory —
    /// even though the photos in it are theirs.
    ///
    /// This is the whole bug: the generator gated on the permission union, whose
    /// first leg is OwnerId == user. Excluding the folder shrinks the *folder*
    /// leg, so the timeline (which gates on folders alone) hid it while memories
    /// went on serving it through the owner leg.
    /// </summary>
    [Fact]
    public async Task AFolderTheUserOnlyAdministers_ProducesNoMemories()
    {
        var user = await CreateUserAsync();
        var adminFolder = await CreateSharedFolderAsync($"admin-{Guid.NewGuid():N}");
        var today = await LocalTodayAsync();

        // The user's own uploads — OwnerId matches, which is what used to let
        // these through regardless of the folder.
        await CreateAssetsAsync(user, adminFolder, today.AddYears(-1), count: 5);
        await ExcludeFromDiscoveryAsync(user, adminFolder);

        await GenerateAsync(user.Id);

        var memories = await WithDbContextAsync(async db => await db.Memories
            .AsNoTracking()
            .Where(m => m.OwnerId == user.Id)
            .ToListAsync());

        Assert.Empty(memories);
    }

    /// <summary>The other half of the same rule: without the opt-out, that very
    /// folder does produce a memory. Otherwise the test above would pass just as
    /// well on a generator that produced nothing at all.</summary>
    [Fact]
    public async Task TheSameFolderProducesAMemoryWhenNotExcluded()
    {
        var user = await CreateUserAsync();
        var sharedFolder = await CreateSharedFolderAsync($"shared-{Guid.NewGuid():N}");
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, sharedFolder, today.AddYears(-1), count: 5);

        await GenerateAsync(user.Id);

        var memories = await WithDbContextAsync(async db => await db.Memories
            .AsNoTracking()
            .Where(m => m.OwnerId == user.Id)
            .ToListAsync());

        Assert.NotEmpty(memories);
    }

    /// <summary>
    /// Over HTTP, not against the DbContext: the grouping is only useful if it
    /// survives the projection, and a field forgotten there ships as "" rather
    /// than failing anything.
    /// </summary>
    [Fact]
    public async Task Feed_ExposesTheRowEachCardBelongsTo()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 3);
        await GenerateAsync(user.Id);

        var feed = await client.GetFromJsonAsync<List<FeedItem>>("/api/memories");

        var card = Assert.Single(feed!);
        Assert.Equal("onthisday", card.ThemeKey);
        Assert.Equal("Hoy", card.GroupTitle);
        // "Hace 1 año" is already short enough to label the card.
        Assert.Null(card.CardLabel);
    }

    /// <summary>
    /// Excluding a folder takes effect on read, not on the next nightly pass. The
    /// memory row is last night's snapshot; if the detail route trusted it, the
    /// user would tick the toggle and still be handed the photos until the pass
    /// ran — and a revoked permission would keep serving them just as long.
    /// </summary>
    [Fact]
    public async Task ExcludingAFolder_EmptiesAnAlreadyGeneratedMemory()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var adminFolder = await CreateSharedFolderAsync($"admin-{Guid.NewGuid():N}");
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, adminFolder, today.AddYears(-1), count: 5);
        await GenerateAsync(user.Id);

        var feed = await client.GetFromJsonAsync<List<FeedItem>>("/api/memories");
        var memoryId = Assert.Single(feed!).Id;

        // The toggle goes on after the memory already exists. No regeneration.
        await ExcludeFromDiscoveryAsync(user, adminFolder);

        var detail = await client.GetFromJsonAsync<DetailItem>($"/api/memories/{memoryId}");

        Assert.Empty(detail!.Assets);
    }

    /// <summary>
    /// The proof that no backfill script is needed: a row written before the
    /// grouping existed gets it from the next run, in place, without losing its
    /// identity. If this fails, deploying needs a migration script after all.
    /// </summary>
    [Fact]
    public async Task Rerunning_FillsTheGroupingOnARowThatPredatesIt()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 3);
        await GenerateAsync(user.Id);

        // Rewind the row to what a pre-migration one looks like: columns added
        // with DEFAULT '' onto rows nobody regenerated yet.
        var before = await WithDbContextAsync(async db =>
        {
            var row = await db.Memories.SingleAsync(m => m.OwnerId == user.Id);
            row.ThemeKey = string.Empty;
            row.GroupTitle = string.Empty;
            row.CardLabel = null;
            await db.SaveChangesAsync();
            return new { row.Id, row.FirstGeneratedAt };
        });

        await GenerateAsync(user.Id);

        var after = await WithDbContextAsync(async db => await db.Memories
            .AsNoTracking()
            .SingleAsync(m => m.OwnerId == user.Id));

        Assert.Equal("onthisday", after.ThemeKey);
        Assert.Equal("Hoy", after.GroupTitle);
        // Same row, refreshed — not a new one that would resurrect a dismissal.
        Assert.Equal(before.Id, after.Id);
        Assert.Equal(before.FirstGeneratedAt, after.FirstGeneratedAt);
    }

    private sealed record FeedItem(
        Guid Id,
        string Kind,
        string Title,
        string ThemeKey,
        string GroupTitle,
        string? CardLabel,
        Guid? CoverAssetId,
        int AssetCount);
    private sealed record DetailItem(Guid Id, Guid? CoverAssetId, List<DetailAsset> Assets);
    private sealed record DetailAsset(Guid Id);
}
