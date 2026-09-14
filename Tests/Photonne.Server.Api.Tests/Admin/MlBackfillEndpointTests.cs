using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Admin;

/// <summary>
/// The admin hub's AI rows, from the server side. Everything here guards a way
/// the screen used to lie: a queue that reports progress while it thrashes on
/// retries, a cancel button that deletes a slice the worker puts straight back,
/// a backfill that queues work a disabled model will discard, and a row whose
/// counters say there is nothing left on a library full of unanalysed photos.
///
/// Hosted services are off in tests, so seeded task rows keep the status they
/// were given — no worker races behind the assertions.
/// </summary>
public sealed class MlBackfillEndpointTests : IntegrationTestBase
{
    public MlBackfillEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record PendingCountDto(int Unprocessed, int InQueue, int Completed, int Retrying, int Failed);
    private sealed record BackfillDto(int Enqueued, int Total);
    private sealed record CancelQueueDto(int Deleted, int StillProcessing);
    private sealed record ErrorDto(string Error);

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private async Task<List<Asset>> SeedImagesAsync(Guid ownerId, int count)
    {
        return await WithDbContextAsync(async db =>
        {
            var assets = Enumerable.Range(0, count).Select(i => new Asset
            {
                FileName = $"photo-{i}.jpg",
                FullPath = $"/assets/users/test/{Guid.NewGuid()}.jpg",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = ".jpg",
                OwnerId = ownerId,
                FileCreatedAt = DateTime.UtcNow,
            }).ToList();

            db.Assets.AddRange(assets);
            await db.SaveChangesAsync();
            return assets;
        });
    }

    private Task SeedTaskAsync(Guid assetId, AssetEnrichmentType type, EnrichmentStatus status, DateTime? nextRetryAt) =>
        WithDbContextAsync(async db =>
        {
            db.AssetEnrichmentTasks.Add(new AssetEnrichmentTask
            {
                AssetId = assetId,
                TaskType = type,
                Status = status,
                NextRetryAt = nextRetryAt,
                AttemptCount = status == EnrichmentStatus.Failed ? 3 : 0,
                StartedAt = status == EnrichmentStatus.Processing ? DateTime.UtcNow : null,
            });
            await db.SaveChangesAsync();
        });

    private async Task SetSettingAsync(string key, string value)
    {
        using var scope = Factory.Services.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
        await settings.SetSettingAsync(key, value, Guid.Empty);
    }

    // ─── The disabled-model gate ─────────────────────────────────────────────

    [Fact]
    public async Task Backfill_WithTheModelSwitchedOff_IsRefused()
    {
        // The service returns early without stamping *CompletedAt, so anything
        // queued here comes straight back into the unprocessed pool. Accepting
        // the request is what made the row queue, drain and show no progress,
        // forever.
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        await SeedImagesAsync(user.Id, 3);
        await SetSettingAsync("ObjectDetection.Enabled", "false");

        var response = await client.PostAsJsonAsync(
            "/api/admin/maintenance/object-detection/backfill",
            new { OnlyMissing = true, All = true });

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);

        // The reason has to travel: the client shows the server's own words.
        var body = await response.Content.ReadFromJsonAsync<ErrorDto>();
        Assert.Contains("Ajustes", body!.Error);

        await WithDbContextAsync(async db =>
            Assert.Equal(0, await db.AssetEnrichmentTasks.CountAsync()));
    }

    [Fact]
    public async Task Backfill_WithTheModelOn_Queues()
    {
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        await SeedImagesAsync(user.Id, 3);
        await SetSettingAsync("ObjectDetection.Enabled", "true");

        var response = await client.PostAsJsonAsync(
            "/api/admin/maintenance/object-detection/backfill",
            new { OnlyMissing = true, All = true });

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<BackfillDto>();
        Assert.Equal(3, body!.Enqueued);
    }

    // ─── "Encolar todo" in one request ───────────────────────────────────────

    [Fact]
    public async Task Backfill_WithAll_IgnoresTheBatchSizeAndQueuesEverything()
    {
        // The point of the flag: no client-side loop, so no loop that fails to
        // terminate. One request has to cover a pool bigger than a batch.
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        await SeedImagesAsync(user.Id, 12);

        var response = await client.PostAsJsonAsync(
            "/api/admin/maintenance/scene-classification/backfill",
            new { BatchSize = 5, OnlyMissing = true, All = true });

        var body = await response.Content.ReadFromJsonAsync<BackfillDto>();
        Assert.Equal(12, body!.Enqueued);

        await WithDbContextAsync(async db => Assert.Equal(12, await db.AssetEnrichmentTasks
            .CountAsync(t => t.TaskType == AssetEnrichmentType.SceneClassification
                          && t.Status == EnrichmentStatus.Pending)));
    }

    [Fact]
    public async Task Backfill_WithoutAll_StillTakesOneBatch()
    {
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        await SeedImagesAsync(user.Id, 12);

        var response = await client.PostAsJsonAsync(
            "/api/admin/maintenance/scene-classification/backfill",
            new { BatchSize = 5, OnlyMissing = true });

        var body = await response.Content.ReadFromJsonAsync<BackfillDto>();
        Assert.Equal(5, body!.Enqueued);
        Assert.Equal(12, body.Total);
    }

    [Fact]
    public async Task Backfill_DoesNotDuplicateAnAlreadyQueuedAsset()
    {
        // The bulk path has to keep the single-asset path's promise, or a second
        // run doubles every queue.
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        var assets = await SeedImagesAsync(user.Id, 4);
        await SeedTaskAsync(assets[0].Id, AssetEnrichmentType.TextRecognition, EnrichmentStatus.Pending, null);

        var response = await client.PostAsJsonAsync(
            "/api/admin/maintenance/text-recognition/backfill",
            new { OnlyMissing = true, All = true });

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        await WithDbContextAsync(async db =>
        {
            Assert.Equal(4, await db.AssetEnrichmentTasks
                .CountAsync(t => t.TaskType == AssetEnrichmentType.TextRecognition));
            Assert.Equal(1, await db.AssetEnrichmentTasks
                .CountAsync(t => t.AssetId == assets[0].Id
                              && t.TaskType == AssetEnrichmentType.TextRecognition));
        });
    }

    // ─── Counters that can tell working from thrashing ───────────────────────

    [Fact]
    public async Task PendingCount_SeparatesRetryingFromGaveUp()
    {
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        var assets = await SeedImagesAsync(user.Id, 4);

        await SeedTaskAsync(assets[0].Id, AssetEnrichmentType.FaceRecognition, EnrichmentStatus.Pending, null);
        await SeedTaskAsync(assets[1].Id, AssetEnrichmentType.FaceRecognition, EnrichmentStatus.Failed, DateTime.UtcNow.AddMinutes(5));
        // Retries exhausted: NextRetryAt null. This one is invisible to every
        // future backfill, which is why it has to be reported separately.
        await SeedTaskAsync(assets[2].Id, AssetEnrichmentType.FaceRecognition, EnrichmentStatus.Failed, null);

        var body = await client.GetFromJsonAsync<PendingCountDto>(
            "/api/admin/maintenance/face-recognition/pending-count");

        Assert.Equal(1, body!.InQueue);
        Assert.Equal(1, body.Retrying);
        Assert.Equal(1, body.Failed);
        // assets[3] (untouched) and assets[1] (still retrying) are what a run
        // would pick up; the queued one and the poisoned one are not.
        Assert.Equal(2, body.Unprocessed);
    }

    [Fact]
    public async Task PendingCount_CountsAnAssetOnce_HoweverManyTimesItFailed()
    {
        // A repeatedly-failing asset accumulates one Failed row per attempt.
        // "1.204 fotos con errores" is actionable; "5.117 intentos" isn't.
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        var assets = await SeedImagesAsync(user.Id, 1);

        await SeedTaskAsync(assets[0].Id, AssetEnrichmentType.ImageEmbedding, EnrichmentStatus.Failed, null);
        await SeedTaskAsync(assets[0].Id, AssetEnrichmentType.ImageEmbedding, EnrichmentStatus.Failed, null);
        await SeedTaskAsync(assets[0].Id, AssetEnrichmentType.ImageEmbedding, EnrichmentStatus.Failed, null);

        var body = await client.GetFromJsonAsync<PendingCountDto>(
            "/api/admin/maintenance/image-embedding/pending-count");

        Assert.Equal(1, body!.Failed);
    }

    // ─── Cancel that actually cancels ────────────────────────────────────────

    [Fact]
    public async Task CancelQueue_AlsoDropsTheRowsWaitingToRetry()
    {
        // Leaving them behind is what made this button look inert: the worker
        // walks due Failed rows back into Pending every five minutes, so the
        // queue refilled itself before the admin finished reading the screen.
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        var assets = await SeedImagesAsync(user.Id, 4);

        await SeedTaskAsync(assets[0].Id, AssetEnrichmentType.ObjectDetection, EnrichmentStatus.Pending, null);
        await SeedTaskAsync(assets[1].Id, AssetEnrichmentType.ObjectDetection, EnrichmentStatus.Failed, DateTime.UtcNow.AddMinutes(1));
        await SeedTaskAsync(assets[2].Id, AssetEnrichmentType.ObjectDetection, EnrichmentStatus.Processing, null);
        await SeedTaskAsync(assets[3].Id, AssetEnrichmentType.ObjectDetection, EnrichmentStatus.Failed, null);

        var response = await client.DeleteAsync("/api/admin/maintenance/object-detection/queue");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<CancelQueueDto>();
        Assert.Equal(2, body!.Deleted);
        // Can't abort an in-flight inference — but saying so beats silence.
        Assert.Equal(1, body.StillProcessing);

        await WithDbContextAsync(async db =>
        {
            var left = await db.AssetEnrichmentTasks.AsNoTracking()
                .Where(t => t.TaskType == AssetEnrichmentType.ObjectDetection)
                .Select(t => t.Status)
                .ToListAsync();
            Assert.Equal(2, left.Count);
            Assert.Contains(EnrichmentStatus.Processing, left);
            // The ones that gave up stay: they belong to the failures registry,
            // where they get retried or suppressed one by one.
            Assert.Contains(EnrichmentStatus.Failed, left);
        });
    }

    [Fact]
    public async Task CancelQueue_AnswersForMediaRecognitionToo()
    {
        // The hub offers this row the same cancel button as the ML ones. It
        // answered 404 because the kind was missing from the route table, and
        // the client swallowed that as "nothing happened".
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");
        var (user, _) = await CreateAuthenticatedUserAsync();
        var assets = await SeedImagesAsync(user.Id, 1);
        await SeedTaskAsync(assets[0].Id, AssetEnrichmentType.MediaRecognition, EnrichmentStatus.Pending, null);

        var response = await client.DeleteAsync("/api/admin/maintenance/media-recognition/queue");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<CancelQueueDto>();
        Assert.Equal(1, body!.Deleted);
    }

    [Fact]
    public async Task CancelQueue_RejectsAnUnknownKind()
    {
        var (_, client) = await CreateAuthenticatedUserAsync(role: "Admin");

        var response = await client.DeleteAsync("/api/admin/maintenance/not-a-kind/queue");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }
}
