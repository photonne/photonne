using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Folders;

/// <summary>
/// Exercises the Year-bucketing option of POST /api/folders/assets/move
/// (organizeByCaptureYear = true): each moved asset is filed into a Year
/// subfolder (e.g. 2026) resolved-or-created under the target, derived from its
/// naive-local CapturedAt. Buckets are reused across moves (idempotent), never
/// duplicated.
/// </summary>
public sealed class FolderMoveOrganizeByYearTests : IntegrationTestBase
{
    public FolderMoveOrganizeByYearTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<Guid> CreateFolderAsync(string path, Guid? parentId = null)
    {
        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last(), ParentFolderId = parentId };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });
    }

    private async Task<Guid> CreateAssetOnDiskAsync(
        TestUser owner, string fileName, string fullPath, Guid folderId, DateTime capturedAt)
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
                FolderId = folderId
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });

        using var scope = Factory.Services.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
        var physical = await settings.ResolvePhysicalPathAsync(fullPath);
        Directory.CreateDirectory(Path.GetDirectoryName(physical)!);
        await File.WriteAllTextAsync(physical, "img");
        return id;
    }

    private sealed record MoveBody(
        Guid? sourceFolderId, Guid targetFolderId, List<Guid> assetIds, bool organizeByCaptureYear);

    private sealed record YearGroupDto(int Year, List<Guid> AssetIds);
    private sealed record YearBreakdownResponse(List<YearGroupDto> Groups);

    [Fact]
    public async Task YearBreakdown_ByIds_GroupsOwnAssetsByCaptureYear()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}";
        var backup = await CreateFolderAsync($"{root}/MobileBackup/Pixel");

        // b (Sep 2026) is newer than a (Feb 2026), so within 2026 b comes first.
        var a = await CreateAssetOnDiskAsync(alice, "a.jpg", $"{root}/MobileBackup/Pixel/a.jpg", backup, new DateTime(2026, 2, 1, 12, 0, 0));
        var b = await CreateAssetOnDiskAsync(alice, "b.jpg", $"{root}/MobileBackup/Pixel/b.jpg", backup, new DateTime(2026, 9, 1, 12, 0, 0));
        var c = await CreateAssetOnDiskAsync(alice, "c.jpg", $"{root}/MobileBackup/Pixel/c.jpg", backup, new DateTime(2024, 7, 1, 12, 0, 0));

        var response = await client.PostAsJsonAsync("/api/assets/year-breakdown",
            new { assetIds = new[] { a, b, c } });
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<YearBreakdownResponse>();

        // Newest year first, ids within a year by capture date desc.
        Assert.Equal(new[] { 2026, 2024 }, body!.Groups.Select(g => g.Year).ToArray());
        Assert.Equal(new[] { b, a }, body.Groups[0].AssetIds);
        Assert.Equal(new[] { c }, body.Groups[1].AssetIds);
    }

    [Fact]
    public async Task Move_OrganizeByYear_FilesEachAssetIntoItsYearSubfolder()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}";
        var backup = await CreateFolderAsync($"{root}/MobileBackup/Pixel");
        var target = await CreateFolderAsync($"{root}/AA");

        var y2026 = await CreateAssetOnDiskAsync(alice, "a.jpg",
            $"{root}/MobileBackup/Pixel/a.jpg", backup, new DateTime(2026, 2, 20, 12, 0, 0));
        var y2025 = await CreateAssetOnDiskAsync(alice, "b.jpg",
            $"{root}/MobileBackup/Pixel/b.jpg", backup, new DateTime(2025, 11, 3, 9, 0, 0));

        var response = await client.PostAsJsonAsync("/api/folders/assets/move",
            new MoveBody(null, target, new List<Guid> { y2026, y2025 }, organizeByCaptureYear: true));
        response.EnsureSuccessStatusCode();

        await WithDbContextAsync(async db =>
        {
            var folder2026 = await db.Folders.FirstOrDefaultAsync(f => f.ParentFolderId == target && f.Name == "2026");
            var folder2025 = await db.Folders.FirstOrDefaultAsync(f => f.ParentFolderId == target && f.Name == "2025");
            Assert.NotNull(folder2026);
            Assert.NotNull(folder2025);
            Assert.Equal($"{root}/AA/2026", folder2026!.Path);
            Assert.Equal($"{root}/AA/2025", folder2025!.Path);

            var a = await db.Assets.FirstAsync(x => x.Id == y2026);
            var b = await db.Assets.FirstAsync(x => x.Id == y2025);
            Assert.Equal(folder2026.Id, a.FolderId);
            Assert.Equal(folder2025.Id, b.FolderId);
            Assert.Contains("/AA/2026/", a.FullPath);
            Assert.Contains("/AA/2025/", b.FullPath);
        });

        // Files physically landed in their year buckets.
        using var scope = Factory.Services.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
        Assert.True(File.Exists(await settings.ResolvePhysicalPathAsync($"{root}/AA/2026/a.jpg")));
        Assert.True(File.Exists(await settings.ResolvePhysicalPathAsync($"{root}/AA/2025/b.jpg")));
    }

    [Fact]
    public async Task Move_OrganizeByYear_ReusesExistingYearBucket_AcrossTwoMoves()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}";
        var backup = await CreateFolderAsync($"{root}/MobileBackup/Pixel");
        var target = await CreateFolderAsync($"{root}/AA");

        var first = await CreateAssetOnDiskAsync(alice, "a.jpg",
            $"{root}/MobileBackup/Pixel/a.jpg", backup, new DateTime(2026, 1, 5, 12, 0, 0));
        var second = await CreateAssetOnDiskAsync(alice, "b.jpg",
            $"{root}/MobileBackup/Pixel/b.jpg", backup, new DateTime(2026, 6, 9, 9, 0, 0));

        await (await client.PostAsJsonAsync("/api/folders/assets/move",
            new MoveBody(null, target, new List<Guid> { first }, organizeByCaptureYear: true)))
            .Content.ReadAsStringAsync();
        await client.PostAsJsonAsync("/api/folders/assets/move",
            new MoveBody(null, target, new List<Guid> { second }, organizeByCaptureYear: true));

        await WithDbContextAsync(async db =>
        {
            // A single 2026 bucket, shared by both moves.
            var buckets = await db.Folders.Where(f => f.ParentFolderId == target && f.Name == "2026").ToListAsync();
            Assert.Single(buckets);

            var a = await db.Assets.FirstAsync(x => x.Id == first);
            var b = await db.Assets.FirstAsync(x => x.Id == second);
            Assert.Equal(buckets[0].Id, a.FolderId);
            Assert.Equal(buckets[0].Id, b.FolderId);
        });
    }
}
