using System.Net.Http.Json;
using Pgvector;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Search;

/// <summary>
/// Pins /api/assets/search across the dimensions delegated to the shared
/// AssetQueryBuilder (docs/smart-albums/). The endpoint used to inline every
/// filter; the builder extraction must stay behaviour-preserving, and smart
/// albums will reuse the same builder, so any divergence in text/date/object/
/// scene/person matching must fail here.
/// </summary>
public sealed class SearchFilterTests : IntegrationTestBase
{
    public SearchFilterTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record SearchItem(Guid Id);
    private sealed record SearchPage(List<SearchItem> Items, bool HasMore);

    private async Task<Guid> CreateFolderAsync(string path) =>
        await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private async Task<Guid> CreateAssetAsync(TestUser owner, string fileName, Guid folderId, DateTime capturedAt) =>
        await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"/assets/users/{owner.Username}/{fileName}",
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                FolderId = folderId
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });

    private async Task AddObjectAsync(Guid assetId, string label) =>
        await WithDbContextAsync(async db =>
        {
            db.Add(new AssetDetectedObject { AssetId = assetId, Label = label, ClassId = 1, Confidence = 0.9f });
            await db.SaveChangesAsync();
        });

    private async Task AddSceneAsync(Guid assetId, string label) =>
        await WithDbContextAsync(async db =>
        {
            db.Add(new AssetClassifiedScene { AssetId = assetId, Label = label, ClassId = 1, Confidence = 0.9f, Rank = 1 });
            await db.SaveChangesAsync();
        });

    /// <summary>Names a person owned by <paramref name="owner"/> and links a
    /// non-rejected face on <paramref name="assetId"/> to it (per-user identity).</summary>
    private async Task<Guid> TagPersonAsync(TestUser owner, Guid assetId, string name) =>
        await WithDbContextAsync(async db =>
        {
            var person = new Person { OwnerId = owner.Id, Name = name };
            db.People.Add(person);
            var face = new Face
            {
                AssetId = assetId,
                BoundingBoxX = 0.1f, BoundingBoxY = 0.1f, BoundingBoxW = 0.2f, BoundingBoxH = 0.2f,
                Confidence = 0.99f,
                Embedding = new Vector(new float[512])
            };
            db.Faces.Add(face);
            await db.SaveChangesAsync();
            db.UserFaceAssignments.Add(new UserFaceAssignment
            {
                FaceId = face.Id,
                UserId = owner.Id,
                PersonId = person.Id,
                IsManuallyAssigned = true,
                IsRejected = false
            });
            await db.SaveChangesAsync();
            return person.Id;
        });

    private sealed record Library(HttpClient Client, Guid BeachDog, Guid Mountain, Guid Plain, Guid PersonId);

    /// <summary>
    /// Seeds three assets: a March "beach-dog" (object dog, scene beach, a named
    /// person), a June "mountain" (scene mountain), and a January "plain" asset
    /// with no annotations. Returns the authed client and the ids.
    /// </summary>
    private async Task<Library> SeedAsync()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}");

        var beachDog = await CreateAssetAsync(alice, "beach-dog.jpg", folderId, new DateTime(2026, 3, 1, 10, 0, 0, DateTimeKind.Utc));
        var mountain = await CreateAssetAsync(alice, "mountain.jpg", folderId, new DateTime(2026, 6, 1, 10, 0, 0, DateTimeKind.Utc));
        var plain = await CreateAssetAsync(alice, "plain.jpg", folderId, new DateTime(2026, 1, 1, 10, 0, 0, DateTimeKind.Utc));

        await AddObjectAsync(beachDog, "dog");
        await AddSceneAsync(beachDog, "beach");
        await AddSceneAsync(mountain, "mountain");
        var personId = await TagPersonAsync(alice, beachDog, "Abuela");

        return new Library(client, beachDog, mountain, plain, personId);
    }

    private static async Task<List<Guid>> IdsAsync(HttpClient client, string query)
    {
        var page = await client.GetFromJsonAsync<SearchPage>($"/api/assets/search?{query}");
        return page!.Items.Select(i => i.Id).ToList();
    }

    [Fact]
    public async Task Text_MatchesFileName()
    {
        var lib = await SeedAsync();
        Assert.Equal(new[] { lib.BeachDog }, await IdsAsync(lib.Client, "q=beach-dog"));
    }

    [Fact]
    public async Task DateRange_FiltersByCapturedAt()
    {
        var lib = await SeedAsync();
        Assert.Equal(new[] { lib.Mountain }, await IdsAsync(lib.Client, "from=2026-05-01"));
    }

    [Fact]
    public async Task ObjectLabel_MatchesDetection()
    {
        var lib = await SeedAsync();
        Assert.Equal(new[] { lib.BeachDog }, await IdsAsync(lib.Client, "objectLabel=dog"));
    }

    [Fact]
    public async Task SceneLabel_MatchesClassification()
    {
        var lib = await SeedAsync();
        Assert.Equal(new[] { lib.Mountain }, await IdsAsync(lib.Client, "sceneLabel=mountain"));
    }

    [Fact]
    public async Task PersonId_MatchesNonRejectedFaceForRequester()
    {
        var lib = await SeedAsync();
        Assert.Equal(new[] { lib.BeachDog }, await IdsAsync(lib.Client, $"personId={lib.PersonId}"));
    }

    [Fact]
    public async Task PersonId_OfAnotherUser_ReturnsNothing()
    {
        var lib = await SeedAsync();
        // A different user's client must not resolve alice's person id.
        var (_, otherClient) = await CreateAuthenticatedUserAsync();
        Assert.Empty(await IdsAsync(otherClient, $"personId={lib.PersonId}"));
    }
}
