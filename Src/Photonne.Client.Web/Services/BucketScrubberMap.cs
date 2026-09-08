using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Mapeo fracción ↔ mes para el scrubber del timeline, construido del esqueleto
/// de buckets (más nuevo primero): la fracción 0 es lo más reciente y 1 lo más
/// antiguo, proporcional a los CONTEOS (no al número de meses), igual que el
/// scrubber del cliente nativo. Clase pura para testear sin navegador.
/// </summary>
public sealed class BucketScrubberMap
{
    private readonly string[] _keys;
    private readonly double[] _startFractions; // fracción donde EMPIEZA cada bucket
    public IReadOnlyList<(string Year, double Fraction)> YearMarkers { get; }

    private BucketScrubberMap(string[] keys, double[] startFractions,
        IReadOnlyList<(string, double)> yearMarkers)
    {
        _keys = keys;
        _startFractions = startFractions;
        YearMarkers = yearMarkers;
    }

    public static BucketScrubberMap? Build(IReadOnlyList<TimelineBucket> buckets)
    {
        var total = buckets.Sum(b => (long)Math.Max(0, b.Count));
        if (buckets.Count == 0 || total == 0) return null;

        var keys = new string[buckets.Count];
        var starts = new double[buckets.Count];
        var years = new List<(string, double)>();
        string? lastYear = null;
        long running = 0;
        for (var i = 0; i < buckets.Count; i++)
        {
            keys[i] = buckets[i].Key;
            starts[i] = running / (double)total;
            var year = buckets[i].Key.Split('-')[0];
            if (year != lastYear)
            {
                lastYear = year;
                years.Add((year, starts[i]));
            }
            running += Math.Max(0, buckets[i].Count);
        }
        return new BucketScrubberMap(keys, starts, years);
    }

    /// <summary>Mes cuyo tramo contiene la fracción [0,1].</summary>
    public string KeyForFraction(double fraction)
    {
        var f = Math.Clamp(fraction, 0, 1);
        // Búsqueda binaria del último start <= f.
        var lo = 0;
        var hi = _keys.Length - 1;
        while (lo < hi)
        {
            var mid = (lo + hi + 1) / 2;
            if (_startFractions[mid] <= f) lo = mid;
            else hi = mid - 1;
        }
        return _keys[lo];
    }

    /// <summary>Fracción donde empieza [key]; 0 si no existe.</summary>
    public double FractionForKey(string key)
    {
        var idx = Array.IndexOf(_keys, key);
        return idx >= 0 ? _startFractions[idx] : 0;
    }
}
