using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.AssetEnrichment;

/// <summary>
/// End-to-end checks of the admin failures registry: a metadata sweep failure
/// must land in /api/admin/enrichment/failures with its cause, and the
/// retry/suppress actions must gate what later sweeps pick up.
/// </summary>
public sealed class AdminEnrichmentFailuresTests : IntegrationTestBase
{
    public AdminEnrichmentFailuresTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record FailureDto(
        Guid TaskId,
        Guid AssetId,
        string FileName,
        DateTime FileCreatedAt,
        Guid? OwnerId,
        string? OwnerName,
        string TaskType,
        string Status,
        string? ErrorMessage,
        int AttemptCount,
        bool IsPermanent,
        DateTime? LastAttemptAt);

    private sealed record FailuresResponse(
        IReadOnlyList<FailureDto> Items,
        string? NextCursor,
        int Total,
        IReadOnlyDictionary<string, int> CountsByType);

    private async Task<Asset> SeedMissingFileAssetAsync(Guid ownerId)
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = "broken.jpg",
                FullPath = $"/assets/users/test/{Guid.NewGuid()}.jpg",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = ".jpg",
                OwnerId = ownerId,
                FileCreatedAt = DateTime.UtcNow,
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset;
        });
    }

    /// <summary>Runs the on-demand metadata stream to completion (the response
    /// enumerable only ends when the background worker finishes).</summary>
    private static async Task RunMetadataSweepAsync(HttpClient admin, bool overwrite = false)
    {
        var response = await admin.GetAsync($"/api/assets/metadata/stream?overwrite={overwrite}");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        await response.Content.ReadAsStringAsync();
    }

    [Fact]
    public async Task MetadataSweepFailure_AppearsInAdminRegistry_WithCause()
    {
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var asset = await SeedMissingFileAssetAsync(owner.Id);

        await RunMetadataSweepAsync(admin);

        var response = await admin.GetAsync("/api/admin/enrichment/failures");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<FailuresResponse>();
        Assert.NotNull(body);

        var row = Assert.Single(body!.Items);
        Assert.Equal(asset.Id, row.AssetId);
        Assert.Equal("Exif", row.TaskType);
        Assert.Equal("Failed", row.Status);
        Assert.StartsWith("Fichero no encontrado", row.ErrorMessage);
        Assert.Equal(1, row.AttemptCount);
        Assert.False(row.IsPermanent);
        Assert.Equal(owner.Username, row.OwnerName);
        Assert.True(body.CountsByType.TryGetValue("Exif", out var exifCount) && exifCount == 1);

        // The type filter finds it too (what the notification actionUrl opens).
        var filtered = await admin.GetFromJsonAsync<FailuresResponse>(
            "/api/admin/enrichment/failures?type=Exif");
        Assert.Single(filtered!.Items);
    }

    [Fact]
    public async Task RepeatedSweeps_AccumulateAttempts_UntilPoisonExcludesTheAsset()
    {
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (owner, _) = await CreateAuthenticatedUserAsync();
        await SeedMissingFileAssetAsync(owner.Id);

        // Exhaust the backoff budget: each sweep bumps AttemptCount because the
        // sweep recorder ignores NextRetryAt pacing (a nightly pass is already
        // day-spaced). ComputeNextRetry returns null — permanent — once the
        // count EXCEEDS Delays.Length, so poison lands on attempt N+1.
        var poisonAttempt = EnrichmentBackoff.Delays.Length + 1;
        for (var i = 0; i < poisonAttempt; i++)
            await RunMetadataSweepAsync(admin);

        var body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        var row = Assert.Single(body!.Items);
        Assert.Equal(poisonAttempt, row.AttemptCount);
        Assert.True(row.IsPermanent);

        // A further sweep skips the poisoned asset entirely: no new attempt.
        await RunMetadataSweepAsync(admin);
        body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        Assert.Equal(poisonAttempt, Assert.Single(body!.Items).AttemptCount);

        // ...but an explicit overwrite-all is the manual retry gesture.
        await RunMetadataSweepAsync(admin, overwrite: true);
        body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        Assert.Equal(poisonAttempt + 1, Assert.Single(body!.Items).AttemptCount);
    }

    [Fact]
    public async Task SuppressedAsset_IsSkippedByEverySweep_AndRetryRevivesIt()
    {
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (owner, _) = await CreateAuthenticatedUserAsync();
        await SeedMissingFileAssetAsync(owner.Id);

        await RunMetadataSweepAsync(admin);
        var body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        var row = Assert.Single(body!.Items);

        var suppress = await admin.PostAsync(
            $"/api/admin/enrichment/failures/{row.TaskId}/suppress", null);
        Assert.Equal(HttpStatusCode.OK, suppress.StatusCode);

        // Even overwrite-all leaves a dismissed asset alone.
        await RunMetadataSweepAsync(admin, overwrite: true);
        body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        row = Assert.Single(body!.Items);
        Assert.Equal("Suppressed", row.Status);
        Assert.Equal(1, row.AttemptCount);

        // Admin retry resets the row to Pending, so it drops off the registry.
        var retry = await admin.PostAsync(
            $"/api/admin/enrichment/failures/{row.TaskId}/retry", null);
        Assert.Equal(HttpStatusCode.OK, retry.StatusCode);
        body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        Assert.Empty(body!.Items);
    }

    [Fact]
    public async Task Registry_RequiresAdminRole()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();
        var response = await client.GetAsync("/api/admin/enrichment/failures");
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }
}
