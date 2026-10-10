using System.Globalization;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Renders the human-readable parts of a memory. Titles are generated on the
/// server (the client shows them verbatim) so the wording lives in one place
/// instead of being reassembled from a kind enum on three platforms.
///
/// The culture is pinned rather than taken from the host: a container's locale
/// is an accident of its base image, and "14 de julio" turning into "July 14"
/// after a rebuild would be a baffling bug to chase.
/// </summary>
internal static class MemoryTitles
{
    private static readonly CultureInfo Spanish = CultureInfo.GetCultureInfo("es-ES");

    /// <summary>"14 de julio de 2019"</summary>
    public static string DayAndYear(DateTime date) =>
        date.ToString("d 'de' MMMM 'de' yyyy", Spanish);

    /// <summary>"Julio de 2019"</summary>
    public static string MonthAndYear(int year, int month) =>
        Capitalize(new DateTime(year, month, 1).ToString("MMMM 'de' yyyy", Spanish));

    /// <summary>"julio" → "Julio". Spanish months are lowercase mid-sentence but
    /// these strings open a title.</summary>
    private static string Capitalize(string value) =>
        string.IsNullOrEmpty(value) ? value : char.ToUpper(value[0], Spanish) + value[1..];

    /// <summary>"12 fotos" / "1 foto"</summary>
    public static string PhotoCount(int count) =>
        count == 1 ? "1 foto" : $"{count} fotos";
}
