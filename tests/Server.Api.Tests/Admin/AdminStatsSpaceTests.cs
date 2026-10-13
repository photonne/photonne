using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Admin;

/// <summary>
/// GET /api/admin/stats splits every byte by space (personal, shared folders,
/// external libraries) so the dashboard can tell a user's own photos apart from
/// the shares an admin fills and from another system's disk.
/// </summary>
public sealed class AdminStatsSpaceTests : IntegrationTestBase
{
    public AdminStatsSpaceTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record AdminUserUsage(
        Guid UserId, long PhotoBytes, long VideoBytes,
        long PersonalBytes, long SharedBytes, long LibraryBytes);
    private sealed record AdminStatsResponse(
        long TotalBytes, long PersonalBytes, long SharedBytes, long LibraryBytes,
        List<AdminUserUsage> Users);

    private Task AddAssetAsync(Guid ownerId, long size, string fullPath, Guid? libraryId = null)
        => WithDbContextAsync(async db =>
        {
            db.Assets.Add(new Asset
            {
                FileName = Path.GetFileName(fullPath),
                FullPath = fullPath,
                FileSize = size,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = ownerId,
                ExternalLibraryId = libraryId
            });
            await db.SaveChangesAsync();
        });

    [Fact]
    public async Task Stats_SplitBytesBySpace_PerUserAndInTotal()
    {
        var (admin, adminClient) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var libraryId = await WithDbContextAsync(async db =>
        {
            var library = new ExternalLibrary { Name = "NAS", Path = "/data/nas", OwnerId = admin.Id };
            db.ExternalLibraries.Add(library);
            await db.SaveChangesAsync();
            return library.Id;
        });

        await AddAssetAsync(admin.Id, 1_000, $"/assets/users/{admin.Username}/a.jpg");
        await AddAssetAsync(admin.Id, 4_000, "/assets/shared/family/b.jpg");
        await AddAssetAsync(admin.Id, 16_000, "/data/nas/c.jpg", libraryId);
        await AddAssetAsync(alice.Id, 300, $"/assets/users/{alice.Username}/d.jpg");

        var stats = await adminClient.GetFromJsonAsync<AdminStatsResponse>("/api/admin/stats");

        Assert.NotNull(stats);
        Assert.Equal(21_300L, stats!.TotalBytes);
        Assert.Equal(1_300L, stats.PersonalBytes);
        Assert.Equal(4_000L, stats.SharedBytes);
        Assert.Equal(16_000L, stats.LibraryBytes);

        var adminUsage = Assert.Single(stats.Users, u => u.UserId == admin.Id);
        Assert.Equal(1_000L, adminUsage.PersonalBytes);
        Assert.Equal(4_000L, adminUsage.SharedBytes);
        Assert.Equal(16_000L, adminUsage.LibraryBytes);
        Assert.Equal(21_000L, adminUsage.PhotoBytes + adminUsage.VideoBytes);

        var aliceUsage = Assert.Single(stats.Users, u => u.UserId == alice.Id);
        Assert.Equal(300L, aliceUsage.PersonalBytes);
        Assert.Equal(0L, aliceUsage.SharedBytes + aliceUsage.LibraryBytes);
    }
}
