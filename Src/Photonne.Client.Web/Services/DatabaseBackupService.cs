using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;

namespace Photonne.Client.Web.Services;

public class DatabaseBackupService : IDatabaseBackupService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public DatabaseBackupService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
    {
        _httpClient   = httpClient;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthHeaderAsync()
    {
        var token = _getTokenFunc != null ? await _getTokenFunc() : null;
        _httpClient.DefaultRequestHeaders.Authorization = !string.IsNullOrEmpty(token)
            ? new AuthenticationHeaderValue("Bearer", token)
            : null;
    }

    public async Task<BackupFileResult> ExportAsync()
    {
        await SetAuthHeaderAsync();

        var response = await _httpClient.GetAsync("/api/admin/database/backup");
        response.EnsureSuccessStatusCode();

        var bytes    = await response.Content.ReadAsByteArrayAsync();
        var fileName = response.Content.Headers.ContentDisposition?.FileNameStar
                    ?? response.Content.Headers.ContentDisposition?.FileName
                    ?? $"photonne_backup.json";

        return new BackupFileResult(bytes, fileName.Trim('"'));
    }

    public async Task<DatabaseRestoreResult> RestoreAsync(Stream fileStream, string fileName)
    {
        await SetAuthHeaderAsync();

        using var content       = new MultipartFormDataContent();
        using var streamContent = new StreamContent(fileStream);
        streamContent.Headers.ContentType = new MediaTypeHeaderValue("application/json");
        content.Add(streamContent, "file", fileName);

        var response = await _httpClient.PostAsync("/api/admin/database/restore", content);

        if (!response.IsSuccessStatusCode)
        {
            string errorMessage;
            try
            {
                var errorDoc = await response.Content.ReadFromJsonAsync<JsonElement>();
                errorMessage = errorDoc.TryGetProperty("error", out var errProp)
                    ? errProp.GetString() ?? "Error desconocido."
                    : "Error desconocido.";
            }
            catch
            {
                errorMessage = $"Error {(int)response.StatusCode}: {response.ReasonPhrase}";
            }
            return new DatabaseRestoreResult(false, errorMessage, null);
        }

        var result = await response.Content.ReadFromJsonAsync<RestoreApiResponse>();
        var stats  = result?.Stats is { } s
            ? new DatabaseRestoreStats(s.Users, s.Assets, s.Albums, s.Folders, s.ExternalLibraries)
            : null;

        return new DatabaseRestoreResult(true, result?.Message ?? "Restauración completada.", stats);
    }

    private record RestoreApiResponse(string Message, RestoreApiStats? Stats);
    private record RestoreApiStats(int Users, int Assets, int Albums, int Folders, int ExternalLibraries);
}
