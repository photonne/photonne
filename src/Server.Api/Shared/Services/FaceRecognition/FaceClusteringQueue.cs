using System.Collections.Concurrent;
using System.Threading.Channels;

namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

/// <summary>
/// In-memory work queue that hands off lazy "ensure up to date" clustering
/// passes from request handlers (<c>/api/people</c>) to
/// <see cref="FaceClusteringBackgroundService"/>. Lives only in process — a
/// missed pass is harmless because the next <c>/api/people</c> request will
/// re-enqueue it, and <see cref="FaceClusteringService.EnsureUpToDateForUserAsync"/>
/// is idempotent.
///
/// Two layers of dedup keep the worker honest:
///   * an in-flight <see cref="ConcurrentDictionary{TKey,TValue}"/> guards
///     against the same user being queued twice while a pass is pending or
///     running — bursts of refreshes collapse into a single pass;
///   * <see cref="FaceClusteringService.EnsureUpToDateForUserAsync"/>'s own
///     cooldown short-circuits passes that ran too recently.
/// </summary>
public class FaceClusteringQueue
{
    private readonly Channel<Guid> _channel = Channel.CreateUnbounded<Guid>();
    private readonly ConcurrentDictionary<Guid, byte> _inflight = new();

    /// <summary>
    /// Tries to enqueue a pass for <paramref name="userId"/>. Returns
    /// <c>false</c> when one is already queued or running for that user — the
    /// caller should treat that as success: the pending pass will pick up any
    /// new state. Safe to call from any thread; never blocks.
    /// </summary>
    public bool TryEnqueue(Guid userId)
    {
        if (!_inflight.TryAdd(userId, 0)) return false;
        if (!_channel.Writer.TryWrite(userId))
        {
            // Channel is unbounded so this only happens after Complete() —
            // i.e. shutdown. Roll back the in-flight marker so a future
            // process (after restart) can enqueue this user again.
            _inflight.TryRemove(userId, out _);
            return false;
        }
        return true;
    }

    /// <summary>Reader consumed by <see cref="FaceClusteringBackgroundService"/>.</summary>
    public ChannelReader<Guid> Reader => _channel.Reader;

    /// <summary>Releases the in-flight marker once a pass finishes (success or
    /// failure) so the user can be re-queued by the next request.</summary>
    public void MarkDone(Guid userId) => _inflight.TryRemove(userId, out _);
}
