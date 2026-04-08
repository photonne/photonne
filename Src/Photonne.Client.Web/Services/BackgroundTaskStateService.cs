using System.Net.Http.Json;
using System.Text.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Singleton service that tracks active background admin tasks.
/// On page load, admin pages call CheckForRunningTaskAsync to see if a task
/// of their type is still running on the server and reconnect automatically.
/// </summary>
public class BackgroundTaskStateService
{
    private readonly HttpClient _httpClient;
    private static readonly JsonSerializerOptions _jsonOptions = new() { PropertyNameCaseInsensitive = true };

    public BackgroundTaskStateService(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    /// <summary>
    /// Query the server for any running task of the given type (e.g. "IndexAssets").
    /// Returns the task DTO if one is found, null otherwise.
    /// </summary>
    public async Task<BackgroundTaskDto?> GetRunningTaskAsync(string taskType, CancellationToken ct = default)
    {
        try
        {
            var tasks = await _httpClient.GetFromJsonAsync<List<BackgroundTaskDto>>(
                "/api/tasks", _jsonOptions, ct);
            return tasks?.FirstOrDefault(t => t.Type == taskType && t.IsRunning);
        }
        catch
        {
            return null;
        }
    }
}
