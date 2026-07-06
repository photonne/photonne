using System.Net.Http.Json;
using Microsoft.Extensions.Options;

namespace Photonne.Server.Api.Shared.Services.Ml;

public interface IMlConfigClient
{
    /// <summary>
    /// Asks the ML service to reload <paramref name="task"/> on the given ONNX
    /// provider spec (see <see cref="MlProviders"/>). Best-effort: returns false
    /// instead of throwing when the service is unreachable or rejects the
    /// change, so a settings save is never blocked by the ML container.
    /// </summary>
    Task<bool> SetProviderAsync(string task, string providerSpec, CancellationToken cancellationToken = default);
}

public class MlConfigClient : IMlConfigClient
{
    private readonly HttpClient _http;
    private readonly ILogger<MlConfigClient> _logger;

    public MlConfigClient(HttpClient http, ILogger<MlConfigClient> logger)
    {
        _http = http;
        _logger = logger;
    }

    public async Task<bool> SetProviderAsync(string task, string providerSpec, CancellationToken cancellationToken = default)
    {
        try
        {
            using var response = await _http.PostAsJsonAsync(
                "/v1/config",
                new { task, providers = providerSpec },
                cancellationToken);

            if (!response.IsSuccessStatusCode)
            {
                var body = await response.Content.ReadAsStringAsync(cancellationToken);
                _logger.LogWarning(
                    "ML config push for task {Task} -> {Providers} failed with {Status}: {Body}",
                    task, providerSpec, (int)response.StatusCode, body);
                return false;
            }

            _logger.LogInformation("ML task {Task} reloaded on providers={Providers}", task, providerSpec);
            return true;
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException)
        {
            _logger.LogWarning(ex,
                "Could not reach ML service to set task {Task} -> {Providers}; the ML container will keep its current providers",
                task, providerSpec);
            return false;
        }
    }
}
