using Microsoft.Extensions.Configuration;
using Photonne.Server.Api.Shared.Authorization;

namespace Photonne.Server.Api.Tests.Auth;

public sealed class JwtSigningKeyTests : IDisposable
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "photonne-jwt-tests", Guid.NewGuid().ToString("N"));
    private string KeyFile => Path.Combine(_dir, "config", "jwt.key");

    private IConfiguration Config(string? key) => new ConfigurationBuilder()
        .AddInMemoryCollection(new Dictionary<string, string?>
        {
            ["Jwt:Key"] = key,
            ["Jwt:KeyFile"] = KeyFile,
        })
        .Build();

    [Fact]
    public void RealKey_IsUsedAsIs()
    {
        const string key = "a-real-secret-that-is-long-enough-0123456789";
        var warnings = new List<string>();

        Assert.Equal(key, JwtSigningKey.Resolve(Config(key), warnings.Add));
        Assert.Empty(warnings);
        Assert.False(File.Exists(KeyFile));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("TOKEN_SUPER_SECRET")]
    [InlineData("cambia_esto_por_clave_jwt_muy_larga_y_aleatoria")]
    [InlineData("short")]
    public void MissingOrPlaceholderKey_IsReplacedByAGeneratedOne(string? configured)
    {
        var warnings = new List<string>();

        var key = JwtSigningKey.Resolve(Config(configured), warnings.Add);

        Assert.NotEqual(configured, key);
        Assert.Null(JwtSigningKey.RejectionReason(key));
        Assert.Equal(key, File.ReadAllText(KeyFile));
        Assert.Single(warnings);
    }

    [Fact]
    public void GeneratedKey_SurvivesARestart()
    {
        var first = JwtSigningKey.Resolve(Config("TOKEN_SUPER_SECRET"), _ => { });
        var second = JwtSigningKey.Resolve(Config("TOKEN_SUPER_SECRET"), _ => { });

        Assert.Equal(first, second);
    }

    public void Dispose()
    {
        if (Directory.Exists(_dir)) Directory.Delete(_dir, recursive: true);
    }
}
