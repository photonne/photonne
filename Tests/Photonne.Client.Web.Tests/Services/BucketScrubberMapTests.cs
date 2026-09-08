using Photonne.Client.Web.Models;
using Photonne.Client.Web.Services;
using Xunit;

namespace Photonne.Client.Web.Tests.Services;

public class BucketScrubberMapTests
{
    private static List<TimelineBucket> Buckets => new()
    {
        new TimelineBucket { Key = "2026-06", Count = 100 },
        new TimelineBucket { Key = "2026-05", Count = 300 },
        new TimelineBucket { Key = "2025-12", Count = 600 },
    };

    [Fact]
    public void Fractions_are_proportional_to_counts_not_month_count()
    {
        var map = BucketScrubberMap.Build(Buckets)!;
        // 2026-06 ocupa [0, 0.1), 2026-05 [0.1, 0.4), 2025-12 [0.4, 1].
        Assert.Equal("2026-06", map.KeyForFraction(0));
        Assert.Equal("2026-06", map.KeyForFraction(0.05));
        Assert.Equal("2026-05", map.KeyForFraction(0.1));
        Assert.Equal("2026-05", map.KeyForFraction(0.39));
        Assert.Equal("2025-12", map.KeyForFraction(0.4));
        Assert.Equal("2025-12", map.KeyForFraction(1));
    }

    [Fact]
    public void Fraction_for_key_is_the_bucket_start()
    {
        var map = BucketScrubberMap.Build(Buckets)!;
        Assert.Equal(0, map.FractionForKey("2026-06"));
        Assert.Equal(0.1, map.FractionForKey("2026-05"), precision: 10);
        Assert.Equal(0.4, map.FractionForKey("2025-12"), precision: 10);
        Assert.Equal(0, map.FractionForKey("1999-01"));
    }

    [Fact]
    public void Year_markers_appear_once_at_the_first_bucket_of_each_year()
    {
        var map = BucketScrubberMap.Build(Buckets)!;
        Assert.Equal(2, map.YearMarkers.Count);
        Assert.Equal(("2026", 0d), map.YearMarkers[0]);
        Assert.Equal("2025", map.YearMarkers[1].Year);
        Assert.Equal(0.4, map.YearMarkers[1].Fraction, precision: 10);
    }

    [Fact]
    public void Out_of_range_fractions_clamp_to_the_ends()
    {
        var map = BucketScrubberMap.Build(Buckets)!;
        Assert.Equal("2026-06", map.KeyForFraction(-3));
        Assert.Equal("2025-12", map.KeyForFraction(7));
    }

    [Fact]
    public void Empty_or_zero_count_skeletons_build_nothing()
    {
        Assert.Null(BucketScrubberMap.Build(new List<TimelineBucket>()));
        Assert.Null(BucketScrubberMap.Build(new List<TimelineBucket>
        {
            new() { Key = "2026-01", Count = 0 }
        }));
    }
}
