using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.UploadAssets;

/// <summary>
/// End-to-end upload flow: POST multipart → file lands in managed library →
/// Asset + AssetExif + AssetThumbnail rows created → asset visible in timeline.
/// This is the single highest-coverage test in the suite — most indexing
/// regressions surface here before reaching production.
/// </summary>
public sealed class UploadPipelineTests : IntegrationTestBase
{
    public UploadPipelineTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record UploadResp(string Message, Guid? AssetId);

    private static MultipartFormDataContent BuildMultipart(string fixturePath)
    {
        var bytes = File.ReadAllBytes(fixturePath);
        var fileContent = new ByteArrayContent(bytes);
        fileContent.Headers.ContentType = MediaTypeHeaderValue.Parse("image/jpeg");
        var form = new MultipartFormDataContent
        {
            { fileContent, "file", Path.GetFileName(fixturePath) }
        };
        return form;
    }

    [Fact]
    public async Task Upload_Jpeg_CreatesAssetAndEnqueuesEnrichmentTasks()
    {
        // Contract since Phase B: /upload returns 200 as soon as the backup minimum is
        // satisfied (file on disk + Asset row + checksum). EXIF, thumbnails, media tags
        // and the ML pipelines are enqueued as AssetEnrichmentTasks; the worker
        // processes them asynchronously. Tests run with IHostedService disabled, so the
        // tasks stay Pending — that's exactly what we want to assert here.
        var (user, client) = await CreateAuthenticatedUserAsync();

        using var form = BuildMultipart(FixturePaths.WithExif);
        var response = await client.PostAsync("/api/assets/upload", form);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<UploadResp>();
        Assert.NotNull(body);
        Assert.NotNull(body!.AssetId);
        var assetId = body.AssetId!.Value;

        await WithDbContextAsync(async db =>
        {
            // Backup contract: Asset row exists with all the minimum-required fields.
            var asset = await db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId);
            Assert.Equal(user.Id, asset.OwnerId);
            Assert.False(string.IsNullOrWhiteSpace(asset.Checksum));
            Assert.StartsWith($"/assets/users/{user.Username}/Uploads/", asset.FullPath);
            Assert.Equal(".jpg", asset.Extension);
            Assert.Equal("Image", asset.Type.ToString());

            // Backup contract does NOT include enrichment outputs — Exif/Thumbnails/Tags
            // only exist after the worker runs (which is disabled in tests).
            var exif = await db.AssetExifs.AsNoTracking().FirstOrDefaultAsync(e => e.AssetId == assetId);
            Assert.Null(exif);
            var thumbs = await db.AssetThumbnails.AsNoTracking().Where(t => t.AssetId == assetId).CountAsync();
            Assert.Equal(0, thumbs);

            // Enrichment plan: at least Exif, Thumbnails and MediaRecognition are
            // always enqueued for image assets. ML tasks are added conditionally by
            // MediaRecognitionService.ShouldTriggerMlJob (needs EXIF data, which we
            // don't have yet at enqueue time, so they may or may not be there).
            var tasks = await db.AssetEnrichmentTasks
                .AsNoTracking()
                .Where(t => t.AssetId == assetId)
                .ToListAsync();

            Assert.Contains(tasks, t => t.TaskType == AssetEnrichmentType.Exif);
            Assert.Contains(tasks, t => t.TaskType == AssetEnrichmentType.Thumbnails);
            Assert.Contains(tasks, t => t.TaskType == AssetEnrichmentType.MediaRecognition);
            // All enqueued tasks start Pending with zero attempts and no schedule.
            Assert.All(tasks, t =>
            {
                Assert.Equal(EnrichmentStatus.Pending, t.Status);
                Assert.Equal(0, t.AttemptCount);
                Assert.Null(t.NextRetryAt);
            });
        });
    }

    [Fact]
    public async Task Upload_WritesFile_UnderUsersManagedUploadsFolder()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();

        using var form = BuildMultipart(FixturePaths.NoMetadata);
        var response = await client.PostAsync("/api/assets/upload", form);
        response.EnsureSuccessStatusCode();

        var expectedDir = Path.Combine(
            Factory.InternalAssetsPath, "users", user.Username, "Uploads");

        Assert.True(Directory.Exists(expectedDir),
            $"expected uploads directory to exist: {expectedDir}");
        Assert.NotEmpty(Directory.GetFiles(expectedDir));
    }

    [Fact]
    public async Task Upload_WithMobileBackupDestination_LandsUnderMobileBackupFolder()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();

        using var form = BuildMultipart(FixturePaths.NoMetadata);
        form.Add(new StringContent("mobile-backup"), "destination");
        var response = await client.PostAsync("/api/assets/upload", form);
        response.EnsureSuccessStatusCode();

        var body = await response.Content.ReadFromJsonAsync<UploadResp>();
        Assert.NotNull(body?.AssetId);

        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.AsNoTracking()
                .FirstAsync(a => a.Id == body!.AssetId!.Value);
            Assert.StartsWith($"/assets/users/{user.Username}/MobileBackup/", asset.FullPath);
        });

        var expectedDir = Path.Combine(
            Factory.InternalAssetsPath, "users", user.Username, "MobileBackup");
        Assert.True(Directory.Exists(expectedDir),
            $"expected MobileBackup directory to exist: {expectedDir}");
        Assert.NotEmpty(Directory.GetFiles(expectedDir));
    }

    [Fact]
    public async Task Upload_WithMobileBackupAndDeviceName_LandsInDeviceSubfolder()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();

        using var form = BuildMultipart(FixturePaths.NoMetadata);
        form.Add(new StringContent("mobile-backup"), "destination");
        form.Add(new StringContent("Pixel 8 Pro"), "deviceName");
        var response = await client.PostAsync("/api/assets/upload", form);
        response.EnsureSuccessStatusCode();

        var body = await response.Content.ReadFromJsonAsync<UploadResp>();
        Assert.NotNull(body?.AssetId);

        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.AsNoTracking()
                .FirstAsync(a => a.Id == body!.AssetId!.Value);
            // Space → underscore via the sanitizer.
            Assert.StartsWith(
                $"/assets/users/{user.Username}/MobileBackup/Pixel_8_Pro/",
                asset.FullPath);
        });

        var expectedDir = Path.Combine(
            Factory.InternalAssetsPath, "users", user.Username, "MobileBackup", "Pixel_8_Pro");
        Assert.True(Directory.Exists(expectedDir),
            $"expected device subfolder to exist: {expectedDir}");
        Assert.NotEmpty(Directory.GetFiles(expectedDir));
    }

    [Fact]
    public async Task Upload_WithInvalidDeviceName_FallsBackToFlatMobileBackup()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();

        using var form = BuildMultipart(FixturePaths.NoMetadata);
        form.Add(new StringContent("mobile-backup"), "destination");
        // Only forbidden characters → sanitizer returns null → flat /MobileBackup/.
        form.Add(new StringContent("///\\..\\"), "deviceName");
        var response = await client.PostAsync("/api/assets/upload", form);
        response.EnsureSuccessStatusCode();

        var body = await response.Content.ReadFromJsonAsync<UploadResp>();
        Assert.NotNull(body?.AssetId);

        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.AsNoTracking()
                .FirstAsync(a => a.Id == body!.AssetId!.Value);
            // No subfolder — path lands directly under MobileBackup/.
            Assert.StartsWith($"/assets/users/{user.Username}/MobileBackup/", asset.FullPath);
            var trailing = asset.FullPath.Substring(
                $"/assets/users/{user.Username}/MobileBackup/".Length);
            Assert.DoesNotContain('/', trailing);
        });
    }

    [Fact]
    public async Task Upload_WithDefaultDestinationAndDeviceName_IgnoresDeviceName()
    {
        // Manual /Uploads should stay flat even if the client accidentally sends a deviceName.
        var (user, client) = await CreateAuthenticatedUserAsync();

        using var form = BuildMultipart(FixturePaths.NoMetadata);
        form.Add(new StringContent("Pixel 8 Pro"), "deviceName");
        var response = await client.PostAsync("/api/assets/upload", form);
        response.EnsureSuccessStatusCode();

        var body = await response.Content.ReadFromJsonAsync<UploadResp>();
        Assert.NotNull(body?.AssetId);

        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.AsNoTracking()
                .FirstAsync(a => a.Id == body!.AssetId!.Value);
            Assert.StartsWith($"/assets/users/{user.Username}/Uploads/", asset.FullPath);
            Assert.DoesNotContain("Pixel", asset.FullPath);
        });
    }

    [Fact]
    public async Task Upload_SameFileTwice_IsDeduplicatedByChecksum()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        using (var first = BuildMultipart(FixturePaths.WithExif))
        {
            (await client.PostAsync("/api/assets/upload", first)).EnsureSuccessStatusCode();
        }

        using var second = BuildMultipart(FixturePaths.WithExif);
        var response = await client.PostAsync("/api/assets/upload", second);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadAsStringAsync();
        Assert.Contains("already exists", body, StringComparison.OrdinalIgnoreCase);

        // Exactly one row in the DB — the second upload short-circuited.
        var assetCount = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().CountAsync());
        Assert.Equal(1, assetCount);
    }

    [Fact]
    public async Task Upload_Rejected_WhenQuotaExceeded()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();

        // Squeeze the quota tighter than the fixture size so the upload fails.
        var fixtureSize = new FileInfo(FixturePaths.WithExif).Length;
        await WithDbContextAsync(async db =>
        {
            var u = await db.Users.FirstAsync(x => x.Id == user.Id);
            u.StorageQuotaBytes = fixtureSize / 2;
            await db.SaveChangesAsync();
        });

        using var form = BuildMultipart(FixturePaths.WithExif);
        var response = await client.PostAsync("/api/assets/upload", form);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);

        var uploaded = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().AnyAsync(a => a.OwnerId == user.Id));
        Assert.False(uploaded);
    }

    [Fact]
    public async Task Upload_EmptyBody_ReturnsBadRequest()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        using var form = new MultipartFormDataContent();
        var response = await client.PostAsync("/api/assets/upload", form);

        // Either a 400 from our handler or a 400 from form-binding: both are fine.
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task UploadedAsset_AppearsInTimeline()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        using (var form = BuildMultipart(FixturePaths.WithExif))
        {
            (await client.PostAsync("/api/assets/upload", form)).EnsureSuccessStatusCode();
        }

        // pageSize is [FromQuery] int with no default in the endpoint signature,
        // so we have to pass one or the binding layer rejects the request with 400.
        var timeline = await client.GetAsync("/api/assets/timeline?pageSize=150");
        Assert.Equal(HttpStatusCode.OK, timeline.StatusCode);

        var body = await timeline.Content.ReadAsStringAsync();
        Assert.Contains("sample-with-exif.jpg", body, StringComparison.OrdinalIgnoreCase);
    }
}
