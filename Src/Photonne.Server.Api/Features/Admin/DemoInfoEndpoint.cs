using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>
/// Anonymous endpoint consumed by the client to know whether the backend is running
/// as a public demo, and when the next reset is scheduled. Used by MainLayout to
/// show the demo banner and by the login page to show the "log in as demo" shortcut.
/// </summary>
public class DemoInfoEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/admin/demo-info", GetDemoInfo)
            .WithName("GetDemoInfo")
            .WithTags("Admin")
            .WithDescription("Returns whether demo mode is enabled and reset metadata.")
            .AllowAnonymous();
    }

    private static IResult GetDemoInfo(
        IOptions<DemoModeOptions> options,
        DemoResetService resetService)
    {
        var opts = options.Value;
        return Results.Ok(new DemoInfoResponse
        {
            Enabled = opts.Enabled,
            DemoUsername = opts.Enabled ? opts.DemoUsername : null,
            DemoPassword = opts.Enabled ? opts.DemoPassword : null,
            ResetIntervalHours = opts.Enabled ? opts.ResetIntervalHours : null,
            NextResetAt = opts.Enabled && resetService.NextResetAt != default
                ? resetService.NextResetAt
                : null
        });
    }
}

public sealed record DemoInfoResponse
{
    public bool Enabled { get; init; }
    public string? DemoUsername { get; init; }
    public string? DemoPassword { get; init; }
    public int? ResetIntervalHours { get; init; }
    public DateTimeOffset? NextResetAt { get; init; }
}
