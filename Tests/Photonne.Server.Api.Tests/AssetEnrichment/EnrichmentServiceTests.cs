using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.AssetEnrichment;

/// <summary>
/// Exercises <see cref="EnrichmentService"/> directly via DI to lock in two
/// invariants the worker + UI rely on:
///   • EnqueueAsync dedups against existing Pending/Processing rows.
///   • ResetAndEnqueueAsync clears the full backoff bookkeeping so retries
///     start fresh.
/// </summary>
public sealed class EnrichmentServiceTests : IntegrationTestBase
{
    public EnrichmentServiceTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<Asset> CreateAssetAsync(Guid ownerId)
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = "test.jpg",
                FullPath = $"/assets/users/test/{Guid.NewGuid()}.jpg",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = ".jpg",
                OwnerId = ownerId,
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset;
        });
    }

    [Fact]
    public async Task EnqueueAsync_SameAssetAndType_ReusesExistingPendingRow()
    {
        var (user, _) = await CreateAuthenticatedUserAsync();
        var asset = await CreateAssetAsync(user.Id);

        using var scope = Factory.Services.CreateScope();
        var service = scope.ServiceProvider.GetRequiredService<IEnrichmentService>();

        await service.EnqueueAsync(asset.Id, AssetEnrichmentType.Thumbnails);
        await service.EnqueueAsync(asset.Id, AssetEnrichmentType.Thumbnails);
        await service.EnqueueAsync(asset.Id, AssetEnrichmentType.Thumbnails);

        await WithDbContextAsync(async db =>
        {
            var rows = await db.AssetEnrichmentTasks
                .AsNoTracking()
                .Where(t => t.AssetId == asset.Id && t.TaskType == AssetEnrichmentType.Thumbnails)
                .ToListAsync();
            Assert.Single(rows);
            Assert.Equal(EnrichmentStatus.Pending, rows[0].Status);
        });
    }

    [Fact]
    public async Task EnqueueAsync_DifferentTypes_CreateSeparateRows()
    {
        var (user, _) = await CreateAuthenticatedUserAsync();
        var asset = await CreateAssetAsync(user.Id);

        using var scope = Factory.Services.CreateScope();
        var service = scope.ServiceProvider.GetRequiredService<IEnrichmentService>();

        await service.EnqueueAsync(asset.Id, AssetEnrichmentType.Exif);
        await service.EnqueueAsync(asset.Id, AssetEnrichmentType.Thumbnails);

        await WithDbContextAsync(async db =>
        {
            var types = await db.AssetEnrichmentTasks
                .AsNoTracking()
                .Where(t => t.AssetId == asset.Id)
                .Select(t => t.TaskType)
                .ToListAsync();
            Assert.Equal(2, types.Count);
            Assert.Contains(AssetEnrichmentType.Exif, types);
            Assert.Contains(AssetEnrichmentType.Thumbnails, types);
        });
    }

    [Fact]
    public async Task ResetAndEnqueueAsync_FailedRow_ClearsBookkeeping()
    {
        var (user, _) = await CreateAuthenticatedUserAsync();
        var asset = await CreateAssetAsync(user.Id);

        // Seed a Failed row with non-trivial state (as if the worker had given up).
        Guid taskId = Guid.Empty;
        await WithDbContextAsync(async db =>
        {
            var task = new AssetEnrichmentTask
            {
                AssetId = asset.Id,
                TaskType = AssetEnrichmentType.FaceRecognition,
                Status = EnrichmentStatus.Failed,
                AttemptCount = 5,
                NextRetryAt = DateTime.UtcNow.AddHours(6),
                ErrorMessage = "model unavailable",
                StartedAt = DateTime.UtcNow.AddMinutes(-30),
                CompletedAt = DateTime.UtcNow,
            };
            db.AssetEnrichmentTasks.Add(task);
            await db.SaveChangesAsync();
            taskId = task.Id;
        });

        using var scope = Factory.Services.CreateScope();
        var service = scope.ServiceProvider.GetRequiredService<IEnrichmentService>();
        var ok = await service.ResetAndEnqueueAsync(taskId);
        Assert.True(ok);

        await WithDbContextAsync(async db =>
        {
            var t = await db.AssetEnrichmentTasks.AsNoTracking().FirstAsync(x => x.Id == taskId);
            Assert.Equal(EnrichmentStatus.Pending, t.Status);
            Assert.Equal(0, t.AttemptCount);
            Assert.Null(t.NextRetryAt);
            Assert.Null(t.ErrorMessage);
            Assert.Null(t.StartedAt);
            Assert.Null(t.CompletedAt);
        });
    }

    [Fact]
    public async Task ResetAndEnqueueAsync_UnknownTaskId_ReturnsFalse()
    {
        using var scope = Factory.Services.CreateScope();
        var service = scope.ServiceProvider.GetRequiredService<IEnrichmentService>();

        var ok = await service.ResetAndEnqueueAsync(Guid.NewGuid());
        Assert.False(ok);
    }
}
