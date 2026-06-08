using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Folders;

/// <summary>
/// The folder-contents endpoint (GET /api/folders/{id}/assets) must hide the
/// motion (.mov) half of a Live Photo, matching TimelineQuery.VisibleAssets —
/// otherwise a reindexed library shows both the still and its clip as two
/// separate items in the folders view. It must also stitch detected tags onto
/// the page so a still opened from a folder still reads as a Live Photo
/// (the viewer's "Ver foto en movimiento" affordance keys off the LivePhoto tag).
/// </summary>
public sealed class FolderVisibilityTests : IntegrationTestBase
{
    public FolderVisibilityTests(PhotonneApiFactory factory) : base(factory) { }

    // Folder-asset wire shape (kept private so the test breaks on format drift).
    private sealed record FolderAsset(Guid Id, string FileName, List<string> Tags);

    private async Task<Guid> CreateFolderAsync(string path) =>
        await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private async Task<Guid> CreateAssetAsync(
        TestUser owner,
        string folderPath,
        Guid folderId,
        string fileName,
        params AssetTagType[] tags) =>
        await WithDbContextAsync(async db =>
        {
            var capturedAt = new DateTime(2026, 2, 1, 10, 0, 0, DateTimeKind.Utc);
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"{folderPath}/{fileName}",
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                FolderId = folderId
            };
            db.Assets.Add(asset);
            foreach (var tag in tags)
            {
                db.AssetTags.Add(new AssetTag { Asset = asset, TagType = tag });
            }
            await db.SaveChangesAsync();
            return asset.Id;
        });

    [Fact]
    public async Task FolderAssets_ExcludeMotionPhotoPart_AndHydrateLivePhotoTag()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        // A readable folder must sit *under* the user's personal root (the root
        // itself is a structural container and never grants access).
        var folderPath = $"/assets/users/{alice.Username}/Camera";
        var folderId = await CreateFolderAsync(folderPath);

        var still = await CreateAssetAsync(alice, folderPath, folderId, "IMG_1234.heic", AssetTagType.LivePhoto);
        var plain = await CreateAssetAsync(alice, folderPath, folderId, "plain.jpg");
        await CreateAssetAsync(alice, folderPath, folderId, "IMG_1234.mov", AssetTagType.MotionPhotoPart);

        var assets = await client.GetFromJsonAsync<List<FolderAsset>>($"/api/folders/{folderId}/assets");

        Assert.NotNull(assets);
        // The .mov half is hidden; the still and the plain photo remain.
        Assert.Equal(new[] { still, plain }.OrderBy(g => g), assets!.Select(a => a.Id).OrderBy(g => g));
        Assert.DoesNotContain(assets, a => a.FileName == "IMG_1234.mov");

        // The still's LivePhoto tag rides along so the viewer shows the affordance.
        var stillItem = Assert.Single(assets, a => a.Id == still);
        Assert.Contains("LivePhoto", stillItem.Tags);
    }
}
