namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Pure functions for the retry-with-backoff policy used by
/// <see cref="EnrichmentWorker"/>. Lives in its own class so the policy can
/// be unit-tested without spinning up a DbContext or the full host.
/// </summary>
public static class EnrichmentBackoff
{
    /// <summary>
    /// Delays applied BEFORE the next retry attempt. <c>Delays[i]</c> is the
    /// wait after a task's <c>AttemptCount</c> reached <c>i+1</c>. Once
    /// <c>AttemptCount</c> exceeds the array length the task is permanently
    /// Failed and only the manual retry endpoint can reset it.
    /// </summary>
    public static readonly TimeSpan[] Delays = new[]
    {
        TimeSpan.FromMinutes(1),
        TimeSpan.FromMinutes(5),
        TimeSpan.FromMinutes(15),
        TimeSpan.FromHours(1),
        TimeSpan.FromHours(6),
    };

    /// <summary>
    /// Returns the absolute <c>NextRetryAt</c> for a task that just failed,
    /// or <c>null</c> when retries are exhausted (caller treats it as a
    /// permanent Failed row).
    /// </summary>
    public static DateTime? ComputeNextRetry(int attemptCount, DateTime now)
    {
        if (attemptCount < 1) return null; // never reached; bail safely.
        if (attemptCount > Delays.Length) return null; // exhausted.
        return now + Delays[attemptCount - 1];
    }

    /// <summary>
    /// Convenience predicate matching the rule the worker uses to decide
    /// whether to schedule another attempt.
    /// </summary>
    public static bool HasRetriesLeft(int attemptCount) =>
        attemptCount >= 1 && attemptCount <= Delays.Length;
}
