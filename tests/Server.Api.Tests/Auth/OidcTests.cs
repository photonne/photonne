using System.Net;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Photonne.Server.Api.Shared.Services.Auth;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Auth;

/// <summary>
/// The OpenID sign-in flow with a stand-in provider: the server's half (state,
/// PKCE, account matching, one-use tickets) is what is under test; the
/// provider's half is Microsoft.IdentityModel's.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class OidcTests : IntegrationTestBase
{
    public OidcTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed class FakeProvider : IOidcProvider
    {
        public Dictionary<string, OidcIdentity> Identities { get; } = new();
        private readonly Dictionary<string, (string Nonce, string Challenge)> _started = new();

        public Task<string> AuthorizeUrlAsync(string redirectUri, string state, string nonce, string codeChallenge, CancellationToken ct)
        {
            _started[state] = (nonce, codeChallenge);
            return Task.FromResult($"https://idp.test/authorize?state={state}&redirect_uri={Uri.EscapeDataString(redirectUri)}");
        }

        public Task<OidcIdentity> RedeemAsync(string code, string redirectUri, string codeVerifier, string nonce, CancellationToken ct)
        {
            // The verifier must be the one behind the challenge sent at the start.
            Assert.Contains(_started.Values, s => s.Nonce == nonce && s.Challenge == OidcFlowStore.Challenge(codeVerifier));
            return Identities.TryGetValue(code, out var identity)
                ? Task.FromResult(identity)
                : throw new OidcException("invalid_grant", "unknown code");
        }
    }

    private sealed record Config(bool Enabled, string? DisplayName);
    private sealed record Session(string Token, UserBody User);
    private sealed record UserBody(Guid Id, string Username);

    private (WebApplicationFactory<Program> Host, FakeProvider Provider) WithOidc()
    {
        var provider = new FakeProvider();
        var host = Factory.WithWebHostBuilder(b => b.ConfigureServices(s =>
        {
            s.Configure<OidcOptions>(o =>
            {
                o.Enabled = true;
                o.Authority = "https://idp.test";
                o.ClientId = "photonne";
                o.DisplayName = "Authentik";
                o.AutoCreateUsers = true;
            });
            s.RemoveAll<IOidcProvider>();
            s.AddSingleton<IOidcProvider>(provider);
        }));
        return (host, provider);
    }

    private static async Task<string> SignInAsync(HttpClient client, string code, string clientKind = "web")
    {
        var start = await client.GetAsync($"/api/auth/oidc/start?client={clientKind}");
        Assert.Equal(HttpStatusCode.Redirect, start.StatusCode);
        var authorize = start.Headers.Location!;
        Assert.Equal("idp.test", authorize.Host);
        var state = System.Web.HttpUtility.ParseQueryString(authorize.Query)["state"];

        var callback = await client.GetAsync($"/api/auth/oidc/callback?code={code}&state={state}");
        Assert.Equal(HttpStatusCode.Redirect, callback.StatusCode);
        return callback.Headers.Location!.OriginalString;
    }

    private static string Query(string url, string key) =>
        System.Web.HttpUtility.ParseQueryString(new Uri(new Uri("http://x"), url).Query)[key]!;

    [Fact]
    public async Task Off_unless_configured()
    {
        var config = await CreateClient().GetFromJsonAsync<Config>("/api/auth/oidc");
        Assert.False(config!.Enabled);
        Assert.Equal(HttpStatusCode.NotFound, (await CreateClient().GetAsync("/api/auth/oidc/start")).StatusCode);
    }

    [Fact]
    public async Task A_verified_email_signs_into_its_account_through_a_one_use_ticket()
    {
        var (host, provider) = WithOidc();
        await using var _ = host;
        var existing = await CreateUserAsync();
        var email = await WithDbContextAsync(db => db.Users.Where(u => u.Id == existing.Id).Select(u => u.Email).FirstAsync());
        provider.Identities["c1"] = new OidcIdentity("sub-1", email.ToUpperInvariant(), true, "whatever", null, null);
        var client = host.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });

        Assert.Equal("Authentik", (await client.GetFromJsonAsync<Config>("/api/auth/oidc"))!.DisplayName);

        var back = await SignInAsync(client, "c1");
        Assert.StartsWith("/login?oidc_ticket=", back);
        var ticket = Query(back, "oidc_ticket");

        var session = await client.PostAsJsonAsync("/api/auth/oidc/exchange", new { ticket, deviceId = "d1", refreshTokenInCookie = false });
        Assert.Equal(existing.Id, (await session.Content.ReadFromJsonAsync<Session>())!.User.Id);
        Assert.Equal(HttpStatusCode.Unauthorized,
            (await client.PostAsJsonAsync("/api/auth/oidc/exchange", new { ticket, deviceId = "d2", refreshTokenInCookie = false })).StatusCode);

        // From now on the subject finds it, whatever the email says.
        Assert.Equal("sub-1", await WithDbContextAsync(db => db.Users.Where(u => u.Id == existing.Id).Select(u => u.OidcSubject).FirstAsync()));
    }

    [Fact]
    public async Task Creates_an_account_only_for_a_verified_email_and_answers_the_app_by_its_link()
    {
        var (host, provider) = WithOidc();
        await using var _ = host;
        var unique = Guid.NewGuid().ToString("N")[..8];
        provider.Identities["new"] = new OidcIdentity($"sub-{unique}", $"{unique}@example.com", true, $"marta {unique}!", "Marta", "Puig");
        provider.Identities["unverified"] = new OidcIdentity($"sub-x-{unique}", $"x{unique}@example.com", false, null, null, null);
        var client = host.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });

        var back = await SignInAsync(client, "new", clientKind: "app");
        Assert.StartsWith("photonne://oidc?ticket=", back);
        var session = await client.PostAsJsonAsync("/api/auth/oidc/exchange",
            new { ticket = Query(back, "ticket"), deviceId = "phone", refreshTokenInCookie = false });
        Assert.Equal($"marta{unique}", (await session.Content.ReadFromJsonAsync<Session>())!.User.Username);

        Assert.Equal("no_account", Query(await SignInAsync(client, "unverified"), "oidc_error"));
    }

    [Fact]
    public async Task A_callback_nobody_started_goes_back_with_an_error()
    {
        var (host, _) = WithOidc();
        await using var __ = host;
        var client = host.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });

        var callback = await client.GetAsync("/api/auth/oidc/callback?code=x&state=forged");
        Assert.Equal("/login?oidc_error=expired", callback.Headers.Location!.OriginalString);
    }
}
