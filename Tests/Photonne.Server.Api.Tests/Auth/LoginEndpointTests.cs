using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Auth;

public sealed class LoginEndpointTests : IntegrationTestBase
{
    public LoginEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record LoginBody(string Username, string Password, string DeviceId);
    private sealed record LoginResponse(string Token, string RefreshToken, UserBody User);
    private sealed record UserBody(string Username, string Email, string Role);

    [Fact]
    public async Task ValidCredentials_ReturnsTokenAndUser()
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync("/api/auth/login", new LoginBody(
            PhotonneApiFactory.AdminUsername,
            PhotonneApiFactory.AdminPassword,
            DeviceId: "test-device-1"));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<LoginResponse>();
        Assert.NotNull(body);
        Assert.False(string.IsNullOrWhiteSpace(body!.Token));
        Assert.False(string.IsNullOrWhiteSpace(body.RefreshToken));
        Assert.Equal(PhotonneApiFactory.AdminUsername, body.User.Username);
        Assert.Equal("Admin", body.User.Role);
    }

    [Fact]
    public async Task WrongPassword_ReturnsUnauthorized()
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync("/api/auth/login", new LoginBody(
            PhotonneApiFactory.AdminUsername,
            "definitely-not-the-password",
            DeviceId: "test-device-2"));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task UnknownUser_ReturnsUnauthorized()
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync("/api/auth/login", new LoginBody(
            "ghost-user",
            "whatever",
            DeviceId: "test-device-3"));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task MissingCredentials_ReturnsBadRequest()
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync("/api/auth/login", new LoginBody(
            Username: "",
            Password: "",
            DeviceId: "test-device-4"));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task MissingDeviceId_ReturnsBadRequest()
    {
        var client = CreateClient();

        var response = await client.PostAsJsonAsync("/api/auth/login", new LoginBody(
            PhotonneApiFactory.AdminUsername,
            PhotonneApiFactory.AdminPassword,
            DeviceId: ""));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }
}
