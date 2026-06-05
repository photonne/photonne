using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Timeline;

/// <summary>
/// Pins every timeline-family endpoint (/timeline, /recent, /timeline/grid,
/// /timeline/index, /timeline-neighbors) to the SAME visibility predicate
/// (TimelineQuery.VisibleAssets). Before the predicate was centralized the
/// inlined copies had diverged — grid/index/neighbors surfaced the motion
/// (.mov) half of Live Photos that /timeline hides. The bucket model
/// (docs/timeline-buckets.md) requires exact count/content agreement, so any
/// new divergence must fail here.
/// </summary>
public sealed class TimelineVisibilityTests : IntegrationTestBase
{
    public TimelineVisibilityTests(PhotonneApiFactory factory) : base(factory) { }

    // ── Response shapes (kept private — tests should break when the wire
    // format changes, not silently re-bind) ─────────────────────────────────
    private sealed record TimelineItem(Guid Id, DateTime FileCreatedAt);
    private sealed record TimelinePage(List<TimelineItem> Items, bool HasMore);
    private sealed record GridItem(Guid Id);
    private sealed record GridSection(string YearMonth, List<GridItem> Items);
    private sealed record IndexEntry(DateTime Date, int Count);
    private sealed record NeighborItem(Guid Id);
    private sealed record NeighborsPage(List<NeighborItem> Items, int CurrentIndex);

    private async Task<Guid> CreateFolderAsync(string path)
    {
        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder
            {
                Path = path,
                Name = path.Split('/').Last()
            };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });
    }

    private async Task<Guid> CreateAssetAsync(
        TestUser owner,
        string fileName,
        Guid? folderId,
        DateTime capturedAt,
        bool isArchived = false,
        bool isFileMissing = false,
        bool isTrashed = false,
        bool isMotionPhotoPart = false)
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"/assets/users/{owner.Username}/{fileName}",
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                FolderId = folderId,
                IsArchived = isArchived,
                IsFileMissing = isFileMissing,
                DeletedAt = isTrashed ? DateTime.UtcNow : null
            };
            db.Assets.Add(asset);
            if (isMotionPhotoPart)
            {
                db.AssetTags.Add(new AssetTag
                {
                    Asset = asset,
                    TagType = AssetTagType.MotionPhotoPart
                });
            }
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    /// <summary>
    /// Seeds one user with two visible assets plus one asset per exclusion
    /// rule (archived, file-missing, trashed, motion-part, folderless,
    /// other-user). Returns the authenticated client and the two visible ids.
    /// </summary>
    private async Task<(HttpClient Client, Guid Newer, Guid Older)> SeedLibraryAsync()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");

        var older = await CreateAssetAsync(alice, "older.jpg", folderId,
            new DateTime(2026, 1, 15, 10, 0, 0, DateTimeKind.Utc));
        var newer = await CreateAssetAsync(alice, "newer.jpg", folderId,
            new DateTime(2026, 2, 20, 10, 0, 0, DateTimeKind.Utc));

        var hiddenDate = new DateTime(2026, 2, 1, 10, 0, 0, DateTimeKind.Utc);
        await CreateAssetAsync(alice, "archived.jpg", folderId, hiddenDate, isArchived: true);
        await CreateAssetAsync(alice, "missing.jpg", folderId, hiddenDate, isFileMissing: true);
        await CreateAssetAsync(alice, "trashed.jpg", folderId, hiddenDate, isTrashed: true);
        await CreateAssetAsync(alice, "live.mov", folderId, hiddenDate, isMotionPhotoPart: true);
        await CreateAssetAsync(alice, "folderless.jpg", folderId: null, hiddenDate);

        var bob = await CreateUserAsync();
        var bobFolderId = await CreateFolderAsync($"/assets/users/{bob.Username}");
        await CreateAssetAsync(bob, "bobs.jpg", bobFolderId, hiddenDate);

        return (client, newer, older);
    }

    [Fact]
    public async Task Timeline_ReturnsOnlyVisibleAssets()
    {
        var (client, newer, older) = await SeedLibraryAsync();

        var page = await client.GetFromJsonAsync<TimelinePage>("/api/assets/timeline?pageSize=500");

        Assert.Equal(new[] { newer, older }, page!.Items.Select(i => i.Id));
        Assert.False(page.HasMore);
    }

    [Fact]
    public async Task Recent_ReturnsOnlyVisibleAssets()
    {
        var (client, newer, older) = await SeedLibraryAsync();

        var items = await client.GetFromJsonAsync<List<TimelineItem>>("/api/assets/recent?limit=100");

        Assert.Equal(new[] { newer, older }, items!.Select(i => i.Id));
    }

    [Fact]
    public async Task Grid_ReturnsOnlyVisibleAssets()
    {
        var (client, newer, older) = await SeedLibraryAsync();

        var sections = await client.GetFromJsonAsync<List<GridSection>>("/api/assets/timeline/grid");

        Assert.NotNull(sections);
        var ids = sections.SelectMany(s => s.Items).Select(i => i.Id).ToList();
        Assert.Equal(new[] { newer, older }, ids);
        Assert.Equal(new[] { "2026-02", "2026-01" }, sections.Select(s => s.YearMonth));
    }

    [Fact]
    public async Task Index_CountsOnlyVisibleAssets()
    {
        var (client, _, _) = await SeedLibraryAsync();

        var index = await client.GetFromJsonAsync<List<IndexEntry>>("/api/assets/timeline/index");

        // One visible asset per day; every hidden asset shares 2026-02-01,
        // which must not appear at all.
        Assert.Equal(2, index!.Count);
        Assert.All(index, e => Assert.Equal(1, e.Count));
        Assert.DoesNotContain(index, e => e.Date.Day == 1);
    }

    [Fact]
    public async Task Neighbors_NavigateOnlyAcrossVisibleAssets()
    {
        var (client, newer, older) = await SeedLibraryAsync();

        var page = await client.GetFromJsonAsync<NeighborsPage>(
            $"/api/assets/{newer}/timeline-neighbors?before=50&after=50");

        // The five hidden assets sit between the two visible ones in capture
        // order — none of them may appear in the pager window.
        Assert.Equal(new[] { newer, older }, page!.Items.Select(i => i.Id));
        Assert.Equal(0, page.CurrentIndex);
    }
}
