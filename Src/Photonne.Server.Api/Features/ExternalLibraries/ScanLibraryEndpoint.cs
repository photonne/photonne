using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.ExternalLibraries;

public class ScanLibraryEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/libraries/{id:guid}/scan/stream", HandleStream)
            .WithName("ScanLibraryStream")
            .WithTags("External Libraries")
            .WithDescription("Streams real-time progress while scanning an external library")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private static async IAsyncEnumerable<ScanProgressUpdate> HandleStream(
        Guid id,
        [FromServices] ExternalLibraryScanService scanService,
        HttpContext ctx,
        [System.Runtime.CompilerServices.EnumeratorCancellation] CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null)
        {
            yield return new ScanProgressUpdate("Unauthorized.", 0, 0, 0, 0, true, "Unauthorized.");
            yield break;
        }

        await foreach (var update in scanService.ScanAsync(id, ct))
            yield return update;
    }

    private static Guid? GetUserId(HttpContext ctx)
    {
        var value = ctx.User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return Guid.TryParse(value, out var id) ? id : null;
    }
}
