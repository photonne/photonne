using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Services;

/// <summary>
/// Pure unit tests for the backoff policy. The worker is hard to exercise
/// end-to-end (BackgroundService + DB + Channels + timing) so we cover the
/// piece that actually decides retry timing here, free of infrastructure.
/// </summary>
public sealed class EnrichmentBackoffTests
{
    [Theory]
    [InlineData(1, 1)]     //  1m
    [InlineData(2, 5)]     //  5m
    [InlineData(3, 15)]    // 15m
    [InlineData(4, 60)]    //  1h
    [InlineData(5, 360)]   //  6h
    public void ComputeNextRetry_HappyPath_AddsCorrectDelay(int attempt, int expectedMinutes)
    {
        var now = new DateTime(2026, 5, 26, 12, 0, 0, DateTimeKind.Utc);

        var next = EnrichmentBackoff.ComputeNextRetry(attempt, now);

        Assert.NotNull(next);
        Assert.Equal(now.AddMinutes(expectedMinutes), next!.Value);
    }

    [Fact]
    public void ComputeNextRetry_BeyondCap_ReturnsNull()
    {
        var now = DateTime.UtcNow;
        // 6th attempt onwards is permanent Failed — no schedule.
        Assert.Null(EnrichmentBackoff.ComputeNextRetry(6, now));
        Assert.Null(EnrichmentBackoff.ComputeNextRetry(100, now));
    }

    [Fact]
    public void ComputeNextRetry_ZeroOrNegative_ReturnsNull()
    {
        // Should never happen (AttemptCount starts at 1 on first failure) but
        // we don't want to crash if it does.
        Assert.Null(EnrichmentBackoff.ComputeNextRetry(0, DateTime.UtcNow));
        Assert.Null(EnrichmentBackoff.ComputeNextRetry(-1, DateTime.UtcNow));
    }

    [Fact]
    public void HasRetriesLeft_MatchesComputeNextRetry()
    {
        // The two helpers must stay in sync — if HasRetriesLeft is true,
        // ComputeNextRetry must return a non-null timestamp.
        var now = DateTime.UtcNow;
        for (var attempt = 1; attempt <= EnrichmentBackoff.Delays.Length + 2; attempt++)
        {
            var hasRetries = EnrichmentBackoff.HasRetriesLeft(attempt);
            var next = EnrichmentBackoff.ComputeNextRetry(attempt, now);
            Assert.Equal(hasRetries, next.HasValue);
        }
    }

    [Fact]
    public void Delays_Are_Strictly_Monotonic()
    {
        // Sanity: exponential-ish backoff should grow. Catches accidental
        // reordering of the array.
        for (var i = 1; i < EnrichmentBackoff.Delays.Length; i++)
        {
            Assert.True(
                EnrichmentBackoff.Delays[i] > EnrichmentBackoff.Delays[i - 1],
                $"Delays[{i}] must be greater than Delays[{i - 1}]");
        }
    }
}
