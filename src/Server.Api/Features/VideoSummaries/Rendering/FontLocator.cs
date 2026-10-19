namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>
/// A font for the titles and captions. The server image installs DejaVu
/// (fonts-dejavu-core); the other paths cover a developer's machine. Without
/// one, summaries simply have no text.
/// </summary>
public static class FontLocator
{
    private static readonly string[] Candidates =
    {
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        @"C:\Windows\Fonts\arialbd.ttf",
    };

    private static readonly Lazy<string?> Found = new(() => Candidates.FirstOrDefault(File.Exists));

    public static string? Find() => Found.Value;
}
