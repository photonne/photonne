using System.Net.Http.Headers;
using System.Net.Http.Json;
using Microsoft.AspNetCore.Components.Forms;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class ShareService : IShareService
{
    // El servidor ya aplica su propio límite (ServerSettings.MaxUploadSizeMb);
    // este solo evita que OpenReadStream corte archivos grandes en el cliente.
    private const long MaxUploadBytes = 10L * 1024 * 1024 * 1024;

    private readonly HttpClient _http;

    public ShareService(HttpClient http)
    {
        _http = http;
    }

    public async Task<SharedContentResponse?> GetSharedContentAsync(string token, string? password = null)
    {
        var url = string.IsNullOrEmpty(password)
            ? $"/api/share/{token}"
            : $"/api/share/{token}?pw={Uri.EscapeDataString(password)}";
        return await _http.GetFromJsonAsync<SharedContentResponse>(url);
    }

    public async Task<bool> UploadAssetAsync(string token, IBrowserFile file, string? uploaderName, string? password)
    {
        using var content = new MultipartFormDataContent();

        var fileContent = new StreamContent(file.OpenReadStream(MaxUploadBytes));
        fileContent.Headers.ContentType = new MediaTypeHeaderValue(
            string.IsNullOrEmpty(file.ContentType) ? "application/octet-stream" : file.ContentType);
        content.Add(fileContent, "file", file.Name);

        if (!string.IsNullOrWhiteSpace(uploaderName))
            content.Add(new StringContent(uploaderName), "uploaderName");
        if (!string.IsNullOrEmpty(password))
            content.Add(new StringContent(password), "pw");
        content.Add(new StringContent(file.LastModified.ToUnixTimeMilliseconds().ToString()), "fileModifiedAt");

        var response = await _http.PostAsync($"/api/share/{token}/upload", content);
        return response.IsSuccessStatusCode;
    }
}
