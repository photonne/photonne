using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Timeline;

/// <summary>
/// Exercises the bucket-model endpoints (docs/timeline-buckets.md):
/// GET /api/assets/timeline/buckets (per-month counts, the skeleton) and
/// GET /api/assets/timeline/buckets/{yyyy-MM} (one month's content).
/// The contract that everything else hangs on: a bucket's count ALWAYS
/// equals its content size — clients reserve scroll height from counts.
/// </summary>
public sealed class TimelineBucketsTests : IntegrationTestBase
{
    public TimelineBucketsTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record Bucket(string Key, int Count);
    private sealed record TimelineItem(Guid Id, DateTime FileCreatedAt);
    private sealed record TimelinePage(List<TimelineItem> Items, bool HasMore);

    private async Task<Guid> CreateFolderAsync(string path)
    {
        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });
    }

    private async Task<Guid> CreateAssetAsync(
        TestUser owner,
        string fileName,
        Guid folderId,
        DateTime capturedAt,
        bool isArchived = false,
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
                IsArchived = isArchived
            };
            db.Assets.Add(asset);
            if (isMotionPhotoPart)
            {
                db.AssetTags.Add(new AssetTag { Asset = asset, TagType = AssetTagType.MotionPhotoPart });
            }
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    /// <summary>
    /// Three months of history with month-boundary edge cases:
    ///   2026-02 → 3 visible (one at the first UTC instant of the month)
    ///   2026-01 → 1 visible (at the last UTC second of the month)
    ///   2025-12 → 1 visible
    /// plus one archived and one motion-part asset in 2026-02 that must not
    /// count anywhere.
    /// </summary>
    private async Task<HttpClient> SeedLibraryAsync()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");

        await CreateAssetAsync(alice, "feb-first-instant.jpg", folderId,
            new DateTime(2026, 2, 1, 0, 0, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "feb-mid.jpg", folderId,
            new DateTime(2026, 2, 14, 12, 0, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "feb-late.jpg", folderId,
            new DateTime(2026, 2, 27, 18, 30, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "jan-last-second.jpg", folderId,
            new DateTime(2026, 1, 31, 23, 59, 59, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "dec.jpg", folderId,
            new DateTime(2025, 12, 25, 9, 0, 0, DateTimeKind.Utc));

        var hiddenDate = new DateTime(2026, 2, 10, 10, 0, 0, DateTimeKind.Utc);
        await CreateAssetAsync(alice, "archived.jpg", folderId, hiddenDate, isArchived: true);
        await CreateAssetAsync(alice, "live.mov", folderId, hiddenDate, isMotionPhotoPart: true);

        return client;
    }

    [Fact]
    public async Task Buckets_ReturnPerMonthCounts_NewestFirst()
    {
        var client = await SeedLibraryAsync();

        var buckets = await client.GetFromJsonAsync<List<Bucket>>("/api/assets/timeline/buckets");

        Assert.Equal(
            new[] { new Bucket("2026-02", 3), new Bucket("2026-01", 1), new Bucket("2025-12", 1) },
            buckets);
    }

    [Fact]
    public async Task BucketItems_AgreeWithCounts_AndCoverTheFullTimeline()
    {
        var client = await SeedLibraryAsync();

        var buckets = await client.GetFromJsonAsync<List<Bucket>>("/api/assets/timeline/buckets");
        var timeline = await client.GetFromJsonAsync<TimelinePage>("/api/assets/timeline?pageSize=500");

        var allBucketItems = new List<TimelineItem>();
        foreach (var bucket in buckets!)
        {
            var items = await client.GetFromJsonAsync<List<TimelineItem>>(
                $"/api/assets/timeline/buckets/{bucket.Key}");

            // THE invariant: reserved skeleton height == real content size.
            Assert.Equal(bucket.Count, items!.Count);
            // Within a bucket, newest first — same order as the timeline.
            Assert.Equal(items.OrderByDescending(i => i.FileCreatedAt).Select(i => i.Id),
                items.Select(i => i.Id));
            allBucketItems.AddRange(items);
        }

        // Concatenating buckets newest-first reproduces the cursor timeline
        // exactly: same ids, same order, nothing lost or duplicated.
        Assert.Equal(timeline!.Items.Select(i => i.Id), allBucketItems.Select(i => i.Id));
    }

    [Fact]
    public async Task BucketItems_RespectMonthBoundaries()
    {
        var client = await SeedLibraryAsync();

        var january = await client.GetFromJsonAsync<List<TimelineItem>>(
            "/api/assets/timeline/buckets/2026-01");
        var february = await client.GetFromJsonAsync<List<TimelineItem>>(
            "/api/assets/timeline/buckets/2026-02");

        // 2026-01-31T23:59:59Z belongs to January; 2026-02-01T00:00:00Z to February.
        Assert.Single(january!);
        Assert.Equal(new DateTime(2026, 1, 31, 23, 59, 59, DateTimeKind.Utc), january[0].FileCreatedAt);
        Assert.Contains(february!, i => i.FileCreatedAt == new DateTime(2026, 2, 1, 0, 0, 0, DateTimeKind.Utc));
    }

    [Fact]
    public async Task BucketItems_EmptyMonth_ReturnsEmptyList()
    {
        var client = await SeedLibraryAsync();

        var items = await client.GetFromJsonAsync<List<TimelineItem>>(
            "/api/assets/timeline/buckets/2020-01");

        Assert.Empty(items!);
    }

    [Theory]
    [InlineData("2026-13")]
    [InlineData("2026")]
    [InlineData("garbage")]
    public async Task BucketItems_InvalidKey_ReturnsBadRequest(string key)
    {
        var client = await SeedLibraryAsync();

        var response = await client.GetAsync($"/api/assets/timeline/buckets/{key}");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }
}
