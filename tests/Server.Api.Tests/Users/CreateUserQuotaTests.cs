using System.Net;
using System.Net.Http.Json;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Users;

/// <summary>
/// POST /api/users: a null quota takes the server default, -1 means "no quota"
/// on purpose (as in PUT /api/users/{id}), and an explicit value wins.
/// </summary>
public sealed class CreateUserQuotaTests : IntegrationTestBase
{
    public CreateUserQuotaTests(PhotonneApiFactory factory) : base(factory) { }

    private const long OneGb = 1_073_741_824L;

    private sealed record CreateUserReq(string Username, string Email, string Password, long? StorageQuotaBytes);
    private sealed record CreatedUser(Guid Id, long? StorageQuotaBytes);

    private async Task SetDefaultQuotaGbAsync(int gb)
    {
        using var scope = Factory.Services.CreateScope();
        var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
        await settings.SetSettingAsync("UserSettings.DefaultStorageQuotaGb", gb.ToString(), Guid.Empty);
    }

    private static async Task<HttpResponseMessage> CreateAsync(HttpClient admin, long? quota)
    {
        var username = "u_" + Guid.NewGuid().ToString("N")[..10];
        return await admin.PostAsJsonAsync("/api/users",
            new CreateUserReq(username, $"{username}@test.local", "Valid-Pass-1!", quota));
    }

    private async Task<long?> CreatedQuotaAsync(HttpClient admin, long? quota)
    {
        var response = await CreateAsync(admin, quota);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        var created = await response.Content.ReadFromJsonAsync<CreatedUser>();
        var stored = await WithDbContextAsync(async db => (await db.Users.FindAsync(created!.Id))!.StorageQuotaBytes);
        Assert.Equal(stored, created!.StorageQuotaBytes);
        return stored;
    }

    [Fact]
    public async Task NullQuota_TakesTheServerDefault()
    {
        await SetDefaultQuotaGbAsync(5);
        var (_, admin) = await CreateAuthenticatedUserAsync("Admin");

        Assert.Equal(5 * OneGb, await CreatedQuotaAsync(admin, null));
    }

    [Fact]
    public async Task MinusOne_CreatesTheUserWithoutQuota_EvenWithADefault()
    {
        await SetDefaultQuotaGbAsync(5);
        var (_, admin) = await CreateAuthenticatedUserAsync("Admin");

        Assert.Null(await CreatedQuotaAsync(admin, -1));
    }

    [Fact]
    public async Task AnExplicitQuota_WinsOverTheDefault()
    {
        await SetDefaultQuotaGbAsync(5);
        var (_, admin) = await CreateAuthenticatedUserAsync("Admin");

        Assert.Equal(2 * OneGb, await CreatedQuotaAsync(admin, 2 * OneGb));
    }

    [Fact]
    public async Task OtherNegativeQuotas_AreRejected()
    {
        var (_, admin) = await CreateAuthenticatedUserAsync("Admin");

        var response = await CreateAsync(admin, -2);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("invalid_quota", await response.Content.ReadAsStringAsync());
    }
}
