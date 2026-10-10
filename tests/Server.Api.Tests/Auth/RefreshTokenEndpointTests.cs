using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Auth;

/// <summary>
/// /api/auth/refresh is the primary place where token rotation, revocation
/// and cross-device invalidation converge. These tests lock down the
/// happy path plus the ways a stolen/stale refresh token must be rejected.
/// </summary>
public sealed class RefreshTokenEndpointTests : IntegrationTestBase
{
    public RefreshTokenEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record LoginReq(string Username, string Password, string DeviceId);
    private sealed record LoginResp(string Token, string RefreshToken);
    private sealed record RefreshReq(string RefreshToken, string DeviceId);

    private async Task<(LoginResp login, string deviceId)> LoginAsync(TestUser user, string? deviceId = null)
    {
        deviceId ??= Guid.NewGuid().ToString("N");
        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/login", new LoginReq(user.Username, user.Password, deviceId));
        response.EnsureSuccessStatusCode();
        var body = await response.Content.ReadFromJsonAsync<LoginResp>();
        return (body!, deviceId);
    }

    [Fact]
    public async Task ValidRefresh_ReturnsNewTokenPair()
    {
        var user = await CreateUserAsync();
        var (login, deviceId) = await LoginAsync(user);

        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(login.RefreshToken, deviceId));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var refreshed = await response.Content.ReadFromJsonAsync<LoginResp>();
        Assert.NotNull(refreshed);
        Assert.False(string.IsNullOrWhiteSpace(refreshed!.Token));
        Assert.False(string.IsNullOrWhiteSpace(refreshed.RefreshToken));
        // Rotation: the old refresh token must no longer work.
        Assert.NotEqual(login.RefreshToken, refreshed.RefreshToken);
    }

    [Fact]
    public async Task OldRefreshToken_IsInvalidated_AfterRotation()
    {
        var user = await CreateUserAsync();
        var (login, deviceId) = await LoginAsync(user);

        var client = CreateClient();

        var first = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(login.RefreshToken, deviceId));
        first.EnsureSuccessStatusCode();

        // Replay the original token — it rotated and must be dead now.
        var replay = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(login.RefreshToken, deviceId));

        Assert.Equal(HttpStatusCode.Unauthorized, replay.StatusCode);
    }

    [Fact]
    public async Task ExpiredRefreshToken_ReturnsUnauthorized_AndDeletesRow()
    {
        var user = await CreateUserAsync();
        var (login, deviceId) = await LoginAsync(user);

        // Push the refresh token into the past.
        await WithDbContextAsync(async db =>
        {
            var token = await db.RefreshTokens.FirstAsync(rt => rt.UserId == user.Id);
            token.ExpiresAt = DateTime.UtcNow.AddMinutes(-5);
            await db.SaveChangesAsync();
        });

        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(login.RefreshToken, deviceId));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);

        // Endpoint removes expired/revoked rows on inspection.
        var remaining = await WithDbContextAsync(db =>
            db.RefreshTokens.AsNoTracking().CountAsync(rt => rt.UserId == user.Id));
        Assert.Equal(0, remaining);
    }

    [Fact]
    public async Task RevokedRefreshToken_ReturnsUnauthorized()
    {
        var user = await CreateUserAsync();
        var (login, deviceId) = await LoginAsync(user);

        await WithDbContextAsync(async db =>
        {
            var token = await db.RefreshTokens.FirstAsync(rt => rt.UserId == user.Id);
            token.RevokedAt = DateTime.UtcNow;
            await db.SaveChangesAsync();
        });

        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(login.RefreshToken, deviceId));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task InactiveUser_CannotRefresh()
    {
        var user = await CreateUserAsync();
        var (login, deviceId) = await LoginAsync(user);

        await WithDbContextAsync(async db =>
        {
            var dbUser = await db.Users.FirstAsync(u => u.Id == user.Id);
            dbUser.IsActive = false;
            await db.SaveChangesAsync();
        });

        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(login.RefreshToken, deviceId));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task WrongDeviceId_DoesNotRefresh()
    {
        var user = await CreateUserAsync();
        var (login, _) = await LoginAsync(user, deviceId: "device-A");

        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(login.RefreshToken, "device-B"));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task SecondLoginOnSameDevice_InvalidatesFirstRefreshToken()
    {
        var user = await CreateUserAsync();
        var (firstLogin, deviceId) = await LoginAsync(user, deviceId: "shared-device");

        // Log in again on the same device — LoginEndpoint wipes previous tokens for (user, device).
        var (_, _) = await LoginAsync(user, deviceId: "shared-device");

        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(firstLogin.RefreshToken, deviceId));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task LoginOnDifferentDevice_DoesNotInvalidateExistingSession()
    {
        var user = await CreateUserAsync();
        var (deviceALogin, deviceA) = await LoginAsync(user, deviceId: "device-A");
        await LoginAsync(user, deviceId: "device-B");

        // Device A's refresh token must survive Device B's login.
        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(deviceALogin.RefreshToken, deviceA));

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Theory]
    [InlineData("", "device-A")]
    [InlineData("refresh-token", "")]
    [InlineData(null, "device-A")]
    public async Task MissingFields_ReturnsBadRequest(string? refreshToken, string deviceId)
    {
        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/refresh",
            new RefreshReq(refreshToken ?? string.Empty, deviceId));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }
}
