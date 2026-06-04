using Microsoft.Extensions.DependencyInjection;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Metadata;

/// <summary>
/// Integration tests for the date-restore task's inference mode and the
/// CaptureDateSource provenance gates. All runs use fromFile=false and
/// writeToFile=false so no physical files are needed — the task only touches
/// the DB. The stream is consumed to completion with a plain GET (it ends
/// when the background task finishes).
/// </summary>
public sealed class RestoreDatesInferenceTests : IntegrationTestBase
{
    public RestoreDatesInferenceTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<HttpClient> AdminClientAsync()
    {
        var admin = new TestUser(
            Guid.Empty,
            PhotonneApiFactory.AdminUsername,
            PhotonneApiFactory.AdminPassword,
            "Admin");
        return await LoginAsClientAsync(admin);
    }

    private Task<Guid> AddAssetAsync(
        string fileName,
        string fullPath,
        DateTime capturedAt,
        CaptureDateSource source,
        DateTime? exifDate = null)
    {
        return WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = fullPath,
                FileSize = 100,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                CapturedAtSource = source
            };
            db.Assets.Add(asset);
            if (exifDate != null)
            {
                db.AssetExifs.Add(new AssetExif { AssetId = asset.Id, DateTimeOriginal = exifDate });
            }
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    private async Task<(DateTime CapturedAt, CaptureDateSource Source, DateTime? ExifDate)> LoadAsync(Guid assetId)
    {
        return await WithDbContextAsync(async db =>
        {
            var a = await db.Assets.Include(x => x.Exif).AsNoTracking().FirstAsync(x => x.Id == assetId);
            return (a.CapturedAt, a.CapturedAtSource, a.Exif?.DateTimeOriginal);
        });
    }

    [Fact]
    public async Task DryRun_CountsButChangesNothing()
    {
        var client = await AdminClientAsync();
        var seeded = new DateTime(2026, 6, 4, 10, 0, 0, DateTimeKind.Utc);
        var assetId = await AddAssetAsync(
            "foto.jpg",
            "/assets/users/u1/2010/2010-08-15 Vacaciones/foto.jpg",
            seeded,
            CaptureDateSource.FileSystem);

        var body = await client.GetStringAsync(
            "/api/assets/dates/restore/stream?inferFromPath=true&dryRun=true");

        Assert.Contains("Simulaci", body); // "Simulación completada"
        var after = await LoadAsync(assetId);
        Assert.Equal(seeded, after.CapturedAt);
        Assert.Equal(CaptureDateSource.FileSystem, after.Source);
        Assert.Null(after.ExifDate);
    }

    [Fact]
    public async Task InferFromPath_UpdatesCapturedAt_AndMarksInferred()
    {
        var client = await AdminClientAsync();
        var seeded = new DateTime(2026, 6, 4, 10, 0, 0, DateTimeKind.Utc);
        var assetId = await AddAssetAsync(
            "foto.jpg",
            "/assets/users/u1/2010/2010-08-15 Vacaciones/foto.jpg",
            seeded,
            CaptureDateSource.FileSystem);

        await client.GetStringAsync("/api/assets/dates/restore/stream?inferFromPath=true");

        var after = await LoadAsync(assetId);
        // Default timezone is UTC in tests → noon local == noon UTC.
        Assert.Equal(new DateTime(2010, 8, 15, 12, 0, 0), after.CapturedAt);
        Assert.Equal(CaptureDateSource.Inferred, after.Source);
        Assert.Equal(after.CapturedAt, after.ExifDate);
    }

    [Fact]
    public async Task FileNameDate_BeatsFolderDate()
    {
        var client = await AdminClientAsync();
        var assetId = await AddAssetAsync(
            "IMG_20111224_180000.jpg",
            "/assets/users/u1/2010/2010-08-15 Vacaciones/IMG_20111224_180000.jpg",
            new DateTime(2026, 6, 4, 10, 0, 0, DateTimeKind.Utc),
            CaptureDateSource.FileSystem);

        await client.GetStringAsync("/api/assets/dates/restore/stream?inferFromPath=true");

        var after = await LoadAsync(assetId);
        Assert.Equal(new DateTime(2011, 12, 24, 18, 0, 0), after.CapturedAt);
    }

    [Fact]
    public async Task ManualDate_IsNeverOverwritten_ByRestoreOrInference()
    {
        var client = await AdminClientAsync();
        var manualDate = new DateTime(1999, 1, 1, 9, 30, 0, DateTimeKind.Utc);
        var exifDate = new DateTime(2005, 5, 5, 5, 5, 5, DateTimeKind.Utc);

        // EXIF row disagrees with the manual date AND the path carries an
        // inferable date — neither may win over Manual.
        var assetId = await AddAssetAsync(
            "foto.jpg",
            "/assets/users/u1/2010/2010-08-15 Vacaciones/foto.jpg",
            manualDate,
            CaptureDateSource.Manual,
            exifDate: exifDate);

        await client.GetStringAsync("/api/assets/dates/restore/stream?inferFromPath=true");

        var after = await LoadAsync(assetId);
        Assert.Equal(manualDate, after.CapturedAt);
        Assert.Equal(CaptureDateSource.Manual, after.Source);
    }

    [Fact]
    public async Task UseFileDate_AppliesMtime_AndWritesItIntoTheFile()
    {
        var client = await AdminClientAsync();

        // Physical EXIF-less JPEG whose mtime holds the real date.
        var realDate = new DateTime(2006, 7, 15, 15, 13, 17, DateTimeKind.Utc);
        var dir = Path.Combine(Factory.InternalAssetsPath, "users", "u1");
        Directory.CreateDirectory(dir);
        var physicalPath = Path.Combine(dir, "old-file.jpg");
        File.Copy(Fixtures.FixturePaths.NoMetadata, physicalPath, overwrite: true);
        File.SetLastWriteTimeUtc(physicalPath, realDate);

        var staleDate = new DateTime(2026, 5, 26, 10, 0, 0, DateTimeKind.Utc);
        var assetId = await AddAssetAsync(
            "old-file.jpg",
            "/assets/users/u1/old-file.jpg",
            staleDate,
            CaptureDateSource.FileSystem);

        try
        {
            await client.GetStringAsync(
                "/api/assets/dates/restore/stream?useFileDate=true&writeToFile=true");

            var after = await LoadAsync(assetId);
            Assert.Equal(realDate, after.CapturedAt);
            Assert.Equal(CaptureDateSource.FileSystem, after.Source);
            // The date is now durable: stored EXIF row AND embedded in the file.
            Assert.Equal(realDate, after.ExifDate);

            using var scope = Factory.Services.CreateScope();
            var extractor = scope.ServiceProvider
                .GetRequiredService<Photonne.Server.Api.Shared.Services.ExifExtractorService>();
            var exif = await extractor.ExtractExifAsync(physicalPath);
            Assert.Equal(realDate, exif!.DateTimeOriginal);
            // mtime survives the EXIF rewrite (ApplyDateToFileAsync re-sets it).
            Assert.Equal(realDate, File.GetLastWriteTimeUtc(physicalPath));

            // Idempotent: a second run finds the stored EXIF date and skips.
            var second = await client.GetStringAsync(
                "/api/assets/dates/restore/stream?useFileDate=true&writeToFile=true");
            Assert.DoesNotContain("actualizados", second);
        }
        finally
        {
            File.Delete(physicalPath);
        }
    }

    [Fact]
    public async Task Suggestion_PreviewsInferredDate_WithoutApplying()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var seeded = new DateTime(2026, 6, 4, 10, 0, 0, DateTimeKind.Utc);
        var assetId = await AddAssetAsync(
            "IMG-20100815-WA0001.jpg",
            $"/assets/users/{alice.Username}/2010/2010-08-15 Vacaciones/IMG-20100815-WA0001.jpg",
            seeded,
            CaptureDateSource.FileSystem);

        var response = await aliceClient.GetAsync($"/api/assets/{assetId}/date/suggestion");
        Assert.Equal(System.Net.HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadAsStringAsync();
        // Filename (noon, from the WhatsApp date) wins over the folder; no
        // physical file exists so there is no EXIF candidate.
        Assert.Contains("2010-08-15T12:00:00", body);
        Assert.Contains("FileName", body);
        Assert.Contains("\"exifDate\":null", body);

        // Preview only — nothing changed.
        var after = await LoadAsync(assetId);
        Assert.Equal(seeded, after.CapturedAt);
        Assert.Equal(CaptureDateSource.FileSystem, after.Source);
    }

    [Fact]
    public async Task ExifDate_IsNotReplaced_ByInference()
    {
        var client = await AdminClientAsync();
        var exifDate = new DateTime(2005, 5, 5, 5, 5, 5, DateTimeKind.Utc);

        var assetId = await AddAssetAsync(
            "foto.jpg",
            "/assets/users/u1/2010/2010-08-15 Vacaciones/foto.jpg",
            exifDate,
            CaptureDateSource.Exif,
            exifDate: exifDate);

        await client.GetStringAsync("/api/assets/dates/restore/stream?inferFromPath=true");

        var after = await LoadAsync(assetId);
        Assert.Equal(exifDate, after.CapturedAt);
        Assert.Equal(CaptureDateSource.Exif, after.Source);
    }
}
