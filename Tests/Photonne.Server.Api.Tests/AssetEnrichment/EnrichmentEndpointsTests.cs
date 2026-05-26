using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.AssetEnrichment;

/// <summary>
/// End-to-end checks of the four endpoints under /api/assets/.../enrichment.
/// Tests run with IHostedService disabled, so all task state is deterministic:
/// rows we seed as Pending stay Pending, rows we seed as Failed stay Failed.
/// </summary>
public sealed class EnrichmentEndpointsTests : IntegrationTestBase
{
    public EnrichmentEndpointsTests(PhotonneApiFactory factory) : base(factory) { }

    // ─── DTOs that mirror the endpoint responses ─────────────────────────────

    private sealed record EnrichmentTaskDto(
        string TaskType,
        string Status,
        string? ErrorMessage,
        int AttemptCount,
        DateTime CreatedAt,
        DateTime? StartedAt,
        DateTime? CompletedAt,
        DateTime? NextRetryAt);

    private sealed record AssetEnrichmentResponse(
        Guid AssetId,
        string FileName,
        IReadOnlyList<EnrichmentTaskDto> Tasks);

    private sealed record PendingAssetDto(
        Guid AssetId,
        string FileName,
        DateTime FileCreatedAt,
        int Pending,
        int Processing,
        int Failed,
        IReadOnlyList<string> FailedTaskTypes);

    private sealed record PendingResponse(
        IReadOnlyList<PendingAssetDto> Items,
        DateTime? NextCursor,
        int TotalAssets);

    private sealed record RetryAllResponse(Guid AssetId, int Retried);

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private async Task<Asset> SeedAssetWithTasksAsync(
        Guid ownerId,
        string fileName,
        (AssetEnrichmentType type, EnrichmentStatus status, string? error)[] tasks,
        DateTime? fileCreatedAt = null)
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"/assets/users/test/{Guid.NewGuid()}.jpg",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = ".jpg",
                OwnerId = ownerId,
                FileCreatedAt = fileCreatedAt ?? DateTime.UtcNow,
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();

            foreach (var (type, status, error) in tasks)
            {
                db.AssetEnrichmentTasks.Add(new AssetEnrichmentTask
                {
                    AssetId = asset.Id,
                    TaskType = type,
                    Status = status,
                    ErrorMessage = error,
                    AttemptCount = status == EnrichmentStatus.Failed ? 3 : 0,
                });
            }
            await db.SaveChangesAsync();
            return asset;
        });
    }

    // ─── GET /api/assets/{id}/enrichment ─────────────────────────────────────

    [Fact]
    public async Task Get_ReturnsTasks_ForOwner()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var asset = await SeedAssetWithTasksAsync(user.Id, "photo.jpg", new[]
        {
            (AssetEnrichmentType.Exif, EnrichmentStatus.Completed, (string?)null),
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Failed, (string?)"ffmpeg crashed"),
        });

        var response = await client.GetAsync($"/api/assets/{asset.Id}/enrichment");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<AssetEnrichmentResponse>();
        Assert.NotNull(body);
        Assert.Equal(asset.Id, body!.AssetId);
        Assert.Equal(2, body.Tasks.Count);
        Assert.Contains(body.Tasks, t => t.TaskType == "Exif" && t.Status == "Completed");
        Assert.Contains(body.Tasks, t =>
            t.TaskType == "Thumbnails"
            && t.Status == "Failed"
            && t.ErrorMessage == "ffmpeg crashed");
    }

    [Fact]
    public async Task Get_ReturnsForbidden_ForOtherUser()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (_, intruderClient) = await CreateAuthenticatedUserAsync();
        var asset = await SeedAssetWithTasksAsync(owner.Id, "photo.jpg", new[]
        {
            (AssetEnrichmentType.Exif, EnrichmentStatus.Completed, (string?)null),
        });

        var response = await intruderClient.GetAsync($"/api/assets/{asset.Id}/enrichment");
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task Get_ReturnsNotFound_ForUnknownAsset()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();
        var response = await client.GetAsync($"/api/assets/{Guid.NewGuid()}/enrichment");
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    // ─── POST /api/assets/{id}/enrichment/retry ──────────────────────────────

    [Fact]
    public async Task Retry_ExistingFailedTask_ResetsToPending()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var asset = await SeedAssetWithTasksAsync(user.Id, "photo.jpg", new[]
        {
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Failed, (string?)"boom"),
        });

        var response = await client.PostAsync(
            $"/api/assets/{asset.Id}/enrichment/retry?taskType=Thumbnails",
            content: null);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        await WithDbContextAsync(async db =>
        {
            var task = await db.AssetEnrichmentTasks.AsNoTracking()
                .FirstAsync(t => t.AssetId == asset.Id && t.TaskType == AssetEnrichmentType.Thumbnails);
            Assert.Equal(EnrichmentStatus.Pending, task.Status);
            Assert.Equal(0, task.AttemptCount);
            Assert.Null(task.ErrorMessage);
        });
    }

    [Fact]
    public async Task Retry_NoExistingTask_CreatesPendingRow()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var asset = await SeedAssetWithTasksAsync(user.Id, "photo.jpg", tasks: Array.Empty<(AssetEnrichmentType, EnrichmentStatus, string?)>());

        var response = await client.PostAsync(
            $"/api/assets/{asset.Id}/enrichment/retry?taskType=Exif",
            content: null);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        await WithDbContextAsync(async db =>
        {
            var task = await db.AssetEnrichmentTasks.AsNoTracking()
                .FirstAsync(t => t.AssetId == asset.Id && t.TaskType == AssetEnrichmentType.Exif);
            Assert.Equal(EnrichmentStatus.Pending, task.Status);
        });
    }

    [Fact]
    public async Task Retry_UnknownTaskType_ReturnsBadRequest()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var asset = await SeedAssetWithTasksAsync(user.Id, "photo.jpg", tasks: Array.Empty<(AssetEnrichmentType, EnrichmentStatus, string?)>());

        var response = await client.PostAsync(
            $"/api/assets/{asset.Id}/enrichment/retry?taskType=Bogus",
            content: null);
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task Retry_OtherUser_ReturnsForbidden()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (_, intruderClient) = await CreateAuthenticatedUserAsync();
        var asset = await SeedAssetWithTasksAsync(owner.Id, "photo.jpg", new[]
        {
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Failed, (string?)"boom"),
        });

        var response = await intruderClient.PostAsync(
            $"/api/assets/{asset.Id}/enrichment/retry?taskType=Thumbnails",
            content: null);
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    // ─── POST /api/assets/{id}/enrichment/retry-all ──────────────────────────

    [Fact]
    public async Task RetryAll_OnlyTouchesFailedTasks()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var asset = await SeedAssetWithTasksAsync(user.Id, "photo.jpg", new[]
        {
            (AssetEnrichmentType.Exif, EnrichmentStatus.Completed, (string?)null),
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Failed, (string?)"boom"),
            (AssetEnrichmentType.MediaRecognition, EnrichmentStatus.Failed, (string?)"kaboom"),
            (AssetEnrichmentType.FaceRecognition, EnrichmentStatus.Pending, (string?)null),
        });

        var response = await client.PostAsync(
            $"/api/assets/{asset.Id}/enrichment/retry-all",
            content: null);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<RetryAllResponse>();
        Assert.NotNull(body);
        Assert.Equal(2, body!.Retried);

        await WithDbContextAsync(async db =>
        {
            var rows = await db.AssetEnrichmentTasks
                .AsNoTracking()
                .Where(t => t.AssetId == asset.Id)
                .OrderBy(t => t.TaskType)
                .ToListAsync();

            var byType = rows.ToDictionary(t => t.TaskType);
            // Completed left alone.
            Assert.Equal(EnrichmentStatus.Completed, byType[AssetEnrichmentType.Exif].Status);
            // Failed → reset to Pending with cleared bookkeeping.
            Assert.Equal(EnrichmentStatus.Pending, byType[AssetEnrichmentType.Thumbnails].Status);
            Assert.Null(byType[AssetEnrichmentType.Thumbnails].ErrorMessage);
            Assert.Equal(EnrichmentStatus.Pending, byType[AssetEnrichmentType.MediaRecognition].Status);
            // Already-Pending row left alone.
            Assert.Equal(EnrichmentStatus.Pending, byType[AssetEnrichmentType.FaceRecognition].Status);
        });
    }

    [Fact]
    public async Task RetryAll_OtherUser_ReturnsForbidden()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (_, intruderClient) = await CreateAuthenticatedUserAsync();
        var asset = await SeedAssetWithTasksAsync(owner.Id, "photo.jpg", new[]
        {
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Failed, (string?)"boom"),
        });

        var response = await intruderClient.PostAsync(
            $"/api/assets/{asset.Id}/enrichment/retry-all",
            content: null);
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    // ─── GET /api/assets/enrichment/pending ──────────────────────────────────

    [Fact]
    public async Task ListPending_ReturnsAssetsWithPendingOrFailed_OmitsFullyCompleted()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var pendingAsset = await SeedAssetWithTasksAsync(user.Id, "pending.jpg", new[]
        {
            (AssetEnrichmentType.Exif, EnrichmentStatus.Completed, (string?)null),
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Pending, (string?)null),
        });
        var failedAsset = await SeedAssetWithTasksAsync(user.Id, "failed.jpg", new[]
        {
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Failed, (string?)"ffmpeg crashed"),
            (AssetEnrichmentType.FaceRecognition, EnrichmentStatus.Failed, (string?)"no model"),
        });
        // Fully completed asset — must NOT appear in the listing.
        await SeedAssetWithTasksAsync(user.Id, "done.jpg", new[]
        {
            (AssetEnrichmentType.Exif, EnrichmentStatus.Completed, (string?)null),
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Completed, (string?)null),
        });

        var response = await client.GetAsync("/api/assets/enrichment/pending?pageSize=50");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<PendingResponse>();

        Assert.NotNull(body);
        Assert.Equal(2, body!.TotalAssets);
        Assert.Equal(2, body.Items.Count);

        var pendingItem = body.Items.First(i => i.AssetId == pendingAsset.Id);
        Assert.Equal(1, pendingItem.Pending);
        Assert.Equal(0, pendingItem.Failed);
        Assert.Empty(pendingItem.FailedTaskTypes);

        var failedItem = body.Items.First(i => i.AssetId == failedAsset.Id);
        Assert.Equal(0, failedItem.Pending);
        Assert.Equal(2, failedItem.Failed);
        Assert.Equal(2, failedItem.FailedTaskTypes.Count);
        Assert.Contains("Thumbnails", failedItem.FailedTaskTypes);
        Assert.Contains("FaceRecognition", failedItem.FailedTaskTypes);
    }

    [Fact]
    public async Task ListPending_DoesNotLeakOtherUsersAssets()
    {
        var (owner, _) = await CreateAuthenticatedUserAsync();
        var (_, intruderClient) = await CreateAuthenticatedUserAsync();
        await SeedAssetWithTasksAsync(owner.Id, "secret.jpg", new[]
        {
            (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Failed, (string?)"boom"),
        });

        var response = await intruderClient.GetAsync("/api/assets/enrichment/pending?pageSize=50");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<PendingResponse>();
        Assert.NotNull(body);
        Assert.Equal(0, body!.TotalAssets);
        Assert.Empty(body.Items);
    }

    [Fact]
    public async Task ListPending_Paginates_ByFileCreatedAtCursor()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var baseDate = DateTime.UtcNow.AddDays(-10);
        var assetIds = new List<Guid>();
        for (var i = 0; i < 4; i++)
        {
            var a = await SeedAssetWithTasksAsync(
                user.Id, $"asset-{i}.jpg",
                new[] { (AssetEnrichmentType.Thumbnails, EnrichmentStatus.Failed, (string?)"err") },
                fileCreatedAt: baseDate.AddDays(i));
            assetIds.Add(a.Id);
        }

        // First page (newest first).
        var firstResponse = await client.GetAsync("/api/assets/enrichment/pending?pageSize=2");
        var firstPage = await firstResponse.Content.ReadFromJsonAsync<PendingResponse>();
        Assert.NotNull(firstPage);
        Assert.Equal(4, firstPage!.TotalAssets);
        Assert.Equal(2, firstPage.Items.Count);
        Assert.NotNull(firstPage.NextCursor);
        // Sorted desc by FileCreatedAt: newest two are indexes 3 and 2.
        Assert.Equal(assetIds[3], firstPage.Items[0].AssetId);
        Assert.Equal(assetIds[2], firstPage.Items[1].AssetId);

        // Second page using the cursor.
        var cursor = firstPage.NextCursor!.Value.ToString("o");
        var secondResponse = await client.GetAsync(
            $"/api/assets/enrichment/pending?pageSize=2&cursor={Uri.EscapeDataString(cursor)}");
        var secondPage = await secondResponse.Content.ReadFromJsonAsync<PendingResponse>();
        Assert.NotNull(secondPage);
        Assert.Equal(2, secondPage!.Items.Count);
        Assert.Equal(assetIds[1], secondPage.Items[0].AssetId);
        Assert.Equal(assetIds[0], secondPage.Items[1].AssetId);
        Assert.Null(secondPage.NextCursor); // no more pages.
    }
}
