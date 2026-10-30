using System.Diagnostics;
using System.Net;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Services;
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

    /// <summary>The service as the server builds it, with some Video:* settings on top.</summary>
    private VideoPlaybackService Service(IServiceScope scope, params (string Key, string? Value)[] video)
    {
        var configuration = new ConfigurationBuilder()
            .AddConfiguration(scope.ServiceProvider.GetRequiredService<IConfiguration>())
            .AddInMemoryCollection(video.Select(v => new KeyValuePair<string, string?>(v.Key, v.Value)))
            .Build();
        return new VideoPlaybackService(
            scope.ServiceProvider.GetRequiredService<ApplicationDbContext>(),
            scope.ServiceProvider.GetRequiredService<SettingsService>(),
            configuration,
            NullLogger<VideoPlaybackService>.Instance);
    }

    [Fact]
    public async Task Waits_without_failing_while_the_disk_is_down_to_its_reserve()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var directory = Path.Combine(Factory.InternalAssetsPath, "users", owner.Username, "Videos");
        Directory.CreateDirectory(directory);
        Ffmpeg($"-f lavfi -i color=c=blue:s=64x48:d=1 -c:v mpeg4 \"{Path.Combine(directory, "full.avi")}\"");
        var video = await CreateVideoAsync(owner, "full.avi");
        var root = Path.Combine(Path.GetTempPath(), "photonne-playback-" + Guid.NewGuid().ToString("N"));

        try
        {
            using (var scope = Factory.Services.CreateScope())
            {
                // No disk has a petabyte free.
                var service = Service(scope, ("Video:PlaybackPath", root), ("Video:MinFreeSpaceGb", "1000000"));
                Assert.False(await service.ProcessNextAsync(CancellationToken.None));
                Assert.NotNull(service.OutOfSpace);
            }
            Assert.False(await WithDbContextAsync(db => db.AssetPlaybacks.AnyAsync(p => p.AssetId == video)));

            using (var scope = Factory.Services.CreateScope())
            {
                var service = Service(scope, ("Video:PlaybackPath", root), ("Video:MinFreeSpaceGb", "0"));
                for (var i = 0; i < 500 && await service.ProcessNextAsync(CancellationToken.None); i++) { }
                Assert.Null(service.OutOfSpace);
            }
            var row = await WithDbContextAsync(db => db.AssetPlaybacks.AsNoTracking().SingleAsync(p => p.AssetId == video));
            Assert.Equal(PlaybackStatus.Ready, row.Status);
            Assert.Equal(Path.Combine(root, $"{video}.mp4"), row.FilePath);
        }
        finally
        {
            if (Directory.Exists(root)) Directory.Delete(root, recursive: true);
        }
    }

    [Fact]
    public async Task A_copy_deleted_by_hand_is_forgotten_and_made_again()
    {
        var (owner, client) = await CreateAuthenticatedUserAsync();
        var directory = Path.Combine(Factory.InternalAssetsPath, "users", owner.Username, "Videos");
        Directory.CreateDirectory(directory);
        Ffmpeg($"-f lavfi -i color=c=green:s=64x48:d=1 -c:v mpeg4 \"{Path.Combine(directory, "lost.avi")}\"");
        var video = await CreateVideoAsync(owner, "lost.avi");

        using (var scope = Factory.Services.CreateScope())
        {
            var service = scope.ServiceProvider.GetRequiredService<VideoPlaybackService>();
            for (var i = 0; i < 500 && await service.ProcessNextAsync(CancellationToken.None); i++) { }
        }
        var copy = (await WithDbContextAsync(db => db.AssetPlaybacks.AsNoTracking().SingleAsync(p => p.AssetId == video))).FilePath!;
        File.Delete(copy);

        // Meanwhile the original plays.
        var response = await client.GetAsync($"/api/assets/{video}/playback");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(new FileInfo(Path.Combine(directory, "lost.avi")).Length, (await response.Content.ReadAsByteArrayAsync()).Length);

        using (var scope = Factory.Services.CreateScope())
        {
            var service = scope.ServiceProvider.GetRequiredService<VideoPlaybackService>();
            await service.SweepOrphansAsync(CancellationToken.None);
            Assert.False(await WithDbContextAsync(db => db.AssetPlaybacks.AnyAsync(p => p.AssetId == video)));
            for (var i = 0; i < 500 && await service.ProcessNextAsync(CancellationToken.None); i++) { }
        }
        Assert.True(File.Exists(copy));
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
