using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Infrastructure;

/// <summary>
/// Base class for integration tests. Resets the DB to a clean, seeded state
/// before each test so tests cannot pollute each other.
/// </summary>
[Collection(IntegrationCollection.Name)]
public abstract class IntegrationTestBase : IAsyncLifetime
{
    protected PhotonneApiFactory Factory { get; }

    protected IntegrationTestBase(PhotonneApiFactory factory)
    {
        Factory = factory;
    }

    protected HttpClient CreateClient() => Factory.CreateClient();

    public async Task InitializeAsync() => await Factory.ResetDatabaseAsync();

    public Task DisposeAsync() => Task.CompletedTask;

    /// <summary>
    /// Creates a user directly via the DbContext (bypassing the API) and returns
    /// the credentials that can be used to log in. Useful for arranging test
    /// scenarios without depending on admin-only user-creation endpoints.
    /// </summary>
    protected async Task<TestUser> CreateUserAsync(string? username = null, string role = "User")
    {
        username ??= "u_" + Guid.NewGuid().ToString("N")[..10];
        var password = "P@ss-" + Guid.NewGuid().ToString("N")[..12];

        using var scope = Factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        var auth = scope.ServiceProvider.GetRequiredService<IAuthService>();

        var user = new User
        {
            Username = username,
            Email = $"{username}@test.local",
            PasswordHash = auth.HashPassword(password),
            Role = role,
            IsActive = true,
            IsEmailVerified = true
        };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        return new TestUser(user.Id, username, password, role);
    }

    /// <summary>
    /// Logs the user in through the real login endpoint and returns an HttpClient
    /// that injects the Bearer token on every request.
    /// </summary>
    protected async Task<HttpClient> LoginAsClientAsync(TestUser user)
    {
        var client = CreateClient();
        var response = await client.PostAsJsonAsync("/api/auth/login", new
        {
            Username = user.Username,
            Password = user.Password,
            DeviceId = Guid.NewGuid().ToString("N")
        });
        response.EnsureSuccessStatusCode();

        var body = await response.Content.ReadFromJsonAsync<LoginBody>();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", body!.Token);
        return client;
    }

    /// <summary>
    /// Creates a user and returns an already-authenticated HttpClient in one call.
    /// </summary>
    protected async Task<(TestUser User, HttpClient Client)> CreateAuthenticatedUserAsync(string role = "User")
    {
        var user = await CreateUserAsync(role: role);
        var client = await LoginAsClientAsync(user);
        return (user, client);
    }

    /// <summary>
    /// Opens a scope and hands the DbContext to the caller so tests can arrange
    /// or assert state directly. The scope is disposed when the callback returns.
    /// </summary>
    protected async Task WithDbContextAsync(Func<ApplicationDbContext, Task> action)
    {
        using var scope = Factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        await action(db);
    }

    protected async Task<T> WithDbContextAsync<T>(Func<ApplicationDbContext, Task<T>> action)
    {
        using var scope = Factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<ApplicationDbContext>();
        return await action(db);
    }

    private sealed record LoginBody(string Token, string RefreshToken);
}

public sealed record TestUser(Guid Id, string Username, string Password, string Role);
