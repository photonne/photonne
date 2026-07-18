using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Organize;

/// <summary>
/// Exercises the "Mover por condiciones" endpoints:
/// POST /api/organize/rule/preview (dry-run count + sample) and
/// POST /api/organize/rule/move (bulk move by rule). The contract mirrors the
/// rest of the Organize feature: a condition rule is ALWAYS intersected with the
/// caller's MobileBackup inbox, so an asset that matches the rule but is already
/// filed out of MobileBackup is never previewed nor moved.
/// </summary>
public sealed class OrganizeRuleTests : IntegrationTestBase
{
    public OrganizeRuleTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record YearCountDto(int Year, int Count);
    private sealed record YearGroupDto(int Year, List<Guid> AssetIds);
    private sealed record PreviewResponse(int Count, List<Guid> SampleAssetIds, List<YearCountDto> YearBreakdown);
    private sealed record ReviewResponse(List<YearGroupDto> Groups);
    private sealed record MoveResponse(int Moved, List<YearCountDto> YearBreakdown);
    private sealed record CountBody(int Count);

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
        string fullPath,
        Guid folderId,
        DateTime capturedAt,
        bool isArchived = false,
        bool isMotionPhotoPart = false,
        bool writeToDisk = false)
    {
        var id = await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = fullPath,
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
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

        if (writeToDisk)
        {
            await WithDbContextAsync(async _ =>
            {
                using var scope = Factory.Services.CreateScope();
                var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
                var physical = await settings.ResolvePhysicalPathAsync(fullPath);
                Directory.CreateDirectory(Path.GetDirectoryName(physical)!);
                await File.WriteAllTextAsync(physical, "img");
            });
        }

        return id;
    }

    /// <summary>
    /// alice: two device subfolders under MobileBackup (Pixel has two datable
    /// photos, iPhone one), an archived + a Live-Photo motion part that must never
    /// match, and one photo already filed into /Familia that shares the pending
    /// photos' date range — the "matches by rule but already organized" case.
    /// </summary>
    private async Task<(TestUser Alice, HttpClient Client, Guid PixelFolderId, Guid FamiliaFolderId, Guid PixNewId, Guid PixOldId)> SeedAsync(bool onDisk = false)
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}";
        var pixelFolder = await CreateFolderAsync($"{root}/MobileBackup/Pixel");
        var iphoneFolder = await CreateFolderAsync($"{root}/MobileBackup/iPhone");
        var familiaFolder = await CreateFolderAsync($"{root}/Familia");

        var pixNew = await CreateAssetAsync(alice, "pix-new.jpg",
            $"{root}/MobileBackup/Pixel/pix-new.jpg", pixelFolder,
            new DateTime(2026, 2, 20, 12, 0, 0, DateTimeKind.Utc), writeToDisk: onDisk);
        var pixOld = await CreateAssetAsync(alice, "pix-old.jpg",
            $"{root}/MobileBackup/Pixel/pix-old.jpg", pixelFolder,
            new DateTime(2026, 2, 5, 8, 0, 0, DateTimeKind.Utc), writeToDisk: onDisk);
        await CreateAssetAsync(alice, "iph.jpg",
            $"{root}/MobileBackup/iPhone/iph.jpg", iphoneFolder,
            new DateTime(2026, 2, 10, 9, 0, 0, DateTimeKind.Utc), writeToDisk: onDisk);

        // Under MobileBackup but excluded by the inbox predicate itself.
        await CreateAssetAsync(alice, "arch.jpg",
            $"{root}/MobileBackup/Pixel/arch.jpg", pixelFolder,
            new DateTime(2026, 2, 15, 10, 0, 0, DateTimeKind.Utc), isArchived: true);
        await CreateAssetAsync(alice, "live.mov",
            $"{root}/MobileBackup/Pixel/live.mov", pixelFolder,
            new DateTime(2026, 2, 15, 10, 0, 0, DateTimeKind.Utc), isMotionPhotoPart: true);

        // Already organized: outside MobileBackup, same date range — must never
        // be previewed or moved by a rule.
        await CreateAssetAsync(alice, "familia.jpg",
            $"{root}/Familia/familia.jpg", familiaFolder,
            new DateTime(2026, 2, 12, 11, 0, 0, DateTimeKind.Utc));

        return (alice, client, pixelFolder, familiaFolder, pixNew, pixOld);
    }

    private static object DateRangeRule(DateTime from, DateTime to) =>
        new { type = "dateRange", from, to };

    private static object FolderRule(Guid folderId) =>
        new { type = "folder", folderIds = new[] { folderId }, includeSubfolders = true };

    [Fact]
    public async Task Preview_FolderCondition_ReturnsOnlyPendingMatches()
    {
        var (_, client, pixelFolderId, _, pixNew, pixOld) = await SeedAsync();

        var response = await client.PostAsJsonAsync("/api/organize/rule/preview",
            new { rule = FolderRule(pixelFolderId), sampleSize = 60 });
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<PreviewResponse>();

        // Pixel MobileBackup subfolder has pix-new + pix-old pending; the archived
        // and motion-part assets sit in the same folder but are excluded.
        Assert.Equal(2, body!.Count);
        Assert.Equal(new HashSet<Guid> { pixNew, pixOld }, body.SampleAssetIds.ToHashSet());
    }

    [Fact]
    public async Task Preview_DateRange_ExcludesAlreadyFiledAssets()
    {
        var (_, client, _, _, _, _) = await SeedAsync();

        // Feb 2026 covers every seeded photo by date, incl. the filed familia.jpg.
        var response = await client.PostAsJsonAsync("/api/organize/rule/preview",
            new
            {
                rule = DateRangeRule(
                    new DateTime(2026, 2, 1, 0, 0, 0, DateTimeKind.Utc),
                    new DateTime(2026, 2, 28, 23, 59, 59, DateTimeKind.Utc)),
                sampleSize = 60
            });
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<PreviewResponse>();

        // Only the 3 pending photos match; the filed familia.jpg (same range) and
        // the archived/motion assets are excluded.
        Assert.Equal(3, body!.Count);
    }

    [Fact]
    public async Task Move_FilesMatchingPendingAssetsIntoTarget_AndDrainsInbox()
    {
        var (alice, client, pixelFolderId, familiaFolderId, pixNew, pixOld) = await SeedAsync(onDisk: true);

        var response = await client.PostAsJsonAsync("/api/organize/rule/move",
            new { rule = FolderRule(pixelFolderId), targetFolderId = familiaFolderId });
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<MoveResponse>();

        Assert.Equal(2, body!.Moved);

        // Both Pixel photos now live under /Familia and left the inbox.
        await WithDbContextAsync(async db =>
        {
            var moved = await db.Assets
                .Where(a => a.Id == pixNew || a.Id == pixOld)
                .ToListAsync();
            Assert.All(moved, a => Assert.Equal(familiaFolderId, a.FolderId));
            Assert.All(moved, a => Assert.DoesNotContain("/MobileBackup/", a.FullPath));
        });

        // The iPhone photo was not matched by the folder rule → still pending.
        var count = await client.GetFromJsonAsync<CountBody>("/api/organize/inbox/count");
        Assert.Equal(1, count!.Count);
    }

    [Fact]
    public async Task Move_LeavesUnmatchedAndFiledAssetsUntouched()
    {
        var (alice, client, pixelFolderId, familiaFolderId, _, _) = await SeedAsync(onDisk: true);

        var familiaBefore = await WithDbContextAsync(async db =>
            await db.Assets.Where(a => a.FileName == "familia.jpg").Select(a => a.FullPath).FirstAsync());

        await client.PostAsJsonAsync("/api/organize/rule/move",
            new { rule = FolderRule(pixelFolderId), targetFolderId = familiaFolderId });

        var familiaAfter = await WithDbContextAsync(async db =>
            await db.Assets.Where(a => a.FileName == "familia.jpg").Select(a => a.FullPath).FirstAsync());

        // The already-filed photo is outside the inbox, so a rule move never touches it.
        Assert.Equal(familiaBefore, familiaAfter);
    }

    [Fact]
    public async Task Move_EmptyMatch_ReturnsZero()
    {
        var (_, client, _, familiaFolderId, _, _) = await SeedAsync();

        // A date range with no pending matches.
        var response = await client.PostAsJsonAsync("/api/organize/rule/move",
            new
            {
                rule = DateRangeRule(
                    new DateTime(2000, 1, 1, 0, 0, 0, DateTimeKind.Utc),
                    new DateTime(2000, 12, 31, 0, 0, 0, DateTimeKind.Utc)),
                targetFolderId = familiaFolderId
            });
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<MoveResponse>();

        Assert.Equal(0, body!.Moved);
    }

    [Fact]
    public async Task Move_OrganizeByYear_FilesMatchesIntoYearSubfolder()
    {
        var (_, client, pixelFolderId, familiaFolderId, pixNew, pixOld) = await SeedAsync(onDisk: true);

        // Both Pixel photos are from 2026, so they share a single 2026 bucket.
        var response = await client.PostAsJsonAsync("/api/organize/rule/move",
            new { rule = FolderRule(pixelFolderId), targetFolderId = familiaFolderId, organizeByCaptureYear = true });
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<MoveResponse>();
        Assert.Equal(2, body!.Moved);

        await WithDbContextAsync(async db =>
        {
            var buckets = await db.Folders
                .Where(f => f.ParentFolderId == familiaFolderId && f.Name == "2026")
                .ToListAsync();
            Assert.Single(buckets);

            var moved = await db.Assets.Where(a => a.Id == pixNew || a.Id == pixOld).ToListAsync();
            Assert.All(moved, a => Assert.Equal(buckets[0].Id, a.FolderId));
            Assert.All(moved, a => Assert.Contains("/Familia/2026/", a.FullPath));
        });
    }

    [Fact]
    public async Task Preview_And_Move_YearBreakdown_Match_AcrossYears()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}";
        var pixel = await CreateFolderAsync($"{root}/MobileBackup/Pixel");
        var familia = await CreateFolderAsync($"{root}/Familia");

        await CreateAssetAsync(alice, "a.jpg", $"{root}/MobileBackup/Pixel/a.jpg", pixel,
            new DateTime(2026, 3, 1, 12, 0, 0, DateTimeKind.Utc), writeToDisk: true);
        await CreateAssetAsync(alice, "b.jpg", $"{root}/MobileBackup/Pixel/b.jpg", pixel,
            new DateTime(2026, 8, 9, 12, 0, 0, DateTimeKind.Utc), writeToDisk: true);
        await CreateAssetAsync(alice, "c.jpg", $"{root}/MobileBackup/Pixel/c.jpg", pixel,
            new DateTime(2025, 5, 4, 12, 0, 0, DateTimeKind.Utc), writeToDisk: true);

        // Preview reports the split (newest year first).
        var previewResp = await client.PostAsJsonAsync("/api/organize/rule/preview",
            new { rule = FolderRule(pixel), sampleSize = 60 });
        previewResp.EnsureSuccessStatusCode();
        var preview = await previewResp.Content.ReadFromJsonAsync<PreviewResponse>();
        Assert.Equal(new[] { (2026, 2), (2025, 1) },
            preview!.YearBreakdown.Select(y => (y.Year, y.Count)).ToArray());

        // The move's real split equals the preview.
        var moveResp = await client.PostAsJsonAsync("/api/organize/rule/move",
            new { rule = FolderRule(pixel), targetFolderId = familia, organizeByCaptureYear = true });
        moveResp.EnsureSuccessStatusCode();
        var move = await moveResp.Content.ReadFromJsonAsync<MoveResponse>();
        Assert.Equal(3, move!.Moved);
        Assert.Equal(
            preview.YearBreakdown.Select(y => (y.Year, y.Count)).ToArray(),
            move.YearBreakdown.Select(y => (y.Year, y.Count)).ToArray());
    }

    [Fact]
    public async Task Review_GroupsMatchingPendingAssetsByYear()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}";
        var pixel = await CreateFolderAsync($"{root}/MobileBackup/Pixel");

        var new2026 = await CreateAssetAsync(alice, "n.jpg", $"{root}/MobileBackup/Pixel/n.jpg", pixel,
            new DateTime(2026, 9, 1, 12, 0, 0, DateTimeKind.Utc));
        var old2026 = await CreateAssetAsync(alice, "o.jpg", $"{root}/MobileBackup/Pixel/o.jpg", pixel,
            new DateTime(2026, 1, 1, 12, 0, 0, DateTimeKind.Utc));
        var y2025 = await CreateAssetAsync(alice, "p.jpg", $"{root}/MobileBackup/Pixel/p.jpg", pixel,
            new DateTime(2025, 6, 1, 12, 0, 0, DateTimeKind.Utc));

        var response = await client.PostAsJsonAsync("/api/organize/rule/review",
            new { rule = FolderRule(pixel) });
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<ReviewResponse>();

        // Years newest-first; within 2026 the newer photo comes first.
        Assert.Equal(new[] { 2026, 2025 }, body!.Groups.Select(g => g.Year).ToArray());
        Assert.Equal(new[] { new2026, old2026 }, body.Groups[0].AssetIds);
        Assert.Equal(new[] { y2025 }, body.Groups[1].AssetIds);
    }

    [Fact]
    public async Task Preview_NullRule_ReturnsBadRequest()
    {
        var (_, client, _, _, _, _) = await SeedAsync();

        var response = await client.PostAsJsonAsync("/api/organize/rule/preview",
            new { rule = (object?)null, sampleSize = 24 });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }
}
