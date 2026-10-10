using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// Re-indexing is what puts right an asset whose stored type no longer matches
/// its extension: the RAW files the upload endpoint filed as videos, the .mpg
/// the indexer filed as photos. There is no migration for it, on purpose — the
/// scan is the one flow that fixes it, today and after any later change to the
/// classification.
/// </summary>
public sealed class AssetIndexingTypeCorrectionTests : IntegrationTestBase
{
    public AssetIndexingTypeCorrectionTests(PhotonneApiFactory factory) : base(factory) { }

    private string WriteFile(string fileName)
    {
        var seed = Guid.NewGuid().ToByteArray();
        var content = Enumerable.Range(0, 4096).Select(i => (byte)(seed[i % 16] ^ (i % 251))).ToArray();
        var directory = Path.Combine(
            Factory.InternalAssetsPath, "users", PhotonneApiFactory.AdminUsername, "t" + Guid.NewGuid().ToString("N")[..8]);
        Directory.CreateDirectory(directory);
        var physicalPath = Path.Combine(directory, fileName);
        File.WriteAllBytes(physicalPath, content);
        return physicalPath;
    }

    private async Task<Asset> IndexAsync(string physicalPath)
    {
        using var scope = Factory.Services.CreateScope();
        var indexer = scope.ServiceProvider.GetRequiredService<AssetIndexingService>();
        var asset = await indexer.IndexFileAsync(physicalPath, Guid.Empty, CancellationToken.None);
        Assert.NotNull(asset);
        return asset!;
    }

    /// <summary>Leaves the row the way an older rule would have stored it.</summary>
    private Task StoreAsAsync(Guid assetId, AssetType type, bool withThumbnail) =>
        WithDbContextAsync(async db =>
        {
            await db.Assets.Where(a => a.Id == assetId)
                .ExecuteUpdateAsync(s => s.SetProperty(a => a.Type, type));
            if (withThumbnail)
            {
                db.AssetThumbnails.Add(new AssetThumbnail
                {
                    AssetId = assetId,
                    Size = ThumbnailSize.Small,
                    FilePath = "thumbs/fake.jpg",
                    Width = 220,
                    Height = 220,
                    FileSize = 10
                });
                await db.SaveChangesAsync();
            }
        });

    private Task<AssetType> StoredTypeAsync(Guid assetId) =>
        WithDbContextAsync(db =>
            db.Assets.AsNoTracking().Where(a => a.Id == assetId).Select(a => a.Type).FirstAsync());

    [Theory]
    // With thumbnails the indexer takes its "already indexed" shortcut;
    // without them it goes the long way. Both have to correct the type.
    [InlineData(true)]
    [InlineData(false)]
    public async Task RawStoredAsVideo_BecomesAnImage(bool withThumbnail)
    {
        var path = WriteFile("IMG_0042.dng");
        var asset = await IndexAsync(path);
        await StoreAsAsync(asset.Id, AssetType.Video, withThumbnail);

        await IndexAsync(path);

        Assert.Equal(AssetType.Image, await StoredTypeAsync(asset.Id));
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public async Task VideoStoredAsImage_BecomesAVideo(bool withThumbnail)
    {
        var path = WriteFile("clip.mpg");
        var asset = await IndexAsync(path);
        await StoreAsAsync(asset.Id, AssetType.Image, withThumbnail);

        await IndexAsync(path);

        Assert.Equal(AssetType.Video, await StoredTypeAsync(asset.Id));
    }

    [Fact]
    public async Task NewFiles_GetTheirTypeFromTheExtension()
    {
        var raw = await IndexAsync(WriteFile("IMG_0043.DNG"));
        var video = await IndexAsync(WriteFile("clip.vob"));
        var photo = await IndexAsync(WriteFile("photo.jpg"));

        Assert.Equal(AssetType.Image, await StoredTypeAsync(raw.Id));
        Assert.Equal(AssetType.Video, await StoredTypeAsync(video.Id));
        Assert.Equal(AssetType.Image, await StoredTypeAsync(photo.Id));
    }
}
