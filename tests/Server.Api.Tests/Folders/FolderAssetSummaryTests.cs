using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Folders;

/// <summary>
/// What a folder card shows of its photos — the count and the newest four
/// (by scan date) as previews — is the same in the folder list, the folder
/// detail (and its subfolders) and the Utilities tree. Trashed photos don't
/// count, except in a trash folder, which counts only them.
/// </summary>
public sealed class FolderAssetSummaryTests : IntegrationTestBase
{
    public FolderAssetSummaryTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record FolderDto(
        Guid Id,
        int AssetCount,
        Guid? FirstAssetId,
        List<Guid> PreviewAssetIds,
        List<FolderDto>? SubFolders);

    private Task<Guid> CreateFolderAsync(string path, Guid? parentId = null) =>
        WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last(), ParentFolderId = parentId };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private Task<Guid> CreateAssetAsync(TestUser owner, Guid folderId, string folderPath, DateTime scannedAt, bool isDeleted = false) =>
        WithDbContextAsync(async db =>
        {
            var fileName = Guid.NewGuid().ToString("N")[..8] + ".jpg";
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"{folderPath}/{fileName}",
                FileSize = 1,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = scannedAt,
                FileModifiedAt = scannedAt,
                CapturedAt = scannedAt,
                ScannedAt = scannedAt,
                OwnerId = owner.Id,
                FolderId = folderId,
                DeletedAt = isDeleted ? scannedAt : null
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });

    private static FolderDto? Find(IEnumerable<FolderDto> folders, Guid id)
    {
        foreach (var folder in folders)
        {
            if (folder.Id == id) return folder;
            if (folder.SubFolders is { } subs && Find(subs, id) is { } found) return found;
        }
        return null;
    }

    [Fact]
    public async Task CountAndPreviews_AgreeAcrossListDetailAndTree()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var path = $"/assets/users/{alice.Username}/Viajes";
        var childPath = $"{path}/Roma";
        var folderId = await CreateFolderAsync(path);
        var childId = await CreateFolderAsync(childPath, folderId);

        var start = new DateTime(2026, 1, 1, 0, 0, 0, DateTimeKind.Utc);
        // Six live photos, oldest first; the newest one is trashed and doesn't count.
        var live = new List<Guid>();
        for (var i = 0; i < 6; i++)
            live.Add(await CreateAssetAsync(alice, folderId, path, start.AddDays(i)));
        await CreateAssetAsync(alice, folderId, path, start.AddDays(10), isDeleted: true);
        var child = await CreateAssetAsync(alice, childId, childPath, start);

        var newestFour = Enumerable.Reverse(live).Take(4).ToList();

        void AssertParent(FolderDto? dto)
        {
            Assert.NotNull(dto);
            Assert.Equal(newestFour, dto!.PreviewAssetIds);
            Assert.Equal(newestFour[0], dto.FirstAssetId);
        }

        void AssertChild(FolderDto? dto)
        {
            Assert.NotNull(dto);
            Assert.Equal(1, dto!.AssetCount);
            Assert.Equal(new[] { child }, dto.PreviewAssetIds);
            Assert.Equal(child, dto.FirstAssetId);
        }

        var list = await client.GetFromJsonAsync<List<FolderDto>>("/api/folders");
        AssertParent(Find(list!, folderId));
        AssertChild(Find(list!, childId));

        var detail = await client.GetFromJsonAsync<FolderDto>($"/api/folders/{folderId}");
        AssertParent(detail);
        Assert.Equal(6, detail!.AssetCount);
        AssertChild(Assert.Single(detail.SubFolders!));

        foreach (var url in new[] { "/api/folders/tree", "/api/utilities/folders/tree" })
        {
            var tree = await client.GetFromJsonAsync<List<FolderDto>>(url);
            AssertParent(Find(tree!, folderId));
            AssertChild(Find(tree!, childId));
        }
    }

    [Fact]
    public async Task EmptyFolder_HasNoCountAndNoPreviews()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var folderId = await CreateFolderAsync($"/assets/users/{alice.Username}/Vacía");

        var detail = await client.GetFromJsonAsync<FolderDto>($"/api/folders/{folderId}");

        Assert.Equal(0, detail!.AssetCount);
        Assert.Null(detail.FirstAssetId);
        Assert.Empty(detail.PreviewAssetIds);
    }
}
