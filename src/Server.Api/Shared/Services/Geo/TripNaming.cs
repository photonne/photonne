using System.Globalization;

namespace Photonne.Server.Api.Shared.Services.Geo;

/// <summary>A place visited on a trip, and how much of the trip was shot there.</summary>
public readonly record struct TripPlaceCount(string Name, int AssetCount);

/// <summary>
/// Titles a trip from the places it visited.
///
/// Cities only — never countries. RegionInfo.DisplayName ignores the culture you
/// ask in and falls back to the NATIVE name, so a trip to Japan would be titled
/// 日本 and one to Morocco المغرب. ("Italia" works only because Italians call it
/// that too.) Rendering a country name in Spanish would mean shipping ~250
/// translations of our own; until something needs it, the city you photographed
/// most is a truthful and unambiguous title.
/// </summary>
public static class TripNaming
{
    private static readonly CultureInfo Spanish = CultureInfo.GetCultureInfo("es-ES");

    /// <summary>
    /// "Girona" · "Roma y Florencia" · "Roma, Florencia y 3 lugares más".
    ///
    /// Falls back to the dates when nothing geocoded — better an honest "Viaje de
    /// julio de 2019" than a trip with no name at all, and it happens whenever a
    /// trip's coordinates all land far from any populated place.
    /// </summary>
    public static string Title(IReadOnlyList<TripPlaceCount> places, DateTime windowStart)
    {
        var ranked = places
            .Where(p => !string.IsNullOrWhiteSpace(p.Name))
            .OrderByDescending(p => p.AssetCount)
            .ThenBy(p => p.Name, StringComparer.CurrentCulture)
            .ToList();

        return ranked.Count switch
        {
            0 => $"Viaje de {MonthAndYear(windowStart)}",
            1 => ranked[0].Name,
            2 => $"{ranked[0].Name} y {ranked[1].Name}",
            _ => $"{ranked[0].Name}, {ranked[1].Name} y {Extra(ranked.Count - 2)}",
        };
    }

    private static string Extra(int count) =>
        count == 1 ? "1 lugar más" : $"{count} lugares más";

    private static string MonthAndYear(DateTime date) =>
        date.ToString("MMMM 'de' yyyy", Spanish);
}
