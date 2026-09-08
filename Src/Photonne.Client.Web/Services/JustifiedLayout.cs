namespace Photonne.Client.Web.Services;

/// <summary>
/// Layout justificado por filas para la rejilla de fotos, como clase pura para
/// poder testearlo sin navegador. Dado el ancho del contenedor y los aspect
/// ratios, reparte celdas en filas cuyo alto se ajusta para que cada fila
/// llene el ancho exacto (estilo Google Photos); la última fila parcial
/// conserva la altura objetivo en vez de estirarse.
///
/// También estima la altura de un mes AÚN SIN HIDRATAR a partir solo de su
/// conteo (el contrato de los buckets), para reservar scroll estable.
/// </summary>
public static class JustifiedLayout
{
    /// <summary>Aspect usado cuando el asset no tiene width/height (sin EXIF).</summary>
    public const double FallbackAspect = 1.5;

    // Un panorama de 6:1 o un escaneo de tira vertical rompen el reparto de
    // filas; se recortan a un rango sano solo a efectos de layout.
    private const double MinAspect = 0.4;
    private const double MaxAspect = 3.0;

    public sealed record Row(int StartIndex, int Count, double Height);

    public sealed record Result(IReadOnlyList<Row> Rows, double TotalHeight);

    /// <summary>
    /// Filas exactas para un mes hidratado. [aspectRatios] en el orden de
    /// render; los alto/ancho por celda se derivan en el componente
    /// (ancho = aspect × alto de su fila).
    /// </summary>
    public static Result Compute(
        IReadOnlyList<double> aspectRatios,
        double containerWidth,
        double targetRowHeight,
        double spacing = 4)
    {
        var rows = new List<Row>();
        if (aspectRatios.Count == 0 || containerWidth <= 0 || targetRowHeight <= 0)
            return new Result(rows, 0);

        var start = 0;
        var aspectSum = 0.0;
        for (var i = 0; i < aspectRatios.Count; i++)
        {
            aspectSum += Clamp(aspectRatios[i]);
            var count = i - start + 1;
            var contentWidth = containerWidth - spacing * (count - 1);
            // La fila se cierra cuando, a altura objetivo, ya desborda el ancho.
            if (aspectSum * targetRowHeight >= contentWidth)
            {
                var height = contentWidth / aspectSum;
                rows.Add(new Row(start, count, height));
                start = i + 1;
                aspectSum = 0;
            }
        }

        if (start < aspectRatios.Count)
        {
            // Última fila parcial: altura objetivo, sin justificar.
            rows.Add(new Row(start, aspectRatios.Count - start, targetRowHeight));
        }

        var total = rows.Sum(r => r.Height) + spacing * Math.Max(0, rows.Count - 1);
        return new Result(rows, total);
    }

    /// <summary>
    /// Altura estimada de un mes sin hidratar, a partir de su conteo. Se usa
    /// para reservar el scroll; al hidratar se sustituye por la exacta y el
    /// contenedor compensa la diferencia.
    /// </summary>
    public static double EstimateHeight(
        int count,
        double containerWidth,
        double targetRowHeight,
        double spacing = 4,
        double assumedAspect = FallbackAspect)
    {
        if (count <= 0 || containerWidth <= 0 || targetRowHeight <= 0) return 0;
        // Espejo del cierre de filas de Compute: una fila se cierra en cuanto
        // desborda a altura objetivo, así que lleva ceil(W / (a·target))
        // celdas y su altura justificada queda por DEBAJO del objetivo.
        var perRow = Math.Max(1, (int)Math.Ceiling(containerWidth / (assumedAspect * targetRowHeight)));
        var rowHeight = (containerWidth - spacing * (perRow - 1)) / (perRow * assumedAspect);
        var rowCount = (int)Math.Ceiling(count / (double)perRow);
        return rowCount * rowHeight + spacing * Math.Max(0, rowCount - 1);
    }

    private static double Clamp(double aspect)
    {
        if (double.IsNaN(aspect) || aspect <= 0) return FallbackAspect;
        return Math.Clamp(aspect, MinAspect, MaxAspect);
    }
}
