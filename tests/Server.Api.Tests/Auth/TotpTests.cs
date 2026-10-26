using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Services.Auth;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Auth;

/// <summary>The algorithm against RFC 6238's own test vectors (SHA-1, last six digits).</summary>
public sealed class TotpAlgorithmTests
{
    private static readonly string Secret = Totp.Base32Encode("12345678901234567890"u8);

    [Theory]
    [InlineData(59, "287082")]
    [InlineData(1111111109, "081804")]
    [InlineData(1234567890, "005924")]
    [InlineData(2000000000, "279037")]
    public void Matches_the_RFC_vectors(long unixSeconds, string expected)
    {
        Assert.Equal(expected, Totp.Code(Secret, Totp.StepAt(DateTimeOffset.FromUnixTimeSeconds(unixSeconds))));
    }

    [Fact]
    public void Accepts_a_step_either_side_but_never_the_same_one_twice()
    {
        var now = DateTimeOffset.FromUnixTimeSeconds(1234567890);
        var step = Totp.StepAt(now);
        Assert.Equal(step - 1, Totp.Verify(Secret, Totp.Code(Secret, step - 1), now, lastStep: null));
        Assert.Equal(step, Totp.Verify(Secret, "005 924", now, lastStep: null));
        Assert.Null(Totp.Verify(Secret, "005924", now, lastStep: step));
        Assert.Null(Totp.Verify(Secret, Totp.Code(Secret, step - 2), now, lastStep: null));
        Assert.Null(Totp.Verify(Secret, "12345", now, lastStep: null));
    }

    [Fact]
    public void Base32_goes_both_ways()
    {
        var bytes = new byte[] { 0, 1, 2, 250, 251, 252, 253, 254, 255 };
        Assert.Equal(bytes, Totp.Base32Decode(Totp.Base32Encode(bytes)));
    }
}

[Collection(IntegrationCollection.Name)]
public class TotpFlowTests : IntegrationTestBase
{
    public TotpFlowTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record LoginBody(string Username, string Password, string DeviceId, string? TotpCode = null);
    private sealed record Setup(string Secret, string OtpAuthUri);
    private sealed record Codes(List<string> RecoveryCodes);
    private sealed record Error(string Code);

    private static string CodeAt(string secret, int stepsFromNow) =>
        Totp.Code(secret, Totp.StepAt(DateTimeOffset.UtcNow) + stepsFromNow);

    [Fact]
    public async Task Once_on_the_login_asks_for_the_code_and_takes_a_recovery_code_once()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();

        var setup = await (await client.PostAsync("/api/auth/totp/setup", null)).Content.ReadFromJsonAsync<Setup>();
        Assert.StartsWith($"otpauth://totp/Photonne:{user.Username}?secret={setup!.Secret}", setup.OtpAuthUri);

        var wrong = await client.PostAsJsonAsync("/api/auth/totp/enable", new { code = "000000" });
        Assert.Equal(HttpStatusCode.BadRequest, wrong.StatusCode);
        var enabled = await client.PostAsJsonAsync("/api/auth/totp/enable", new { code = CodeAt(setup.Secret, 0) });
        var codes = (await enabled.Content.ReadFromJsonAsync<Codes>())!.RecoveryCodes;
        Assert.Equal(10, codes.Count);

        var anonymous = CreateClient();
        var noCode = await anonymous.PostAsJsonAsync("/api/auth/login", new LoginBody(user.Username, user.Password, "d1"));
        Assert.Equal(HttpStatusCode.Unauthorized, noCode.StatusCode);
        Assert.Equal("totp_required", (await noCode.Content.ReadFromJsonAsync<Error>())!.Code);

        var badCode = await anonymous.PostAsJsonAsync("/api/auth/login", new LoginBody(user.Username, user.Password, "d1", "123456"));
        Assert.Equal("totp_invalid", (await badCode.Content.ReadFromJsonAsync<Error>())!.Code);

        // The enable used this step's code: the next one gets in, and only once.
        var next = CodeAt(setup.Secret, 1);
        var ok = await anonymous.PostAsJsonAsync("/api/auth/login", new LoginBody(user.Username, user.Password, "d1", next));
        Assert.Equal(HttpStatusCode.OK, ok.StatusCode);
        var replay = await anonymous.PostAsJsonAsync("/api/auth/login", new LoginBody(user.Username, user.Password, "d2", next));
        Assert.Equal(HttpStatusCode.Unauthorized, replay.StatusCode);

        var recovery = codes[0].ToUpperInvariant();
        Assert.Equal(HttpStatusCode.OK,
            (await anonymous.PostAsJsonAsync("/api/auth/login", new LoginBody(user.Username, user.Password, "d3", recovery))).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized,
            (await anonymous.PostAsJsonAsync("/api/auth/login", new LoginBody(user.Username, user.Password, "d4", recovery))).StatusCode);

        var status = await client.GetFromJsonAsync<Dictionary<string, object>>("/api/auth/totp");
        Assert.Equal("9", status!["recoveryCodesLeft"].ToString());
    }

    [Fact]
    public async Task Turning_it_off_takes_the_password_and_an_admin_can_reset_it()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();
        var setup = await (await client.PostAsync("/api/auth/totp/setup", null)).Content.ReadFromJsonAsync<Setup>();
        await client.PostAsJsonAsync("/api/auth/totp/enable", new { code = CodeAt(setup!.Secret, 0) });

        Assert.Equal(HttpStatusCode.BadRequest,
            (await client.PostAsJsonAsync("/api/auth/totp/disable", new { password = "nope" })).StatusCode);

        var (_, admin) = await CreateAuthenticatedUserAsync(role: "Admin");
        Assert.Equal(HttpStatusCode.NoContent, (await admin.PostAsync($"/api/users/{user.Id}/totp/reset", null)).StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, (await client.PostAsync($"/api/users/{user.Id}/totp/reset", null)).StatusCode);

        var login = await CreateClient().PostAsJsonAsync("/api/auth/login", new LoginBody(user.Username, user.Password, "d1"));
        Assert.Equal(HttpStatusCode.OK, login.StatusCode);
    }
}
