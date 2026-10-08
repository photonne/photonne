using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Features.Settings;

public class SettingsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/settings")
            .WithTags("Settings")
            .RequireAuthorization();

        group.MapGet("", GetSetting)
            .WithName("GetSetting")
            .WithDescription("Gets a setting value by key (pass key as query string: ?key=...)");

        group.MapPost("", SaveSetting)
            .WithName("SaveSetting")
            .ProducesProblem(StatusCodes.Status403Forbidden)
            .WithDescription("Saves or updates a setting");

        var adminGroup = app.MapGroup("/api/settings")
            .WithTags("Settings")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        adminGroup.MapGet("/server-info", GetServerInfo)
            .WithName("GetServerInfo")
            .WithDescription("Gets server hardware information (processor count, etc.)");
    }

    private async Task<Results<Ok<SettingValueResponse>, UnauthorizedHttpResult>> GetSetting(
        [FromQuery] string key,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        var effectiveUserId = IsGlobalKey(key) ? Guid.Empty : userId;
        var value = await settingsService.GetSettingAsync(key, effectiveUserId);
        return TypedResults.Ok(new SettingValueResponse(key, value));
    }

    private async Task<Results<Ok<SaveSettingResponse>, BadRequest<ApiError>, UnauthorizedHttpResult, ForbidHttpResult, ProblemHttpResult>> SaveSetting(
        [FromBody] SaveSettingRequest request,
        [FromServices] SettingsService settingsService,
        [FromServices] IMlConfigClient mlConfig,
        [FromServices] IOptionsMonitor<DemoModeOptions> demoOptions,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Key))
            return TypedResults.BadRequest(new ApiError("Key is required", "key_required"));

        if (!TryGetUserId(user, out var userId))
        {
            return TypedResults.Unauthorized();
        }

        var isGlobal = IsGlobalKey(request.Key);
        // Server-wide settings must not be writable by ordinary users; only the
        // owning user's own (non-global) settings are self-service.
        if (isGlobal && !user.IsInRole("Admin"))
        {
            return TypedResults.Forbid();
        }

        // The public demo user is an Admin, but global settings (workers, ML,
        // scheduler...) stay immutable so visitors don't break the demo for
        // everyone else. Their own settings remain self-service. Values the demo
        // needs (map key...) are pinned via DemoMode:Settings instead.
        if (isGlobal && demoOptions.CurrentValue.Enabled)
        {
            return TypedResults.Problem(DemoModeGuardMiddleware.CreateBlockedProblem());
        }

        var effectiveUserId = isGlobal ? Guid.Empty : userId;
        await settingsService.SetSettingAsync(request.Key, request.Value ?? "", effectiveUserId);

        // A per-task compute-device change takes effect immediately by asking
        // the ML service to reload that task on the chosen provider. Best-effort
        // (the client never throws), so a stopped ML container doesn't fail the
        // save — the startup reconcile re-syncs it later.
        if (isGlobal && MlProviders.KeyToTask.TryGetValue(request.Key, out var task))
        {
            var spec = MlProviders.DeviceToProviderSpec(request.Value);
            await mlConfig.SetProviderAsync(task, spec, cancellationToken);
        }

        return TypedResults.Ok(new SaveSettingResponse("Setting saved successfully"));
    }

    /// <summary>
    /// Returns true for keys that are server-wide globals (stored under Guid.Empty).
    /// TaskSettings.*             — background worker counts
    /// ServerSettings.*           — server configuration (limits, public URL…)
    /// TrashSettings.*            — trash behaviour (enabled, retention, quota)
    /// UserSettings.*             — default values applied when creating new user accounts
    /// MetadataSettings.*         — EXIF/IPTC extraction behaviour
    /// NightlyTaskSettings.*      — nightly scheduled tasks (schedule, enabled tasks, last run)
    /// NotificationSettings.*     — notification system (enabled types, retention, per-user cap)
    /// FaceRecognition.*          — face recognition runtime overrides (enable, thresholds, compute device)
    /// ObjectDetection.*          — object detection runtime overrides (enable, compute device)
    /// SceneClassification.*      — scene classification runtime overrides (enable, compute device)
    /// TextRecognition.*          — text recognition runtime overrides (enable, compute device)
    /// Embedding.*                — image embedding runtime overrides (enable, compute device)
    /// </summary>
    private static bool IsGlobalKey(string key) =>
        key.StartsWith("TaskSettings.", StringComparison.Ordinal) ||
        key.StartsWith("ServerSettings.", StringComparison.Ordinal) ||
        key.StartsWith("TrashSettings.", StringComparison.Ordinal) ||
        key.StartsWith("UserSettings.", StringComparison.Ordinal) ||
        key.StartsWith("MetadataSettings.", StringComparison.Ordinal) ||
        key.StartsWith("NightlyTaskSettings.", StringComparison.Ordinal) ||
        key.StartsWith("NotificationSettings.", StringComparison.Ordinal) ||
        key.StartsWith("FaceRecognition.", StringComparison.Ordinal) ||
        key.StartsWith("ObjectDetection.", StringComparison.Ordinal) ||
        key.StartsWith("SceneClassification.", StringComparison.Ordinal) ||
        key.StartsWith("TextRecognition.", StringComparison.Ordinal) ||
        key.StartsWith("Embedding.", StringComparison.Ordinal);

    private static Ok<ServerInfoResponse> GetServerInfo() =>
        TypedResults.Ok(new ServerInfoResponse(Environment.ProcessorCount));

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(userIdClaim?.Value, out userId);
    }
}

public class SaveSettingRequest
{
    public string Key { get; set; } = string.Empty;
    public string Value { get; set; } = string.Empty;
}

public sealed record SettingValueResponse(string Key, string Value);

public sealed record SaveSettingResponse(string Message);

public sealed record ServerInfoResponse(int ProcessorCount);
