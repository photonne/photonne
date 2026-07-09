using System.Net;
using System.Net.Http.Json;
using Pgvector;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Albums;

/// <summary>
/// End-to-end coverage of the smart-album rule engine through
/// POST /api/albums/preview (resolver + compiler + visibility). Pins the
/// AND/OR/NOT tree, the person any/all semantics, folder subtree expansion, and
/// the per-user identity gate documented in docs/smart-albums/.
/// </summary>
public sealed class SmartAlbumPreviewTests : IntegrationTestBase
{
    public SmartAlbumPreviewTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record PreviewResponse(int Count, List<Guid> SampleAssetIds);

    private async Task<int> PreviewCountAsync(HttpClient client, object rule)
    {
        var resp = await client.PostAsJsonAsync("/api/albums/preview", new { rule });
        resp.EnsureSuccessStatusCode();
        var body = await resp.Content.ReadFromJsonAsync<PreviewResponse>();
        return body!.Count;
    }

    private async Task<Guid> CreateFolderAsync(string path) =>
        await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private async Task<Guid> CreateAssetAsync(TestUser owner, string relPath, Guid folderId, DateTime capturedAt) =>
        await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = relPath.Split('/').Last(),
                FullPath = $"/assets/users/{owner.Username}/{relPath}",
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

    private async Task AddSceneAsync(Guid assetId, string label) =>
        await WithDbContextAsync(async db =>
        {
            db.Add(new AssetClassifiedScene { AssetId = assetId, Label = label, ClassId = 1, Confidence = 0.9f, Rank = 1 });
            await db.SaveChangesAsync();
        });

    private async Task<Guid> CreatePersonAsync(TestUser owner, string name) =>
        await WithDbContextAsync(async db =>
        {
            var person = new Person { OwnerId = owner.Id, Name = name };
            db.People.Add(person);
            await db.SaveChangesAsync();
            return person.Id;
        });

    private async Task LinkPersonAsync(TestUser owner, Guid assetId, Guid personId) =>
        await WithDbContextAsync(async db =>
        {
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
                FaceId = face.Id, UserId = owner.Id, PersonId = personId, IsManuallyAssigned = true, IsRejected = false
            });
            await db.SaveChangesAsync();
        });

    private sealed record World(
        TestUser Alice, HttpClient Client,
        Guid Abuela, Guid Nieto,
        Guid BeachDog, Guid Both, Guid NietoOnly, Guid MountainTrip,
        Guid RootFolder, Guid ViajesFolder);

    /// <summary>
    /// alice's library: beachDog (scene beach + Abuela), both (Abuela + Nieto),
    /// nietoOnly (Nieto), and mountainTrip (scene mountain) inside a "Viajes"
    /// subfolder. Two named people. Returns everything the tests assert on.
    /// </summary>
    private async Task<World> SeedAsync()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var root = await CreateFolderAsync($"/assets/users/{alice.Username}");
        var viajes = await CreateFolderAsync($"/assets/users/{alice.Username}/Viajes");

        var abuela = await CreatePersonAsync(alice, "Abuela");
        var nieto = await CreatePersonAsync(alice, "Nieto");

        var beachDog = await CreateAssetAsync(alice, "beach-dog.jpg", root, new DateTime(2026, 3, 1, 10, 0, 0, DateTimeKind.Utc));
        var both = await CreateAssetAsync(alice, "both.jpg", root, new DateTime(2026, 3, 2, 10, 0, 0, DateTimeKind.Utc));
        var nietoOnly = await CreateAssetAsync(alice, "nieto.jpg", root, new DateTime(2026, 3, 3, 10, 0, 0, DateTimeKind.Utc));
        var mountainTrip = await CreateAssetAsync(alice, "Viajes/mountain.jpg", viajes, new DateTime(2026, 6, 1, 10, 0, 0, DateTimeKind.Utc));

        await AddSceneAsync(beachDog, "beach");
        await AddSceneAsync(mountainTrip, "mountain");
        await LinkPersonAsync(alice, beachDog, abuela);
        await LinkPersonAsync(alice, both, abuela);
        await LinkPersonAsync(alice, both, nieto);
        await LinkPersonAsync(alice, nietoOnly, nieto);

        return new World(alice, client, abuela, nieto, beachDog, both, nietoOnly, mountainTrip, root, viajes);
    }

    [Fact]
    public async Task Person_MatchAny_ReturnsEitherPerson()
    {
        var w = await SeedAsync();
        var count = await PreviewCountAsync(w.Client, new
        {
            type = "person", match = "any", personIds = new[] { w.Abuela, w.Nieto }
        });
        // beachDog (Abuela), both (Abuela+Nieto), nietoOnly (Nieto)
        Assert.Equal(3, count);
    }

    [Fact]
    public async Task Person_MatchAll_ReturnsOnlyAssetsWithEveryone()
    {
        var w = await SeedAsync();
        var count = await PreviewCountAsync(w.Client, new
        {
            type = "person", match = "all", personIds = new[] { w.Abuela, w.Nieto }
        });
        Assert.Equal(1, count); // only "both"
    }

    [Fact]
    public async Task Or_UnionsBranches()
    {
        var w = await SeedAsync();
        var count = await PreviewCountAsync(w.Client, new
        {
            op = "OR",
            conditions = new object[]
            {
                new { type = "scene", labels = new[] { "beach" } },
                new { type = "scene", labels = new[] { "mountain" } }
            }
        });
        Assert.Equal(2, count); // beachDog + mountainTrip
    }

    [Fact]
    public async Task And_IntersectsBranches()
    {
        var w = await SeedAsync();
        var count = await PreviewCountAsync(w.Client, new
        {
            op = "AND",
            conditions = new object[]
            {
                new { type = "person", match = "any", personIds = new[] { w.Abuela } },
                new { type = "scene", labels = new[] { "beach" } }
            }
        });
        Assert.Equal(1, count); // beachDog only
    }

    [Fact]
    public async Task Not_ExcludesBranch()
    {
        var w = await SeedAsync();
        var count = await PreviewCountAsync(w.Client, new
        {
            type = "not",
            condition = new { type = "scene", labels = new[] { "beach" } }
        });
        Assert.Equal(3, count); // everything except beachDog
    }

    [Fact]
    public async Task Folder_ExpandsSubtree()
    {
        var w = await SeedAsync();
        var count = await PreviewCountAsync(w.Client, new
        {
            type = "folder", folderIds = new[] { w.ViajesFolder }, includeSubfolders = true
        });
        Assert.Equal(1, count); // mountainTrip lives in Viajes
    }

    [Fact]
    public async Task Person_OfOwner_NotResolvableByAnotherViewer()
    {
        var w = await SeedAsync();
        var (_, bob) = await CreateAuthenticatedUserAsync();
        // Preview is owner-anchored to the REQUESTER; bob has no identity for
        // alice's person ids, so the rule resolves to nothing.
        var count = await PreviewCountAsync(bob, new
        {
            type = "person", match = "any", personIds = new[] { w.Abuela, w.Nieto }
        });
        Assert.Equal(0, count);
    }

    [Fact]
    public async Task UnknownConditionType_Returns400()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();
        var resp = await client.PostAsJsonAsync("/api/albums/preview", new { rule = new { type = "wormhole" } });
        Assert.Equal(HttpStatusCode.BadRequest, resp.StatusCode);
    }

    // ── Persistence: create a smart album, then read its (dynamic) assets ────

    private sealed record CreatedAlbum(Guid Id, string Kind);
    private sealed record AssetItem(Guid Id);

    private async Task<CreatedAlbum> CreateSmartAlbumAsync(HttpClient client, string name, object rule)
    {
        var resp = await client.PostAsJsonAsync("/api/albums", new { name, smartRule = rule });
        resp.EnsureSuccessStatusCode();
        return (await resp.Content.ReadFromJsonAsync<CreatedAlbum>())!;
    }

    private static async Task<List<Guid>> AlbumAssetIdsAsync(HttpClient client, Guid albumId)
    {
        var items = await client.GetFromJsonAsync<List<AssetItem>>($"/api/albums/{albumId}/assets");
        return items!.Select(i => i.Id).ToList();
    }

    [Fact]
    public async Task CreateSmartAlbum_PersistsKind_AndResolvesAssetsDynamically()
    {
        var w = await SeedAsync();
        var album = await CreateSmartAlbumAsync(w.Client, "Familia", new
        {
            type = "person", match = "any", personIds = new[] { w.Abuela, w.Nieto }
        });

        Assert.Equal("Smart", album.Kind);
        var ids = await AlbumAssetIdsAsync(w.Client, album.Id);
        Assert.Equal(3, ids.Count); // beachDog, both, nietoOnly
        Assert.Contains(w.BeachDog, ids);
        Assert.Contains(w.Both, ids);
        Assert.Contains(w.NietoOnly, ids);
    }

    [Fact]
    public async Task NewMatchingAsset_AppearsInSmartAlbum_WithoutReediting()
    {
        var w = await SeedAsync();
        var album = await CreateSmartAlbumAsync(w.Client, "Playa", new
        {
            type = "scene", labels = new[] { "beach" }
        });
        Assert.Single(await AlbumAssetIdsAsync(w.Client, album.Id)); // beachDog

        // A brand-new beach photo added after album creation must show up on its own.
        var newBeach = await CreateAssetAsync(w.Alice, "new-beach.jpg", w.RootFolder, new DateTime(2026, 7, 1, 10, 0, 0, DateTimeKind.Utc));
        await AddSceneAsync(newBeach, "beach");

        var ids = await AlbumAssetIdsAsync(w.Client, album.Id);
        Assert.Equal(2, ids.Count);
        Assert.Contains(newBeach, ids);
    }

    [Fact]
    public async Task CreateSmartAlbum_InvalidRule_Returns400()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();
        var resp = await client.PostAsJsonAsync("/api/albums", new
        {
            name = "Roto",
            smartRule = new { type = "wormhole" }
        });
        Assert.Equal(HttpStatusCode.BadRequest, resp.StatusCode);
    }
}
