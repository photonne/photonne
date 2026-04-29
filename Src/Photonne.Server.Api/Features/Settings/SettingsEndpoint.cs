using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

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
            .WithDescription("Saves or updates a setting");
            
        group.MapGet("/assets-path", GetAssetsPath)
            .WithName("GetAssetsPath")
            .WithDescription("Gets the current configured assets path");

        var adminGroup = app.MapGroup("/api/settings")
            .WithTags("Settings")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        adminGroup.MapGet("/server-info", GetServerInfo)
            .WithName("GetServerInfo")
            .WithDescription("Gets server hardware information (processor count, etc.)");
    }

    private async Task<IResult> GetSetting(
        [FromQuery] string key,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var effectiveUserId = IsGlobalKey(key) ? Guid.Empty : userId;
        var value = await settingsService.GetSettingAsync(key, effectiveUserId);
        return Results.Ok(new { key, value });
    }

    private async Task<IResult> SaveSetting(
        [FromBody] SaveSettingRequest request,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user)
    {
        if (string.IsNullOrWhiteSpace(request.Key))
            return Results.BadRequest("Key is required");

        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var effectiveUserId = IsGlobalKey(request.Key) ? Guid.Empty : userId;
        await settingsService.SetSettingAsync(request.Key, request.Value ?? "", effectiveUserId);
        return Results.Ok(new { message = "Setting saved successfully" });
    }

    private async Task<IResult> GetAssetsPath(
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var path = await settingsService.GetAssetsPathAsync(userId);
        return Results.Ok(new { path });
    }

    /// <summary>
    /// Returns true for keys that are server-wide globals (stored under Guid.Empty).
    /// TaskSettings.*     — background worker counts
    /// ServerSettings.*        — server configuration (paths, limits, public URL…)
    /// TrashSettings.*         — trash behaviour (enabled, retention, quota)
    /// UserSettings.*          — default values applied when creating new user accounts
    /// MetadataSettings.*      — EXIF/IPTC extraction behaviour
    /// NightlyTaskSettings.*      — nightly scheduled tasks (schedule, enabled tasks, last run)
    /// NotificationSettings.*     — notification system (enabled types, retention, per-user cap)
    /// FaceRecognition.*          — face recognition runtime overrides (enable, thresholds)
    /// ObjectDetection.*          — object detection runtime overrides (enable)
    /// SceneClassification.*      — scene classification runtime overrides (enable)
    /// TextRecognition.*          — text recognition runtime overrides (enable)
    /// AssetsPath                 — legacy global key for the managed assets directory
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
        key.Equals("AssetsPath", StringComparison.Ordinal);

    private static IResult GetServerInfo() =>
        Results.Ok(new { processorCount = Environment.ProcessorCount });

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
