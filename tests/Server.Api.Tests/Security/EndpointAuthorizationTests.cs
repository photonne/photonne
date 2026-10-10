using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Security;

/// <summary>
/// Every /api endpoint must say who can call it: <c>RequireAuthorization</c>
/// or an explicit <c>AllowAnonymous</c>. There is no fallback policy (it would
/// also gate the static web app), so a forgotten call silently ships an
/// anonymous endpoint — which is how the media endpoints ended up readable by
/// anyone holding an asset id.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class EndpointAuthorizationTests
{
    private readonly PhotonneApiFactory _factory;

    public EndpointAuthorizationTests(PhotonneApiFactory factory) => _factory = factory;

    [Fact]
    public void EveryApiEndpoint_DeclaresItsAuthorization()
    {
        var sources = _factory.Services.GetServices<EndpointDataSource>();
        var undeclared = sources
            .SelectMany(s => s.Endpoints)
            .OfType<RouteEndpoint>()
            .Where(e => e.RoutePattern.RawText?.StartsWith("/api", StringComparison.OrdinalIgnoreCase) == true)
            .Where(e => e.Metadata.GetMetadata<IAuthorizeData>() is null
                        && e.Metadata.GetMetadata<IAllowAnonymous>() is null)
            .Select(e => e.DisplayName ?? e.RoutePattern.RawText)
            .Order()
            .ToList();

        Assert.True(undeclared.Count == 0,
            "Endpoints without RequireAuthorization/AllowAnonymous:\n" + string.Join("\n", undeclared));
    }
}
