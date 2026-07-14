using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Features.Memories.Generation;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;

namespace Photonne.Server.Api.Features.Memories;

/// <summary>Admin-only: regenerates the Recuerdos feed for every active user
/// right now, instead of waiting for the nightly pass. Same code path as
/// NightlySchedulerService.RunMemoriesAsync.</summary>
public class MemoriesRebuildEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/maintenance")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapPost("/memories/rebuild", Handle)
            .WithName("RebuildMemories")
            .WithDescription("Regenerates every active user's memories feed");
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] IServiceScopeFactory scopeFactory,
        CancellationToken ct)
    {
        var userIds = await db.Users
            .Where(u => u.IsActive)
            .Select(u => u.Id)
            .ToListAsync(ct);

        int created = 0, updated = 0, removed = 0, failed = 0;

        foreach (var userId in userIds)
        {
            try
            {
                // Fresh scope per user — AssetVisibilityService caches a
                // permission snapshot per instance and must not cross users.
                using var scope = scopeFactory.CreateScope();
                var generation = scope.ServiceProvider.GetRequiredService<MemoryGenerationService>();

                var result = await generation.RunForUserAsync(userId, ct);
                created += result.Created;
                updated += result.Updated;
                removed += result.Removed;
            }
            catch (OperationCanceledException) { throw; }
            catch (Exception ex)
            {
                failed++;
                Console.WriteLine($"[MEMORIES] Rebuild failed for user {userId}: {ex.Message}");
            }
        }

        return Results.Ok(new
        {
            users = userIds.Count,
            created,
            updated,
            removed,
            failed,
            message = $"Procesados {userIds.Count} usuario(s): {created} nuevos, {updated} actualizados, {removed} retirados, {failed} error(es).",
        });
    }
}
