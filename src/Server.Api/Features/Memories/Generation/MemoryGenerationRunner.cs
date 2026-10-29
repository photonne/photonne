using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Regenerates the Recuerdos feed of every active user. Per-user by necessity:
/// face identity is private, so the same shared photo yields different
/// memories for different people. Shared by the nightly run and
/// <see cref="MemoryDailyRefreshService"/>.
/// </summary>
internal static class MemoryGenerationRunner
{
    public sealed record Totals(int UsersProcessed, int UsersFailed, int Created, int Updated, int Removed);

    public static async Task<Totals> RunForAllUsersAsync(IServiceScopeFactory scopeFactory, string logTag, CancellationToken ct)
    {
        int created = 0, updated = 0, removed = 0, usersProcessed = 0, usersFailed = 0;

        List<Guid> userIds;
        using (var scope = scopeFactory.CreateScope())
        {
            var dbContext = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
            userIds = await dbContext.Users
                .Where(u => u.IsActive)
                .Select(u => u.Id)
                .ToListAsync(ct);
        }

        foreach (var userId in userIds)
        {
            if (ct.IsCancellationRequested) break;
            try
            {
                // A scope per user: MemoryGenerationService depends on
                // AssetVisibilityService, which caches permission snapshots on the
                // instance and must never be shared across users.
                using var userScope = scopeFactory.CreateScope();
                var generation = userScope.ServiceProvider.GetRequiredService<MemoryGenerationService>();

                var result = await generation.RunForUserAsync(userId, ct);
                created += result.Created;
                updated += result.Updated;
                removed += result.Removed;
                usersProcessed++;
            }
            catch (OperationCanceledException) { throw; }
            catch (Exception ex)
            {
                usersFailed++;
                Console.WriteLine($"[{logTag}] Memories error for user {userId}: {ex.Message}");
            }
        }

        return new Totals(usersProcessed, usersFailed, created, updated, removed);
    }
}
