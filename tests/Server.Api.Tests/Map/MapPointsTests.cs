using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Map;

/// <summary>
/// GET /api/assets/map/points: one point per visible geotagged photo of the
/// caller, with its coordinates, capture date and whether it has a thumbnail.
/// Trashed, missing, (0,0) and other users' photos are left out.
/// </summary>
public sealed class MapPointsTests : IntegrationTestBase
{
    public MapPointsTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record PointBody(Guid Id, double Latitude, double Longitude, bool HasThumbnail, DateTime Date);

    private Task<Guid> CreateFolderAsync(string path) =>
        WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    private Task<Guid> CreateAssetAsync(
        TestUser owner,
        Guid folderId,
        string folderPath,
        double? latitude,
        double? longitude,
        DateTime capturedAt,
        bool withThumbnail = false,
        bool isDeleted = false,
        bool isFileMissing = false) =>
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
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                FolderId = folderId,
                DeletedAt = isDeleted ? capturedAt : null,
                IsFileMissing = isFileMissing
            };
            db.Assets.Add(asset);
            if (latitude.HasValue || longitude.HasValue)
                db.AssetExifs.Add(new AssetExif { AssetId = asset.Id, Latitude = latitude, Longitude = longitude });
            if (withThumbnail)
                db.AssetThumbnails.Add(new AssetThumbnail
                {
                    AssetId = asset.Id,
                    Size = ThumbnailSize.Small,
                    FilePath = $"/thumbs/{fileName}"
                });
            await db.SaveChangesAsync();
            return asset.Id;
        });

    [Fact]
    public async Task Points_AreTheCallersVisibleGeotaggedPhotos()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var (bob, _) = await CreateAuthenticatedUserAsync();
        var alicePath = $"/assets/users/{alice.Username}/Viajes";
        var bobPath = $"/assets/users/{bob.Username}/Viajes";
        var aliceFolder = await CreateFolderAsync(alicePath);
        var bobFolder = await CreateFolderAsync(bobPath);
        var date = new DateTime(2026, 7, 14, 10, 0, 0, DateTimeKind.Utc);

        var rome = await CreateAssetAsync(alice, aliceFolder, alicePath, 41.9, 12.5, date, withThumbnail: true);
        var lisbon = await CreateAssetAsync(alice, aliceFolder, alicePath, 38.7, -9.1, date.AddDays(1));
        await CreateAssetAsync(alice, aliceFolder, alicePath, null, null, date);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 0, 0, date);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 40.4, -3.7, date, isDeleted: true);
        await CreateAssetAsync(alice, aliceFolder, alicePath, 40.4, -3.7, date, isFileMissing: true);
        await CreateAssetAsync(bob, bobFolder, bobPath, 48.8, 2.3, date);

        var points = await client.GetFromJsonAsync<List<PointBody>>("/api/assets/map/points");

        Assert.NotNull(points);
        Assert.Equal(new[] { lisbon, rome }.OrderBy(id => id), points!.Select(p => p.Id).OrderBy(id => id));

        var romePoint = Assert.Single(points, p => p.Id == rome);
        Assert.Equal(41.9, romePoint.Latitude);
        Assert.Equal(12.5, romePoint.Longitude);
        Assert.True(romePoint.HasThumbnail);
        Assert.Equal(date, romePoint.Date.ToUniversalTime());

        Assert.False(Assert.Single(points, p => p.Id == lisbon).HasThumbnail);
    }
}
