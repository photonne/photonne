using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.DemoMode;

/// <summary>
/// The demo guards must block mutating admin-panel endpoints when the public demo
/// is running, while still letting self-service endpoints and the regular-account
/// lifecycle through (visitors must be able to create, use and delete one).
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
    private sealed record CreateUserReq(string Username, string Email, string Password, string? Role = null);
    private sealed record CreatedUserResp(Guid Id, string Role);
    private sealed record DeleteAccountReq(string Password);
    private sealed record ChangePasswordReq(string CurrentPassword, string NewPassword);
    private sealed record UpdateProfileReq(string Username);
    private sealed record SaveSettingReq(string Key, string Value);

    private Task<HttpClient> CreateAdminDemoClientAsync()
        => LoginDemoClientAsync(PhotonneApiFactory.AdminUsername, PhotonneApiFactory.AdminPassword);

    private async Task<HttpClient> LoginDemoClientAsync(string username, string password)
    {
        var client = _demoFactory.CreateClient();
        var login = await client.PostAsJsonAsync("/api/auth/login", new LoginReq(
            username,
            password,
            Guid.NewGuid().ToString("N")));
        login.EnsureSuccessStatusCode();

        var body = await login.Content.ReadFromJsonAsync<LoginResp>();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", body!.Token);
        return client;
    }

    [Fact]
    public async Task CreateAdminUser_IsBlocked_InDemoMode()
    {
        var client = await CreateAdminDemoClientAsync();

        var response = await client.PostAsJsonAsync("/api/users", new CreateUserReq(
            Username: "hijacker",
            Email: "hijacker@test.local",
            Password: "Doesn't-Matter-1!",
            Role: "Admin"));

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);

        var body = await response.Content.ReadAsStringAsync();
        Assert.Contains("demoMode", body);
    }

    [Fact]
    public async Task RegularUser_CanBeCreated_AndDeleteItsOwnAccount_InDemoMode()
    {
        // The store review needs the whole lifecycle in the demo: the admin creates a
        // regular account, the visitor signs in with it and deletes it from the app.
        var admin = await CreateAdminDemoClientAsync();
        var username = "visitor-" + Guid.NewGuid().ToString("N")[..6];
        const string password = "Valid-Pass-1!";

        var created = await admin.PostAsJsonAsync("/api/users", new CreateUserReq(
            Username: username,
            Email: $"{username}@test.local",
            Password: password));
        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        var createdBody = await created.Content.ReadFromJsonAsync<CreatedUserResp>();
        Assert.Equal("User", createdBody!.Role);

        var visitor = await LoginDemoClientAsync(username, password);
        var deleted = await visitor.PostAsJsonAsync("/api/users/me/delete-account", new DeleteAccountReq(password));

        Assert.Equal(HttpStatusCode.NoContent, deleted.StatusCode);
    }

    [Fact]
    public async Task DeleteAdminUser_IsBlocked_InDemoMode()
    {
        var target = await CreateUserAsync(role: "Admin");
        var client = await CreateAdminDemoClientAsync();

        var response = await client.DeleteAsync($"/api/users/{target.Id}");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task DeleteRegularUser_IsAllowed_InDemoMode()
    {
        var target = await CreateUserAsync();
        var client = await CreateAdminDemoClientAsync();

        var response = await client.DeleteAsync($"/api/users/{target.Id}");

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
    }

    [Fact]
    public async Task DeleteOwnAdminAccount_IsBlocked_InDemoMode()
    {
        // The shared demo account is an admin and must survive for the next visitor.
        var client = await CreateAdminDemoClientAsync();

        var response = await client.PostAsJsonAsync("/api/users/me/delete-account",
            new DeleteAccountReq(PhotonneApiFactory.AdminPassword));

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task ChangeOwnPassword_IsBlocked_ForAdmin_InDemoMode()
    {
        // The demo credentials are on the login page: changing them would lock
        // every other visitor out until the next reset.
        var client = await CreateAdminDemoClientAsync();

        var response = await client.PostAsJsonAsync("/api/users/me/change-password",
            new ChangePasswordReq(PhotonneApiFactory.AdminPassword, "Another-Pass-1!"));

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task RenameOwnAccount_IsBlocked_ForAdmin_InDemoMode()
    {
        var client = await CreateAdminDemoClientAsync();

        var response = await client.PutAsJsonAsync("/api/users/me",
            new UpdateProfileReq("renamed-" + Guid.NewGuid().ToString("N")[..6]));

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task ChangeOwnPassword_IsAllowed_ForRegularUser_InDemoMode()
    {
        var user = await CreateUserAsync();
        var client = await LoginDemoClientAsync(user.Username, user.Password);

        var response = await client.PostAsJsonAsync("/api/users/me/change-password",
            new ChangePasswordReq(user.Password, "Another-Pass-1!"));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
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
    public async Task SaveGlobalSetting_IsBlocked_InDemoMode()
    {
        // Admin role notwithstanding, server-wide settings stay immutable so a
        // visitor can't break the demo for everyone else.
        var client = await CreateAdminDemoClientAsync();

        var response = await client.PostAsJsonAsync("/api/settings",
            new SaveSettingReq("ServerSettings.MapTileApiKey", "visitor-key"));

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Contains("demoMode", await response.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task SavePersonalSetting_IsAllowed_InDemoMode()
    {
        // Same endpoint, but a non-global key only touches the caller's own
        // settings, which must stay self-service in the demo.
        var client = await CreateAdminDemoClientAsync();

        var response = await client.PostAsJsonAsync("/api/settings",
            new SaveSettingReq("DemoTest.PersonalPreference", "on"));

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
