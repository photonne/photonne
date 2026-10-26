using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.Auth;

namespace Photonne.Server.Api.Features.Auth;

public record TotpStatusResponse(bool Enabled, int RecoveryCodesLeft);

/// <summary>The secret to put in the authenticator app, as text and as the otpauth:// link of a QR code.</summary>
public record TotpSetupResponse(string Secret, string OtpAuthUri);

public record TotpCodeRequest(string Code);

public record TotpRecoveryCodesResponse(List<string> RecoveryCodes);

public record TotpDisableRequest(string Password);

/// <summary>
/// Two-step verification for one's own account: set up (a new secret, not
/// yet required), enable (proving the app shows the right code; answers the
/// recovery codes, once) and disable (with the password). An admin can turn
/// it off for someone who lost both phone and codes.
/// </summary>
public class TotpEndpoint : IEndpoint
{
    private const string Issuer = "Photonne";

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/auth/totp").WithTags("Authentication").RequireAuthorization();

        group.MapGet("", Status)
            .WithName("GetTotpStatus")
            .WithDescription("Whether two-step verification is on for the caller, and how many recovery codes are left");
        group.MapPost("setup", Setup)
            .WithName("SetupTotp")
            .WithDescription("Starts setting up two-step verification: a new secret for the authenticator app");
        group.MapPost("enable", Enable)
            .WithName("EnableTotp")
            .WithDescription("Turns two-step verification on after checking a code; returns the recovery codes, once");
        group.MapPost("disable", Disable)
            .WithName("DisableTotp")
            .WithDescription("Turns two-step verification off, confirming the password");

        app.MapPost("/api/users/{id:guid}/totp/reset", Reset)
            .WithName("ResetUserTotp")
            .WithTags("Users")
            .WithDescription("Turns a user's two-step verification off, for one who lost the phone and the codes (Admin only)")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private static Guid? UserId(ClaimsPrincipal user) =>
        Guid.TryParse(user.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var id) ? id : null;

    private static async Task<Results<Ok<TotpStatusResponse>, UnauthorizedHttpResult>> Status(
        ClaimsPrincipal principal, [FromServices] ApplicationDbContext db, CancellationToken ct)
    {
        if (UserId(principal) is not Guid id || await db.Users.FindAsync([id], ct) is not { } user)
            return TypedResults.Unauthorized();
        return TypedResults.Ok(new TotpStatusResponse(user.TotpEnabled, RecoveryCodes.Left(user.TotpRecoveryCodes)));
    }

    private static async Task<Results<Ok<TotpSetupResponse>, BadRequest<ApiError>, UnauthorizedHttpResult>> Setup(
        ClaimsPrincipal principal, [FromServices] ApplicationDbContext db, CancellationToken ct)
    {
        if (UserId(principal) is not Guid id || await db.Users.FindAsync([id], ct) is not { } user)
            return TypedResults.Unauthorized();
        if (user.TotpEnabled)
            return TypedResults.BadRequest(new ApiError("Two-step verification is already on", "totp_already_enabled"));

        user.TotpSecret = Totp.NewSecret();
        await db.SaveChangesAsync(ct);
        return TypedResults.Ok(new TotpSetupResponse(user.TotpSecret, Totp.OtpAuthUri(Issuer, user.Username, user.TotpSecret)));
    }

    private static async Task<Results<Ok<TotpRecoveryCodesResponse>, BadRequest<ApiError>, UnauthorizedHttpResult>> Enable(
        [FromBody] TotpCodeRequest request, ClaimsPrincipal principal, [FromServices] ApplicationDbContext db, CancellationToken ct)
    {
        if (UserId(principal) is not Guid id || await db.Users.FindAsync([id], ct) is not { } user)
            return TypedResults.Unauthorized();
        if (user.TotpEnabled)
            return TypedResults.BadRequest(new ApiError("Two-step verification is already on", "totp_already_enabled"));
        if (string.IsNullOrEmpty(user.TotpSecret))
            return TypedResults.BadRequest(new ApiError("Set it up first", "totp_not_set_up"));

        var step = Totp.Verify(user.TotpSecret, request.Code, DateTimeOffset.UtcNow, lastStep: null);
        if (step is null)
            return TypedResults.BadRequest(new ApiError("The code is not right", "totp_invalid"));

        var codes = RecoveryCodes.Generate();
        user.TotpEnabled = true;
        user.TotpLastStep = step;
        user.TotpRecoveryCodes = string.Join(';', codes.Select(RecoveryCodes.Hash));
        await db.SaveChangesAsync(ct);
        return TypedResults.Ok(new TotpRecoveryCodesResponse(codes));
    }

    private static async Task<Results<NoContent, BadRequest<ApiError>, UnauthorizedHttpResult>> Disable(
        [FromBody] TotpDisableRequest request, ClaimsPrincipal principal, [FromServices] ApplicationDbContext db,
        [FromServices] IAuthService auth, CancellationToken ct)
    {
        if (UserId(principal) is not Guid id || await db.Users.FindAsync([id], ct) is not { } user)
            return TypedResults.Unauthorized();
        if (!auth.VerifyPassword(request.Password ?? string.Empty, user.PasswordHash))
            return TypedResults.BadRequest(new ApiError("The password is not right", "invalid_current_password"));

        TurnOff(user);
        await db.SaveChangesAsync(ct);
        return TypedResults.NoContent();
    }

    private static async Task<Results<NoContent, NotFound<ApiError>>> Reset(
        Guid id, [FromServices] ApplicationDbContext db, CancellationToken ct)
    {
        if (await db.Users.FindAsync([id], ct) is not { } user)
            return TypedResults.NotFound(new ApiError($"User {id} not found", "user_not_found"));
        TurnOff(user);
        await db.SaveChangesAsync(ct);
        return TypedResults.NoContent();
    }

    private static void TurnOff(Shared.Models.User user)
    {
        user.TotpEnabled = false;
        user.TotpSecret = null;
        user.TotpRecoveryCodes = null;
        user.TotpLastStep = null;
    }
}
