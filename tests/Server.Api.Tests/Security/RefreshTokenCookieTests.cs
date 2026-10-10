using System.Net;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Security;

/// <summary>
/// A web client that logs in with <c>refreshTokenInCookie</c> never sees its
/// refresh token: it lives in an HttpOnly cookie that refresh rotates and
/// logout revokes.
/// </summary>
public sealed class RefreshTokenCookieTests : IntegrationTestBase
{
    public RefreshTokenCookieTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record TokenBody(string Token, string RefreshToken);

    [Fact]
    public async Task Login_KeepsTheRefreshToken_OutOfTheBody()
    {
        var user = await CreateUserAsync();
        var browser = CreateBrowser();

        var response = await browser.PostAsJsonAsync("/api/auth/login", LoginBody(user, Guid.NewGuid().ToString("N")));

        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<TokenBody>();
        Assert.False(string.IsNullOrEmpty(body!.Token));
        Assert.Equal(string.Empty, body.RefreshToken);
        var cookie = Assert.Single(response.Headers.GetValues("Set-Cookie"), c => c.StartsWith(RefreshTokenCookie.Name + "="));
        Assert.Contains("httponly", cookie, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("samesite=strict", cookie, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("path=/api/auth", cookie, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task Refresh_ReadsAndRotatesTheCookie()
    {
        var user = await CreateUserAsync();
        var deviceId = Guid.NewGuid().ToString("N");
        var browser = CreateBrowser();
        (await browser.PostAsJsonAsync("/api/auth/login", LoginBody(user, deviceId))).EnsureSuccessStatusCode();

        var first = await browser.PostAsJsonAsync("/api/auth/refresh", new { DeviceId = deviceId });
        var second = await browser.PostAsJsonAsync("/api/auth/refresh", new { DeviceId = deviceId });

        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.Equal(HttpStatusCode.OK, second.StatusCode);
        var body = await second.Content.ReadFromJsonAsync<TokenBody>();
        Assert.False(string.IsNullOrEmpty(body!.Token));
        Assert.Equal(string.Empty, body.RefreshToken);
    }

    [Fact]
    public async Task Logout_RevokesTheRefreshToken()
    {
        var user = await CreateUserAsync();
        var deviceId = Guid.NewGuid().ToString("N");
        var browser = CreateBrowser();
        (await browser.PostAsJsonAsync("/api/auth/login", LoginBody(user, deviceId))).EnsureSuccessStatusCode();

        (await browser.PostAsync("/api/auth/logout", null)).EnsureSuccessStatusCode();
        var remaining = await WithDbContextAsync(db => Task.FromResult(db.RefreshTokens.Count(rt => rt.UserId == user.Id)));
        var refresh = await browser.PostAsJsonAsync("/api/auth/refresh", new { DeviceId = deviceId });

        Assert.Equal(0, remaining);
        Assert.NotEqual(HttpStatusCode.OK, refresh.StatusCode);
    }

    private HttpClient CreateBrowser()
        => Factory.CreateClient(new WebApplicationFactoryClientOptions { HandleCookies = true });

    private static object LoginBody(TestUser user, string deviceId) => new
    {
        Username = user.Username,
        Password = user.Password,
        DeviceId = deviceId,
        RefreshTokenInCookie = true
    };
}
