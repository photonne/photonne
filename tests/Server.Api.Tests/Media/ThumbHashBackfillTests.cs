using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace Photonne.Server.Api.Tests.Media;

/// <summary>
/// Thumbnails made before ThumbHash get theirs from the file; one that can't
/// be read is marked empty so the sweep moves on instead of retrying it.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class ThumbHashBackfillTests : IntegrationTestBase
{
    public ThumbHashBackfillTests(PhotonneApiFactory factory) : base(factory) { }

    [Fact]
    public async Task Fills_the_hash_from_the_thumbnail_file_and_marks_unreadable_ones()
    {
        var owner = await CreateUserAsync();
        var file = Path.Combine(Path.GetTempPath(), $"{Guid.NewGuid():N}.png");
        using (var image = new Image<Rgba32>(40, 30, new Rgba32(200, 120, 40)))
            await image.SaveAsPngAsync(file);

        var (readable, missing) = await WithDbContextAsync(async db =>
        {
            Guid NewAsset()
            {
                var name = $"{Guid.NewGuid():N}.jpg";
                var asset = new Asset
                {
                    FileName = name,
                    FullPath = $"/assets/users/{owner.Username}/{name}",
                    FileSize = 3,
                    Checksum = Guid.NewGuid().ToString("N"),
                    Type = AssetType.Image,
                    Extension = "jpg",
                    FileCreatedAt = DateTime.UtcNow,
                    FileModifiedAt = DateTime.UtcNow,
                    CapturedAt = DateTime.UtcNow,
                    OwnerId = owner.Id,
                };
                db.Assets.Add(asset);
                return asset.Id;
            }

            var withFile = new AssetThumbnail { AssetId = NewAsset(), Size = ThumbnailSize.Small, FilePath = file, Width = 40, Height = 30 };
            var withoutFile = new AssetThumbnail { AssetId = NewAsset(), Size = ThumbnailSize.Small, FilePath = "/nope/missing.webp", Width = 40, Height = 30 };
            db.AssetThumbnails.AddRange(withFile, withoutFile);
            await db.SaveChangesAsync();
            return (withFile.Id, withoutFile.Id);
        });

        using (var scope = Factory.Services.CreateScope())
        {
            var backfill = scope.ServiceProvider.GetRequiredService<ThumbHashBackfill>();
            while (await backfill.RunBatchAsync(500, CancellationToken.None) == 500) { }
        }

        var hashes = await WithDbContextAsync(db => db.AssetThumbnails
            .Where(t => t.Id == readable || t.Id == missing)
            .ToDictionaryAsync(t => t.Id, t => t.ThumbHash));
        Assert.False(string.IsNullOrEmpty(hashes[readable]));
        Assert.Equal(string.Empty, hashes[missing]);
        File.Delete(file);
    }
}
