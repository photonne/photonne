using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Videos;

/// <summary>
/// /api/assets/videos lists the videos of the timeline: same visibility as
/// the timeline (TimelineQuery.VisibleAssets), photos and Live Photo clips
/// left out, newest first, paged by capture date.
/// </summary>
public sealed class VideosEndpointTests : IntegrationTestBase
{
    public VideosEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record VideoItem(Guid Id, string Type);
    private sealed record VideosPage(List<VideoItem> Items, bool HasMore, DateTime? NextCursor);

    private async Task<Guid> CreateFolderAsync(string path) =>
        await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private async Task<Guid> CreateAssetAsync(
        TestUser owner, string fileName, Guid folderId, DateTime capturedAt, AssetType type,
        bool isArchived = false, bool isMotionPhotoPart = false) =>
        await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"/assets/users/{owner.Username}/{fileName}",
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = type,
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
                db.AssetTags.Add(new AssetTag { Asset = asset, TagType = AssetTagType.MotionPhotoPart });
            await db.SaveChangesAsync();
            return asset.Id;
        });

    private static DateTime Day(int day) => new(2026, 3, day, 10, 0, 0, DateTimeKind.Utc);

    [Fact]
    public async Task ListsOnlyVisibleVideos_NewestFirst()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");

        var older = await CreateAssetAsync(alice, "older.mp4", folderId, Day(1), AssetType.Video);
        var newer = await CreateAssetAsync(alice, "newer.mp4", folderId, Day(5), AssetType.Video);
        await CreateAssetAsync(alice, "photo.jpg", folderId, Day(3), AssetType.Image);
        await CreateAssetAsync(alice, "live.mov", folderId, Day(3), AssetType.Video, isMotionPhotoPart: true);
        await CreateAssetAsync(alice, "archived.mp4", folderId, Day(3), AssetType.Video, isArchived: true);

        var bob = await CreateUserAsync();
        var bobFolderId = await CreateFolderAsync($"/assets/users/{bob.Username}");
        await CreateAssetAsync(bob, "bobs.mp4", bobFolderId, Day(3), AssetType.Video);

        var page = await client.GetFromJsonAsync<VideosPage>("/api/assets/videos");

        Assert.Equal(new[] { newer, older }, page!.Items.Select(i => i.Id));
        Assert.All(page.Items, i => Assert.Equal("Video", i.Type));
        Assert.False(page.HasMore);
        Assert.Null(page.NextCursor);
    }

    [Fact]
    public async Task PagesByCaptureDateCursor()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        var ids = new List<Guid>();
        for (var day = 5; day >= 1; day--)
            ids.Add(await CreateAssetAsync(alice, $"v{day}.mp4", folderId, Day(day), AssetType.Video));

        var first = await client.GetFromJsonAsync<VideosPage>("/api/assets/videos?pageSize=3");
        Assert.Equal(ids.Take(3), first!.Items.Select(i => i.Id));
        Assert.True(first.HasMore);

        var cursor = Uri.EscapeDataString(first.NextCursor!.Value.ToString("O"));
        var second = await client.GetFromJsonAsync<VideosPage>($"/api/assets/videos?pageSize=3&cursor={cursor}");
        Assert.Equal(ids.Skip(3), second!.Items.Select(i => i.Id));
        Assert.False(second.HasMore);
    }
}
