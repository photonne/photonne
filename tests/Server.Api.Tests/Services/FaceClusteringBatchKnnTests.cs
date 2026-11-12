using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Pgvector;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.FaceRecognition;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// The batch pass on the pgvector kNN path, the one large libraries take. The
/// orphans' ids now go once into a temporary table and every neighbour query
/// reads from it, instead of repeating the whole visibility filter n times:
/// the clusters have to come out the same, built only from the user's orphans.
/// </summary>
public sealed class FaceClusteringBatchKnnTests : IntegrationTestBase
{
    public FaceClusteringBatchKnnTests(PhotonneApiFactory factory) : base(factory) { }

    private static Vector Unit(int axis, float lean = 0f)
    {
        var v = new float[512];
        v[axis] = 1f;
        if (lean != 0f) v[(axis + 1) % 512] = lean;
        var norm = MathF.Sqrt(v.Sum(x => x * x));
        for (var i = 0; i < v.Length; i++) v[i] /= norm;
        return new Vector(v);
    }

    private async Task<Face> SeedFaceAsync(Guid ownerId, Vector embedding) =>
        await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = $"{Guid.NewGuid():N}.jpg",
                FullPath = $"/assets/users/test/{Guid.NewGuid():N}.jpg",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = ".jpg",
                OwnerId = ownerId,
                FileCreatedAt = DateTime.UtcNow,
            };
            db.Assets.Add(asset);
            var face = new Face { AssetId = asset.Id, Confidence = 0.9f, Embedding = embedding };
            db.Faces.Add(face);
            await db.SaveChangesAsync();
            return face;
        });

    private async Task UseKnnPathAsync()
    {
        // Any orphan count above the switchover takes the kNN path.
        using var scope = Factory.Services.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
        await settings.SetSettingAsync(FaceClusteringService.KnnSwitchoverThresholdKey, "0", Guid.Empty);
    }

    private async Task<int> RunBatchAsync(Guid userId)
    {
        using var scope = Factory.Services.CreateScope();
        var clustering = scope.ServiceProvider.GetRequiredService<FaceClusteringService>();
        return await clustering.RunForUserAsync(userId, CancellationToken.None);
    }

    [Fact]
    public async Task KnnBatch_GroupsTheUsersOrphans_AndLeavesEveryoneElseOut()
    {
        await UseKnnPathAsync();
        var user = await CreateUserAsync();
        var stranger = await CreateUserAsync();

        var ana = new[]
        {
            await SeedFaceAsync(user.Id, Unit(0)),
            await SeedFaceAsync(user.Id, Unit(0, lean: 0.1f)),
            await SeedFaceAsync(user.Id, Unit(0, lean: 0.2f)),
        };
        var bea = new[]
        {
            await SeedFaceAsync(user.Id, Unit(5)),
            await SeedFaceAsync(user.Id, Unit(5, lean: 0.1f)),
        };
        var alone = await SeedFaceAsync(user.Id, Unit(9));
        // Right next to Ana, but on a photo the user can't see.
        var hidden = await SeedFaceAsync(stranger.Id, Unit(0, lean: 0.05f));

        var created = await RunBatchAsync(user.Id);

        Assert.Equal(2, created);
        await WithDbContextAsync(async db =>
        {
            var assignments = await db.UserFaceAssignments
                .Where(uf => uf.UserId == user.Id)
                .ToDictionaryAsync(uf => uf.FaceId, uf => uf.PersonId);

            Assert.Equal(5, assignments.Count);
            Assert.Single(ana.Select(f => assignments[f.Id]).Distinct());
            Assert.Single(bea.Select(f => assignments[f.Id]).Distinct());
            Assert.NotEqual(assignments[ana[0].Id], assignments[bea[0].Id]);
            Assert.False(assignments.ContainsKey(alone.Id));
            Assert.False(await db.UserFaceAssignments.AnyAsync(uf => uf.FaceId == hidden.Id));

            var people = await db.People.Where(p => p.OwnerId == user.Id).ToListAsync();
            Assert.Equal(new[] { 2, 3 }, people.Select(p => p.FaceCount).Order());
        });
    }

    [Fact]
    public async Task KnnBatch_RunsAgainOnTheSameConnection_WithoutLeftovers()
    {
        // The temporary table is per connection and the pool hands connections
        // back out: a second pass must start from its own orphans, not from
        // the ids the first one left behind.
        await UseKnnPathAsync();
        var first = await CreateUserAsync();
        var second = await CreateUserAsync();
        await SeedFaceAsync(first.Id, Unit(0));
        await SeedFaceAsync(first.Id, Unit(0, lean: 0.1f));
        var lone = await SeedFaceAsync(second.Id, Unit(0, lean: 0.05f));
        await SeedFaceAsync(second.Id, Unit(7));

        Assert.Equal(1, await RunBatchAsync(first.Id));
        Assert.Equal(0, await RunBatchAsync(second.Id));

        await WithDbContextAsync(async db =>
            Assert.False(await db.UserFaceAssignments.AnyAsync(uf => uf.FaceId == lone.Id)));
    }
}
