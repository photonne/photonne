using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Maintenance;

/// <summary>
/// End-to-end checks of the indexing-coverage task: a physical file without an
/// asset row must surface as unindexed, an indexed one must not, and the
/// persisted snapshot must be readable from the admin endpoint. Counts are
/// asserted by membership, not exact totals — the factory's asset root is
/// shared across the test collection and other tests leave files behind.
/// </summary>
public sealed class IndexingCoverageTests : IntegrationTestBase
{
    public IndexingCoverageTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record CoverageResponse(
        bool HasResult,
        DateTime? VerifiedAtUtc,
        int TotalFiles,
        int Indexed,
        int Unsupported,
        int Unindexed,
        IReadOnlyList<string> UnindexedPaths,
        bool UnindexedTruncated,
        int OfflineLibraries);

    private string WritePhysicalFile(string relativePath, byte[]? content = null)
    {
        var physical = Path.Combine(Factory.InternalAssetsPath,
            relativePath.Replace('/', Path.DirectorySeparatorChar));
        Directory.CreateDirectory(Path.GetDirectoryName(physical)!);
        File.WriteAllBytes(physical, content ?? new byte[] { 1, 2, 3 });
        return "/assets/" + relativePath.Replace('\\', '/');
    }

    [Fact]
    public async Task CoverageTask_ClassifiesIndexedUnindexedAndUnsupported()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");

        var marker = Guid.NewGuid().ToString("N")[..8];
        var indexedPath = WritePhysicalFile($"users/cov{marker}/indexed-{marker}.jpg");
        var orphanPath = WritePhysicalFile($"users/cov{marker}/orphan-{marker}.jpg");
        WritePhysicalFile($"users/cov{marker}/notes-{marker}.txt");

        await WithDbContextAsync(async db =>
        {
            db.Assets.Add(new Asset
            {
                FileName = $"indexed-{marker}.jpg",
                FullPath = indexedPath,
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = ".jpg",
                OwnerId = owner.Id,
                FileCreatedAt = DateTime.UtcNow,
            });
            await db.SaveChangesAsync();
        });

        // The generic maintenance stream runs the kind; the response enumerable
        // only completes when the background worker finishes.
        var stream = await admin.GetAsync("/api/admin/maintenance/indexing-coverage/stream");
        Assert.Equal(HttpStatusCode.OK, stream.StatusCode);
        var ndjson = await stream.Content.ReadAsStringAsync();
        Assert.Contains("sin indexar", ndjson);

        var coverage = await admin.GetFromJsonAsync<CoverageResponse>("/api/admin/indexing-coverage");
        Assert.NotNull(coverage);
        Assert.True(coverage!.HasResult);
        Assert.NotNull(coverage.VerifiedAtUtc);
        Assert.True(coverage.Unindexed >= 1);
        Assert.True(coverage.Unsupported >= 1);
        Assert.Contains(orphanPath, coverage.UnindexedPaths);
        Assert.DoesNotContain(indexedPath, coverage.UnindexedPaths);
        Assert.Equal(0, coverage.OfflineLibraries);
        Assert.True(coverage.TotalFiles >= 3);
    }

    [Fact]
    public async Task CoverageEndpoint_ReportsNoResult_BeforeFirstRun()
    {
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        var coverage = await admin.GetFromJsonAsync<CoverageResponse>("/api/admin/indexing-coverage");
        Assert.NotNull(coverage);
        Assert.False(coverage!.HasResult);
        Assert.Null(coverage.VerifiedAtUtc);
    }

    [Fact]
    public async Task CoverageEndpoint_RequiresAdminRole()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();
        var response = await client.GetAsync("/api/admin/indexing-coverage");
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }
}
