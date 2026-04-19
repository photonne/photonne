using System.Net;
using System.Text.Json;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.JSInterop;
using Photonne.Client.Web.Services;

namespace Photonne.Client.Web.Tests.Services;

public sealed class AuthServiceLoginTests
{
    [Fact]
    public async Task Login_WithUnauthorized_MapsToInvalidCredentials()
    {
        var service = BuildService(HttpStatusCode.Unauthorized);

        var result = await service.LoginAsync("user", "wrong");

        Assert.False(result.Success);
        Assert.Equal(LoginErrorKind.InvalidCredentials, result.Error);
    }

    [Fact]
    public async Task Login_WithTooManyRequests_MapsToRateLimited()
    {
        var service = BuildService(HttpStatusCode.TooManyRequests);

        var result = await service.LoginAsync("user", "pwd");

        Assert.False(result.Success);
        Assert.Equal(LoginErrorKind.RateLimited, result.Error);
    }

    [Fact]
    public async Task Login_With500_MapsToServerError()
    {
        var service = BuildService(HttpStatusCode.InternalServerError);

        var result = await service.LoginAsync("user", "pwd");

        Assert.False(result.Success);
        Assert.Equal(LoginErrorKind.ServerError, result.Error);
    }

    [Fact]
    public async Task Login_WithNetworkFailure_MapsToNetworkError()
    {
        var service = BuildService(new HttpRequestException("boom"));

        var result = await service.LoginAsync("user", "pwd");

        Assert.False(result.Success);
        Assert.Equal(LoginErrorKind.NetworkError, result.Error);
    }

    [Fact]
    public async Task Login_WithMalformedJson_MapsToServerError()
    {
        var service = BuildService(HttpStatusCode.OK, body: "not-json-at-all");

        var result = await service.LoginAsync("user", "pwd");

        Assert.False(result.Success);
        Assert.Equal(LoginErrorKind.ServerError, result.Error);
    }

    [Fact]
    public async Task Login_WithValidResponse_ReturnsOk()
    {
        var payload = JsonSerializer.Serialize(new
        {
            token = "jwt-token",
            refreshToken = "refresh-token",
            user = new { id = Guid.NewGuid(), username = "alice", email = "a@b.c", role = "User" }
        });

        var service = BuildService(HttpStatusCode.OK, body: payload);

        var result = await service.LoginAsync("alice", "pwd");

        Assert.True(result.Success);
        Assert.Equal(LoginErrorKind.None, result.Error);
    }

    private static AuthService BuildService(HttpStatusCode status, string body = "")
    {
        var handler = new StubHandler(_ => new HttpResponseMessage(status)
        {
            Content = new StringContent(body, System.Text.Encoding.UTF8, "application/json")
        });
        var http = new HttpClient(handler) { BaseAddress = new Uri("http://localhost") };
        return new AuthService(http, new StubJsRuntime(), NullLogger<AuthService>.Instance);
    }

    private static AuthService BuildService(Exception thrown)
    {
        var handler = new StubHandler(_ => throw thrown);
        var http = new HttpClient(handler) { BaseAddress = new Uri("http://localhost") };
        return new AuthService(http, new StubJsRuntime(), NullLogger<AuthService>.Instance);
    }

    private sealed class StubHandler(Func<HttpRequestMessage, HttpResponseMessage> respond) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
            => Task.FromResult(respond(request));
    }

    /// <summary>
    /// Minimal IJSRuntime fake: returns empty for localStorage.getItem and ignores setItem.
    /// Lets AuthService run end-to-end without touching a real browser.
    /// </summary>
    private sealed class StubJsRuntime : IJSRuntime
    {
        public ValueTask<TValue> InvokeAsync<TValue>(string identifier, object?[]? args)
            => ValueTask.FromResult(default(TValue)!);

        public ValueTask<TValue> InvokeAsync<TValue>(string identifier, CancellationToken cancellationToken, object?[]? args)
            => ValueTask.FromResult(default(TValue)!);
    }
}
