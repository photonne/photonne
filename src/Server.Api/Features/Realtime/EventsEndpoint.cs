using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services.Realtime;

namespace Photonne.Server.Api.Features.Realtime;

/// <summary>
/// Server-Sent Events: what changed for the caller (see
/// <see cref="UserEventTypes"/>), so the web and the apps reload by
/// themselves when photos arrive from the phone, the indexer or another
/// device. Each event only says what to reload; the data comes from the usual
/// endpoints. A burst (an import of a thousand photos) is sent as one event
/// per type a second, and a comment every 25 s keeps proxies from closing an
/// idle stream.
/// </summary>
public class EventsEndpoint : IEndpoint
{
    private static readonly TimeSpan Heartbeat = TimeSpan.FromSeconds(25);
    private static readonly TimeSpan Coalesce = TimeSpan.FromSeconds(1);

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/events", Handle)
            .WithName("StreamEvents")
            .WithTags("Realtime")
            .WithDescription("Server-Sent Events stream of what changed for the caller (library, notifications)")
            .Produces<string>(StatusCodes.Status200OK, "text/event-stream")
            .Produces(StatusCodes.Status401Unauthorized)
            .RequireAuthorization();
    }

    private static async Task Handle(
        HttpContext context,
        [FromServices] UserEventBus bus,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var userId))
        {
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            return;
        }

        context.Response.ContentType = "text/event-stream";
        context.Response.Headers.CacheControl = "no-cache";
        // nginx and friends buffer responses unless told not to.
        context.Response.Headers["X-Accel-Buffering"] = "no";

        using var subscription = bus.Subscribe(userId);
        var reader = subscription.Reader;
        try
        {
            await WriteAsync(context, ": connected\n\n", ct);
            Task<bool>? waiting = null;
            while (!ct.IsCancellationRequested)
            {
                waiting ??= reader.WaitToReadAsync(ct).AsTask();
                if (await Task.WhenAny(waiting, Task.Delay(Heartbeat, ct)) != waiting)
                {
                    await WriteAsync(context, ": ping\n\n", ct);
                    continue;
                }
                if (!await waiting) break;
                waiting = null;

                // Let the burst finish, then say each kind of change once.
                await Task.Delay(Coalesce, ct);
                var types = new SortedSet<string>(StringComparer.Ordinal);
                while (reader.TryRead(out var type)) types.Add(type);
                var message = new StringBuilder();
                foreach (var type in types) message.Append("event: ").Append(type).Append("\ndata: {}\n\n");
                if (message.Length > 0) await WriteAsync(context, message.ToString(), ct);
            }
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // The client went away.
        }
    }

    private static async Task WriteAsync(HttpContext context, string text, CancellationToken ct)
    {
        await context.Response.WriteAsync(text, ct);
        await context.Response.Body.FlushAsync(ct);
    }
}
