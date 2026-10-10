using Microsoft.AspNetCore.Http;
using Photonne.Server.Api.Shared.Authorization;

namespace Photonne.Server.Api.Tests.Security;

/// <summary>
/// Which requests the media cookie authenticates: only GET/HEAD on the routes
/// an &lt;img&gt;/&lt;video&gt; loads.
/// </summary>
public sealed class MediaSessionCookieTests
{
    [Theory]
    [InlineData("GET", "/api/assets/a1/thumbnail")]
    [InlineData("GET", "/api/assets/a1/content")]
    [InlineData("GET", "/api/assets/a1/motion")]
    [InlineData("GET", "/api/assets/a1/motion/frames/0")]
    [InlineData("HEAD", "/api/assets/a1/motion/frames/42")]
    public void MediaRoutes_AcceptTheCookie(string method, string path)
        => Assert.True(MediaSessionCookie.IsMediaRequest(Request(method, path)));

    [Theory]
    [InlineData("GET", "/api/assets/a1")]
    [InlineData("GET", "/api/assets/a1/motion/frames")]
    [InlineData("GET", "/api/assets/a1/motion/frames/abc")]
    [InlineData("POST", "/api/assets/a1/motion/frames/3/save")]
    [InlineData("POST", "/api/assets/a1/motion/frames/3")]
    [InlineData("GET", "/api/assets/a1/frames/3")]
    [InlineData("GET", "/api/share/t1/assets/a1/content")]
    public void OtherRoutes_IgnoreTheCookie(string method, string path)
        => Assert.False(MediaSessionCookie.IsMediaRequest(Request(method, path)));

    private static HttpRequest Request(string method, string path)
    {
        var context = new DefaultHttpContext();
        context.Request.Method = method;
        context.Request.Path = path;
        return context.Request;
    }
}
