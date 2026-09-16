using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Pgvector;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.FaceRecognition;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// The online attach the face worker runs after every photo: the new face goes
/// to the Person whose confirmed face is nearest, if near enough. It used to be
/// one query through UserFaceAssignments that Postgres couldn't serve from the
/// vector index; now it's a kNN over Faces first and the confirmation filter
/// second, and the Person's count is bumped rather than recounted. Same answers
/// as before, on a real pgvector so the index path is what runs.
/// </summary>
public sealed class FaceClusteringOnlineAssignTests : IntegrationTestBase
{
    public FaceClusteringOnlineAssignTests(PhotonneApiFactory factory) : base(factory) { }

    private static Vector Unit(int axis, float lean = 0f)
    {
        // A unit vector along `axis`, optionally leaning slightly into the
        // next axis: lean 0.1 is a cosine distance of ~0.005 from the pure
        // axis, well inside the 0.42 attach threshold; another axis is 1.0.
        var v = new float[512];
        v[axis] = 1f;
        if (lean != 0f) v[(axis + 1) % 512] = lean;
        var norm = MathF.Sqrt(v.Sum(x => x * x));
        for (var i = 0; i < v.Length; i++) v[i] /= norm;
        return new Vector(v);
    }

    private async Task<Asset> SeedImageAsync(Guid ownerId) =>
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
            await db.SaveChangesAsync();
            return asset;
        });

    private async Task<Face> SeedFaceAsync(Guid assetId, Vector embedding) =>
        await WithDbContextAsync(async db =>
        {
            var face = new Face { AssetId = assetId, Confidence = 0.9f, Embedding = embedding };
            db.Faces.Add(face);
            await db.SaveChangesAsync();
            return face;
        });

    /// <summary>A Person of the user with one confirmed face, count already 1.</summary>
    private async Task<Person> SeedPersonWithFaceAsync(Guid userId, Guid faceId) =>
        await WithDbContextAsync(async db =>
        {
            var person = new Person { OwnerId = userId, Name = "Ana", FaceCount = 1, CoverFaceId = faceId };
            db.People.Add(person);
            db.UserFaceAssignments.Add(new UserFaceAssignment
            {
                FaceId = faceId,
                UserId = userId,
                PersonId = person.Id,
                IsManuallyAssigned = true,
            });
            await db.SaveChangesAsync();
            return person;
        });

    private async Task AssignAsync(Guid userId, Guid assetId)
    {
        using var scope = Factory.Services.CreateScope();
        var clustering = scope.ServiceProvider.GetRequiredService<FaceClusteringService>();
        await clustering.AssignNewFacesForUserAsync(userId, assetId, CancellationToken.None);
    }

    [Fact]
    public async Task ANewFaceNearAConfirmedOne_JoinsThatPerson_AndBumpsItsCount()
    {
        var user = await CreateUserAsync();
        var known = await SeedImageAsync(user.Id);
        var knownFace = await SeedFaceAsync(known.Id, Unit(0));
        var person = await SeedPersonWithFaceAsync(user.Id, knownFace.Id);

        var photo = await SeedImageAsync(user.Id);
        var newFace = await SeedFaceAsync(photo.Id, Unit(0, lean: 0.1f));

        await AssignAsync(user.Id, photo.Id);

        await WithDbContextAsync(async db =>
        {
            var assignment = await db.UserFaceAssignments
                .SingleAsync(uf => uf.FaceId == newFace.Id && uf.UserId == user.Id);
            Assert.Equal(person.Id, assignment.PersonId);
            Assert.False(assignment.IsManuallyAssigned);

            var reloaded = await db.People.SingleAsync(p => p.Id == person.Id);
            Assert.Equal(2, reloaded.FaceCount);
        });
    }

    [Fact]
    public async Task ANewFaceFarFromEveryone_StaysAnOrphan_AndCountsDoNotMove()
    {
        var user = await CreateUserAsync();
        var known = await SeedImageAsync(user.Id);
        var knownFace = await SeedFaceAsync(known.Id, Unit(0));
        var person = await SeedPersonWithFaceAsync(user.Id, knownFace.Id);

        var photo = await SeedImageAsync(user.Id);
        var newFace = await SeedFaceAsync(photo.Id, Unit(7));

        await AssignAsync(user.Id, photo.Id);

        await WithDbContextAsync(async db =>
        {
            Assert.False(await db.UserFaceAssignments.AnyAsync(uf => uf.FaceId == newFace.Id));
            Assert.Equal(1, (await db.People.SingleAsync(p => p.Id == person.Id)).FaceCount);
        });
    }

    [Fact]
    public async Task AnotherUsersPerson_IsNotAMatch()
    {
        // The confirmation filter is per user: a face U's neighbour confirmed
        // to *their* Person says nothing about who U thinks it is.
        var owner = await CreateUserAsync();
        var other = await CreateUserAsync();
        var known = await SeedImageAsync(other.Id);
        var knownFace = await SeedFaceAsync(known.Id, Unit(0));
        await SeedPersonWithFaceAsync(other.Id, knownFace.Id);
        // The owner needs at least one Person for the online path to run at all.
        var ownersOwn = await SeedFaceAsync((await SeedImageAsync(owner.Id)).Id, Unit(3));
        await SeedPersonWithFaceAsync(owner.Id, ownersOwn.Id);

        var photo = await SeedImageAsync(owner.Id);
        var newFace = await SeedFaceAsync(photo.Id, Unit(0, lean: 0.1f));

        await AssignAsync(owner.Id, photo.Id);

        await WithDbContextAsync(async db =>
            Assert.False(await db.UserFaceAssignments.AnyAsync(uf => uf.FaceId == newFace.Id && uf.UserId == owner.Id)));
    }
}
