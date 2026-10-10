using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Maintenance;

/// <summary>
/// Exercises POST /api/admin/maintenance/purge-missing: permanent deletion of
/// assets flagged IsFileMissing plus folder records whose physical directory
/// no longer exists. The managed library root is the per-run tmp directory
/// from PhotonneApiFactory, so creating/removing files and directories here
/// drives exactly what the endpoint sees on disk.
/// </summary>
public sealed class PurgeMissingTests : IntegrationTestBase
{
    public PurgeMissingTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record MaintenanceTaskResult(bool Success, string Message, int Processed, int Affected);

    private async Task<HttpClient> LoginAsAdminAsync()
    {
        // Admin user is seeded by the factory.
        var admin = new TestUser(
            Guid.Empty, // not used; login uses username/password
            PhotonneApiFactory.AdminUsername,
            PhotonneApiFactory.AdminPassword,
            "Admin");
        return await LoginAsClientAsync(admin);
    }

    private async Task<Guid> CreateFolderAsync(string path, Guid? parentId = null, bool createPhysicalDir = false)
    {
        if (createPhysicalDir)
            Directory.CreateDirectory(ToPhysical(path));

        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder
            {
                Path = path,
                Name = path.Split('/').Last(),
                ParentFolderId = parentId
            };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });
    }

    private async Task<Guid> CreateAssetAsync(
        TestUser owner,
        string fileName,
        Guid? folderId = null,
        bool isFileMissing = false,
        bool createPhysicalFile = false)
    {
        var fullPath = $"/assets/users/{owner.Username}/{fileName}";

        if (createPhysicalFile)
        {
            var physical = ToPhysical(fullPath);
            Directory.CreateDirectory(Path.GetDirectoryName(physical)!);
            await File.WriteAllBytesAsync(physical, new byte[] { 1, 2, 3 });
        }

        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = fullPath,
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = owner.Id,
                FolderId = folderId,
                IsFileMissing = isFileMissing
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    /// <summary>Maps an /assets/* virtual path to the test run's tmp library root.</summary>
    private string ToPhysical(string virtualPath) =>
        Path.Combine(
            Factory.InternalAssetsPath,
            virtualPath.Substring("/assets/".Length).Replace('/', Path.DirectorySeparatorChar));

    [Fact]
    public async Task Purge_DeletesMissingAssets_AndKeepsHealthyOnes()
    {
        var adminClient = await LoginAsAdminAsync();
        var alice = await CreateUserAsync();

        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}", createPhysicalDir: true);
        var missingId = await CreateAssetAsync(alice, "gone.jpg", folderId, isFileMissing: true);
        var healthyId = await CreateAssetAsync(alice, "here.jpg", folderId, createPhysicalFile: true);

        var response = await adminClient.PostAsync("/api/admin/maintenance/purge-missing", null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var result = await response.Content.ReadFromJsonAsync<MaintenanceTaskResult>();
        Assert.True(result!.Success);
        Assert.Equal(1, result.Affected);

        var remainingIds = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().Select(a => a.Id).ToListAsync());
        Assert.DoesNotContain(missingId, remainingIds);
        Assert.Contains(healthyId, remainingIds);

        // The folder still has a live asset and its directory exists — it stays.
        var folderExists = await WithDbContextAsync(db =>
            db.Folders.AsNoTracking().AnyAsync(f => f.Id == folderId));
        Assert.True(folderExists);
    }

    [Fact]
    public async Task Purge_DryRun_DeletesNothing()
    {
        var adminClient = await LoginAsAdminAsync();
        var alice = await CreateUserAsync();

        var missingId = await CreateAssetAsync(alice, "gone.jpg", isFileMissing: true);

        var response = await adminClient.PostAsync("/api/admin/maintenance/purge-missing?dryRun=true", null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var result = await response.Content.ReadFromJsonAsync<MaintenanceTaskResult>();
        Assert.True(result!.Success);
        Assert.Equal(1, result.Affected);

        var stillThere = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().AnyAsync(a => a.Id == missingId));
        Assert.True(stillThere);
    }

    [Fact]
    public async Task Purge_RemovesOrphanFolderChain_DeepestFirst()
    {
        var adminClient = await LoginAsAdminAsync();
        var alice = await CreateUserAsync();

        // Neither directory exists on disk; child references parent (FK Restrict).
        var parentId = await CreateFolderAsync($"/assets/users/{alice.Username}/vanished");
        var childId = await CreateFolderAsync($"/assets/users/{alice.Username}/vanished/sub", parentId);

        // Control folder whose directory DOES exist — must survive.
        var keptId = await CreateFolderAsync($"/assets/users/{alice.Username}/kept", createPhysicalDir: true);

        var response = await adminClient.PostAsync("/api/admin/maintenance/purge-missing", null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var remaining = await WithDbContextAsync(db =>
            db.Folders.AsNoTracking().Select(f => f.Id).ToListAsync());
        Assert.DoesNotContain(parentId, remaining);
        Assert.DoesNotContain(childId, remaining);
        Assert.Contains(keptId, remaining);
    }

    [Fact]
    public async Task Purge_KeepsOrphanFolder_WhenSubtreeStillHasAssets()
    {
        var adminClient = await LoginAsAdminAsync();
        var alice = await CreateUserAsync();

        // Directory is gone but a healthy (non-missing) asset still points to the
        // folder — e.g. a path mismatch. Never delete the record in that case.
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}/vanished");
        await CreateAssetAsync(alice, "still-referenced.jpg", folderId, createPhysicalFile: true);

        var response = await adminClient.PostAsync("/api/admin/maintenance/purge-missing", null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var folderExists = await WithDbContextAsync(db =>
            db.Folders.AsNoTracking().AnyAsync(f => f.Id == folderId));
        Assert.True(folderExists);
    }

    [Fact]
    public async Task Purge_HealsAsset_WhoseFileReappeared()
    {
        var adminClient = await LoginAsAdminAsync();
        var alice = await CreateUserAsync();

        // Flagged missing but the file is back on disk (e.g. restored backup).
        var assetId = await CreateAssetAsync(alice, "back.jpg", isFileMissing: true, createPhysicalFile: true);

        var response = await adminClient.PostAsync("/api/admin/maintenance/purge-missing", null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var asset = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().FirstAsync(a => a.Id == assetId));
        Assert.False(asset.IsFileMissing);
    }

    [Fact]
    public async Task Purge_CascadesAssetDependents()
    {
        var adminClient = await LoginAsAdminAsync();
        var alice = await CreateUserAsync();

        var assetId = await CreateAssetAsync(alice, "gone.jpg", isFileMissing: true);
        await WithDbContextAsync(async db =>
        {
            db.AssetExifs.Add(new AssetExif { AssetId = assetId });
            await db.SaveChangesAsync();
        });

        var response = await adminClient.PostAsync("/api/admin/maintenance/purge-missing", null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var exifExists = await WithDbContextAsync(db =>
            db.AssetExifs.AsNoTracking().AnyAsync(e => e.AssetId == assetId));
        Assert.False(exifExists);
    }

    [Fact]
    public async Task Purge_ReturnsForbidden_ForNonAdmin()
    {
        var (_, userClient) = await CreateAuthenticatedUserAsync();

        var response = await userClient.PostAsync("/api/admin/maintenance/purge-missing", null);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }
}
