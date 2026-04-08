using System.Runtime.CompilerServices;
using System.Text.Json;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Tasks;

/// <summary>
/// Endpoints for querying and subscribing to background admin tasks.
/// </summary>
public class BackgroundTasksEndpoint : IEndpoint
{
    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        // List all active/recent tasks
        app.MapGet("/api/tasks", ([Microsoft.AspNetCore.Mvc.FromServices] BackgroundTaskManager manager) =>
        {
            var tasks = manager.GetAll().Select(e => new
            {
                id = e.Id,
                type = e.Type.ToString(),
                status = e.Status,
                percentage = e.Percentage,
                lastMessage = e.LastMessage,
                startedAt = e.StartedAt,
                finishedAt = e.FinishedAt,
                parameters = e.Parameters
            });
            return Results.Ok(tasks);
        })
        .WithName("GetBackgroundTasks")
        .WithTags("Tasks")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));

        // Cancel a running task
        app.MapDelete("/api/tasks/{id:guid}", (
            Guid id,
            [Microsoft.AspNetCore.Mvc.FromServices] BackgroundTaskManager manager) =>
        {
            var entry = manager.Get(id);
            if (entry == null) return Results.NotFound();
            if (entry.IsFinished) return Results.BadRequest("Task already finished.");

            entry.Cts.Cancel();
            entry.Finish("Cancelled");
            return Results.NoContent();
        })
        .WithName("CancelBackgroundTask")
        .WithTags("Tasks")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));

        // Subscribe to a task's live update stream (SSE via IAsyncEnumerable<JsonElement>)
        // Returns all buffered updates from the start, then live ones.
        app.MapGet("/api/tasks/{id:guid}/stream", (
            Guid id,
            [Microsoft.AspNetCore.Mvc.FromServices] BackgroundTaskManager manager,
            CancellationToken cancellationToken) =>
            StreamTask(id, manager, cancellationToken))
        .WithName("StreamBackgroundTask")
        .WithTags("Tasks")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private static async IAsyncEnumerable<JsonElement> StreamTask(
        Guid id,
        BackgroundTaskManager manager,
        [EnumeratorCancellation] CancellationToken ct)
    {
        var entry = manager.Get(id);
        if (entry == null) yield break;

        await foreach (var json in entry.StreamAsync(0, ct))
        {
            JsonElement element;
            try
            {
                // Clone makes the element independent of the document's lifetime
                using var doc = JsonDocument.Parse(json);
                element = doc.RootElement.Clone();
            }
            catch { continue; }
            yield return element;
        }
    }
}
