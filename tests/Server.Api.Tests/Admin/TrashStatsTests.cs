using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Admin;

/// <summary>
/// GET /api/admin/trash/stats returns global totals plus a per-user breakdown so
/// an admin can see whose assets fill the trash. The breakdown reuses the same
/// owner grouping the quota calculation already does.
/// </summary>
public sealed class TrashStatsTests : IntegrationTestBase
{
    public TrashStatsTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record TrashUserStat(Guid UserId, string Username, int Items, long Bytes, int ExpiredItems, bool OverQuota);
    private sealed record TrashStatsResponse(
        int TotalItems, long TotalBytes, int ExpiredItems,
        int RetentionDays, int MaxQuotaMb, int OverQuotaUsers, long OverQuotaBytes,
        List<TrashUserStat> PerUser);

    private Task AddTrashedAssetAsync(TestUser owner, long size, string fileName)
        => WithDbContextAsync(async db =>
        {
            db.Assets.Add(new Asset
            {
                FileName = fileName,
                FullPath = $"/assets/users/{owner.Username}/_trash/{fileName}",
                FileSize = size,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = owner.Id,
                DeletedAt = DateTime.UtcNow
            });
            await db.SaveChangesAsync();
        });

    [Fact]
    public async Task Stats_ReportsPerUserBreakdown_WithResolvedUsernames()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();
        var (_, adminClient) = await CreateAuthenticatedUserAsync(role: "Admin");

        await AddTrashedAssetAsync(alice, 1000, "a1.jpg");
        await AddTrashedAssetAsync(alice, 2000, "a2.jpg");
        await AddTrashedAssetAsync(bob, 500, "b1.jpg");

        var stats = await adminClient.GetFromJsonAsync<TrashStatsResponse>("/api/admin/trash/stats");

        Assert.NotNull(stats);
        // Totals still cover everyone.
        Assert.Equal(3, stats!.TotalItems);
        Assert.Equal(3500, stats.TotalBytes);

        var aliceStat = Assert.Single(stats.PerUser, u => u.UserId == alice.Id);
        Assert.Equal(alice.Username, aliceStat.Username);
        Assert.Equal(2, aliceStat.Items);
        Assert.Equal(3000, aliceStat.Bytes);

        var bobStat = Assert.Single(stats.PerUser, u => u.UserId == bob.Id);
        Assert.Equal(bob.Username, bobStat.Username);
        Assert.Equal(1, bobStat.Items);
        Assert.Equal(500, bobStat.Bytes);

        // Largest owner first.
        Assert.Equal(alice.Id, stats.PerUser.First().UserId);
    }
}
