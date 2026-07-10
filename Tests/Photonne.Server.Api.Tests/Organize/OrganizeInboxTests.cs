using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Organize;

/// <summary>
/// Exercises the "Para organizar" inbox endpoints:
/// GET /api/organize/inbox (unorganized = still under MobileBackup, newest first)
/// and GET /api/organize/inbox/count (the live badge). The contract: an asset is
/// pending only while it lives under the caller's MobileBackup subtree, so the
/// inbox drains to zero as things are filed away, and the count always agrees
/// with the list.
/// </summary>
public sealed class OrganizeInboxTests : IntegrationTestBase
{
    public OrganizeInboxTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record InboxItem(Guid Id, string FullPath, DateTime FileCreatedAt);
    private sealed record InboxPage(List<InboxItem> Items, bool HasMore, DateTime? NextCursor);
    private sealed record CountBody(int Count);

    private async Task<Guid> CreateFolderAsync(string path)
    {
        return await WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });
    }

    private async Task<Guid> CreateAssetAsync(
        TestUser owner,
        string fileName,
        string fullPath,
        Guid folderId,
        DateTime capturedAt,
        bool isArchived = false,
        bool isMotionPhotoPart = false)
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = fullPath,
                FileSize = 3,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                FolderId = folderId,
                IsArchived = isArchived
            };
            db.Assets.Add(asset);
            if (isMotionPhotoPart)
            {
                db.AssetTags.Add(new AssetTag { Asset = asset, TagType = AssetTagType.MotionPhotoPart });
            }
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    /// <summary>
    /// alice's library: two device subfolders under MobileBackup with visible
    /// assets, plus an archived one and a Live Photo .mov half that must never
    /// count, and one asset already filed into a personal folder (organized).
    /// </summary>
    private async Task<(TestUser Alice, HttpClient Client, Guid MobileAssetId)> SeedAsync()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var root = $"/assets/users/{alice.Username}";
        var pixelFolder = await CreateFolderAsync($"{root}/MobileBackup/Pixel");
        var iphoneFolder = await CreateFolderAsync($"{root}/MobileBackup/iPhone");
        var filedFolder = await CreateFolderAsync($"{root}/Familia");

        var mobileAssetId = await CreateAssetAsync(alice, "pix-new.jpg",
            $"{root}/MobileBackup/Pixel/pix-new.jpg", pixelFolder,
            new DateTime(2026, 2, 20, 12, 0, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "pix-old.jpg",
            $"{root}/MobileBackup/Pixel/pix-old.jpg", pixelFolder,
            new DateTime(2026, 1, 5, 8, 0, 0, DateTimeKind.Utc));
        await CreateAssetAsync(alice, "iph.jpg",
            $"{root}/MobileBackup/iPhone/iph.jpg", iphoneFolder,
            new DateTime(2026, 2, 10, 9, 0, 0, DateTimeKind.Utc));

        // Excluded: archived + motion-part, both under MobileBackup.
        await CreateAssetAsync(alice, "arch.jpg",
            $"{root}/MobileBackup/Pixel/arch.jpg", pixelFolder,
            new DateTime(2026, 2, 15, 10, 0, 0, DateTimeKind.Utc), isArchived: true);
        await CreateAssetAsync(alice, "live.mov",
            $"{root}/MobileBackup/Pixel/live.mov", pixelFolder,
            new DateTime(2026, 2, 15, 10, 0, 0, DateTimeKind.Utc), isMotionPhotoPart: true);

        // Already organized: outside MobileBackup, must not appear.
        await CreateAssetAsync(alice, "familia.jpg",
            $"{root}/Familia/familia.jpg", filedFolder,
            new DateTime(2026, 2, 25, 11, 0, 0, DateTimeKind.Utc));

        return (alice, client, mobileAssetId);
    }

    [Fact]
    public async Task Inbox_ListsOnlyMobileBackupAssets_NewestFirst()
    {
        var (_, client, _) = await SeedAsync();

        var page = await client.GetFromJsonAsync<InboxPage>("/api/organize/inbox?pageSize=100");

        Assert.NotNull(page);
        Assert.Equal(3, page!.Items.Count);
        Assert.All(page.Items, i => Assert.Contains("/MobileBackup/", i.FullPath));
        Assert.DoesNotContain(page.Items, i => i.FullPath.EndsWith("familia.jpg"));
        // Newest first across both device subfolders.
        Assert.Equal(
            page.Items.OrderByDescending(i => i.FileCreatedAt).Select(i => i.Id),
            page.Items.Select(i => i.Id));
    }

    [Fact]
    public async Task Count_AgreesWithList()
    {
        var (_, client, _) = await SeedAsync();

        var count = await client.GetFromJsonAsync<CountBody>("/api/organize/inbox/count");
        var page = await client.GetFromJsonAsync<InboxPage>("/api/organize/inbox?pageSize=100");

        Assert.Equal(page!.Items.Count, count!.Count);
        Assert.Equal(3, count.Count);
    }

    [Fact]
    public async Task Inbox_DrainsWhenAssetIsFiledOutOfMobileBackup()
    {
        var (alice, client, mobileAssetId) = await SeedAsync();
        var filedFolderId = await WithDbContextAsync(async db =>
            (await db.Folders.FirstAsync(f => f.Path.EndsWith("/Familia"))).Id);

        // Simulate the move endpoint's mutation: repoint the asset out of
        // MobileBackup into a filed folder.
        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.FirstAsync(a => a.Id == mobileAssetId);
            asset.FolderId = filedFolderId;
            asset.FullPath = $"/assets/users/{alice.Username}/Familia/pix-new.jpg";
            await db.SaveChangesAsync();
        });

        var count = await client.GetFromJsonAsync<CountBody>("/api/organize/inbox/count");
        var page = await client.GetFromJsonAsync<InboxPage>("/api/organize/inbox?pageSize=100");

        Assert.Equal(2, count!.Count);
        Assert.DoesNotContain(page!.Items, i => i.Id == mobileAssetId);
    }

    [Fact]
    public async Task Inbox_Paginates_WithExclusiveCursor()
    {
        var (_, client, _) = await SeedAsync();

        var first = await client.GetFromJsonAsync<InboxPage>("/api/organize/inbox?pageSize=2");
        Assert.NotNull(first);
        Assert.Equal(2, first!.Items.Count);
        Assert.True(first.HasMore);
        Assert.NotNull(first.NextCursor);

        var second = await client.GetFromJsonAsync<InboxPage>(
            $"/api/organize/inbox?pageSize=2&cursor={Uri.EscapeDataString(first.NextCursor!.Value.ToString("O"))}");
        Assert.NotNull(second);
        Assert.Single(second!.Items);
        Assert.False(second.HasMore);
        // No overlap between pages.
        Assert.Empty(first.Items.Select(i => i.Id).Intersect(second.Items.Select(i => i.Id)));
    }

    [Fact]
    public async Task Inbox_IsPerUser()
    {
        var (_, aliceClient, _) = await SeedAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();

        var bobCount = await bobClient.GetFromJsonAsync<CountBody>("/api/organize/inbox/count");
        var bobPage = await bobClient.GetFromJsonAsync<InboxPage>("/api/organize/inbox?pageSize=100");

        Assert.Equal(0, bobCount!.Count);
        Assert.Empty(bobPage!.Items);
    }
}
