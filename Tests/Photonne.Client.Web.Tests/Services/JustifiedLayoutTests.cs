using Photonne.Client.Web.Services;
using Xunit;

namespace Photonne.Client.Web.Tests.Services;

public class JustifiedLayoutTests
{
    private const double Width = 1000;
    private const double Target = 200;
    private const double Spacing = 4;

    [Fact]
    public void Full_rows_fill_the_container_width_exactly()
    {
        // 8 celdas 3:2 → a 200px de alto cada una mide 300px: caben 3 y pico por fila.
        var aspects = Enumerable.Repeat(1.5, 8).ToList();
        var result = JustifiedLayout.Compute(aspects, Width, Target, Spacing);

        foreach (var row in result.Rows.Take(result.Rows.Count - 1))
        {
            var contentWidth = row.Height * Enumerable.Range(row.StartIndex, row.Count)
                .Sum(i => 1.5) + Spacing * (row.Count - 1);
            Assert.Equal(Width, contentWidth, precision: 6);
        }
    }

    [Fact]
    public void Every_cell_lands_in_exactly_one_row_in_order()
    {
        var aspects = new List<double> { 1.5, 0.75, 1.0, 2.0, 1.33, 0.5, 1.5, 1.5, 1.0 };
        var result = JustifiedLayout.Compute(aspects, Width, Target, Spacing);

        var covered = result.Rows.SelectMany(r => Enumerable.Range(r.StartIndex, r.Count)).ToList();
        Assert.Equal(Enumerable.Range(0, aspects.Count), covered);
    }

    [Fact]
    public void A_partial_last_row_keeps_the_target_height()
    {
        // Una sola celda no llena la fila: no debe estirarse a pantalla completa.
        var result = JustifiedLayout.Compute(new List<double> { 1.5 }, Width, Target, Spacing);
        var row = Assert.Single(result.Rows);
        Assert.Equal(Target, row.Height);
        Assert.Equal(Target, result.TotalHeight);
    }

    [Fact]
    public void Total_height_matches_rows_plus_spacing()
    {
        var aspects = Enumerable.Repeat(1.0, 20).ToList();
        var result = JustifiedLayout.Compute(aspects, Width, Target, Spacing);
        var expected = result.Rows.Sum(r => r.Height) + Spacing * (result.Rows.Count - 1);
        Assert.Equal(expected, result.TotalHeight, precision: 6);
    }

    [Fact]
    public void Pathological_aspects_are_clamped_not_propagated()
    {
        // Panorama 10:1 y tira vertical 1:10: el layout no debe producir filas
        // absurdas ni alturas negativas.
        var result = JustifiedLayout.Compute(new List<double> { 10, 0.1, double.NaN, -3 }, Width, Target, Spacing);
        Assert.All(result.Rows, r => Assert.InRange(r.Height, 1, Target * 3));
        Assert.True(result.TotalHeight > 0);
    }

    [Fact]
    public void Estimate_scales_linearly_with_count_and_is_positive()
    {
        var one = JustifiedLayout.EstimateHeight(30, Width, Target, Spacing);
        var two = JustifiedLayout.EstimateHeight(60, Width, Target, Spacing);
        Assert.True(one > 0);
        Assert.True(two >= one * 1.8 && two <= one * 2.2);
    }

    [Fact]
    public void Estimate_is_in_the_ballpark_of_the_exact_layout_for_average_content()
    {
        var aspects = Enumerable.Repeat(JustifiedLayout.FallbackAspect, 100).ToList();
        var exact = JustifiedLayout.Compute(aspects, Width, Target, Spacing).TotalHeight;
        var estimate = JustifiedLayout.EstimateHeight(100, Width, Target, Spacing);
        // La estimación reserva scroll: basta con que esté en el mismo orden
        // de magnitud (±40%) para que la compensación posterior sea pequeña.
        Assert.InRange(estimate, exact * 0.6, exact * 1.4);
    }

    [Fact]
    public void Empty_and_degenerate_inputs_yield_zero()
    {
        Assert.Equal(0, JustifiedLayout.Compute(new List<double>(), Width, Target).TotalHeight);
        Assert.Equal(0, JustifiedLayout.EstimateHeight(0, Width, Target));
        Assert.Equal(0, JustifiedLayout.EstimateHeight(10, 0, Target));
    }
}
