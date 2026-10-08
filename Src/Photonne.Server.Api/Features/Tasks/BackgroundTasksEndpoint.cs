using System.Runtime.CompilerServices;
using System.Text.Json;
using Microsoft.AspNetCore.Http.HttpResults;
using Photonne.Server.Api.Shared.Dtos;
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
            var tasks = manager.GetAll().Select(e => new BackgroundTaskResponse(
                e.Id,
                e.Type.ToString(),
                e.Status,
                e.Percentage,
                e.LastMessage,
                e.StartedAt,
                e.FinishedAt,
                e.Parameters)).ToList();
            return TypedResults.Ok(tasks);
        })
        .WithName("GetBackgroundTasks")
        .WithTags("Tasks")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));

        // Cancel a running task
        app.MapDelete("/api/tasks/{id:guid}", Results<NoContent, NotFound<ApiError>, BadRequest<ApiError>> (
            Guid id,
            [Microsoft.AspNetCore.Mvc.FromServices] BackgroundTaskManager manager) =>
        {
            var entry = manager.Get(id);
            if (entry == null) return TypedResults.NotFound(new ApiError($"Task {id} not found", "task_not_found"));
            if (entry.IsFinished) return TypedResults.BadRequest(new ApiError("Task already finished.", "task_already_finished"));

            entry.Cts.Cancel();
            entry.Finish("Cancelled");
            return TypedResults.NoContent();
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

public sealed record BackgroundTaskResponse(
    Guid Id,
    string Type,
    string Status,
    double Percentage,
    string LastMessage,
    DateTime StartedAt,
    DateTime? FinishedAt,
    IReadOnlyDictionary<string, string> Parameters);
