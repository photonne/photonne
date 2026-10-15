using Photonne.Server.Api.Features.Admin;

namespace Photonne.Server.Api.Tests.Admin;

/// <summary>
/// Cada componente se publica con su etiqueta (server-vX.Y.Z, native-vX.Y.Z,
/// ml-vX.Y.Z): el aviso de actualización del servidor y el de la app de
/// escritorio no deben mezclar las releases de otro componente.
/// </summary>
public sealed class VersionReleaseSelectionTests
{
    private static VersionEndpoint.GitHubRelease Release(string tag, bool draft = false, bool prerelease = false) =>
        new(tag, $"https://github.com/photonne/photonne/releases/tag/{tag}", null, null, draft, prerelease);

    [Theory]
    [InlineData("server-v1.201.0", "server", "1.201.0")]
    [InlineData("native-v2.0.3", "native", "2.0.3")]
    [InlineData("ml-v1.200.2", "ml", "1.200.2")]
    [InlineData("v1.200.1", null, "1.200.1")]
    public void ParseTag_SplitsComponentAndVersion(string tag, string? component, string version)
    {
        Assert.Equal((component, version), VersionEndpoint.ParseTag(tag));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("latest")]
    [InlineData("-v1.0.0")]
    [InlineData("server-v")]
    public void ParseTag_RejectsUnknownTags(string? tag)
    {
        Assert.Null(VersionEndpoint.ParseTag(tag));
    }

    [Fact]
    public void PickLatest_IgnoresOtherComponents()
    {
        var releases = new[]
        {
            Release("native-v1.205.0"),
            Release("ml-v1.210.0"),
            Release("server-v1.201.0"),
            Release("v1.200.1"),
        };

        Assert.Equal("1.201.0", VersionEndpoint.PickLatest(releases, "server")?.Version);
        Assert.Equal("1.205.0", VersionEndpoint.PickLatest(releases, "native")?.Version);
    }

    [Fact]
    public void PickLatest_FallsBackToReleasesFromBeforeTheSplit()
    {
        var releases = new[] { Release("server-v1.201.0"), Release("v1.200.1"), Release("v1.199.0") };

        var native = VersionEndpoint.PickLatest(releases, "native");

        Assert.Equal("1.200.1", native?.Version);
        Assert.Equal("https://github.com/photonne/photonne/releases/tag/v1.200.1", native?.HtmlUrl);
    }

    [Fact]
    public void PickLatest_TakesTheHighestVersionNotTheNewest()
    {
        // Las releases llegan por fecha: un parche de una rama vieja no gana.
        var releases = new[] { Release("server-v1.9.1"), Release("server-v1.10.0") };

        Assert.Equal("1.10.0", VersionEndpoint.PickLatest(releases, "server")?.Version);
    }

    [Fact]
    public void PickLatest_SkipsDraftsAndPrereleases()
    {
        var releases = new[]
        {
            Release("server-v1.203.0", draft: true),
            Release("server-v1.202.0-rc.1", prerelease: true),
            Release("server-v1.201.0"),
        };

        Assert.Equal("1.201.0", VersionEndpoint.PickLatest(releases, "server")?.Version);
    }

    [Fact]
    public void PickLatest_ReturnsNullWithoutReleases()
    {
        Assert.Null(VersionEndpoint.PickLatest([], "native"));
    }
}
