using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Pgvector;
using Photonne.Server.Api.Features.Memories.Generation;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Memories;

/// <summary>
/// The person / scene / object generators. What matters here is what they refuse
/// to build: a memory out of a low-confidence guess, or out of a face somebody
/// else named.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class MemoryGeneratorKindsTests : IntegrationTestBase
{
    public MemoryGeneratorKindsTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<DateTime> LocalTodayAsync()
    {
        using var scope = Factory.Services.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
        var tz = await MetadataTimeZone.ResolveAsync(settings);
        return MetadataTimeZone.LocalNow(tz).Date;
    }

    private async Task GenerateAsync(Guid userId)
    {
        using var scope = Factory.Services.CreateScope();
        var generation = scope.ServiceProvider.GetRequiredService<MemoryGenerationService>();
        await generation.RunForUserAsync(userId, CancellationToken.None);
    }

    private async Task<Guid> CreateFolderAsync(TestUser owner) =>
        await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = $"/assets/users/{owner.Username}", Name = owner.Username };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    /// <summary>Creates assets and returns their ids, newest last.</summary>
    private async Task<List<Guid>> CreateAssetsAsync(
        TestUser owner, Guid folderId, DateTime capturedAt, int count) =>
        await WithDbContextAsync(async db =>
        {
            var ids = new List<Guid>();
            for (var i = 0; i < count; i++)
            {
                var name = $"{Guid.NewGuid():N}.jpg";
                var asset = new Asset
                {
                    FileName = name,
                    FullPath = $"/assets/users/{owner.Username}/{name}",
                    FileSize = 3,
                    Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                    Type = AssetType.Image,
                    Extension = "jpg",
                    FileCreatedAt = capturedAt,
                    FileModifiedAt = capturedAt,
                    CapturedAt = capturedAt.AddMinutes(i),
                    OwnerId = owner.Id,
                    FolderId = folderId,
                };
                db.Assets.Add(asset);
                ids.Add(asset.Id);
            }
            await db.SaveChangesAsync();
            return ids;
        });

    private async Task TagSceneAsync(IEnumerable<Guid> assetIds, string label, float confidence) =>
        await WithDbContextAsync(async db =>
        {
            foreach (var id in assetIds)
                db.AssetClassifiedScenes.Add(new AssetClassifiedScene
                {
                    AssetId = id,
                    Label = label,
                    ClassId = 1,
                    Confidence = confidence,
                    Rank = 1,
                });
            await db.SaveChangesAsync();
        });

    /// <summary>Names a person and attributes the given assets' faces to them, as
    /// <paramref name="identityUser"/> — identity is per-user, so who does the
    /// naming is load-bearing, not incidental.</summary>
    private async Task<Guid> NamePersonAsync(
        TestUser owner, TestUser identityUser, string name, IEnumerable<Guid> assetIds) =>
        await WithDbContextAsync(async db =>
        {
            var ids = assetIds.ToList();
            var person = new Person
            {
                OwnerId = owner.Id,
                Name = name,
                FaceCount = ids.Count,
            };
            db.People.Add(person);
            await db.SaveChangesAsync();

            foreach (var assetId in ids)
            {
                var face = new Face
                {
                    AssetId = assetId,
                    BoundingBoxX = 0.1f, BoundingBoxY = 0.1f,
                    BoundingBoxW = 0.2f, BoundingBoxH = 0.2f,
                    Confidence = 0.99f,
                    // pgvector(512), NOT NULL. Its contents are irrelevant here —
                    // these tests assign identity by hand and never cluster.
                    Embedding = new Vector(new float[512]),
                };
                db.Faces.Add(face);
                db.UserFaceAssignments.Add(new UserFaceAssignment
                {
                    Face = face,
                    UserId = identityUser.Id,
                    PersonId = person.Id,
                    IsManuallyAssigned = true,
                });
            }
            await db.SaveChangesAsync();
            return person.Id;
        });

    [Fact]
    public async Task CuratedScene_IgnoresLowConfidenceGuesses()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var year = (await LocalTodayAsync()).AddYears(-1);

        // The pipeline stores scenes from 0.15 up. At that score the classifier is
        // guessing across 365 classes, and a keepsake built on a guess is worse
        // than no keepsake at all.
        var unsure = await CreateAssetsAsync(user, folder, year, count: 20);
        await TagSceneAsync(unsure, "beach", confidence: 0.20f);

        await GenerateAsync(user.Id);

        var scenes = await WithDbContextAsync(async db => await db.Memories
            .CountAsync(m => m.OwnerId == user.Id && m.Kind == MemoryKind.CuratedScene));

        Assert.Equal(0, scenes);
    }

    [Fact]
    public async Task CuratedScene_BuildsAPerYearMemoryFromConfidentLabels()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var lastYear = (await LocalTodayAsync()).AddYears(-1);

        var beach = await CreateAssetsAsync(user, folder, lastYear, count: 14);
        await TagSceneAsync(beach, "beach", confidence: 0.80f);

        await GenerateAsync(user.Id);

        var memory = await WithDbContextAsync(async db => await db.Memories
            .SingleAsync(m => m.OwnerId == user.Id && m.Kind == MemoryKind.CuratedScene));

        Assert.Equal($"Días de playa de {lastYear.Year}", memory.Title);
        Assert.Equal(14, memory.AssetCount);
        Assert.Equal($"scene:beach:{lastYear.Year}", memory.DedupeKey);
    }

    [Fact]
    public async Task CuratedScene_SplitsTheSameThemeAcrossYears()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        await TagSceneAsync(
            await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 13), "beach", 0.9f);
        await TagSceneAsync(
            await CreateAssetsAsync(user, folder, today.AddYears(-2), count: 13), "beach", 0.9f);

        await GenerateAsync(user.Id);

        var titles = await WithDbContextAsync(async db => await db.Memories
            .Where(m => m.OwnerId == user.Id && m.Kind == MemoryKind.CuratedScene)
            .Select(m => m.Title)
            .ToListAsync());

        // Two stories, not one bucket holding every beach photo ever taken —
        // that would be a smart album wearing a memory's clothes.
        Assert.Equal(2, titles.Count);
        Assert.Contains($"Días de playa de {today.AddYears(-1).Year}", titles);
        Assert.Contains($"Días de playa de {today.AddYears(-2).Year}", titles);
    }

    [Fact]
    public async Task PersonThroughYears_NeedsSeveralYears()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        // Plenty of photos, but all from one summer: that's an event, not a life.
        var oneYear = await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 25);
        await NamePersonAsync(user, user, "Martina", oneYear);

        await GenerateAsync(user.Id);

        var count = await WithDbContextAsync(async db => await db.Memories
            .CountAsync(m => m.OwnerId == user.Id && m.Kind == MemoryKind.PersonThroughYears));

        Assert.Equal(0, count);
    }

    [Fact]
    public async Task PersonThroughYears_BuildsAMemorySpanningTheirYears()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        var seen = new List<Guid>();
        foreach (var yearsAgo in new[] { 1, 2, 3, 4 })
            seen.AddRange(await CreateAssetsAsync(user, folder, today.AddYears(-yearsAgo), count: 6));
        await NamePersonAsync(user, user, "Martina", seen);

        await GenerateAsync(user.Id);

        var memory = await WithDbContextAsync(async db => await db.Memories
            .SingleAsync(m => m.OwnerId == user.Id && m.Kind == MemoryKind.PersonThroughYears));

        Assert.Equal("Martina a lo largo de los años", memory.Title);
        Assert.Equal($"{today.AddYears(-4).Year} – {today.AddYears(-1).Year}", memory.Subtitle);
        Assert.Equal(24, memory.AssetCount);
    }

    [Fact]
    public async Task PersonMemories_BelongOnlyToWhoeverNamedTheFace()
    {
        // The contract that makes memories per-user rather than per-user by
        // convention: detection is shared, naming is private. Alice naming her
        // sister must never put a card in Bob's feed, even over shared photos.
        var alice = await CreateUserAsync();
        var bob = await CreateUserAsync();
        var folder = await CreateFolderAsync(alice);
        var today = await LocalTodayAsync();

        var seen = new List<Guid>();
        foreach (var yearsAgo in new[] { 1, 2, 3 })
            seen.AddRange(await CreateAssetsAsync(alice, folder, today.AddYears(-yearsAgo), count: 8));
        await NamePersonAsync(owner: alice, identityUser: alice, name: "Martina", assetIds: seen);

        await GenerateAsync(alice.Id);
        await GenerateAsync(bob.Id);

        var aliceHas = await WithDbContextAsync(async db => await db.Memories
            .CountAsync(m => m.OwnerId == alice.Id && m.Kind == MemoryKind.PersonThroughYears));
        var bobHas = await WithDbContextAsync(async db => await db.Memories
            .CountAsync(m => m.OwnerId == bob.Id && m.Kind == MemoryKind.PersonThroughYears));

        Assert.Equal(1, aliceHas);
        Assert.Equal(0, bobHas);
    }

    [Fact]
    public async Task PeopleTogether_BuildsAStablePairMemory()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var today = await LocalTodayAsync();

        // 22 so each of them clears MinFaceCount (a person needs a real presence
        // to be considered at all) as well as the pair's own MinTogether.
        var shared = await CreateAssetsAsync(user, folder, today.AddYears(-1), count: 22);
        await NamePersonAsync(user, user, "Martina", shared);
        await NamePersonAsync(user, user, "Joan", shared);

        await GenerateAsync(user.Id);
        // Twice: the pair key is folded from an unordered set, so a run that
        // produced (Joan, Martina) instead of (Martina, Joan) would duplicate.
        await GenerateAsync(user.Id);

        var memories = await WithDbContextAsync(async db => await db.Memories
            .Where(m => m.OwnerId == user.Id && m.Kind == MemoryKind.PeopleTogether)
            .ToListAsync());

        var memory = Assert.Single(memories);
        Assert.Contains("Martina", memory.Title);
        Assert.Contains("Joan", memory.Title);
        Assert.Equal(22, memory.AssetCount);
    }

    [Fact]
    public async Task PetsAndFood_FindsThePetsButNotTheStreetFurniture()
    {
        var user = await CreateUserAsync();
        var folder = await CreateFolderAsync(user);
        var lastYear = (await LocalTodayAsync()).AddYears(-1);

        var dogs = await CreateAssetsAsync(user, folder, lastYear, count: 9);
        var traffic = await CreateAssetsAsync(user, folder, lastYear, count: 30);

        await WithDbContextAsync(async db =>
        {
            foreach (var id in dogs)
                db.AssetDetectedObjects.Add(new AssetDetectedObject
                {
                    AssetId = id, Label = "dog", ClassId = 16, Confidence = 0.9f,
                    BoundingBoxX = 0.1f, BoundingBoxY = 0.1f, BoundingBoxW = 0.5f, BoundingBoxH = 0.5f,
                });
            // COCO knows 80 classes and most of them are this. Nobody wants a
            // card about them, so no theme claims them.
            foreach (var id in traffic)
                db.AssetDetectedObjects.Add(new AssetDetectedObject
                {
                    AssetId = id, Label = "traffic light", ClassId = 9, Confidence = 0.95f,
                    BoundingBoxX = 0.1f, BoundingBoxY = 0.1f, BoundingBoxW = 0.1f, BoundingBoxH = 0.1f,
                });
            await db.SaveChangesAsync();
        });

        await GenerateAsync(user.Id);

        var memories = await WithDbContextAsync(async db => await db.Memories
            .Where(m => m.OwnerId == user.Id && m.Kind == MemoryKind.PetsAndFood)
            .ToListAsync());

        var memory = Assert.Single(memories);
        Assert.Equal($"Tus mascotas en {lastYear.Year}", memory.Title);
        Assert.Equal(9, memory.AssetCount);
    }
}
