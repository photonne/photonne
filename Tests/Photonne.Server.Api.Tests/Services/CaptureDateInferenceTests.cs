using Photonne.Server.Api.Shared.Services;
using static Photonne.Server.Api.Shared.Services.CaptureDateInferenceService;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// Pure unit tests over the static parsing core — no DB, no DI. Locks down
/// the filename/path patterns and their priority (filename beats folder,
/// more-specific folder beats less-specific).
/// </summary>
public sealed class CaptureDateInferenceTests
{
    private static DateTime? Infer(string fileName, string fullPath, out InferenceOrigin origin) =>
        TryInferLocal(fileName, fullPath, out origin);

    // ── Filename patterns ─────────────────────────────────────────────────────

    [Theory]
    [InlineData("IMG_20100815_123456.jpg")]
    [InlineData("VID-20100815-123456.mp4")]
    [InlineData("20100815_123456.jpg")]
    [InlineData("Screenshot_20100815-123456.png")]
    public void FullTimestamp_InFileName_ParsesDateAndTime(string fileName)
    {
        var result = Infer(fileName, $"/assets/users/marc/{fileName}", out var origin);

        Assert.Equal(new DateTime(2010, 8, 15, 12, 34, 56), result);
        Assert.Equal(InferenceOrigin.FileName, origin);
    }

    [Fact]
    public void PixelStyle_WithMillis_ParsesFullTime()
    {
        // PXL files append milliseconds (HHMMSSmmm) — the pattern tolerates
        // and discards the trailing 3 digits.
        var result = Infer("PXL_20100815_123456789.jpg", "/x/PXL_20100815_123456789.jpg", out _);

        Assert.Equal(new DateTime(2010, 8, 15, 12, 34, 56), result);
    }

    [Fact]
    public void DashedTimestamp_DropboxStyle_Parses()
    {
        var result = Infer("2010-08-15 12.34.56.jpg", "/x/2010-08-15 12.34.56.jpg", out _);

        Assert.Equal(new DateTime(2010, 8, 15, 12, 34, 56), result);
    }

    [Fact]
    public void WhatsApp_DateOnly_DefaultsToNoon()
    {
        var result = Infer("IMG-20100815-WA0001.jpg", "/x/IMG-20100815-WA0001.jpg", out var origin);

        Assert.Equal(new DateTime(2010, 8, 15, 12, 0, 0), result);
        Assert.Equal(InferenceOrigin.FileName, origin);
    }

    [Theory]
    [InlineData("IMG_20991301_123456.jpg")] // month 13
    [InlineData("IMG_20100230_123456.jpg")] // Feb 30th
    [InlineData("IMG_19490815_123456.jpg")] // before plausible window
    public void InvalidDates_InFileName_AreRejected(string fileName)
    {
        var result = Infer(fileName, $"/plain/{fileName}", out _);

        Assert.Null(result);
    }

    [Fact]
    public void EightDigitNoise_ThatIsNotADate_IsRejected()
    {
        // 4032×3024 resolution masquerading as digits — month 32 is invalid.
        var result = Infer("render_40323024.png", "/plain/render_40323024.png", out _);

        Assert.Null(result);
    }

    // ── Folder-path patterns ──────────────────────────────────────────────────

    [Fact]
    public void DatePrefixedFolder_UsersStructure_ParsesAtNoon()
    {
        // The user's library layout: <year>/yyyy-MM-dd Nombre/archivo
        var result = Infer("foto.jpg", "/assets/shared/Fotos/2010/2010-08-15 Vacaciones Ibiza/foto.jpg", out var origin);

        Assert.Equal(new DateTime(2010, 8, 15, 12, 0, 0), result);
        Assert.Equal(InferenceOrigin.FolderPath, origin);
    }

    [Fact]
    public void NumericHierarchy_YearMonthDay_Parses()
    {
        var result = Infer("foto.jpg", "/assets/users/marc/2010/08/15/foto.jpg", out _);

        Assert.Equal(new DateTime(2010, 8, 15, 12, 0, 0), result);
    }

    [Fact]
    public void NumericHierarchy_YearMonth_DefaultsToFirstDay()
    {
        var result = Infer("foto.jpg", "/assets/users/marc/2010/08/foto.jpg", out _);

        Assert.Equal(new DateTime(2010, 8, 1, 12, 0, 0), result);
    }

    [Fact]
    public void BareYearFolder_IsLastResort_January1st()
    {
        var result = Infer("foto.jpg", "/assets/shared/Fotos/2010/sueltas/foto.jpg", out _);

        Assert.Equal(new DateTime(2010, 1, 1, 12, 0, 0), result);
    }

    [Fact]
    public void NoDateAnywhere_ReturnsNull()
    {
        var result = Infer("foto.jpg", "/assets/users/marc/varios/foto.jpg", out _);

        Assert.Null(result);
    }

    // ── Priority ──────────────────────────────────────────────────────────────

    [Fact]
    public void FileName_Beats_FolderPath()
    {
        var result = Infer(
            "IMG_20111224_180000.jpg",
            "/fotos/2010/2010-08-15 Vacaciones/IMG_20111224_180000.jpg",
            out var origin);

        Assert.Equal(new DateTime(2011, 12, 24, 18, 0, 0), result);
        Assert.Equal(InferenceOrigin.FileName, origin);
    }

    [Fact]
    public void DeepestDatedFolder_Beats_ShallowerOnes()
    {
        var result = Infer(
            "foto.jpg",
            "/fotos/2009-01-01 Backup/2010-08-15 Vacaciones/foto.jpg",
            out _);

        Assert.Equal(new DateTime(2010, 8, 15, 12, 0, 0), result);
    }
}
