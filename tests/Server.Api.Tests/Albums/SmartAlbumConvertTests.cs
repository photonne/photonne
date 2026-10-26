using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Albums;

/// <summary>
/// A smart album can be frozen into a manual one that keeps its photos, and a
/// date rule can be a rolling window ("the last 30 days").
/// </summary>
public sealed class SmartAlbumConvertTests : IntegrationTestBase
{
    public SmartAlbumConvertTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record AlbumItem(Guid Id, int AssetCount, string Kind);
    private sealed record AssetItem(Guid Id);

    private Task<Guid> CreateAssetAsync(TestUser owner, DateTime capturedAt) =>
        WithDbContextAsync(async db =>
        {
            var name = $"{Guid.NewGuid():N}.jpg";
            var asset = new Asset
            {
                FileName = name,
                FullPath = $"/assets/users/{owner.Username}/{name}",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });

    private static async Task<Guid> CreateSmartAlbumAsync(HttpClient client, object rule)
    {
        var response = await client.PostAsJsonAsync("/api/albums", new { name = "Reciente", smartRule = rule });
        response.EnsureSuccessStatusCode();
        return (await response.Content.ReadFromJsonAsync<AlbumItem>())!.Id;
    }

    private static async Task<List<Guid>> AlbumAssetIdsAsync(HttpClient client, Guid albumId) =>
        (await client.GetFromJsonAsync<List<AssetItem>>($"/api/albums/{albumId}/assets"))!.Select(i => i.Id).ToList();

    [Fact]
    public async Task RollingWindow_KeepsOnlyTheLastDays()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var recent = await CreateAssetAsync(alice, DateTime.UtcNow.AddDays(-5));
        await CreateAssetAsync(alice, DateTime.UtcNow.AddDays(-100));

        var albumId = await CreateSmartAlbumAsync(client, new { type = "dateRange", last = "30d" });

        Assert.Equal(new[] { recent }, await AlbumAssetIdsAsync(client, albumId));
    }

    [Fact]
    public async Task RollingWindow_RejectsAnUnknownUnit()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var response = await client.PostAsJsonAsync("/api/albums",
            new { name = "Mal", smartRule = new { type = "dateRange", last = "3w" } });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task Convert_TurnsTheAlbumManual_KeepingItsPhotosNewestFirst()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var older = await CreateAssetAsync(alice, DateTime.UtcNow.AddDays(-10));
        var newer = await CreateAssetAsync(alice, DateTime.UtcNow.AddDays(-2));
        var albumId = await CreateSmartAlbumAsync(client, new { type = "dateRange", last = "30d" });

        var response = await client.PostAsync($"/api/albums/{albumId}/convert-to-manual", content: null);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var album = await response.Content.ReadFromJsonAsync<AlbumItem>();
        Assert.Equal("Manual", album!.Kind);
        Assert.Equal(2, album.AssetCount);
        Assert.Equal(new[] { newer, older }, await AlbumAssetIdsAsync(client, albumId));

        var stored = await WithDbContextAsync(db => db.Albums.AsNoTracking().FirstAsync(a => a.Id == albumId));
        Assert.Null(stored.SmartRule);

        // A photo that would have matched the rule no longer joins.
        await CreateAssetAsync(alice, DateTime.UtcNow.AddDays(-1));
        Assert.Equal(2, (await AlbumAssetIdsAsync(client, albumId)).Count);
    }

    [Fact]
    public async Task Convert_IsForTheOwnerOfASmartAlbumOnly()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var albumId = await CreateSmartAlbumAsync(aliceClient, new { type = "dateRange", last = "1y" });
        await WithDbContextAsync(async db =>
        {
            db.AlbumPermissions.Add(new AlbumPermission
            {
                AlbumId = albumId, UserId = bob.Id, GrantedByUserId = alice.Id, CanRead = true, CanWrite = true
            });
            await db.SaveChangesAsync();
        });

        var asReader = await bobClient.PostAsync($"/api/albums/{albumId}/convert-to-manual", content: null);
        Assert.Equal(HttpStatusCode.Forbidden, asReader.StatusCode);

        var manual = await aliceClient.PostAsJsonAsync("/api/albums", new { name = "Normal" });
        var manualId = (await manual.Content.ReadFromJsonAsync<AlbumItem>())!.Id;
        var notSmart = await aliceClient.PostAsync($"/api/albums/{manualId}/convert-to-manual", content: null);
        Assert.Equal(HttpStatusCode.BadRequest, notSmart.StatusCode);
    }

    [Theory]
    [InlineData("90d", "2026-07-29")]
    [InlineData("1d", "2026-10-26")]
    [InlineData("6m", "2026-04-26")]
    [InlineData("1y", "2025-10-26")]
    public void RollingWindowStart_CountsBackFromToday(string last, string expected)
    {
        var now = new DateTime(2026, 10, 26, 15, 30, 0, DateTimeKind.Utc);

        Assert.Equal(DateTime.Parse(expected), SmartRuleCompiler.RollingWindowStart(last, now));
    }

    [Theory]
    [InlineData("0d")]
    [InlineData("90")]
    [InlineData("-3m")]
    [InlineData("2w")]
    public void RollingWindowStart_RejectsMalformedWindows(string last)
    {
        Assert.Throws<SmartRuleException>(() => SmartRuleCompiler.RollingWindowStart(last, DateTime.UtcNow));
    }
}
