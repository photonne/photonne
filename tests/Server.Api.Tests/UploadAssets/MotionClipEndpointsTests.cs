using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.UploadAssets;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.UploadAssets;

/// <summary>
/// The iOS Live Photo clip: the phone backup uploads the still, then attaches
/// the paired .mov next to it so the sibling pairing tags the still LivePhoto.
/// </summary>
public sealed class MotionClipEndpointsTests : IntegrationTestBase
{
    public MotionClipEndpointsTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record UploadResp(string Message, Guid? AssetId);
    private sealed record MissingResp(List<Guid> Missing);

    private static MultipartFormDataContent StillForm(string fileName)
    {
        // Bytes after the JPEG end marker are ignored by decoders but change the
        // checksum, so the upload's global dedup never hands back another still.
        var bytes = File.ReadAllBytes(FixturePaths.NoMetadata).Concat(Guid.NewGuid().ToByteArray()).ToArray();
        var fileContent = new ByteArrayContent(bytes);
        fileContent.Headers.ContentType = MediaTypeHeaderValue.Parse("image/jpeg");
        var form = new MultipartFormDataContent { { fileContent, "file", fileName } };
        form.Add(new StringContent("mobile-backup"), "destination");
        return form;
    }

    private static MultipartFormDataContent ClipForm(string fileName)
    {
        // The endpoint stores the bytes as they come; it never decodes them.
        var fileContent = new ByteArrayContent(Guid.NewGuid().ToByteArray());
        fileContent.Headers.ContentType = MediaTypeHeaderValue.Parse("video/quicktime");
        return new MultipartFormDataContent { { fileContent, "file", fileName } };
    }

    private async Task<Guid> UploadStillAsync(HttpClient client, string fileName)
    {
        using var form = StillForm(fileName);
        var response = await client.PostAsync("/api/assets/upload", form);
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<UploadResp>();
        return body!.AssetId!.Value;
    }

    [Fact]
    public async Task Attach_StoresClipNextToStill_AndTagsBoth()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();
        var stillId = await UploadStillAsync(client, "IMG_0001.JPG");

        using var clipForm = ClipForm("IMG_0001.MOV");
        var response = await client.PostAsync($"/api/assets/{stillId}/motion-clip", clipForm);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        await WithDbContextAsync(async db =>
        {
            var still = await db.Assets.AsNoTracking().Include(a => a.Tags).FirstAsync(a => a.Id == stillId);
            Assert.Contains(still.Tags, t => t.TagType == AssetTagType.LivePhoto);

            var clip = await db.Assets.AsNoTracking().Include(a => a.Tags)
                .FirstAsync(a => a.Id != stillId && a.OwnerId == still.OwnerId);
            Assert.Equal("IMG_0001.mov", clip.FileName);
            Assert.Equal(still.FolderId, clip.FolderId);
            Assert.Equal(AssetType.Video, clip.Type);
            Assert.Contains(clip.Tags, t => t.TagType == AssetTagType.MotionPhotoPart);
        });

        var motion = await client.GetAsync($"/api/assets/{stillId}/motion");
        Assert.Equal(HttpStatusCode.OK, motion.StatusCode);
    }

    [Fact]
    public async Task Attach_Twice_KeepsOneClip()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var stillId = await UploadStillAsync(client, "IMG_0002.JPG");

        using (var first = ClipForm("IMG_0002.MOV"))
            (await client.PostAsync($"/api/assets/{stillId}/motion-clip", first)).EnsureSuccessStatusCode();
        using (var second = ClipForm("IMG_0002.MOV"))
            (await client.PostAsync($"/api/assets/{stillId}/motion-clip", second)).EnsureSuccessStatusCode();

        await WithDbContextAsync(async db =>
        {
            Assert.Equal(2, await db.Assets.CountAsync(a => a.OwnerId == user.Id));
        });
    }

    [Fact]
    public async Task Attach_ToAnotherUsersStill_IsForbidden()
    {
        var (_, ownerClient) = await CreateAuthenticatedUserAsync();
        var (_, otherClient) = await CreateAuthenticatedUserAsync();
        var stillId = await UploadStillAsync(ownerClient, "IMG_0003.JPG");

        using var clipForm = ClipForm("IMG_0003.MOV");
        var response = await otherClient.PostAsync($"/api/assets/{stillId}/motion-clip", clipForm);
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task Missing_ListsOnlyStillsWithoutClip()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();
        var paired = await UploadStillAsync(client, "IMG_0004.JPG");
        var unpaired = await UploadStillAsync(client, "IMG_0005.PNG");
        using (var clipForm = ClipForm("IMG_0004.MOV"))
            (await client.PostAsync($"/api/assets/{paired}/motion-clip", clipForm)).EnsureSuccessStatusCode();

        var response = await client.PostAsJsonAsync("/api/assets/motion-clips/missing", new
        {
            AssetIds = new[] { paired, unpaired, Guid.NewGuid() }
        });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<MissingResp>();
        Assert.Equal(new[] { unpaired }, body!.Missing);
    }
}

/// <summary>Where the attached clip lands, relative to its still.</summary>
public sealed class MotionClipPathTests
{
    [Theory]
    [InlineData("/lib/IMG_1.HEIC", "IMG_1.MOV", "/lib/IMG_1.mov")]
    [InlineData("/lib/abc_IMG_1.HEIC", "IMG_1.MOV", "/lib/abc_IMG_1.mov")]
    [InlineData("/lib/IMG_1.HEIC", "clip.MP4", "/lib/IMG_1.mp4")]
    [InlineData("/lib/IMG_1.HEIC", "clip", "/lib/IMG_1.mov")]
    public void ClipPathFor_UsesStillNameAndLowerCaseExtension(string still, string uploaded, string expected)
    {
        Assert.Equal(expected, MotionClipEndpoints.ClipPathFor(still, uploaded));
    }
}
