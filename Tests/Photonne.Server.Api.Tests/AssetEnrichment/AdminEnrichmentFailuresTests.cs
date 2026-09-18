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
        DateTime? LastAttemptAt,
        string FailureKind = "",
        string? FailureCode = null);

    private sealed record FailuresResponse(
        IReadOnlyList<FailureDto> Items,
        string? NextCursor,
        int Total,
        IReadOnlyDictionary<string, int> CountsByType,
        IReadOnlyDictionary<string, int>? CountsByKind = null,
        int Retrying = 0,
        int Suppressed = 0);

    private sealed record RetryAllResponse(int Retried);

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

    /// <summary>Leaves every Failed row out of attempts, as the backoff would
    /// after enough runs.</summary>
    private Task ExhaustRetriesAsync() =>
        WithDbContextAsync(async db =>
        {
            await db.AssetEnrichmentTasks
                .Where(t => t.Status == EnrichmentStatus.Failed)
                .ExecuteUpdateAsync(u => u.SetProperty(t => t.NextRetryAt, (DateTime?)null));
        });

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
        // A first failure still has retries scheduled: listed, and said so, but
        // the counters are for what's out of attempts — the number Run Tasks
        // shows as "N con errores".
        Assert.Equal(0, body.Total);
        Assert.Equal(1, body.Retrying);
        Assert.False(body.CountsByType.ContainsKey("Exif"));

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

    // ─── Cause, not just "it failed" ─────────────────────────────────────────

    [Fact]
    public async Task AMissingFile_IsFiledAsTheAssetsOwnProblem()
    {
        // "Failed with the attempts used up" is the same row whether the photo
        // is corrupt or the ML container was down all night, and the two need
        // opposite responses. The kind is what separates them.
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (owner, _) = await CreateAuthenticatedUserAsync();
        await SeedMissingFileAssetAsync(owner.Id);

        await RunMetadataSweepAsync(admin);

        var body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        var row = Assert.Single(body!.Items);
        Assert.Equal("Permanent", row.FailureKind);

        // Counted by cause once its attempts run out.
        await ExhaustRetriesAsync();
        body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        Assert.True(body!.CountsByKind!.TryGetValue("Permanent", out var permanent) && permanent == 1);
    }

    [Fact]
    public async Task TheKindFilter_NarrowsTheList()
    {
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (owner, _) = await CreateAuthenticatedUserAsync();
        await SeedMissingFileAssetAsync(owner.Id);

        await RunMetadataSweepAsync(admin);
        await ExhaustRetriesAsync();

        var permanent = await admin.GetFromJsonAsync<FailuresResponse>(
            "/api/admin/enrichment/failures?kind=Permanent");
        Assert.Single(permanent!.Items);

        var transient = await admin.GetFromJsonAsync<FailuresResponse>(
            "/api/admin/enrichment/failures?kind=Transient");
        Assert.Empty(transient!.Items);

        // Counters stay global so switching filters never hides where the rest is.
        Assert.True(transient.CountsByKind!.TryGetValue("Permanent", out var count) && count == 1);
    }

    [Fact]
    public async Task AnUnknownKind_IsRejectedRatherThanIgnored()
    {
        // Silently returning everything would make a filtered retry-all a very
        // bad surprise.
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");

        var response = await admin.GetAsync("/api/admin/enrichment/failures?kind=Whatever");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task RetryAll_ScopedToACause_LeavesTheRestAlone()
    {
        // The whole point of classifying: retrying the ones whose cause is the
        // file itself only reproduces the same failure and refills the queue.
        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (owner, _) = await CreateAuthenticatedUserAsync();
        await SeedMissingFileAssetAsync(owner.Id);

        await RunMetadataSweepAsync(admin);

        var response = await admin.PostAsync(
            "/api/admin/enrichment/failures/retry-all?kind=Transient", null);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var result = await response.Content.ReadFromJsonAsync<RetryAllResponse>();
        Assert.Equal(0, result!.Retried);

        // Still there, still permanent — not quietly swept back into the queue.
        var body = await admin.GetFromJsonAsync<FailuresResponse>("/api/admin/enrichment/failures");
        Assert.Single(body!.Items);
    }
}
