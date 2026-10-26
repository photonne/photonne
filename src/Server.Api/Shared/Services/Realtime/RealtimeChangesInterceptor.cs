using System.Runtime.CompilerServices;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.ChangeTracking;
using Microsoft.EntityFrameworkCore.Diagnostics;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.Realtime;

/// <summary>
/// Turns saved changes into <see cref="UserEventBus"/> events, so every way a
/// photo or a notification changes (an upload, the indexer, the app, a rule)
/// reaches the open clients without each endpoint having to remember to say
/// so. Read before the save (the change tracker forgets after), published only
/// once it succeeded.
/// </summary>
public sealed class RealtimeChangesInterceptor(UserEventBus bus) : SaveChangesInterceptor
{
    // An asset's own bookkeeping (scan dates, enrichment flags…) changes all
    // the time in the background; only these change what a client shows.
    private static readonly string[] VisibleAssetProperties =
    [
        nameof(Asset.DeletedAt),
        nameof(Asset.IsArchived),
        nameof(Asset.IsFavorite),
        nameof(Asset.CapturedAt),
        nameof(Asset.FolderId),
        nameof(Asset.IsFileMissing),
    ];

    private readonly ConditionalWeakTable<DbContext, HashSet<(Guid UserId, string Type)>> _pending = new();

    public override InterceptionResult<int> SavingChanges(DbContextEventData eventData, InterceptionResult<int> result)
    {
        Collect(eventData.Context);
        return result;
    }

    public override ValueTask<InterceptionResult<int>> SavingChangesAsync(
        DbContextEventData eventData, InterceptionResult<int> result, CancellationToken cancellationToken = default)
    {
        Collect(eventData.Context);
        return ValueTask.FromResult(result);
    }

    public override int SavedChanges(SaveChangesCompletedEventData eventData, int result)
    {
        Publish(eventData.Context);
        return result;
    }

    public override ValueTask<int> SavedChangesAsync(
        SaveChangesCompletedEventData eventData, int result, CancellationToken cancellationToken = default)
    {
        Publish(eventData.Context);
        return ValueTask.FromResult(result);
    }

    public override void SaveChangesFailed(DbContextErrorEventData eventData)
    {
        if (eventData.Context is { } context) _pending.Remove(context);
    }

    public override Task SaveChangesFailedAsync(DbContextErrorEventData eventData, CancellationToken cancellationToken = default)
    {
        if (eventData.Context is { } context) _pending.Remove(context);
        return Task.CompletedTask;
    }

    private void Collect(DbContext? context)
    {
        if (context is null) return;
        var events = new HashSet<(Guid, string)>();
        foreach (var entry in context.ChangeTracker.Entries())
        {
            switch (entry.Entity)
            {
                case Asset asset when ChangesWhatShows(entry):
                    if (asset.OwnerId is Guid owner) events.Add((owner, UserEventTypes.Library));
                    break;
                case Notification notification when entry.State is EntityState.Added or EntityState.Modified or EntityState.Deleted:
                    events.Add((notification.UserId, UserEventTypes.Notifications));
                    break;
            }
        }
        if (events.Count > 0) _pending.AddOrUpdate(context, events);
    }

    private static bool ChangesWhatShows(EntityEntry entry) => entry.State switch
    {
        EntityState.Added or EntityState.Deleted => true,
        EntityState.Modified => VisibleAssetProperties.Any(name => entry.Property(name).IsModified),
        _ => false,
    };

    private void Publish(DbContext? context)
    {
        if (context is null || !_pending.TryGetValue(context, out var events)) return;
        _pending.Remove(context);
        foreach (var (userId, type) in events) bus.Publish(userId, type);
    }
}
