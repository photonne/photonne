using Photonne.Server.Api.Features.Auth;

namespace Photonne.Server.Api.Tests.Auth;

public sealed class RefreshTokenHelperTests
{
    [Fact]
    public void GenerateToken_ProducesHighEntropyValues()
    {
        var a = RefreshTokenHelper.GenerateToken();
        var b = RefreshTokenHelper.GenerateToken();

        Assert.NotEqual(a, b);
        Assert.False(string.IsNullOrWhiteSpace(a));
        // 64 random bytes → 88 chars in standard base64.
        Assert.True(a.Length >= 80);
    }

    [Fact]
    public void HashToken_IsDeterministicAndDifferentFromInput()
    {
        var token = RefreshTokenHelper.GenerateToken();

        var hash1 = RefreshTokenHelper.HashToken(token);
        var hash2 = RefreshTokenHelper.HashToken(token);

        Assert.Equal(hash1, hash2);
        Assert.NotEqual(token, hash1);
    }

    [Fact]
    public void HashToken_DifferentInputsProduceDifferentHashes()
    {
        var hashA = RefreshTokenHelper.HashToken("token-a");
        var hashB = RefreshTokenHelper.HashToken("token-b");

        Assert.NotEqual(hashA, hashB);
    }
}
