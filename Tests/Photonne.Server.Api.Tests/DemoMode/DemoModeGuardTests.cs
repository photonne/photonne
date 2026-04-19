using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.DemoMode;

/// <summary>
/// DemoModeGuardMiddleware must block mutating admin-panel endpoints when the
/// public demo is running, while still letting self-service endpoints
/// through (the demo user has to be able to use its own account).
/// </summary>
public sealed class DemoModeGuardTests : IntegrationTestBase, IDisposable
{
    private readonly WebApplicationFactory<Program> _demoFactory;

    public DemoModeGuardTests(PhotonneApiFactory factory) : base(factory)
    {
        _demoFactory = factory.WithDemoMode();
    }

    private sealed record LoginReq(string Username, string Password, string DeviceId);
    private sealed record LoginResp(string Token, string RefreshToken);
    private sealed record CreateUserReq(string Username, string Email, string Password);

    private async Task<HttpClient> CreateAdminDemoClientAsync()
    {
        var client = _demoFactory.CreateClient();
        var login = await client.PostAsJsonAsync("/api/auth/login", new LoginReq(
            PhotonneApiFactory.AdminUsername,
            PhotonneApiFactory.AdminPassword,
            Guid.NewGuid().ToString("N")));
        login.EnsureSuccessStatusCode();

        var body = await login.Content.ReadFromJsonAsync<LoginResp>();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", body!.Token);
        return client;
    }

    [Fact]
    public async Task CreateUser_IsBlocked_InDemoMode()
    {
        var client = await CreateAdminDemoClientAsync();

        var response = await client.PostAsJsonAsync("/api/users", new CreateUserReq(
            Username: "hijacker",
            Email: "hijacker@test.local",
            Password: "Doesn't-Matter-1!"));

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);

        var body = await response.Content.ReadAsStringAsync();
        Assert.Contains("demoMode", body);
    }

    [Fact]
    public async Task DeleteUser_IsBlocked_InDemoMode()
    {
        var client = await CreateAdminDemoClientAsync();

        var response = await client.DeleteAsync($"/api/users/{Guid.NewGuid()}");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task GetCurrentUser_IsAllowed_InDemoMode()
    {
        // Self-service endpoints must keep working so the demo user can see
        // their own profile; only destructive admin ops are gated.
        var client = await CreateAdminDemoClientAsync();

        var response = await client.GetAsync("/api/users/me");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task ListUsers_IsAllowed_InDemoMode()
    {
        // Read-only listing keeps the admin pages rendering in the demo.
        var client = await CreateAdminDemoClientAsync();

        var response = await client.GetAsync("/api/users");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task CreateUser_IsAllowed_WhenDemoDisabled()
    {
        // Sanity check against the non-demo factory so we know the guard is
        // what's blocking — not some other auth/validation rule.
        var (_, adminClient) = await CreateAuthenticatedUserAsync(role: "Admin");

        var response = await adminClient.PostAsJsonAsync("/api/users", new CreateUserReq(
            Username: "new-" + Guid.NewGuid().ToString("N")[..6],
            Email: $"u-{Guid.NewGuid():N}@test.local",
            Password: "Valid-Pass-1!"));

        Assert.NotEqual(HttpStatusCode.Forbidden, response.StatusCode);
    }

    public void Dispose() => _demoFactory.Dispose();
}
