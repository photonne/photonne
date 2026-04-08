using System.Net;
using System.Net.Http.Headers;

namespace Photonne.Client.Web.Services;

public sealed class AuthRefreshHandler : DelegatingHandler
{
    public static readonly HttpRequestOptionsKey<bool> SkipAuthRefresh = new("SkipAuthRefresh");

    private readonly Func<IAuthService> _getAuthService;

    public AuthRefreshHandler(Func<IAuthService> getAuthService)
    {
        _getAuthService = getAuthService;
    }

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        if (ShouldSkip(request))
        {
            return await base.SendAsync(request, cancellationToken);
        }

        var response = await base.SendAsync(request, cancellationToken);
        if (response.StatusCode != HttpStatusCode.Unauthorized)
        {
            return response;
        }

        var authService = _getAuthService();
        var refreshed = await authService.TryRefreshTokenAsync();
        if (!refreshed)
        {
            await authService.LogoutAsync();
            return response;
        }

        response.Dispose();
        var retryRequest = await CloneRequestAsync(request, cancellationToken);
        var token = await authService.GetTokenAsync();
        if (!string.IsNullOrWhiteSpace(token))
        {
            retryRequest.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }

        return await base.SendAsync(retryRequest, cancellationToken);
    }

    private static bool ShouldSkip(HttpRequestMessage request)
    {
        if (request.Options.TryGetValue(SkipAuthRefresh, out var skip) && skip)
        {
            return true;
        }

        var uri = request.RequestUri?.ToString() ?? string.Empty;
        return uri.Contains("/api/auth/login", StringComparison.OrdinalIgnoreCase)
               || uri.Contains("/api/auth/refresh", StringComparison.OrdinalIgnoreCase);
    }

    private static async Task<HttpRequestMessage> CloneRequestAsync(
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        var clone = new HttpRequestMessage(request.Method, request.RequestUri);

        foreach (var header in request.Headers)
        {
            clone.Headers.TryAddWithoutValidation(header.Key, header.Value);
        }

        if (request.Content != null)
        {
            var memoryStream = new MemoryStream();
            await request.Content.CopyToAsync(memoryStream, cancellationToken);
            memoryStream.Position = 0;
            clone.Content = new StreamContent(memoryStream);
            foreach (var header in request.Content.Headers)
            {
                clone.Content.Headers.TryAddWithoutValidation(header.Key, header.Value);
            }
        }

        return clone;
    }
}
