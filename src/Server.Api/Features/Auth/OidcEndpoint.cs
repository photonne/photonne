using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Services.Auth;

namespace Photonne.Server.Api.Features.Auth;

public record OidcConfigResponse(bool Enabled, string? DisplayName);

public record OidcExchangeRequest(string Ticket, string DeviceId, bool RefreshTokenInCookie);

/// <summary>
/// Sign-in through an OpenID Connect provider. The server runs the whole
/// authorization-code flow (with PKCE): <c>start</c> sends the browser to the
/// provider, the provider sends it back to <c>callback</c>, and the callback
/// hands the web (<c>/login?oidc_ticket=…</c>) or the app
/// (<c>photonne://oidc?ticket=…</c>) a one-use ticket that <c>exchange</c>
/// turns into the usual session — so no token ever travels in a URL.
///
/// An account is matched by the provider's subject, then by a verified email
/// (and remembers the subject); without one, it is created only when
/// <see cref="OidcOptions.AutoCreateUsers"/> says so. Two-step verification
/// isn't asked here: that is the provider's business.
/// </summary>
public partial class OidcEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/auth/oidc").WithTags("Authentication").AllowAnonymous();

        group.MapGet("", Config)
            .WithName("GetOidcConfig")
            .WithDescription("Whether sign-in with an OpenID provider is on, and the provider's name for the button");
        group.MapGet("start", Start)
            .Produces(StatusCodes.Status302Found)
            .WithName("StartOidc")
            .WithDescription("Sends the browser to the OpenID provider (client=web|app)");
        group.MapGet("callback", Callback)
            .Produces(StatusCodes.Status302Found)
            .WithName("OidcCallback")
            .WithDescription("Where the OpenID provider sends the browser back; redirects to the web or the app with a one-use ticket");
        group.MapPost("exchange", Exchange)
            .WithName("ExchangeOidcTicket")
            .WithDescription("Turns the one-use ticket of an OpenID sign-in into a session, like /api/auth/login")
            .RequireRateLimiting("demo-login");
    }

    private static Ok<OidcConfigResponse> Config([FromServices] IOptions<OidcOptions> options) =>
        TypedResults.Ok(options.Value.IsConfigured
            ? new OidcConfigResponse(true, options.Value.DisplayName)
            : new OidcConfigResponse(false, null));

    private static string RedirectUri(OidcOptions options, HttpRequest request)
    {
        var origin = string.IsNullOrWhiteSpace(options.PublicUrl)
            ? $"{request.Scheme}://{request.Host}"
            : options.PublicUrl.TrimEnd('/');
        return $"{origin}/api/auth/oidc/callback";
    }

    private static async Task<Results<RedirectHttpResult, NotFound<ApiError>>> Start(
        [FromQuery] string? client,
        [FromServices] IOptions<OidcOptions> options,
        [FromServices] IOidcProvider provider,
        [FromServices] OidcFlowStore flows,
        HttpContext context,
        CancellationToken ct)
    {
        if (!options.Value.IsConfigured)
            return TypedResults.NotFound(new ApiError("Sign-in with an OpenID provider is off", "oidc_disabled"));

        var (state, pending) = flows.Start(client == "app" ? "app" : "web");
        var url = await provider.AuthorizeUrlAsync(
            RedirectUri(options.Value, context.Request), state, pending.Nonce, OidcFlowStore.Challenge(pending.CodeVerifier), ct);
        return TypedResults.Redirect(url);
    }

    private static async Task<RedirectHttpResult> Callback(
        [FromQuery] string? code,
        [FromQuery] string? state,
        [FromQuery] string? error,
        [FromServices] IOptions<OidcOptions> options,
        [FromServices] IOidcProvider provider,
        [FromServices] OidcFlowStore flows,
        [FromServices] ApplicationDbContext db,
        [FromServices] IAuthService auth,
        [FromServices] SettingsService settings,
        [FromServices] ILogger<OidcEndpoint> logger,
        HttpContext context,
        CancellationToken ct)
    {
        var pending = flows.Take(state);
        var client = pending?.Client ?? "web";
        var origin = string.IsNullOrWhiteSpace(options.Value.PublicUrl)
            ? $"{context.Request.Scheme}://{context.Request.Host}"
            : options.Value.PublicUrl.TrimEnd('/');

        RedirectHttpResult Back(string key, string value) => TypedResults.Redirect(client == "app"
            ? $"photonne://oidc?{key}={Uri.EscapeDataString(value)}&server={Uri.EscapeDataString(origin)}"
            : $"/login?oidc_{key}={Uri.EscapeDataString(value)}");

        if (pending is null) return Back("error", "expired");
        if (!string.IsNullOrEmpty(error) || string.IsNullOrEmpty(code)) return Back("error", "denied");

        try
        {
            var identity = await provider.RedeemAsync(code, RedirectUri(options.Value, context.Request), pending.CodeVerifier, pending.Nonce, ct);
            var user = await FindOrCreateAsync(identity, options.Value, db, auth, settings, ct);
            if (user is null) return Back("error", "no_account");
            if (!user.IsActive) return Back("error", "inactive");
            return Back("ticket", flows.IssueTicket(user.Id));
        }
        catch (OidcException ex)
        {
            logger.LogWarning("OpenID sign-in failed: {Code} {Message}", ex.Code, ex.Message);
            return Back("error", "provider");
        }
        catch (Exception ex) when (ex is HttpRequestException or InvalidOperationException)
        {
            logger.LogWarning(ex, "OpenID provider unreachable or misconfigured");
            return Back("error", "provider");
        }
    }

    private static async Task<Results<Ok<LoginResponse>, BadRequest<ApiError>, UnauthorizedHttpResult>> Exchange(
        [FromBody] OidcExchangeRequest request,
        [FromServices] OidcFlowStore flows,
        [FromServices] ApplicationDbContext db,
        [FromServices] IAuthService auth,
        HttpContext context,
        CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(request.DeviceId))
            return TypedResults.BadRequest(new ApiError("DeviceId is required", "device_id_required"));
        if (flows.Redeem(request.Ticket) is not Guid userId) return TypedResults.Unauthorized();
        var user = await db.Users.FindAsync([userId], ct);
        if (user is null || !user.IsActive) return TypedResults.Unauthorized();

        return TypedResults.Ok(await SessionIssuer.IssueAsync(
            user, request.DeviceId.Trim(), request.RefreshTokenInCookie, db, auth, context, ct));
    }

    /// <summary>The account for <paramref name="identity"/>: by subject, then verified email, then a new one if allowed.</summary>
    internal static async Task<User?> FindOrCreateAsync(
        OidcIdentity identity, OidcOptions options, ApplicationDbContext db, IAuthService auth, SettingsService settings, CancellationToken ct)
    {
        var user = await db.Users.FirstOrDefaultAsync(u => u.OidcSubject == identity.Subject, ct);
        if (user is not null) return user;

        if (identity.EmailVerified && !string.IsNullOrWhiteSpace(identity.Email))
        {
            var email = identity.Email.Trim().ToLowerInvariant();
            user = await db.Users.FirstOrDefaultAsync(u => u.Email.ToLower() == email && u.OidcSubject == null, ct);
            if (user is not null)
            {
                user.OidcSubject = identity.Subject;
                await db.SaveChangesAsync(ct);
                return user;
            }
        }

        if (!options.AutoCreateUsers || !identity.EmailVerified || string.IsNullOrWhiteSpace(identity.Email))
            return null;

        var defaultRole = await settings.GetSettingAsync("UserSettings.DefaultRole", Guid.Empty, "User");
        var defaultQuotaGb = await settings.GetSettingAsync("UserSettings.DefaultStorageQuotaGb", Guid.Empty, "0");
        user = new User
        {
            Username = await FreeUsernameAsync(identity, db, ct),
            Email = identity.Email.Trim(),
            // No password: this account signs in through the provider (an
            // admin can set one with "reset password").
            PasswordHash = auth.HashPassword(OidcFlowStore.NewToken()),
            FirstName = identity.GivenName,
            LastName = identity.FamilyName,
            Role = defaultRole == "Admin" ? "User" : defaultRole,
            IsActive = true,
            StorageQuotaBytes = int.TryParse(defaultQuotaGb, out var gb) && gb > 0 ? gb * 1_073_741_824L : null,
            OidcSubject = identity.Subject,
            CreatedAt = DateTime.UtcNow
        };
        db.Users.Add(user);
        await db.SaveChangesAsync(ct);
        return user;
    }

    [GeneratedRegex("[^A-Za-z0-9._-]")]
    private static partial Regex NotUsernameChars();

    /// <summary>preferred_username, else the email's local part, made valid and unique.</summary>
    private static async Task<string> FreeUsernameAsync(OidcIdentity identity, ApplicationDbContext db, CancellationToken ct)
    {
        var raw = identity.PreferredUsername ?? identity.Email?.Split('@')[0] ?? "user";
        var stem = NotUsernameChars().Replace(raw, "");
        if (stem.Length == 0) stem = "user";
        if (stem.Length > 56) stem = stem[..56];
        var candidate = stem;
        for (var i = 2; await db.Users.AnyAsync(u => u.Username.ToLower() == candidate.ToLower(), ct); i++)
            candidate = $"{stem}{i}";
        return candidate;
    }
}
