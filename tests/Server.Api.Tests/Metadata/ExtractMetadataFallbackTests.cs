using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Metadata;

/// <summary>
/// Reproduces the stale-snapshot bug: an asset indexed when the file's mtime
/// was wrong (first rsync without -t), later fixed on disk by a second rsync —
/// but the DB columns were never refreshed, so the CapturedAt-from-file
/// fallback kept using the stale date. The extraction pass must re-stat the
/// physical file instead of trusting the DB snapshot.
/// </summary>
public sealed class ExtractMetadataFallbackTests : IntegrationTestBase
{
    public ExtractMetadataFallbackTests(PhotonneApiFactory factory) : base(factory) { }

    [Fact]
    public async Task Extraction_RestatsFile_WhenDbDatesAreStale()
    {
        var admin = new TestUser(
            Guid.Empty,
            PhotonneApiFactory.AdminUsername,
            PhotonneApiFactory.AdminPassword,
            "Admin");
        var adminClient = await LoginAsClientAsync(admin);

        // Physical file: EXIF-less JPEG whose on-disk mtime holds the REAL
        // date (the rsync-preserved one).
        var realDate = new DateTime(2006, 7, 15, 15, 13, 17, DateTimeKind.Utc);
        var dir = Path.Combine(Factory.InternalAssetsPath, "users", "u1");
        Directory.CreateDirectory(dir);
        var physicalPath = Path.Combine(dir, "ian.jpg");
        File.Copy(FixturePaths.NoMetadata, physicalPath, overwrite: true);
        File.SetLastWriteTimeUtc(physicalPath, realDate);

        // DB row: stale snapshot — both date columns frozen at the bad first
        // copy, so EffectiveFileCreatedAt computed from the DB would be wrong.
        var staleDate = new DateTime(2026, 5, 26, 10, 0, 0, DateTimeKind.Utc);
        var assetId = await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = "ian.jpg",
                FullPath = "/assets/users/u1/ian.jpg",
                FileSize = new FileInfo(physicalPath).Length,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = staleDate,
                FileModifiedAt = staleDate,
                CapturedAt = staleDate,
                CapturedAtSource = CaptureDateSource.FileSystem
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });

        try
        {
            // Run the extraction task to completion (the stream ends when the
            // background worker finishes).
            await adminClient.GetStringAsync("/api/assets/metadata/stream?overwrite=false");

            var after = await WithDbContextAsync(async db =>
                await db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));

            // CapturedAt must come from the LIVE mtime, not the stale snapshot,
            // and the snapshot columns must be refreshed along the way.
            Assert.Equal(realDate, after.CapturedAt);
            Assert.Equal(CaptureDateSource.FileSystem, after.CapturedAtSource);
            Assert.Equal(realDate, after.FileModifiedAt);
        }
        finally
        {
            File.Delete(physicalPath);
        }
    }
}
