using System.Collections.Concurrent;
using System.Threading.Channels;

namespace Photonne.Server.Api.Shared.Services.Realtime;

/// <summary>What changed for a user, as the clients' event stream names it.</summary>
public static class UserEventTypes
{
    /// <summary>Photos added, gone, back, archived, favourited, re-dated or moved: reload what shows them.</summary>
    public const string Library = "library";

    /// <summary>A notification arrived or changed: reload the bell.</summary>
    public const string Notifications = "notifications";
}

/// <summary>
/// In-process fan-out of <see cref="UserEventTypes"/> to each user's open
/// event streams (<c>GET /api/events</c>). Nothing is stored: a client that
/// isn't connected simply reloads when it comes back. One process only — a
/// second replica would need a shared bus (Postgres LISTEN/NOTIFY).
/// </summary>
public sealed class UserEventBus
{
    // Bounded per connection: a client that stops reading loses the oldest
    // events, which is fine — each one only says "reload this".
    private const int Capacity = 64;

    private readonly ConcurrentDictionary<Guid, ConcurrentDictionary<Guid, Channel<string>>> _subscribers = new();

    public Subscription Subscribe(Guid userId)
    {
        var channel = Channel.CreateBounded<string>(new BoundedChannelOptions(Capacity)
        {
            FullMode = BoundedChannelFullMode.DropOldest,
            SingleReader = true,
        });
        var id = Guid.NewGuid();
        _subscribers.GetOrAdd(userId, _ => new()).TryAdd(id, channel);
        return new Subscription(this, userId, id, channel.Reader);
    }

    public void Publish(Guid userId, string type)
    {
        if (!_subscribers.TryGetValue(userId, out var connections)) return;
        foreach (var channel in connections.Values) channel.Writer.TryWrite(type);
    }

    /// <summary>Open streams of <paramref name="userId"/>.</summary>
    public int ConnectionCount(Guid userId) =>
        _subscribers.TryGetValue(userId, out var connections) ? connections.Count : 0;

    private void Unsubscribe(Guid userId, Guid id)
    {
        if (!_subscribers.TryGetValue(userId, out var connections)) return;
        if (connections.TryRemove(id, out var channel)) channel.Writer.TryComplete();
        if (connections.IsEmpty) _subscribers.TryRemove(new KeyValuePair<Guid, ConcurrentDictionary<Guid, Channel<string>>>(userId, connections));
    }

    public sealed class Subscription(UserEventBus bus, Guid userId, Guid id, ChannelReader<string> reader) : IDisposable
    {
        public ChannelReader<string> Reader { get; } = reader;

        public void Dispose() => bus.Unsubscribe(userId, id);
    }
}
