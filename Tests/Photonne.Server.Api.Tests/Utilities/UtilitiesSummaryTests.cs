using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Utilities;

/// <summary>
/// GET /api/utilities/summary: the live figures of the Utilities hub. They must
/// agree with the screens they summarise — same scope (the caller's readable
/// folders, no trash, no archive) and the same "recoverable" definition as the
/// duplicates screen (per group, total minus the largest copy).
/// </summary>
public sealed class UtilitiesSummaryTests : IntegrationTestBase
{
    public UtilitiesSummaryTests(PhotonneApiFactory factory) : base(factory) { }

    private const long Threshold = 100L * 1024 * 1024;

    private sealed record SummaryBody(
        int DuplicateGroups,
        int DuplicateAssets,
        long DuplicateRecoverableBytes,
        int LargeFilesCount,
        long LargeFilesBytes,
        int UnsupportedCount);

    private Task<Guid> CreateFolderAsync(string path) =>
        WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private Task CreateAssetAsync(
        TestUser owner,
        Guid folderId,
        string folderPath,
        long size,
        string? checksum = null,
        bool isArchived = false,
        bool isDeleted = false) =>
        WithDbContextAsync(async db =>
        {
            var fileName = Guid.NewGuid().ToString("N")[..8] + ".jpg";
            var now = DateTime.UtcNow;
            db.Assets.Add(new Asset
            {
                FileName = fileName,
                FullPath = $"{folderPath}/{fileName}",
                FileSize = size,
                Checksum = checksum ?? Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = now,
                FileModifiedAt = now,
                CapturedAt = now,
                OwnerId = owner.Id,
                FolderId = folderId,
                IsArchived = isArchived,
                DeletedAt = isDeleted ? now : null
            });
            await db.SaveChangesAsync();
        });

    private Task CreateUnsupportedAsync(TestUser owner, Guid folderId, string folderPath, long size) =>
        WithDbContextAsync(async db =>
        {
            var fileName = Guid.NewGuid().ToString("N")[..8] + ".txt";
            db.UnsupportedFiles.Add(new UnsupportedFile
            {
                FileName = fileName,
                FullPath = $"{folderPath}/{fileName}",
                FileSize = size,
                Extension = ".txt",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = owner.Id,
                FolderId = folderId
            });
            await db.SaveChangesAsync();
        });

    [Fact]
    public async Task Summary_CountsOnlyTheCallersVisibleAssets()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();
        var alicePath = $"/assets/users/{alice.Username}/Fotos";
        var bobPath = $"/assets/users/{bob.Username}/Fotos";
        var aliceFolder = await CreateFolderAsync(alicePath);
        var bobFolder = await CreateFolderAsync(bobPath);

        var groupA = new string('a', 64);
        var groupB = new string('b', 64);

        // Group A: 100 + 300 → keep 300, recoverable 100.
        await CreateAssetAsync(alice, aliceFolder, alicePath, 100, groupA);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 300, groupA);
        // Group B: 3 × 50 → recoverable 100. The archived and trashed copies don't count.
        await CreateAssetAsync(alice, aliceFolder, alicePath, 50, groupB);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 50, groupB);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 50, groupB);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 999, groupB, isArchived: true);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 999, groupB, isDeleted: true);
        // Large files: one over the threshold counts, one under doesn't, and
        // archived or trashed big ones don't either.
        await CreateAssetAsync(alice, aliceFolder, alicePath, Threshold + 10);
        await CreateAssetAsync(alice, aliceFolder, alicePath, Threshold - 1);
        await CreateAssetAsync(alice, aliceFolder, alicePath, Threshold * 2, isArchived: true);
        await CreateAssetAsync(alice, aliceFolder, alicePath, Threshold * 3, isDeleted: true);
        await CreateUnsupportedAsync(alice, aliceFolder, alicePath, 7);

        // Bob's copies share alice's checksums and are bigger, but aren't hers to see.
        await CreateAssetAsync(bob, bobFolder, bobPath, 50_000, groupA);
        await CreateAssetAsync(bob, bobFolder, bobPath, 60_000, groupB);
        await CreateAssetAsync(bob, bobFolder, bobPath, Threshold * 4);
        await CreateUnsupportedAsync(bob, bobFolder, bobPath, 7);
        await CreateUnsupportedAsync(bob, bobFolder, bobPath, 7);

        var summary = await aliceClient.GetFromJsonAsync<SummaryBody>("/api/utilities/summary");

        Assert.NotNull(summary);
        Assert.Equal(2, summary!.DuplicateGroups);
        Assert.Equal(5, summary.DuplicateAssets);
        Assert.Equal(200, summary.DuplicateRecoverableBytes);
        Assert.Equal(1, summary.LargeFilesCount);
        Assert.Equal(Threshold + 10, summary.LargeFilesBytes);
        Assert.Equal(1, summary.UnsupportedCount);
    }

    [Fact]
    public async Task Summary_LargeFiles_ThresholdIsInclusive()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var path = $"/assets/users/{alice.Username}/Fotos";
        var folder = await CreateFolderAsync(path);
        await CreateAssetAsync(alice, folder, path, Threshold - 1);
        await CreateAssetAsync(alice, folder, path, Threshold);
        await CreateAssetAsync(alice, folder, path, Threshold * 5);

        var summary = await aliceClient.GetFromJsonAsync<SummaryBody>("/api/utilities/summary");

        Assert.Equal(2, summary!.LargeFilesCount);
        Assert.Equal(Threshold * 6, summary.LargeFilesBytes);
    }

    [Fact]
    public async Task Summary_IsAllZero_ForAnEmptyLibrary()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var summary = await client.GetFromJsonAsync<SummaryBody>("/api/utilities/summary");

        Assert.Equal(new SummaryBody(0, 0, 0, 0, 0, 0), summary);
    }
}
