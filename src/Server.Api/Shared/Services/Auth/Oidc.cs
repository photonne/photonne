using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Protocols;
using Microsoft.IdentityModel.Protocols.OpenIdConnect;
using Microsoft.IdentityModel.Tokens;

namespace Photonne.Server.Api.Shared.Services.Auth;

/// <summary>
/// Sign-in with an OpenID Connect provider (Authentik, Keycloak, Authelia,
/// Google…), from the <c>Oidc</c> section (env <c>Oidc__Authority</c>…).
/// </summary>
public sealed class OidcOptions
{
    public bool Enabled { get; set; }

    /// <summary>The provider's issuer URL; its discovery document lives under it.</summary>
    public string? Authority { get; set; }

    public string? ClientId { get; set; }
    public string? ClientSecret { get; set; }

    /// <summary>What the button says: "Entrar con {DisplayName}".</summary>
    public string DisplayName { get; set; } = "SSO";

    public string Scopes { get; set; } = "openid profile email";

    /// <summary>
    /// Whether someone the provider vouches for but who has no account here
    /// gets one. Off by default: the provider may know more people than
    /// should see these photos.
    /// </summary>
    public bool AutoCreateUsers { get; set; }

    /// <summary>
    /// This server's public address (https://fotos.example.com), for the
    /// redirect URI registered at the provider. Taken from the request when
    /// empty, which is wrong behind a proxy that doesn't forward the scheme.
    /// </summary>
    public string? PublicUrl { get; set; }

    public bool IsConfigured => Enabled && !string.IsNullOrWhiteSpace(Authority) && !string.IsNullOrWhiteSpace(ClientId);
}

/// <summary>Who the provider says signed in.</summary>
public sealed record OidcIdentity(string Subject, string? Email, bool EmailVerified, string? PreferredUsername, string? GivenName, string? FamilyName);

/// <summary>The provider's side of the authorization-code flow.</summary>
public interface IOidcProvider
{
    Task<string> AuthorizeUrlAsync(string redirectUri, string state, string nonce, string codeChallenge, CancellationToken ct);

    /// <summary>Redeems the code and validates the ID token (signature, issuer, audience, nonce).</summary>
    Task<OidcIdentity> RedeemAsync(string code, string redirectUri, string codeVerifier, string nonce, CancellationToken ct);
}

public sealed class OidcProvider : IOidcProvider
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(20) };
    private readonly OidcOptions _options;
    private readonly ConfigurationManager<OpenIdConnectConfiguration>? _configuration;

    public OidcProvider(IOptions<OidcOptions> options)
    {
        _options = options.Value;
        if (!_options.IsConfigured) return;
        var authority = _options.Authority!.TrimEnd('/');
        _configuration = new ConfigurationManager<OpenIdConnectConfiguration>(
            $"{authority}/.well-known/openid-configuration",
            new OpenIdConnectConfigurationRetriever(),
            new HttpDocumentRetriever(Http) { RequireHttps = authority.StartsWith("https://", StringComparison.OrdinalIgnoreCase) });
    }

    private Task<OpenIdConnectConfiguration> ConfigurationAsync(CancellationToken ct) =>
        (_configuration ?? throw new InvalidOperationException("OIDC is not configured")).GetConfigurationAsync(ct);

    public async Task<string> AuthorizeUrlAsync(string redirectUri, string state, string nonce, string codeChallenge, CancellationToken ct)
    {
        var configuration = await ConfigurationAsync(ct);
        var query = new Dictionary<string, string>
        {
            ["response_type"] = "code",
            ["client_id"] = _options.ClientId!,
            ["redirect_uri"] = redirectUri,
            ["scope"] = _options.Scopes,
            ["state"] = state,
            ["nonce"] = nonce,
            ["code_challenge"] = codeChallenge,
            ["code_challenge_method"] = "S256",
        };
        var separator = configuration.AuthorizationEndpoint.Contains('?') ? "&" : "?";
        return configuration.AuthorizationEndpoint + separator +
               string.Join("&", query.Select(kv => $"{Uri.EscapeDataString(kv.Key)}={Uri.EscapeDataString(kv.Value)}"));
    }

    public async Task<OidcIdentity> RedeemAsync(string code, string redirectUri, string codeVerifier, string nonce, CancellationToken ct)
    {
        var configuration = await ConfigurationAsync(ct);
        var form = new Dictionary<string, string>
        {
            ["grant_type"] = "authorization_code",
            ["code"] = code,
            ["redirect_uri"] = redirectUri,
            ["client_id"] = _options.ClientId!,
            ["code_verifier"] = codeVerifier,
        };
        if (!string.IsNullOrEmpty(_options.ClientSecret)) form["client_secret"] = _options.ClientSecret;

        using var response = await Http.PostAsync(configuration.TokenEndpoint, new FormUrlEncodedContent(form), ct);
        var body = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode)
            throw new OidcException("token_exchange_failed", $"Token endpoint answered {(int)response.StatusCode}");

        using var json = System.Text.Json.JsonDocument.Parse(body);
        if (!json.RootElement.TryGetProperty("id_token", out var idTokenElement))
            throw new OidcException("no_id_token", "The provider sent no ID token");

        var validation = await new JsonWebTokenHandler().ValidateTokenAsync(idTokenElement.GetString(), new TokenValidationParameters
        {
            ValidIssuer = configuration.Issuer,
            ValidAudience = _options.ClientId,
            IssuerSigningKeys = configuration.SigningKeys,
            ValidateLifetime = true,
            ClockSkew = TimeSpan.FromMinutes(2),
        });
        if (!validation.IsValid)
            throw new OidcException("invalid_id_token", validation.Exception?.Message ?? "Invalid ID token");

        var claims = validation.ClaimsIdentity;
        string? Claim(string type) => claims.FindFirst(type)?.Value;
        if (Claim("nonce") != nonce)
            throw new OidcException("invalid_id_token", "Nonce mismatch");

        var subject = Claim("sub") ?? throw new OidcException("invalid_id_token", "No subject");
        return new OidcIdentity(
            subject,
            Claim("email"),
            string.Equals(Claim("email_verified"), "true", StringComparison.OrdinalIgnoreCase),
            Claim("preferred_username"),
            Claim("given_name"),
            Claim("family_name"));
    }
}

public sealed class OidcException(string code, string message) : Exception(message)
{
    public string Code { get; } = code;
}

/// <summary>
/// The short-lived halves of the flow, in memory (one server process): what
/// a started sign-in needs back at the callback, and the one-use tickets the
/// callback hands the client to exchange for a session.
/// </summary>
public sealed class OidcFlowStore
{
    public sealed record Pending(string CodeVerifier, string Nonce, string Client, DateTime ExpiresAt);

    private sealed record Ticket(Guid UserId, DateTime ExpiresAt);

    private static readonly TimeSpan PendingLifetime = TimeSpan.FromMinutes(10);
    private static readonly TimeSpan TicketLifetime = TimeSpan.FromMinutes(2);

    private readonly ConcurrentDictionary<string, Pending> _pending = new();
    private readonly ConcurrentDictionary<string, Ticket> _tickets = new();

    public static string NewToken() => Base64Url(RandomNumberGenerator.GetBytes(32));

    public static string Challenge(string verifier) => Base64Url(SHA256.HashData(Encoding.ASCII.GetBytes(verifier)));

    public (string State, Pending Pending) Start(string client)
    {
        Sweep();
        var state = NewToken();
        var pending = new Pending(NewToken(), NewToken(), client, DateTime.UtcNow + PendingLifetime);
        _pending[state] = pending;
        return (state, pending);
    }

    /// <summary>The sign-in <paramref name="state"/> started, once.</summary>
    public Pending? Take(string? state) =>
        state is not null && _pending.TryRemove(state, out var pending) && pending.ExpiresAt > DateTime.UtcNow ? pending : null;

    public string IssueTicket(Guid userId)
    {
        Sweep();
        var ticket = NewToken();
        _tickets[ticket] = new Ticket(userId, DateTime.UtcNow + TicketLifetime);
        return ticket;
    }

    /// <summary>The user a ticket was issued for, once.</summary>
    public Guid? Redeem(string? ticket) =>
        ticket is not null && _tickets.TryRemove(ticket, out var entry) && entry.ExpiresAt > DateTime.UtcNow ? entry.UserId : null;

    private void Sweep()
    {
        var now = DateTime.UtcNow;
        foreach (var (key, value) in _pending) if (value.ExpiresAt <= now) _pending.TryRemove(key, out _);
        foreach (var (key, value) in _tickets) if (value.ExpiresAt <= now) _tickets.TryRemove(key, out _);
    }

    private static string Base64Url(byte[] bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}
