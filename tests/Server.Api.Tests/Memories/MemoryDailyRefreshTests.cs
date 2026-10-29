using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Features.Memories.Generation;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Memories;

/// <summary>
/// "Un día como hoy" follows the calendar: the daily refresh regenerates the
/// memories once per local day without the nightly run, and the feed never
/// shows an OnThisDay row written for another day.
/// </summary>
public sealed class MemoryDailyRefreshTests : IntegrationTestBase
{
    public MemoryDailyRefreshTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record FeedItem(Guid Id, string Kind, string Title);

    private async Task<DateTime> LocalTodayAsync()
    {
        using var scope = Factory.Services.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
        var tz = await MetadataTimeZone.ResolveAsync(settings);
        return MetadataTimeZone.LocalNow(tz).Date;
    }

    private MemoryDailyRefreshService CreateRefresh() =>
        new(Factory.Services.GetRequiredService<IServiceScopeFactory>());

    private Task CreateAssetsAsync(TestUser owner, Guid folderId, DateTime capturedAt, int count) =>
        WithDbContextAsync(async db =>
        {
            for (var i = 0; i < count; i++)
            {
                var name = $"{Guid.NewGuid():N}.jpg";
                db.Assets.Add(new Asset
                {
                    FileName = name,
                    FullPath = $"/assets/users/{owner.Username}/{name}",
                    FileSize = 3,
                    Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                    Type = AssetType.Image,
                    Extension = "jpg",
                    FileCreatedAt = capturedAt,
                    FileModifiedAt = capturedAt,
                    CapturedAt = capturedAt.AddMinutes(i),
                    OwnerId = owner.Id,
                    FolderId = folderId,
                });
            }
            await db.SaveChangesAsync();
        });

    private Task<Guid> CreateFolderAsync(TestUser owner) =>
        WithDbContextAsync(async db =>
        {
            var path = $"/assets/users/{owner.Username}";
            var folder = new Folder { Path = path, Name = owner.Username };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    [Fact]
    public async Task Refresh_GeneratesTodaysMemories_OncePerDay_WithoutTheNightlyRun()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();
        await CreateAssetsAsync(user, folder, today.AddYears(-2), count: 4);

        var refresh = CreateRefresh();

        // NightlyTaskSettings.Enabled is off by default: the refresh doesn't need it.
        Assert.True(await refresh.TryRunAsync(CancellationToken.None));
        var feed = await client.GetFromJsonAsync<List<FeedItem>>("/api/memories");
        Assert.Contains(feed!, m => m.Kind == nameof(MemoryKind.OnThisDay) && m.Title == "Hace 2 años");

        // Same local day: nothing to do.
        Assert.False(await refresh.TryRunAsync(CancellationToken.None));
    }

    [Fact]
    public async Task Refresh_IsOff_WhenMemoriesAreSwitchedOff()
    {
        using (var scope = Factory.Services.CreateScope())
        {
            var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
            await settings.SetSettingAsync("NightlyTaskSettings.Memories.Enabled", "false", Guid.Empty);
        }

        Assert.False(await CreateRefresh().TryRunAsync(CancellationToken.None));
    }

    [Fact]
    public async Task Feed_HidesOnThisDayMemoriesOfAnotherDay()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var today = await LocalTodayAsync();
        var yesterday = today.AddDays(-1);

        Memory OnThisDay(DateTime day, string title) => new()
        {
            OwnerId = user.Id,
            Kind = MemoryKind.OnThisDay,
            Title = title,
            ThemeKey = "onthisday",
            DedupeKey = $"onthisday:{day.Year - 1:D4}-{day.Month:D2}-{day.Day:D2}",
            WindowStart = day.AddYears(-1),
            WindowEnd = day.AddYears(-1),
        };

        await WithDbContextAsync(async db =>
        {
            db.Memories.Add(OnThisDay(today, "Hoy"));
            db.Memories.Add(OnThisDay(yesterday, "Ayer"));
            await db.SaveChangesAsync();
        });

        var feed = await client.GetFromJsonAsync<List<FeedItem>>("/api/memories");

        var onThisDay = feed!.Where(m => m.Kind == nameof(MemoryKind.OnThisDay)).Select(m => m.Title);
        Assert.Equal(new[] { "Hoy" }, onThisDay);
    }
}
