using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
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
    public async Task Upload_Jpeg_CreatesAsset_Exif_AndThumbnails()
    {
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
            var asset = await db.Assets
                .Include(a => a.Exif)
                .Include(a => a.Thumbnails)
                .AsNoTracking()
                .FirstAsync(a => a.Id == assetId);

            Assert.Equal(user.Id, asset.OwnerId);
            Assert.False(string.IsNullOrWhiteSpace(asset.Checksum));
            Assert.StartsWith($"/assets/users/{user.Id}/Uploads/", asset.FullPath);
            Assert.Equal(".jpg", asset.Extension);
            Assert.Equal("Image", asset.Type.ToString());

            // EXIF extracted from the fixture:
            Assert.NotNull(asset.Exif);
            Assert.Equal("Canon", asset.Exif!.CameraMake);
            Assert.Equal("EOS R5", asset.Exif.CameraModel);

            // Three thumbnails persisted + actually written to disk:
            Assert.Equal(3, asset.Thumbnails.Count);
            Assert.All(asset.Thumbnails, t => Assert.True(File.Exists(t.FilePath),
                $"thumbnail missing on disk: {t.FilePath}"));
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
            Factory.InternalAssetsPath, "users", user.Id.ToString(), "Uploads");

        Assert.True(Directory.Exists(expectedDir),
            $"expected uploads directory to exist: {expectedDir}");
        Assert.NotEmpty(Directory.GetFiles(expectedDir));
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
