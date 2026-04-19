using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// Drives ThumbnailGeneratorService against real JPEG fixtures and inspects
/// the on-disk output. Relies on the factory's per-run THUMBNAILS_PATH so the
/// /data/thumbnails hard-coded default doesn't touch the host filesystem.
/// </summary>
public sealed class ThumbnailGeneratorTests : IntegrationTestBase
{
    public ThumbnailGeneratorTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<List<AssetThumbnail>> GenerateAsync(string sourcePath, Guid assetId)
    {
        using var scope = Factory.Services.CreateScope();
        var gen = scope.ServiceProvider.GetRequiredService<ThumbnailGeneratorService>();
        return await gen.GenerateThumbnailsAsync(sourcePath, assetId);
    }

    [Fact]
    public async Task GeneratesThreeSizes_ForJpeg()
    {
        var assetId = Guid.NewGuid();
        var thumbs = await GenerateAsync(FixturePaths.WithExif, assetId);

        Assert.Equal(3, thumbs.Count);
        Assert.Contains(thumbs, t => t.Size == ThumbnailSize.Small);
        Assert.Contains(thumbs, t => t.Size == ThumbnailSize.Medium);
        Assert.Contains(thumbs, t => t.Size == ThumbnailSize.Large);
    }

    [Fact]
    public async Task WritesAllThumbnailFilesToDisk()
    {
        var assetId = Guid.NewGuid();
        var thumbs = await GenerateAsync(FixturePaths.WithExif, assetId);

        foreach (var t in thumbs)
        {
            Assert.False(string.IsNullOrWhiteSpace(t.FilePath));
            Assert.True(File.Exists(t.FilePath),
                $"{t.Size} thumbnail missing on disk: {t.FilePath}");
            var info = new FileInfo(t.FilePath);
            Assert.True(info.Length > 0, $"{t.Size} thumbnail is empty");
        }
    }

    [Fact]
    public async Task ThumbnailSizesAreDescending_FromLargeToSmall()
    {
        // A regression where sizes collapsed (all three coming out identical) would
        // bloat storage and blur previews. Fast-fail by asserting the actual
        // pixel dimensions come out strictly ordered.
        var thumbs = await GenerateAsync(FixturePaths.WithExif, Guid.NewGuid());

        var byName = thumbs.ToDictionary(t => t.Size);
        var small = ReadDimensions(byName[ThumbnailSize.Small].FilePath);
        var medium = ReadDimensions(byName[ThumbnailSize.Medium].FilePath);
        var large = ReadDimensions(byName[ThumbnailSize.Large].FilePath);

        Assert.True(small.Width <= medium.Width, "Small should not be wider than Medium");
        Assert.True(medium.Width <= large.Width, "Medium should not be wider than Large");
    }

    [Fact]
    public async Task PersistsThumbnailsUnderConfiguredBasePath()
    {
        // The factory redirects THUMBNAILS_PATH to a tmp dir; every generated
        // file must land under it, never in /data/thumbnails.
        var thumbs = await GenerateAsync(FixturePaths.WithExif, Guid.NewGuid());

        Assert.NotEmpty(thumbs);
        foreach (var t in thumbs)
        {
            Assert.DoesNotContain("/data/thumbnails", t.FilePath);
        }
    }

    private static (int Width, int Height) ReadDimensions(string path)
    {
        var img = SixLabors.ImageSharp.Image.Identify(path);
        return (img.Width, img.Height);
    }
}
