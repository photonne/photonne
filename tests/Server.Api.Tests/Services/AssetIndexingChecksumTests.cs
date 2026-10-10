using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// Covers the disk-is-truth checksum semantics of AssetIndexingService.IndexFileAsync:
/// a checksum match whose recorded file is gone is a MOVE (same asset, path updated,
/// enrichment kept), while a match whose file still exists is a physical COPY that
/// gets its own row — visible in its own folder and surfaced by Duplicates later.
/// </summary>
public sealed class AssetIndexingChecksumTests : IntegrationTestBase
{
    public AssetIndexingChecksumTests(PhotonneApiFactory factory) : base(factory) { }

    private string WriteFile(string relativePath, byte[] content)
    {
        var physicalPath = Path.Combine(Factory.InternalAssetsPath, relativePath.Replace('/', Path.DirectorySeparatorChar));
        Directory.CreateDirectory(Path.GetDirectoryName(physicalPath)!);
        File.WriteAllBytes(physicalPath, content);
        return physicalPath;
    }

    private async Task<Asset?> IndexAsync(string physicalPath)
    {
        using var scope = Factory.Services.CreateScope();
        var indexer = scope.ServiceProvider.GetRequiredService<AssetIndexingService>();
        return await indexer.IndexFileAsync(physicalPath, Guid.Empty, CancellationToken.None);
    }

    // Content unique per test: the DB resets between tests but the shared
    // on-disk assets root does not, so a fixed payload would checksum-collide
    // with files left behind by a previous test.
    private static byte[] SampleBytes()
    {
        var seed = Guid.NewGuid().ToByteArray();
        return Enumerable.Range(0, 4096).Select(i => (byte)(seed[i % 16] ^ (i % 251))).ToArray();
    }

    // Unique directory per test for the same reason.
    private static string UniqueDir() => "t" + Guid.NewGuid().ToString("N")[..8];

    [Fact]
    public async Task MovedFile_WithThumbnails_IsRelocatedToSharedFolder()
    {
        var dir = UniqueDir();
        var originalPath = WriteFile($"users/{PhotonneApiFactory.AdminUsername}/{dir}/photo.jpg", SampleBytes());
        var original = await IndexAsync(originalPath);
        Assert.NotNull(original);

        // Simulate a completed enrichment plus a prior scan that noticed the file gone.
        await WithDbContextAsync(async db =>
        {
            db.AssetThumbnails.Add(new AssetThumbnail
            {
                AssetId = original!.Id,
                Size = ThumbnailSize.Small,
                FilePath = "thumbs/fake.jpg",
                Width = 220,
                Height = 220,
                FileSize = 10
            });
            await db.Assets.Where(a => a.Id == original.Id)
                .ExecuteUpdateAsync(s => s.SetProperty(a => a.IsFileMissing, true));
            await db.SaveChangesAsync();
        });

        var movedPath = Path.Combine(Factory.InternalAssetsPath, "shared", dir, "photo.jpg");
        Directory.CreateDirectory(Path.GetDirectoryName(movedPath)!);
        File.Move(originalPath, movedPath);

        var reindexed = await IndexAsync(movedPath);

        Assert.NotNull(reindexed);
        Assert.Equal(original!.Id, reindexed!.Id);

        var (asset, folderPath, thumbnailCount, totalWithChecksum) = await WithDbContextAsync(async db =>
        {
            var a = await db.Assets.AsNoTracking().FirstAsync(x => x.Id == original.Id);
            var f = a.FolderId.HasValue
                ? (await db.Folders.AsNoTracking().FirstAsync(x => x.Id == a.FolderId.Value)).Path
                : null;
            var thumbs = await db.AssetThumbnails.CountAsync(t => t.AssetId == a.Id);
            var total = await db.Assets.CountAsync(x => x.Checksum == a.Checksum && x.DeletedAt == null);
            return (a, f, thumbs, total);
        });

        Assert.Equal($"/assets/shared/{dir}/photo.jpg", asset.FullPath);
        Assert.False(asset.IsFileMissing);
        Assert.Equal($"/assets/shared/{dir}", folderPath);
        Assert.Equal(1, thumbnailCount);
        Assert.Equal(1, totalWithChecksum);

        // Ownership follows the shared-space rule: the primary admin.
        var adminId = await WithDbContextAsync(db =>
            db.Users.AsNoTracking()
                .Where(u => u.Username == PhotonneApiFactory.AdminUsername)
                .Select(u => u.Id)
                .FirstAsync());
        Assert.Equal(adminId, asset.OwnerId);
    }

    [Fact]
    public async Task CopiedFile_GetsItsOwnAssetRow()
    {
        var dir = UniqueDir();
        var bytes = SampleBytes();
        var originalPath = WriteFile($"users/{PhotonneApiFactory.AdminUsername}/{dir}/photo.jpg", bytes);
        var original = await IndexAsync(originalPath);
        Assert.NotNull(original);

        var copyPath = WriteFile($"shared/{dir}/photo.jpg", bytes);
        var copy = await IndexAsync(copyPath);

        Assert.NotNull(copy);
        Assert.NotEqual(original!.Id, copy!.Id);

        var (originalRow, copyRow) = await WithDbContextAsync(async db =>
        {
            var o = await db.Assets.AsNoTracking().FirstAsync(a => a.Id == original.Id);
            var c = await db.Assets.AsNoTracking().FirstAsync(a => a.Id == copy.Id);
            return (o, c);
        });

        Assert.Equal(originalRow.Checksum, copyRow.Checksum);
        Assert.Equal($"/assets/users/{PhotonneApiFactory.AdminUsername}/{dir}/photo.jpg", originalRow.FullPath);
        Assert.Equal($"/assets/shared/{dir}/photo.jpg", copyRow.FullPath);
        Assert.Null(originalRow.DeletedAt);
        Assert.Null(copyRow.DeletedAt);
    }

    [Fact]
    public async Task Reindex_OfUnchangedCopy_DoesNotDuplicateRows()
    {
        var dir = UniqueDir();
        var bytes = SampleBytes();
        var originalPath = WriteFile($"users/{PhotonneApiFactory.AdminUsername}/{dir}/photo.jpg", bytes);
        await IndexAsync(originalPath);

        var copyPath = WriteFile($"shared/{dir}/photo.jpg", bytes);
        var copy = await IndexAsync(copyPath);
        Assert.NotNull(copy);

        // A second indexing pass over the same on-disk state must be a no-op:
        // the copy resolves by path, the original keeps its row.
        var reindexedCopy = await IndexAsync(copyPath);
        Assert.NotNull(reindexedCopy);
        Assert.Equal(copy!.Id, reindexedCopy!.Id);

        var total = await WithDbContextAsync(db =>
            db.Assets.CountAsync(a => a.Checksum == copy.Checksum && a.DeletedAt == null));
        Assert.Equal(2, total);
    }
}
