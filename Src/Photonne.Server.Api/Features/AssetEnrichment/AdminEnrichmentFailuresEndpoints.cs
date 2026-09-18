using System.Globalization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.AssetEnrichment;

/// <summary>
/// Admin-wide registry of enrichment failures: every Failed/Suppressed task row
/// across ALL users, with its cause and attempt count, plus the retry/suppress
/// actions the "Assets con problemas" screen offers. Only the latest row per
/// (asset, task type) counts — older rows superseded by a newer attempt are
/// history, not open problems.
/// </summary>
public class AdminEnrichmentFailuresEndpoints : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/enrichment/failures")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("", HandleList)
            .WithName("AdminListEnrichmentFailures")
            .WithDescription("Lists Failed and Suppressed enrichment tasks across all users, newest first.");

        group.MapPost("/{taskId:guid}/retry", HandleRetry)
            .WithName("AdminRetryEnrichmentFailure")
            .WithDescription("Resets one Failed/Suppressed task back to Pending and re-enqueues it.");

        group.MapPost("/retry-all", HandleRetryAll)
            .WithName("AdminRetryAllEnrichmentFailures")
            .WithDescription("Resets every definitively Failed task (optionally of one type or cause) back to Pending. Suppressed rows, and the ones with a retry still scheduled, are left alone.");

        group.MapPost("/{taskId:guid}/suppress", HandleSuppress)
            .WithName("AdminSuppressEnrichmentFailure")
            .WithDescription("Marks one Failed task as Suppressed so no sweep or backfill ever retries the asset again.");
    }

    private sealed record AdminEnrichmentFailureDto(
        Guid TaskId,
        Guid AssetId,
        string FileName,
        DateTime FileCreatedAt,
        Guid? OwnerId,
        string? OwnerName,
        string TaskType,
        string Status,
        string? ErrorMessage,
        int AttemptCount,
        bool IsPermanent,
        DateTime? LastAttemptAt,
        // What kind of failure this was — "Transient" / "Permanent" /
        // "NeedsAction" / "Unknown". IsPermanent only says the attempts ran
        // out, which happens just as surely when the ML container is down as
        // when the file is corrupt; this is what separates the two.
        string FailureKind,
        // The service's own error token, when it sent one. Lets a wall of
        // failures read as "3.412 veces model_not_loaded" instead of 3.412
        // sentences.
        string? FailureCode);

    private sealed record AdminEnrichmentFailuresResponse(
        IReadOnlyList<AdminEnrichmentFailureDto> Items,
        string? NextCursor,
        int Total,
        IReadOnlyDictionary<string, int> CountsByType,
        // Same idea as CountsByType, by cause: it answers "is this worth
        // retrying?" before the admin reads a single row.
        IReadOnlyDictionary<string, int> CountsByKind,
        // The listed rows that Total leaves out, under the same filter, so the
        // screen can say why there are more rows than problems: the ones still
        // retrying on their own, and the ones somebody dismissed.
        int Retrying,
        int Suppressed);

    private async Task<IResult> HandleList(
        [FromQuery] string? type,
        [FromQuery] string? kind,
        [FromQuery] string? cursor,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken,
        [FromQuery] int pageSize = 50)
    {
        AssetEnrichmentType? parsedType = null;
        if (!string.IsNullOrWhiteSpace(type))
        {
            if (!Enum.TryParse<AssetEnrichmentType>(type, ignoreCase: true, out var value))
            {
                return Results.BadRequest(new
                {
                    error = $"Unknown task type '{type}'. Valid: {string.Join(", ", Enum.GetNames<AssetEnrichmentType>())}"
                });
            }
            parsedType = value;
        }

        EnrichmentFailureKind? parsedKind = null;
        if (!string.IsNullOrWhiteSpace(kind))
        {
            if (!Enum.TryParse<EnrichmentFailureKind>(kind, ignoreCase: true, out var value))
            {
                return Results.BadRequest(new
                {
                    error = $"Unknown failure kind '{kind}'. Valid: {string.Join(", ", Enum.GetNames<EnrichmentFailureKind>())}"
                });
            }
            parsedKind = value;
        }

        var capped = Math.Clamp(pageSize, 1, 200);

        var open = EnrichmentFailureQueries.OpenProblems(dbContext).AsNoTracking();

        // Every number on the screen counts the definitive ones only — the same
        // thing "N con errores" counts on Run Tasks, so the chip you land on
        // says what the row you tapped said. The list below still shows the
        // rows that are retrying on their own and the dismissed ones, with
        // their badge: they're worth seeing, they just aren't waiting for you.
        var definitive = open.Definitive();

        // Chip counters always span every type so switching filters never hides
        // where the remaining problems live.
        var countsByType = (await definitive
                .GroupBy(t => t.TaskType)
                .Select(g => new { g.Key, Count = g.Count() })
                .ToListAsync(cancellationToken))
            .ToDictionary(g => g.Key.ToString(), g => g.Count);

        var countsByKind = (await definitive
                .GroupBy(t => t.FailureKind)
                .Select(g => new { g.Key, Count = g.Count() })
                .ToListAsync(cancellationToken))
            .ToDictionary(g => g.Key.ToString(), g => g.Count);

        var filtered = parsedType.HasValue
            ? open.Where(t => t.TaskType == parsedType.Value)
            : open;
        if (parsedKind.HasValue)
            filtered = filtered.Where(t => t.FailureKind == parsedKind.Value);

        var total = await filtered.Definitive().CountAsync(cancellationToken);
        var retrying = await filtered.Retrying().CountAsync(cancellationToken);
        var suppressed = await filtered.CountAsync(t => t.Status == EnrichmentStatus.Suppressed, cancellationToken);

        // Keyset pagination on (CreatedAt desc, Id desc): stable under the
        // retries/suppressions the screen itself triggers between pages.
        if (TryParseCursor(cursor, out var cursorCreatedAt, out var cursorId))
        {
            filtered = filtered.Where(t =>
                t.CreatedAt < cursorCreatedAt ||
                (t.CreatedAt == cursorCreatedAt && t.Id.CompareTo(cursorId) < 0));
        }

        var page = await filtered
            .OrderByDescending(t => t.CreatedAt)
            .ThenByDescending(t => t.Id)
            .Take(capped + 1)
            .Select(t => new
            {
                t.Id, t.AssetId, t.TaskType, t.Status, t.ErrorMessage, t.AttemptCount,
                t.NextRetryAt, t.CreatedAt, t.StartedAt, t.CompletedAt,
                t.FailureKind, t.FailureCode,
                t.Asset.FileName, t.Asset.FileCreatedAt, t.Asset.OwnerId,
                OwnerName = t.Asset.Owner != null ? t.Asset.Owner.Username : null
            })
            .ToListAsync(cancellationToken);

        var hasMore = page.Count > capped;
        if (hasMore) page.RemoveAt(page.Count - 1);

        var items = page.Select(t => new AdminEnrichmentFailureDto(
            t.Id, t.AssetId, t.FileName, t.FileCreatedAt, t.OwnerId, t.OwnerName,
            t.TaskType.ToString(), t.Status.ToString(), t.ErrorMessage, t.AttemptCount,
            IsPermanent: t.Status == EnrichmentStatus.Failed && t.NextRetryAt == null,
            LastAttemptAt: t.CompletedAt ?? t.StartedAt ?? t.CreatedAt,
            FailureKind: t.FailureKind.ToString(),
            FailureCode: t.FailureCode)).ToList();

        var nextCursor = hasMore
            ? FormatCursor(page[^1].CreatedAt, page[^1].Id)
            : null;

        return Results.Ok(new AdminEnrichmentFailuresResponse(
            items, nextCursor, total, countsByType, countsByKind, retrying, suppressed));
    }

    private async Task<IResult> HandleRetry(
        Guid taskId,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IEnrichmentService enrichmentService,
        CancellationToken cancellationToken)
    {
        var exists = await dbContext.AssetEnrichmentTasks
            .AnyAsync(t => t.Id == taskId, cancellationToken);
        if (!exists) return Results.NotFound();

        var ok = await enrichmentService.ResetAndEnqueueAsync(taskId, cancellationToken);
        if (!ok) return Results.NotFound();

        return Results.Ok(new { taskId, status = EnrichmentStatus.Pending.ToString() });
    }

    private sealed record RetryAllResponse(int Retried);

    private async Task<IResult> HandleRetryAll(
        [FromQuery] string? type,
        [FromQuery] string? kind,
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IEnrichmentService enrichmentService,
        CancellationToken cancellationToken)
    {
        AssetEnrichmentType? parsedType = null;
        if (!string.IsNullOrWhiteSpace(type))
        {
            if (!Enum.TryParse<AssetEnrichmentType>(type, ignoreCase: true, out var value))
                return Results.BadRequest(new { error = $"Unknown task type '{type}'." });
            parsedType = value;
        }

        EnrichmentFailureKind? parsedKind = null;
        if (!string.IsNullOrWhiteSpace(kind))
        {
            if (!Enum.TryParse<EnrichmentFailureKind>(kind, ignoreCase: true, out var value))
                return Results.BadRequest(new { error = $"Unknown failure kind '{kind}'." });
            parsedKind = value;
        }

        // Exactly the rows the button's count announced.
        var query = EnrichmentFailureQueries.OpenProblems(dbContext).AsNoTracking().Definitive();
        if (parsedType.HasValue)
            query = query.Where(t => t.TaskType == parsedType.Value);
        // Retrying the ones whose cause is the file itself only reproduces the
        // same failure and buries the queue again.
        if (parsedKind.HasValue)
            query = query.Where(t => t.FailureKind == parsedKind.Value);

        var ids = await query.Select(t => t.Id).ToListAsync(cancellationToken);

        var retried = 0;
        foreach (var id in ids)
        {
            if (cancellationToken.IsCancellationRequested) break;
            if (await enrichmentService.ResetAndEnqueueAsync(id, cancellationToken))
                retried++;
        }

        return Results.Ok(new RetryAllResponse(retried));
    }

    private async Task<IResult> HandleSuppress(
        Guid taskId,
        [FromServices] ApplicationDbContext dbContext,
        CancellationToken cancellationToken)
    {
        var task = await dbContext.AssetEnrichmentTasks
            .FirstOrDefaultAsync(t => t.Id == taskId, cancellationToken);
        if (task == null) return Results.NotFound();

        if (task.Status == EnrichmentStatus.Suppressed)
            return Results.Ok(new { taskId, status = task.Status.ToString() });

        // Only settled rows can be dismissed: a Pending/Processing row belongs
        // to the worker and flipping it here would race its own state machine.
        if (task.Status is EnrichmentStatus.Pending or EnrichmentStatus.Processing)
            return Results.Conflict(new { error = "Task is currently queued or running; cancel or let it finish first." });

        task.Status = EnrichmentStatus.Suppressed;
        task.NextRetryAt = null;
        await dbContext.SaveChangesAsync(cancellationToken);

        return Results.Ok(new { taskId, status = task.Status.ToString() });
    }

    private static string FormatCursor(DateTime createdAt, Guid id) =>
        $"{createdAt.Ticks.ToString(CultureInfo.InvariantCulture)}_{id:N}";

    private static bool TryParseCursor(string? cursor, out DateTime createdAt, out Guid id)
    {
        createdAt = default;
        id = default;
        if (string.IsNullOrWhiteSpace(cursor)) return false;
        var parts = cursor.Split('_', 2);
        if (parts.Length != 2) return false;
        if (!long.TryParse(parts[0], NumberStyles.None, CultureInfo.InvariantCulture, out var ticks)) return false;
        if (!Guid.TryParse(parts[1], out id)) return false;
        createdAt = new DateTime(ticks, DateTimeKind.Utc);
        return true;
    }
}
