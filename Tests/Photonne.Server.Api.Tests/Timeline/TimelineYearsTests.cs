using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Timeline;

/// <summary>
/// Exercises GET /api/assets/timeline/years — the compressed yearly view:
/// per-year totals plus an evenly-distributed sample. Order is pinned to
/// newest-first at both levels (years and items within a year), matching
/// every other timeline surface.
/// </summary>
public sealed class TimelineYearsTests : IntegrationTestBase
{
    public TimelineYearsTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record YearItem(Guid Id, DateTime FileCreatedAt);
    private sealed record YearEntry(int Year, int Count, List<YearItem> Items);

    private async Task<Guid> CreateFolderAsync(string path)
    {
        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });
    }

    private async Task<Guid> CreateAssetAsync(
        TestUser owner,
        string fileName,
        Guid folderId,
        DateTime capturedAt,
        bool isArchived = false,
        bool isMotionPhotoPart = false)
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"/assets/users/{owner.Username}/{fileName}",
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = ".jpg",
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                FolderId = folderId,
                IsArchived = isArchived
            };
            db.Assets.Add(asset);
            if (isMotionPhotoPart)
            {
                db.AssetTags.Add(new AssetTag { Asset = asset, TagType = AssetTagType.MotionPhotoPart });
            }
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    [Fact]
    public async Task Years_DescendByYear_AndByDateWithinEachYear()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        await CreateAssetAsync(alice, "a.jpg", folderId, new DateTime(2025, 3, 1, 0, 0, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "b.jpg", folderId, new DateTime(2026, 1, 10, 0, 0, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "c.jpg", folderId, new DateTime(2026, 5, 20, 0, 0, 0, DateTimeKind.Utc));

        var years = await client.GetFromJsonAsync<List<YearEntry>>("/api/assets/timeline/years");

        Assert.Equal(new[] { 2026, 2025 }, years!.Select(y => y.Year));
        var y2026 = years[0];
        Assert.Equal(2, y2026.Count);
        // Newest first within the year.
        Assert.Equal(
            y2026.Items.OrderByDescending(i => i.FileCreatedAt).Select(i => i.Id),
            y2026.Items.Select(i => i.Id));
    }

    [Fact]
    public async Task Sample_SpreadsAcrossTheYear_NotJustTheNewestMonths()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        // One asset per month of 2026.
        for (var month = 1; month <= 12; month++)
        {
            await CreateAssetAsync(alice, $"m{month}.jpg", folderId,
                new DateTime(2026, month, 15, 12, 0, 0, DateTimeKind.Utc));
        }

        var years = await client.GetFromJsonAsync<List<YearEntry>>("/api/assets/timeline/years?sample=4");

        var year = Assert.Single(years!);
        Assert.Equal(12, year.Count);
        // step = ceil(12/4) = 3 → newest-first indices 0,3,6,9 → months 12,9,6,3.
        Assert.Equal(new[] { 12, 9, 6, 3 }, year.Items.Select(i => i.FileCreatedAt.Month));
    }

    [Fact]
    public async Task SmallYears_ReturnEveryItem()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        await CreateAssetAsync(alice, "a.jpg", folderId, new DateTime(2026, 2, 1, 0, 0, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "b.jpg", folderId, new DateTime(2026, 7, 1, 0, 0, 0, DateTimeKind.Utc));

        var years = await client.GetFromJsonAsync<List<YearEntry>>("/api/assets/timeline/years?sample=24");

        var year = Assert.Single(years!);
        Assert.Equal(2, year.Count);
        Assert.Equal(2, year.Items.Count);
    }

    [Fact]
    public async Task HiddenAssets_AreExcludedFromCountAndSample()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");
        var visible = await CreateAssetAsync(alice, "ok.jpg", folderId,
            new DateTime(2026, 2, 1, 0, 0, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "archived.jpg", folderId,
            new DateTime(2026, 3, 1, 0, 0, 0, DateTimeKind.Utc), isArchived: true);
        await CreateAssetAsync(alice, "live.mov", folderId,
            new DateTime(2026, 4, 1, 0, 0, 0, DateTimeKind.Utc), isMotionPhotoPart: true);

        var years = await client.GetFromJsonAsync<List<YearEntry>>("/api/assets/timeline/years");

        var year = Assert.Single(years!);
        Assert.Equal(1, year.Count);
        Assert.Equal(visible, Assert.Single(year.Items).Id);
    }

    [Fact]
    public async Task EmptyLibrary_ReturnsEmptyList()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var years = await client.GetFromJsonAsync<List<YearEntry>>("/api/assets/timeline/years");

        Assert.Empty(years!);
    }
}
