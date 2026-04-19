using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// Exercises ExifExtractorService against real JPEG fixtures with hand-crafted
/// EXIF blocks (see Fixtures/FixturePaths). These are "thin integration" tests:
/// the service is resolved from the factory's DI so SettingsService (with its
/// real DbContext) and the configuration stack are wired up exactly like prod.
/// </summary>
public sealed class ExifExtractorTests : IntegrationTestBase
{
    public ExifExtractorTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<T> ResolveAndRunAsync<T>(Func<ExifExtractorService, Task<T>> action)
    {
        using var scope = Factory.Services.CreateScope();
        var extractor = scope.ServiceProvider.GetRequiredService<ExifExtractorService>();
        return await action(extractor);
    }

    [Fact]
    public void FixturesExistOnDisk()
    {
        // Fast-fails the whole test class if the csproj forgets to copy the fixtures.
        Assert.True(File.Exists(FixturePaths.WithExif), $"Missing fixture at {FixturePaths.WithExif}");
        Assert.True(File.Exists(FixturePaths.NoMetadata));
        Assert.True(File.Exists(FixturePaths.NegativeGps));
    }

    [Fact]
    public async Task FullExif_IsExtracted_FromRichJpeg()
    {
        var exif = await ResolveAndRunAsync(e => e.ExtractExifAsync(FixturePaths.WithExif));

        Assert.NotNull(exif);
        Assert.Equal("Canon", exif!.CameraMake);
        Assert.Equal("EOS R5", exif.CameraModel);
        Assert.Equal(800, exif.Iso);
        Assert.Equal(1, exif.Orientation);
        Assert.Equal(100, exif.Width);
        Assert.Equal(100, exif.Height);
    }

    [Fact]
    public async Task DateTimeOriginal_IsParsedAsUtc_FromExifString()
    {
        var exif = await ResolveAndRunAsync(e => e.ExtractExifAsync(FixturePaths.WithExif));

        Assert.NotNull(exif!.DateTimeOriginal);
        // Fixture carries DateTimeOriginal=2024:01:15 12:30:45; default tz is UTC.
        Assert.Equal(new DateTime(2024, 1, 15, 12, 30, 45, DateTimeKind.Utc), exif.DateTimeOriginal);
    }

    [Fact]
    public async Task PositiveGps_ProducesPositiveCoordinates()
    {
        // Madrid (~40.4168° N, 3.7038° W). Longitude must be NEGATIVE (west of
        // Greenwich) per the GPSLongitudeRef='W' tag, not the absolute value.
        var exif = await ResolveAndRunAsync(e => e.ExtractExifAsync(FixturePaths.WithExif));

        Assert.NotNull(exif!.Latitude);
        Assert.NotNull(exif.Longitude);
        Assert.InRange(exif.Latitude!.Value, 40.40, 40.43);
        Assert.InRange(exif.Longitude!.Value, -3.71, -3.70);
    }

    [Fact]
    public async Task NegativeGps_ProducesNegativeCoordinates_FromSouthWestRefs()
    {
        // Buenos Aires (~34.6037° S, 58.3816° W). Both must be negative.
        var exif = await ResolveAndRunAsync(e => e.ExtractExifAsync(FixturePaths.NegativeGps));

        Assert.NotNull(exif!.Latitude);
        Assert.NotNull(exif.Longitude);
        Assert.InRange(exif.Latitude!.Value, -34.61, -34.60);
        Assert.InRange(exif.Longitude!.Value, -58.39, -58.38);
    }

    [Fact]
    public async Task JpegWithoutExif_ReturnsEntity_WithoutCameraOrGps()
    {
        // No EXIF block at all — ImageSharp still fills Width/Height, but the
        // camera / date / GPS fields must stay null instead of crashing.
        var exif = await ResolveAndRunAsync(e => e.ExtractExifAsync(FixturePaths.NoMetadata));

        Assert.NotNull(exif);
        Assert.Null(exif!.CameraMake);
        Assert.Null(exif.CameraModel);
        Assert.Null(exif.DateTimeOriginal);
        Assert.Null(exif.Latitude);
        Assert.Null(exif.Longitude);
        Assert.Equal(100, exif.Width);
        Assert.Equal(100, exif.Height);
    }

    [Fact]
    public async Task UnsupportedExtension_ReturnsNull()
    {
        var exif = await ResolveAndRunAsync(e => e.ExtractExifAsync("/tmp/not-really.txt"));

        Assert.Null(exif);
    }

    [Fact]
    public async Task MissingFile_DoesNotThrow()
    {
        // ReadMetadata throws → service catches and returns an empty AssetExif
        // (not null). Guards against a broken deployment silently crashing
        // the indexing pipeline.
        var exif = await ResolveAndRunAsync(e =>
            e.ExtractExifAsync(Path.Combine(Path.GetTempPath(), $"{Guid.NewGuid():N}.jpg")));

        Assert.NotNull(exif);
        Assert.Null(exif!.CameraMake);
        Assert.Null(exif.Width);
    }

    [Fact]
    public async Task Mp4Video_DimensionsExtracted_ViaFfprobe()
    {
        // Sanity-check that the video branch (ffprobe via Xabe.FFmpeg) wires
        // up correctly. The fixture is a 64×64 1-second clip.
        var exif = await ResolveAndRunAsync(e => e.ExtractExifAsync(FixturePaths.Video));

        Assert.NotNull(exif);
        Assert.Equal(64, exif!.Width);
        Assert.Equal(64, exif.Height);
    }
}
