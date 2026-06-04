using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Fixtures;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// Exercises ExifWriterService against real JPEG fixtures. The key contract is
/// the round-trip: a date written by the writer must be read back unchanged by
/// ExifExtractorService (both interpret EXIF as wall-clock in the configured
/// timezone, default UTC). Both services are resolved from the factory's DI so
/// SettingsService and configuration are wired like prod.
/// </summary>
public sealed class ExifWriterTests : IntegrationTestBase
{
    public ExifWriterTests(PhotonneApiFactory factory) : base(factory) { }

    private static string CopyToTemp(string source)
    {
        var dest = Path.Combine(Path.GetTempPath(), $"{Guid.NewGuid():N}{Path.GetExtension(source)}");
        File.Copy(source, dest, overwrite: true);
        return dest;
    }

    [Fact]
    public async Task ApplyDateToFile_OnVideo_SetsModifiedTime_WithoutExif()
    {
        var path = CopyToTemp(FixturePaths.Video);
        try
        {
            var date = new DateTime(2012, 9, 1, 8, 0, 0, DateTimeKind.Utc);

            using var scope = Factory.Services.CreateScope();
            var writer = scope.ServiceProvider.GetRequiredService<ExifWriterService>();

            var result = await writer.ApplyDateToFileAsync(path, date);

            // Videos can't carry EXIF, but the mtime becomes the date carrier —
            // the file itself now holds the capture date.
            Assert.False(result.ExifWritten);
            Assert.True(result.ModifiedTimeSet);
            Assert.True(result.FileTouched);
            Assert.Equal(date, File.GetLastWriteTimeUtc(path));
        }
        finally
        {
            File.Delete(path);
        }
    }

    [Fact]
    public async Task ApplyDateToFile_OnJpeg_WritesExif_AndModifiedTime()
    {
        var path = CopyToTemp(FixturePaths.WithExif);
        try
        {
            var date = new DateTime(2015, 2, 14, 18, 30, 0, DateTimeKind.Utc);

            using var scope = Factory.Services.CreateScope();
            var writer = scope.ServiceProvider.GetRequiredService<ExifWriterService>();
            var extractor = scope.ServiceProvider.GetRequiredService<ExifExtractorService>();

            var result = await writer.ApplyDateToFileAsync(path, date);

            Assert.True(result.ExifWritten);
            Assert.True(result.ModifiedTimeSet);
            Assert.Equal(date, File.GetLastWriteTimeUtc(path));

            var exif = await extractor.ExtractExifAsync(path);
            Assert.Equal(date, exif!.DateTimeOriginal);
        }
        finally
        {
            File.Delete(path);
        }
    }

    [Fact]
    public async Task WriteDateTaken_RoundTrips_ThroughExtractor()
    {
        var path = CopyToTemp(FixturePaths.WithExif);
        try
        {
            var newDate = new DateTime(2019, 7, 4, 9, 25, 30, DateTimeKind.Utc);

            using var scope = Factory.Services.CreateScope();
            var writer = scope.ServiceProvider.GetRequiredService<ExifWriterService>();
            var extractor = scope.ServiceProvider.GetRequiredService<ExifExtractorService>();

            var result = await writer.WriteDateTakenAsync(path, newDate);
            Assert.True(result.FileWritten, result.Reason);

            var exif = await extractor.ExtractExifAsync(path);
            Assert.NotNull(exif);
            // Default timezone is UTC, so the value round-trips exactly.
            Assert.Equal(newDate, exif!.DateTimeOriginal);
        }
        finally
        {
            File.Delete(path);
        }
    }

    [Fact]
    public async Task WriteDateTaken_AddsDate_ToJpegWithoutExif()
    {
        var path = CopyToTemp(FixturePaths.NoMetadata);
        try
        {
            var newDate = new DateTime(2021, 12, 31, 23, 59, 0, DateTimeKind.Utc);

            using var scope = Factory.Services.CreateScope();
            var writer = scope.ServiceProvider.GetRequiredService<ExifWriterService>();
            var extractor = scope.ServiceProvider.GetRequiredService<ExifExtractorService>();

            var result = await writer.WriteDateTakenAsync(path, newDate);
            Assert.True(result.FileWritten, result.Reason);

            var exif = await extractor.ExtractExifAsync(path);
            Assert.Equal(newDate, exif!.DateTimeOriginal);
        }
        finally
        {
            File.Delete(path);
        }
    }

    [Fact]
    public async Task WriteDateTaken_OnVideo_ReportsNotWritten()
    {
        using var scope = Factory.Services.CreateScope();
        var writer = scope.ServiceProvider.GetRequiredService<ExifWriterService>();

        var result = await writer.WriteDateTakenAsync(FixturePaths.Video, DateTime.UtcNow);

        Assert.False(result.FileWritten);
        Assert.NotNull(result.Reason);
    }

    [Fact]
    public async Task WriteDateTaken_OnMissingFile_ReportsNotWritten()
    {
        using var scope = Factory.Services.CreateScope();
        var writer = scope.ServiceProvider.GetRequiredService<ExifWriterService>();

        var ghost = Path.Combine(Path.GetTempPath(), $"{Guid.NewGuid():N}.jpg");
        var result = await writer.WriteDateTakenAsync(ghost, DateTime.UtcNow);

        Assert.False(result.FileWritten);
    }
}
