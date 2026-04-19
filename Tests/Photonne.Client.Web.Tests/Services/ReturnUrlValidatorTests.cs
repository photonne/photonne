using Photonne.Client.Web.Services;

namespace Photonne.Client.Web.Tests.Services;

public sealed class ReturnUrlValidatorTests
{
    [Theory]
    [InlineData("/")]
    [InlineData("/albums")]
    [InlineData("/folders/library/1b5c")]
    [InlineData("/search?q=beach")]
    public void Safe_SiteLocalPath(string url)
    {
        Assert.True(ReturnUrlValidator.IsSafe(url));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public void Rejects_NullOrWhitespace(string? url)
    {
        Assert.False(ReturnUrlValidator.IsSafe(url));
    }

    [Theory]
    [InlineData("https://evil.com/x")]
    [InlineData("http://evil.com")]
    [InlineData("//evil.com/x")]
    [InlineData("//evil.com")]
    public void Rejects_AbsoluteOrProtocolRelative(string url)
    {
        // Protocol-relative URLs like "//evil.com" are interpreted by browsers
        // as scheme-relative to the current page's scheme and would redirect
        // off-site. Must be rejected.
        Assert.False(ReturnUrlValidator.IsSafe(url));
    }

    [Theory]
    [InlineData("albums")]
    [InlineData("../admin")]
    [InlineData("javascript:alert(1)")]
    [InlineData("data:text/html;base64,xxx")]
    public void Rejects_PathsThatDontStartWithSingleSlash(string url)
    {
        Assert.False(ReturnUrlValidator.IsSafe(url));
    }
}
