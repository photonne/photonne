using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Configuration;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Auth;

public sealed class PasswordHashingTests
{
    // Hashing doesn't touch the settings or the database.
    private readonly AuthService _auth = new(new ConfigurationBuilder().Build(), new SettingsService(null!));

    [Fact]
    public void NewHash_IsSaltedPbkdf2AndVerifies()
    {
        var first = _auth.HashPassword("Correct-Horse-1");
        var second = _auth.HashPassword("Correct-Horse-1");

        Assert.StartsWith("pbkdf2-sha512$", first);
        Assert.NotEqual(first, second);
        Assert.True(_auth.VerifyPassword("Correct-Horse-1", first));
        Assert.False(_auth.VerifyPassword("correct-horse-1", first));
        Assert.False(_auth.NeedsRehash(first));
    }

    [Fact]
    public void LegacySha256Hash_StillVerifiesAndAsksForRehash()
    {
        var legacy = Convert.ToBase64String(SHA256.HashData(Encoding.UTF8.GetBytes("Old-Password-1")));

        Assert.True(_auth.VerifyPassword("Old-Password-1", legacy));
        Assert.False(_auth.VerifyPassword("Other-Password-1", legacy));
        Assert.True(_auth.NeedsRehash(legacy));
    }

    [Theory]
    [InlineData("")]
    [InlineData("not base64 at all")]
    [InlineData("pbkdf2-sha512$abc$x$y")]
    [InlineData("pbkdf2-sha512$1000$%%%$%%%")]
    public void MalformedHash_DoesNotVerify(string hash)
    {
        Assert.False(_auth.VerifyPassword("anything", hash));
    }
}
