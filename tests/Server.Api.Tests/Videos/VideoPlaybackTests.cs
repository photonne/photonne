using System.Diagnostics;
using System.Net;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Video;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Videos;

/// <summary>
/// A video that plays everywhere is served as is; one that doesn't gets an
/// H.264 copy, served by /playback, and the copy goes when the video goes.
/// Needs ffmpeg (the server image ships it; CI installs it).
/// </summary>
[Collection(IntegrationCollection.Name)]
public class VideoPlaybackTests : IntegrationTestBase
{
    public VideoPlaybackTests(PhotonneApiFactory factory) : base(factory) { }

    private static void Ffmpeg(string arguments)
    {
        using var process = Process.Start(new ProcessStartInfo("ffmpeg", "-hide_banner -loglevel error -y " + arguments)
        {
            RedirectStandardError = true,
            UseShellExecute = false
        })!;
        process.StandardError.ReadToEnd();
        process.WaitForExit();
        Assert.Equal(0, process.ExitCode);
    }

    private Task<Guid> CreateVideoAsync(TestUser owner, string fileName) => WithDbContextAsync(async db =>
    {
        var asset = new Asset
        {
            FileName = fileName,
            FullPath = $"/assets/users/{owner.Username}/Videos/{fileName}",
            FileSize = 1024,
            Checksum = Guid.NewGuid().ToString("N"),
            Type = AssetType.Video,
            Extension = Path.GetExtension(fileName).TrimStart('.'),
            FileCreatedAt = DateTime.UtcNow,
            FileModifiedAt = DateTime.UtcNow,
            CapturedAt = DateTime.UtcNow.AddYears(5), // first in line
            OwnerId = owner.Id
        };
        db.Assets.Add(asset);
        await db.SaveChangesAsync();
        return asset.Id;
    });

    [Fact]
    public async Task Plays_the_original_or_a_copy_and_drops_the_copy_with_the_video()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var directory = Path.Combine(Factory.InternalAssetsPath, "users", owner.Username, "Videos");
        Directory.CreateDirectory(directory);
        File.Copy(FixturePaths.Video, Path.Combine(directory, "plays.mp4"), overwrite: true);
        Ffmpeg($"-f lavfi -i color=c=red:s=64x48:d=1 -c:v mpeg4 \"{Path.Combine(directory, "old.avi")}\"");

        var plays = await CreateVideoAsync(owner, "plays.mp4");
        var old = await CreateVideoAsync(owner, "old.avi");

        using (var scope = Factory.Services.CreateScope())
        {
            var service = scope.ServiceProvider.GetRequiredService<VideoPlaybackService>();
            for (var i = 0; i < 500 && await service.ProcessNextAsync(CancellationToken.None); i++) { }
        }

        var rows = await WithDbContextAsync(db => db.AssetPlaybacks.AsNoTracking()
            .Where(p => p.AssetId == plays || p.AssetId == old)
            .ToDictionaryAsync(p => p.AssetId));
        Assert.Equal(PlaybackStatus.NotNeeded, rows[plays].Status);
        Assert.Equal(PlaybackStatus.Ready, rows[old].Status);
        var copy = rows[old].FilePath!;
        Assert.True(File.Exists(copy));

        var copyResponse = await client.GetAsync($"/api/assets/{old}/playback");
        Assert.Equal(HttpStatusCode.OK, copyResponse.StatusCode);
        Assert.Equal("video/mp4", copyResponse.Content.Headers.ContentType?.MediaType);
        Assert.Equal(new FileInfo(copy).Length, (await copyResponse.Content.ReadAsByteArrayAsync()).Length);

        var originalResponse = await client.GetAsync($"/api/assets/{plays}/playback");
        Assert.Equal(HttpStatusCode.OK, originalResponse.StatusCode);
        Assert.Equal(new FileInfo(FixturePaths.Video).Length, (await originalResponse.Content.ReadAsByteArrayAsync()).Length);

        // The video goes for good: its row goes with it, and the sweep takes the file.
        await WithDbContextAsync(async db =>
        {
            db.Assets.Remove(await db.Assets.FindAsync(old) ?? throw new InvalidOperationException());
            await db.SaveChangesAsync();
        });
        using (var scope = Factory.Services.CreateScope())
            await scope.ServiceProvider.GetRequiredService<VideoPlaybackService>().SweepOrphansAsync(CancellationToken.None);
        Assert.False(File.Exists(copy));
    }

    [Fact]
    public async Task Someone_else_cant_play_it()
    {
        var owner = await CreateUserAsync();
        var (_, stranger) = await CreateAuthenticatedUserAsync();
        var video = await CreateVideoAsync(owner, "private.mp4");

        var response = await stranger.GetAsync($"/api/assets/{video}/playback");
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }
}
