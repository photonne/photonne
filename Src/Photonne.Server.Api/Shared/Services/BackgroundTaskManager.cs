using System.Collections.Concurrent;
using System.Runtime.CompilerServices;

namespace Photonne.Server.Api.Shared.Services;

public enum BackgroundTaskType { IndexAssets, Thumbnails, Metadata, LibraryScan }

/// <summary>
/// Tracks a single background admin task that survives HTTP disconnections.
/// Multiple HTTP clients can subscribe to the same task via StreamAsync().
/// </summary>
public class BackgroundTaskEntry
{
    public Guid Id { get; } = Guid.NewGuid();
    public BackgroundTaskType Type { get; init; }
    public DateTime StartedAt { get; } = DateTime.UtcNow;
    public DateTime? FinishedAt { get; private set; }
    public string Status { get; private set; } = "Running"; // Running | Completed | Cancelled | Failed
    public double Percentage { get; private set; }
    public string LastMessage { get; private set; } = "";
    public IReadOnlyDictionary<string, string> Parameters { get; init; } = new Dictionary<string, string>();

    /// <summary>Independent CTS — not tied to any HTTP request lifetime.</summary>
    public CancellationTokenSource Cts { get; } = new();

    // ── Update history & fan-out ─────────────────────────────────────────────

    private readonly List<string> _updates = new();
    private readonly List<TaskCompletionSource<int>> _waiters = new();
    private readonly object _sync = new();
    private bool _finished;

    /// <summary>
    /// Push a serialized JSON update from the background worker.
    /// Thread-safe; wakes all waiting subscribers.
    /// </summary>
    public void Push(string jsonUpdate, double percentage, string message)
    {
        lock (_sync)
        {
            Percentage = percentage;
            LastMessage = message;
            _updates.Add(jsonUpdate);
            var count = _updates.Count;
            foreach (var w in _waiters) w.TrySetResult(count);
            _waiters.Clear();
        }
    }

    /// <summary>Mark task as finished and release all waiting subscribers.</summary>
    public void Finish(string status)
    {
        lock (_sync)
        {
            Status = status;
            FinishedAt = DateTime.UtcNow;
            _finished = true;
            foreach (var w in _waiters) w.TrySetResult(_updates.Count);
            _waiters.Clear();
        }
    }

    public bool IsFinished => _finished;

    public int UpdateCount { get { lock (_sync) return _updates.Count; } }

    /// <summary>
    /// Async stream of all task updates starting from <paramref name="fromIndex"/>.
    /// Replays buffered items first, then blocks until new items arrive or task finishes.
    /// Multiple callers can subscribe concurrently.
    /// </summary>
    public async IAsyncEnumerable<string> StreamAsync(
        int fromIndex,
        [EnumeratorCancellation] CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            int count;
            bool finished;
            TaskCompletionSource<int>? waiter = null;

            lock (_sync)
            {
                count = _updates.Count;
                finished = _finished;

                if (fromIndex >= count && !finished)
                {
                    waiter = new TaskCompletionSource<int>(TaskCreationOptions.RunContinuationsAsynchronously);
                    _waiters.Add(waiter);
                }
            }

            // Drain all buffered updates
            while (fromIndex < count && !ct.IsCancellationRequested)
            {
                string item;
                lock (_sync) item = _updates[fromIndex];
                yield return item;
                fromIndex++;
            }

            if (finished && fromIndex >= count) yield break;

            if (waiter != null)
            {
                using var reg = ct.Register(() => waiter.TrySetCanceled());
                try { count = await waiter.Task; }
                catch (OperationCanceledException) { yield break; }
            }
        }
    }
}

/// <summary>
/// Singleton registry of all active (and recently finished) background admin tasks.
/// </summary>
public class BackgroundTaskManager
{
    private readonly ConcurrentDictionary<Guid, BackgroundTaskEntry> _tasks = new();

    /// <summary>Register a new task and return its entry (with a fresh, independent CTS).</summary>
    public BackgroundTaskEntry Register(BackgroundTaskType type, Dictionary<string, string>? parameters = null)
    {
        CleanupOld(TimeSpan.FromHours(1));

        var entry = new BackgroundTaskEntry
        {
            Type = type,
            Parameters = parameters ?? new Dictionary<string, string>()
        };
        _tasks[entry.Id] = entry;
        return entry;
    }

    public BackgroundTaskEntry? Get(Guid id) => _tasks.TryGetValue(id, out var e) ? e : null;

    public IEnumerable<BackgroundTaskEntry> GetAll() => _tasks.Values;

    public IEnumerable<BackgroundTaskEntry> GetRunning() =>
        _tasks.Values.Where(e => e.Status == "Running");

    public IEnumerable<BackgroundTaskEntry> GetByType(BackgroundTaskType type) =>
        _tasks.Values.Where(e => e.Type == type);

    public BackgroundTaskEntry? GetRunningByType(BackgroundTaskType type) =>
        _tasks.Values.FirstOrDefault(e => e.Type == type && e.Status == "Running");

    public void CleanupOld(TimeSpan maxAge)
    {
        var cutoff = DateTime.UtcNow - maxAge;
        foreach (var entry in _tasks.Values
                     .Where(e => e.IsFinished && e.FinishedAt < cutoff)
                     .ToList())
        {
            _tasks.TryRemove(entry.Id, out _);
        }
    }
}
