using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.UploadAssets;

/// <summary>
/// /api/assets/sync deduplicates against the caller's own library only: a
/// copy another user has must neither be handed back (its id and path give
/// away their username and folders) nor swallow the file.
/// </summary>
public sealed class SyncAssetDedupTests : IntegrationTestBase
{
    public SyncAssetDedupTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record SyncResp(string Message, Guid? AssetId, string? FullPath);

    /// <summary>The fixture in the user's own Inbox, ready to sync.</summary>
    private string StageSource(TestUser user)
    {
        var dir = Path.Combine(Factory.InternalAssetsPath, "users", user.Username, "Inbox");
        Directory.CreateDirectory(dir);
        var path = Path.Combine(dir, $"{Guid.NewGuid():N}.jpg");
        File.Copy(FixturePaths.WithExif, path);
        return path;
    }

    /// <summary>
    /// A row of <paramref name="owner"/> with the fixture's checksum; its file
    /// is on disk unless <paramref name="onDisk"/> says otherwise.
    /// </summary>
    private async Task<Guid> SeedCopyAsync(TestUser owner, bool onDisk)
    {
        var name = $"{Guid.NewGuid():N}.jpg";
        var virtualPath = $"/assets/users/{owner.Username}/{name}";
        if (onDisk)
        {
            var dir = Path.Combine(Factory.InternalAssetsPath, "users", owner.Username);
            Directory.CreateDirectory(dir);
            File.Copy(FixturePaths.WithExif, Path.Combine(dir, name));
        }
        var checksum = await new FileHashService().CalculateFileHashAsync(FixturePaths.WithExif);
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = name,
                FullPath = virtualPath,
                FileSize = 1,
                Checksum = checksum,
                Type = AssetType.Image,
                Extension = ".jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = owner.Id
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    private static async Task<SyncResp> SyncAsync(HttpClient client, string path)
    {
        var response = await client.PostAsync($"/api/assets/sync?path={Uri.EscapeDataString(path)}", null);
        response.EnsureSuccessStatusCode();
        return (await response.Content.ReadFromJsonAsync<SyncResp>())!;
    }

    [Fact]
    public async Task SameFileAsAnotherUser_IsSyncedIntoTheCallersLibrary()
    {
        var other = await CreateUserAsync();
        var theirs = await SeedCopyAsync(other, onDisk: true);
        var (user, client) = await CreateAuthenticatedUserAsync();

        var body = await SyncAsync(client, StageSource(user));

        Assert.Equal("Asset synced and indexed", body.Message);
        Assert.NotEqual(theirs, body.AssetId);
        Assert.DoesNotContain(other.Username, body.FullPath);
        var stored = await WithDbContextAsync(db =>
            db.Assets.AsNoTracking().SingleAsync(a => a.Id == body.AssetId));
        Assert.Equal(user.Id, stored.OwnerId);
        Assert.StartsWith($"/assets/users/{user.Username}/MobileBackup/", stored.FullPath);
    }

    [Fact]
    public async Task SameFileAsAnotherUsersMissingOne_DoesNotTakeOverTheirRow()
    {
        // Indexing treats a checksum match whose file is gone as a move. The
        // other user's row must stay theirs, pointing where it pointed.
        var other = await CreateUserAsync();
        var theirs = await SeedCopyAsync(other, onDisk: false);
        var (user, client) = await CreateAuthenticatedUserAsync();

        var body = await SyncAsync(client, StageSource(user));

        Assert.NotEqual(theirs, body.AssetId);
        await WithDbContextAsync(async db =>
        {
            var theirRow = await db.Assets.AsNoTracking().SingleAsync(a => a.Id == theirs);
            Assert.Equal(other.Id, theirRow.OwnerId);
            Assert.StartsWith($"/assets/users/{other.Username}/", theirRow.FullPath);
            var mine = await db.Assets.AsNoTracking().SingleAsync(a => a.Id == body.AssetId);
            Assert.Equal(user.Id, mine.OwnerId);
        });
    }

    [Fact]
    public async Task SameFileAsOneTheCallerHas_IsNotCopiedAgain()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var mine = await SeedCopyAsync(user, onDisk: true);

        var body = await SyncAsync(client, StageSource(user));

        Assert.Equal("Asset already exists (same content)", body.Message);
        Assert.Equal(mine, body.AssetId);
    }
}
