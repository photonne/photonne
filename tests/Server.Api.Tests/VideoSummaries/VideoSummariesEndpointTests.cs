using System.Diagnostics;
using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Features.VideoSummaries;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.VideoSummaries;

/// <summary>
/// /api/video-summaries: a user asks for the summary of a year or of an album
/// they can open, it renders in the background into their own «Recuerdos»
/// folder as an ordinary video, and asking again re-renders the same asset.
/// The worker doesn't run under test (hosted services are removed), so the
/// tests drive <see cref="VideoSummaryGenerator"/> directly.
/// </summary>
public sealed class VideoSummariesEndpointTests : IntegrationTestBase
{
    public VideoSummariesEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record Summary(
        Guid Id, string Source, int? Year, Guid? AlbumId, string Title, bool IncludePhotos,
        string Orientation, int DurationMinutes, string Status, double Progress, string? Error, Guid? AssetId, int ClipCount);

    private sealed record YearRow(int Year, int Videos, int Photos);

    private sealed record ApiErrorBody(string Error, string Code);

    private static DateTime In2023(int month, int day) => new(2023, month, day, 10, 0, 0, DateTimeKind.Utc);

    private async Task<Guid> CreateFolderAsync(string path) =>
        await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    /// <summary>An asset row; with <paramref name="realFile"/>, a real clip or
    /// photo on disk behind it so it can actually be rendered.</summary>
    private async Task<Guid> CreateAssetAsync(
        TestUser owner, string fileName, Guid folderId, DateTime capturedAt, AssetType type, bool realFile = false,
        bool portrait = false)
    {
        var virtualPath = $"/assets/users/{owner.Username}/{fileName}";
        if (realFile)
        {
            var physical = Path.Combine(Factory.InternalAssetsPath, "users", owner.Username, fileName);
            Directory.CreateDirectory(Path.GetDirectoryName(physical)!);
            if (type == AssetType.Video)
                await Ffmpeg("-f", "lavfi", "-i", $"testsrc=size={(portrait ? "360x640" : "640x360")}:rate=30:duration=3",
                    "-f", "lavfi", "-i", "sine=frequency=330:duration=3",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", physical);
            else
                await Ffmpeg("-f", "lavfi", "-i", $"testsrc=size={(portrait ? "300x400" : "400x300")}", "-frames:v", "1", physical);
        }

        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = virtualPath,
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = type,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                FolderId = folderId,
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    private async Task<Guid> CreateAlbumAsync(TestUser owner, string name, params Guid[] assetIds) =>
        await WithDbContextAsync(async db =>
        {
            var album = new Album { Name = name, OwnerId = owner.Id };
            db.Albums.Add(album);
            var order = 0;
            foreach (var id in assetIds)
                db.AlbumAssets.Add(new AlbumAsset { Album = album, AssetId = id, Order = order++ });
            await db.SaveChangesAsync();
            return album.Id;
        });

    private async Task RunGeneratorAsync(Guid summaryId)
    {
        using var scope = Factory.Services.CreateScope();
        await scope.ServiceProvider.GetRequiredService<VideoSummaryGenerator>().RunAsync(summaryId, CancellationToken.None);
    }

    [Fact]
    public async Task YearSummary_RendersIntoRecuerdos_AndReRendersInPlace()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        await CreateAssetAsync(alice, "a.mp4", folderId, In2023(3, 1), AssetType.Video, realFile: true);
        await CreateAssetAsync(alice, "b.mp4", folderId, In2023(8, 15), AssetType.Video, realFile: true);
        await CreateAssetAsync(alice, "c.jpg", folderId, In2023(5, 2), AssetType.Image, realFile: true);
        // Another year: not material for 2023.
        await CreateAssetAsync(alice, "old.mp4", folderId, In2023(1, 1).AddYears(-1), AssetType.Video);

        var response = await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "year", year = 2023, includePhotos = true });
        Assert.Equal(HttpStatusCode.Accepted, response.StatusCode);
        var requested = (await response.Content.ReadFromJsonAsync<Summary>())!;
        Assert.Equal("pending", requested.Status);
        Assert.Equal("Resumen 2023", requested.Title);

        await RunGeneratorAsync(requested.Id);

        var done = (await client.GetFromJsonAsync<Summary>($"/api/video-summaries/{requested.Id}"))!;
        Assert.True(done.Status == "completed", done.Error);
        Assert.Equal(3, done.ClipCount);
        Assert.NotNull(done.AssetId);

        var asset = await WithDbContextAsync(db => db.Assets.AsNoTracking().FirstAsync(a => a.Id == done.AssetId));
        Assert.Equal($"/assets/users/{alice.Username}/Recuerdos/Resumen 2023.mp4", asset.FullPath);
        Assert.Equal(AssetType.Video, asset.Type);
        Assert.Equal(alice.Id, asset.OwnerId);
        // Right after the last moment it shows.
        Assert.Equal(In2023(8, 15).AddSeconds(1), asset.CapturedAt);
        var physical = Path.Combine(Factory.InternalAssetsPath, "users", alice.Username, "Recuerdos", "Resumen 2023.mp4");
        Assert.True(File.Exists(physical));

        // The summary itself is not material: the year's counts don't change.
        var years = (await client.GetFromJsonAsync<List<YearRow>>("/api/video-summaries/years"))!;
        Assert.Equal(new YearRow(2023, 2, 1), years.Single(y => y.Year == 2023));

        // Asking again re-renders over the same asset instead of adding a copy.
        var again = await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "year", year = 2023, includePhotos = false });
        Assert.Equal(HttpStatusCode.Accepted, again.StatusCode);
        var second = (await again.Content.ReadFromJsonAsync<Summary>())!;
        Assert.Equal(requested.Id, second.Id);
        await RunGeneratorAsync(second.Id);

        var redone = (await client.GetFromJsonAsync<Summary>($"/api/video-summaries/{second.Id}"))!;
        Assert.Equal("completed", redone.Status);
        Assert.Equal(done.AssetId, redone.AssetId);
        Assert.Equal(2, redone.ClipCount);
        Assert.Equal(1, await WithDbContextAsync(db =>
            db.Assets.CountAsync(a => a.FullPath.Contains("/Recuerdos/"))));

        var list = (await client.GetFromJsonAsync<List<Summary>>("/api/video-summaries"))!;
        Assert.Equal(requested.Id, Assert.Single(list).Id);
    }

    [Fact]
    public async Task PortraitSummary_TakesOnlyPortraitMaterial_InAPortraitFrame()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        await CreateAssetAsync(alice, "tall1.mp4", folderId, In2023(4, 1), AssetType.Video, realFile: true, portrait: true);
        await CreateAssetAsync(alice, "wide.mp4", folderId, In2023(4, 2), AssetType.Video, realFile: true);
        await CreateAssetAsync(alice, "tall2.mp4", folderId, In2023(4, 3), AssetType.Video, realFile: true, portrait: true);
        await CreateAssetAsync(alice, "tall.jpg", folderId, In2023(4, 4), AssetType.Image, realFile: true, portrait: true);
        await CreateAssetAsync(alice, "wide.jpg", folderId, In2023(4, 5), AssetType.Image, realFile: true);

        var response = await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "year", year = 2023, includePhotos = true, orientation = "portrait", durationMinutes = 1 });
        Assert.Equal(HttpStatusCode.Accepted, response.StatusCode);
        var requested = (await response.Content.ReadFromJsonAsync<Summary>())!;
        Assert.Equal("portrait", requested.Orientation);
        Assert.Equal(1, requested.DurationMinutes);

        await RunGeneratorAsync(requested.Id);

        var done = (await client.GetFromJsonAsync<Summary>($"/api/video-summaries/{requested.Id}"))!;
        Assert.True(done.Status == "completed", done.Error);
        // Two portrait videos and the portrait photo; the landscape ones stay out.
        Assert.Equal(3, done.ClipCount);
        var physical = Path.Combine(Factory.InternalAssetsPath, "users", alice.Username, "Recuerdos", "Resumen 2023.mp4");
        var probe = await Photonne.Server.Api.Features.VideoSummaries.Rendering.FfmpegProcess
            .ProbeAsync(physical, CancellationToken.None);
        Assert.Equal(1080, probe!.DisplayWidth);
        Assert.Equal(1920, probe.DisplayHeight);
    }

    [Fact]
    public async Task Options_AreValidated()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        await CreateAssetAsync(alice, "a.mp4", folderId, In2023(1, 1), AssetType.Video);
        await CreateAssetAsync(alice, "b.mp4", folderId, In2023(1, 2), AssetType.Video);

        async Task<string?> Code(object body)
        {
            var response = await client.PostAsJsonAsync("/api/video-summaries", body);
            return response.StatusCode == HttpStatusCode.BadRequest
                ? (await response.Content.ReadFromJsonAsync<ApiErrorBody>())!.Code
                : null;
        }

        Assert.Equal("invalid_duration", await Code(new { source = "year", year = 2023, durationMinutes = 0 }));
        Assert.Equal("invalid_duration", await Code(new { source = "year", year = 2023, durationMinutes = 11 }));
        Assert.Equal("invalid_orientation", await Code(new { source = "year", year = 2023, orientation = "square" }));

        // Defaults: every shape, five minutes.
        var defaults = await client.PostAsJsonAsync("/api/video-summaries", new { source = "year", year = 2023 });
        var summary = (await defaults.Content.ReadFromJsonAsync<Summary>())!;
        Assert.Equal("all", summary.Orientation);
        Assert.Equal(5, summary.DurationMinutes);
    }

    [Fact]
    public async Task VideoAnalysis_IsStored_AndReusedWhenRenderingAgain()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        var first = await CreateAssetAsync(alice, "a.mp4", folderId, In2023(5, 1), AssetType.Video, realFile: true);
        var second = await CreateAssetAsync(alice, "b.mp4", folderId, In2023(5, 2), AssetType.Video, realFile: true);

        async Task RenderAsync()
        {
            var response = await client.PostAsJsonAsync("/api/video-summaries",
                new { source = "year", year = 2023, includePhotos = false });
            var summary = (await response.Content.ReadFromJsonAsync<Summary>())!;
            await RunGeneratorAsync(summary.Id);
            var done = (await client.GetFromJsonAsync<Summary>($"/api/video-summaries/{summary.Id}"))!;
            Assert.True(done.Status == "completed", done.Error);
        }

        await RenderAsync();
        var analyses = await WithDbContextAsync(db => db.AssetVideoAnalyses.AsNoTracking()
            .OrderBy(a => a.AnalyzedAt).ToListAsync());
        Assert.Equal(new[] { first, second }.OrderBy(id => id), analyses.Select(a => a.AssetId).OrderBy(id => id));
        Assert.All(analyses, a => Assert.Contains("\"motion\"", a.Profile));

        await RenderAsync();
        var again = await WithDbContextAsync(db => db.AssetVideoAnalyses.AsNoTracking().ToListAsync());
        // Same rows, not analysed again.
        Assert.Equal(
            analyses.Select(a => (a.AssetId, a.AnalyzedAt)).OrderBy(x => x.AssetId),
            again.Select(a => (a.AssetId, a.AnalyzedAt)).OrderBy(x => x.AssetId));
    }

    [Fact]
    public async Task AlbumSummary_UsesTheAlbum_AndWaitsWhenAlreadyQueued()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        var first = await CreateAssetAsync(alice, "trip1.mp4", folderId, In2023(6, 1), AssetType.Video, realFile: true);
        var second = await CreateAssetAsync(alice, "trip2.mp4", folderId, In2023(6, 2), AssetType.Video, realFile: true);
        await CreateAssetAsync(alice, "not-in-album.mp4", folderId, In2023(6, 3), AssetType.Video, realFile: true);
        var albumId = await CreateAlbumAsync(alice, "Viaje: Roma/Nápoles", first, second);

        var response = await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "album", albumId, includePhotos = false });
        Assert.Equal(HttpStatusCode.Accepted, response.StatusCode);
        var requested = (await response.Content.ReadFromJsonAsync<Summary>())!;
        Assert.Equal("Resumen · Viaje: Roma/Nápoles", requested.Title);
        Assert.Equal(albumId, requested.AlbumId);

        // Still pending: asking again doesn't queue a second render.
        var duplicate = await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "album", albumId, includePhotos = true });
        Assert.Equal(HttpStatusCode.OK, duplicate.StatusCode);
        Assert.False((await duplicate.Content.ReadFromJsonAsync<Summary>())!.IncludePhotos);

        await RunGeneratorAsync(requested.Id);

        var done = (await client.GetFromJsonAsync<Summary>($"/api/video-summaries/{requested.Id}"))!;
        Assert.True(done.Status == "completed", done.Error);
        Assert.Equal(2, done.ClipCount);
        var path = await WithDbContextAsync(db => db.Assets.Where(a => a.Id == done.AssetId).Select(a => a.FullPath).FirstAsync());
        // The album name, made safe for a file name.
        Assert.Equal($"/assets/users/{alice.Username}/Recuerdos/Resumen · Viaje Roma Nápoles.mp4", path);
    }

    [Fact]
    public async Task AlbumSummary_IsNotFound_ForSomeoneWhoCannotOpenTheAlbum()
    {
        var alice = await CreateUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        var a = await CreateAssetAsync(alice, "a.mp4", folderId, In2023(1, 1), AssetType.Video);
        var b = await CreateAssetAsync(alice, "b.mp4", folderId, In2023(1, 2), AssetType.Video);
        var albumId = await CreateAlbumAsync(alice, "Privado", a, b);

        var (_, bobClient) = await CreateAuthenticatedUserAsync();
        var response = await bobClient.PostAsJsonAsync("/api/video-summaries",
            new { source = "album", albumId, includePhotos = true });

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(0, await WithDbContextAsync(db => db.VideoSummaries.CountAsync()));
    }

    [Fact]
    public async Task NotEnoughMaterial_IsRejected_SuggestingPhotosWhenThereAreSome()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        await CreateAssetAsync(alice, "only.mp4", folderId, In2023(2, 1), AssetType.Video);
        await CreateAssetAsync(alice, "p.jpg", folderId, In2023(2, 2), AssetType.Image);

        var withoutPhotos = await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "year", year = 2023, includePhotos = false });
        Assert.Equal(HttpStatusCode.BadRequest, withoutPhotos.StatusCode);
        var error = (await withoutPhotos.Content.ReadFromJsonAsync<ApiErrorBody>())!;
        Assert.Equal("not_enough_media", error.Code);
        Assert.Contains("incluir las fotos", error.Error);

        // With the photo there are two things to show: accepted.
        var withPhotos = await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "year", year = 2023, includePhotos = true });
        Assert.Equal(HttpStatusCode.Accepted, withPhotos.StatusCode);

        var badSource = await client.PostAsJsonAsync("/api/video-summaries", new { source = "folder" });
        Assert.Equal(HttpStatusCode.BadRequest, badSource.StatusCode);
    }

    [Fact]
    public async Task OneUser_CannotQueueMoreThanThreeAtOnce()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        foreach (var year in new[] { 2020, 2021, 2022, 2023 })
        {
            await CreateAssetAsync(alice, $"{year}-a.mp4", folderId, new DateTime(year, 5, 1), AssetType.Video);
            await CreateAssetAsync(alice, $"{year}-b.mp4", folderId, new DateTime(year, 6, 1), AssetType.Video);
        }

        foreach (var year in new[] { 2020, 2021, 2022 })
        {
            var accepted = await client.PostAsJsonAsync("/api/video-summaries",
                new { source = "year", year, includePhotos = false });
            Assert.Equal(HttpStatusCode.Accepted, accepted.StatusCode);
        }

        var fourth = await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "year", year = 2023, includePhotos = false });
        Assert.Equal(HttpStatusCode.BadRequest, fourth.StatusCode);
        Assert.Equal("summary_queue_full", (await fourth.Content.ReadFromJsonAsync<ApiErrorBody>())!.Code);
    }

    [Fact]
    public async Task Summaries_ArePrivateToTheirOwner()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        await CreateAssetAsync(alice, "a.mp4", folderId, In2023(1, 1), AssetType.Video);
        await CreateAssetAsync(alice, "b.mp4", folderId, In2023(1, 2), AssetType.Video);
        var response = await aliceClient.PostAsJsonAsync("/api/video-summaries",
            new { source = "year", year = 2023, includePhotos = false });
        var summary = (await response.Content.ReadFromJsonAsync<Summary>())!;

        var (_, bobClient) = await CreateAuthenticatedUserAsync();
        Assert.Equal(HttpStatusCode.NotFound,
            (await bobClient.GetAsync($"/api/video-summaries/{summary.Id}")).StatusCode);
        Assert.Empty((await bobClient.GetFromJsonAsync<List<Summary>>("/api/video-summaries"))!);
        // Bob sees none of Alice's material either.
        Assert.Empty((await bobClient.GetFromJsonAsync<List<YearRow>>("/api/video-summaries/years"))!);
    }

    [Fact]
    public async Task MissingAlbum_FailsTheRender_AndTellsTheUser()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        var a = await CreateAssetAsync(alice, "a.mp4", folderId, In2023(1, 1), AssetType.Video);
        var b = await CreateAssetAsync(alice, "b.mp4", folderId, In2023(1, 2), AssetType.Video);
        var albumId = await CreateAlbumAsync(alice, "Se borrará", a, b);
        var summary = (await (await client.PostAsJsonAsync("/api/video-summaries",
            new { source = "album", albumId, includePhotos = false })).Content.ReadFromJsonAsync<Summary>())!;

        await WithDbContextAsync(async db =>
        {
            await db.Albums.Where(x => x.Id == albumId).ExecuteDeleteAsync();
        });
        await RunGeneratorAsync(summary.Id);

        var failed = (await client.GetFromJsonAsync<Summary>($"/api/video-summaries/{summary.Id}"))!;
        Assert.Equal("failed", failed.Status);
        Assert.Contains("álbum", failed.Error);
        Assert.True(await WithDbContextAsync(db =>
            db.Notifications.AnyAsync(n => n.UserId == alice.Id && n.Type == NotificationType.JobFailed)));
    }

    private static async Task Ffmpeg(params string[] args)
    {
        var psi = new ProcessStartInfo("ffmpeg")
        {
            RedirectStandardError = true,
            UseShellExecute = false,
        };
        foreach (var a in new[] { "-hide_banner", "-loglevel", "error", "-y" }.Concat(args)) psi.ArgumentList.Add(a);
        using var process = Process.Start(psi)!;
        var stderr = await process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();
        Assert.True(process.ExitCode == 0, "ffmpeg failed: " + stderr);
    }
}
