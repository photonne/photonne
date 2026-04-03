using System.Net.Http.Headers;
using System.Net.Http.Json;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class NotificationService : INotificationService
{
    private readonly HttpClient _http;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public NotificationService(HttpClient http, Func<Task<string?>>? getTokenFunc = null)
    {
        _http = http;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthAsync()
    {
        string? token = _getTokenFunc != null ? await _getTokenFunc() : null;
        _http.DefaultRequestHeaders.Authorization = !string.IsNullOrEmpty(token)
            ? new AuthenticationHeaderValue("Bearer", token)
            : null;
    }

    public async Task<NotificationsPageResponse> GetNotificationsAsync(int page = 1, int pageSize = 20, bool unreadOnly = false)
    {
        await SetAuthAsync();
        var url = $"/api/notifications?page={page}&pageSize={pageSize}&unreadOnly={unreadOnly.ToString().ToLower()}";
        return await _http.GetFromJsonAsync<NotificationsPageResponse>(url) ?? new();
    }

    public async Task<int> GetUnreadCountAsync()
    {
        await SetAuthAsync();
        var result = await _http.GetFromJsonAsync<UnreadCountResponse>("/api/notifications/unread-count");
        return result?.Count ?? 0;
    }

    public async Task MarkAsReadAsync(Guid id)
    {
        await SetAuthAsync();
        await _http.PatchAsync($"/api/notifications/{id}/read", null);
    }

    public async Task MarkAllAsReadAsync()
    {
        await SetAuthAsync();
        await _http.PatchAsync("/api/notifications/read-all", null);
    }

    private sealed class UnreadCountResponse
    {
        public int Count { get; set; }
    }
}
