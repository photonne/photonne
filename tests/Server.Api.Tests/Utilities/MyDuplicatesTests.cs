using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Utilities;

/// <summary>
/// GET /api/utilities/duplicates: groups of 2+ visible assets sharing a
/// checksum, biggest group first. Same scope as the Utilities hub (the
/// caller's readable folders, no trash, no archive), and each copy carries the
/// EXIF size, thumbnail flag and tags the duplicates screen shows.
/// </summary>
public sealed class MyDuplicatesTests : IntegrationTestBase
{
    public MyDuplicatesTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record AssetBody(
        Guid Id,
        long FileSize,
        bool HasExif,
        bool HasThumbnails,
        int? Width,
        int? Height,
        List<string> Tags);

    private sealed record GroupBody(string Hash, long TotalSize, List<AssetBody> Assets);

    private Task<Guid> CreateFolderAsync(string path) =>
        WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private Task<Guid> CreateAssetAsync(
        TestUser owner,
        Guid folderId,
        string folderPath,
        long size,
        string? checksum = null,
        bool isArchived = false,
        bool isDeleted = false) =>
        WithDbContextAsync(async db =>
        {
            var fileName = Guid.NewGuid().ToString("N")[..8] + ".jpg";
            var now = DateTime.UtcNow;
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"{folderPath}/{fileName}",
                FileSize = size,
                Checksum = checksum ?? Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = now,
                FileModifiedAt = now,
                CapturedAt = now,
                OwnerId = owner.Id,
                FolderId = folderId,
                IsArchived = isArchived,
                DeletedAt = isDeleted ? now : null
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });

    [Fact]
    public async Task Duplicates_GroupsOnlyTheCallersVisibleAssets_BiggestFirst()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();
        var alicePath = $"/assets/users/{alice.Username}/Fotos";
        var bobPath = $"/assets/users/{bob.Username}/Fotos";
        var aliceFolder = await CreateFolderAsync(alicePath);
        var bobFolder = await CreateFolderAsync(bobPath);

        var groupA = new string('a', 64);
        var groupB = new string('b', 64);
        var single = new string('c', 64);

        // Group A: 100 + 300 = 400.
        await CreateAssetAsync(alice, aliceFolder, alicePath, 100, groupA);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 300, groupA);
        // Group B: 3 × 50 = 150. The archived and trashed copies don't count.
        await CreateAssetAsync(alice, aliceFolder, alicePath, 50, groupB);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 50, groupB);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 50, groupB);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 999, groupB, isArchived: true);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 999, groupB, isDeleted: true);
        // One visible copy plus a trashed one isn't a duplicate.
        await CreateAssetAsync(alice, aliceFolder, alicePath, 10, single);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 10, single, isDeleted: true);
        // Bob's copies share alice's checksums but aren't hers to see.
        await CreateAssetAsync(bob, bobFolder, bobPath, 50_000, groupA);
        await CreateAssetAsync(bob, bobFolder, bobPath, 60_000, single);

        var groups = await aliceClient.GetFromJsonAsync<List<GroupBody>>("/api/utilities/duplicates");

        Assert.NotNull(groups);
        Assert.Equal(new[] { groupA, groupB }, groups!.Select(g => g.Hash));
        Assert.Equal(new long[] { 400, 150 }, groups.Select(g => g.TotalSize));
        Assert.Equal(new[] { 2, 3 }, groups.Select(g => g.Assets.Count));
    }

    [Fact]
    public async Task Duplicates_CarryExifThumbnailsAndTags()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var path = $"/assets/users/{alice.Username}/Fotos";
        var folder = await CreateFolderAsync(path);
        var checksum = new string('d', 64);
        var rich = await CreateAssetAsync(alice, folder, path, 200, checksum);
        var bare = await CreateAssetAsync(alice, folder, path, 100, checksum);

        await WithDbContextAsync(async db =>
        {
            db.AssetExifs.Add(new AssetExif { AssetId = rich, Width = 4000, Height = 3000 });
            db.AssetThumbnails.Add(new AssetThumbnail
            {
                AssetId = rich,
                Size = ThumbnailSize.Small,
                FilePath = "/thumbs/rich.jpg"
            });
            db.AssetTags.Add(new AssetTag { AssetId = rich, TagType = AssetTagType.Panorama });
            var tag = new UserTag { OwnerId = alice.Id, Name = "Cumpleaños", NormalizedName = "cumpleaños" };
            db.UserTags.Add(tag);
            db.AssetUserTags.Add(new AssetUserTag { AssetId = rich, UserTag = tag });
            await db.SaveChangesAsync();
        });

        var groups = await client.GetFromJsonAsync<List<GroupBody>>("/api/utilities/duplicates");

        var assets = Assert.Single(groups!).Assets;
        var richBody = Assert.Single(assets, a => a.Id == rich);
        Assert.True(richBody.HasExif);
        Assert.True(richBody.HasThumbnails);
        Assert.Equal(4000, richBody.Width);
        Assert.Equal(3000, richBody.Height);
        Assert.Equal(new[] { "Cumpleaños", "Panorama" }, richBody.Tags);

        var bareBody = Assert.Single(assets, a => a.Id == bare);
        Assert.False(bareBody.HasExif);
        Assert.False(bareBody.HasThumbnails);
        Assert.Null(bareBody.Width);
        Assert.Empty(bareBody.Tags);
    }

    [Fact]
    public async Task Duplicates_IsEmpty_ForAnEmptyLibrary()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var groups = await client.GetFromJsonAsync<List<GroupBody>>("/api/utilities/duplicates");

        Assert.Empty(groups!);
    }
}
