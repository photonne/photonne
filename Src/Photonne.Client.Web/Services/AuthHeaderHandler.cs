using System.Net.Http.Headers;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Añade el Bearer a CADA petición en el pipeline del HttpClient, en lugar del
/// viejo SetAuthHeaderAsync que mutaba DefaultRequestHeaders del cliente
/// compartido — una carrera en cuanto el workspace hidrata buckets en
/// paralelo. Si la petición ya trae Authorization (p. ej. un servicio legado
/// que aún fija el header) se respeta; AuthRefreshHandler sigue aguas abajo
/// reintentando los 401 con el token renovado.
/// </summary>
public class AuthHeaderHandler : DelegatingHandler
{
    private readonly Func<IAuthService> _authServiceFactory;

    public AuthHeaderHandler(Func<IAuthService> authServiceFactory)
    {
        _authServiceFactory = authServiceFactory;
    }

    protected override async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        if (request.Headers.Authorization == null)
        {
            var token = await _authServiceFactory().GetTokenAsync();
            if (!string.IsNullOrEmpty(token))
            {
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            }
        }

        return await base.SendAsync(request, cancellationToken);
    }
}
