using System.Globalization;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.VideoSummaries.Rendering;

/// <summary>The words on screen: the line under the title and each chapter's caption.</summary>
/// <param name="Captions">One per clip, null except at the first clip of a chapter.</param>
public sealed record SummaryText(string? Subtitle, string?[] Captions);

/// <summary>
/// Splits a summary into chapters — months for a year, days for an album —
/// and words them in Spanish, with the place when most of the chapter's
/// placed clips agree on one: "Julio · Barcelona", "12 de agosto · Roma".
/// A summary that is one chapter gets no captions (nothing to mark). Pure.
/// </summary>
public static class SummaryChapters
{
    private static readonly CultureInfo Spanish = CultureInfo.GetCultureInfo("es-ES");

    public static SummaryText Describe(
        IReadOnlyList<SummaryClip> clips, VideoSummarySource source, IReadOnlyDictionary<Guid, string> places)
    {
        if (clips.Count == 0) return new SummaryText(null, Array.Empty<string?>());

        var byMonth = source == VideoSummarySource.Year;
        string Key(SummaryClip c) => byMonth ? c.CapturedAt.ToString("yyyy-MM") : c.CapturedAt.ToString("yyyy-MM-dd");

        var captions = new string?[clips.Count];
        var chapters = clips.Select((c, i) => (c, i)).GroupBy(x => Key(x.c)).ToList();
        if (chapters.Count > 1)
        {
            foreach (var chapter in chapters)
            {
                var first = chapter.First();
                var when = byMonth ? Capitalize(Month(first.c.CapturedAt)) : Day(first.c.CapturedAt);
                var place = MainPlace(chapter.Select(x => x.c.AssetId), places);
                captions[first.i] = place != null ? $"{when} · {place}" : when;
            }
        }

        return new SummaryText(Range(clips[0].CapturedAt, clips[^1].CapturedAt, byMonth), captions);
    }

    /// <summary>"Enero – diciembre de 2024", "12 – 18 de agosto de 2024", "28 de julio – 3 de agosto de 2024".</summary>
    internal static string Range(DateTime from, DateTime to, bool byMonth)
    {
        if (byMonth)
        {
            if (from.Year == to.Year && from.Month == to.Month) return $"{Capitalize(Month(from))} de {from.Year}";
            return from.Year == to.Year
                ? $"{Capitalize(Month(from))} – {Month(to)} de {to.Year}"
                : $"{Capitalize(Month(from))} de {from.Year} – {Month(to)} de {to.Year}";
        }

        if (from.Date == to.Date) return $"{Day(from)} de {from.Year}";
        if (from.Year != to.Year) return $"{Day(from)} de {from.Year} – {Day(to)} de {to.Year}";
        if (from.Month != to.Month) return $"{Day(from)} – {Day(to)} de {to.Year}";
        return $"{from.Day} – {Day(to)} de {to.Year}";
    }

    private static string? MainPlace(IEnumerable<Guid> assetIds, IReadOnlyDictionary<Guid, string> places)
    {
        var named = assetIds.Distinct().Select(id => places.GetValueOrDefault(id)).Where(p => p != null).ToList();
        if (named.Count == 0) return null;
        var top = named.GroupBy(p => p).OrderByDescending(g => g.Count()).First();
        return top.Count() * 2 >= named.Count ? top.Key : null;
    }

    private static string Month(DateTime d) => Spanish.DateTimeFormat.GetMonthName(d.Month);

    private static string Day(DateTime d) => $"{d.Day} de {Month(d)}";

    private static string Capitalize(string s) => s.Length == 0 ? s : char.ToUpper(s[0], Spanish) + s[1..];
}
