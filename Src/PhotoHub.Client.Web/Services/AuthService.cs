using System.Net.Http.Json;
using Microsoft.JSInterop;
using PhotoHub.Client.Shared.Services;

namespace PhotoHub.Client.Web.Services;

public class AuthService : IAuthService
{
    private readonly HttpClient _httpClient;
    private readonly IJSRuntime _jsRuntime;
    private const string TokenKey = "authToken";
    private const string RefreshTokenKey = "refreshToken";
    private const string UserKey = "authUser";
    private const string DeviceIdKey = "deviceId";
    private UserDto? _currentUser;
    private Task<UserDto?>? _getUserTask;

    public event Action? OnAuthStateChanged;

    public AuthService(HttpClient httpClient, IJSRuntime jsRuntime)
    {
        _httpClient = httpClient;
        _jsRuntime = jsRuntime;
    }

    public async Task<bool> LoginAsync(string username, string password)
    {
        try
        {
            var deviceId = await EnsureDeviceIdAsync();
            var request = new LoginRequest { Username = username, Password = password, DeviceId = deviceId };
            var response = await _httpClient.PostAsJsonAsync("/api/auth/login", request);

            if (response.IsSuccessStatusCode)
            {
                var loginResponse = await response.Content.ReadFromJsonAsync<LoginResponse>();
                if (loginResponse != null)
                {
                    await SaveTokenAsync(loginResponse.Token);
                    await SaveRefreshTokenAsync(loginResponse.RefreshToken);
                    await SaveUserAsync(loginResponse.User);
                    _currentUser = loginResponse.User;
                    OnAuthStateChanged?.Invoke();
                    return true;
                }
            }
            return false;
        }
        catch
        {
            return false;
        }
    }

    public async Task LogoutAsync()
    {
        await RemoveTokenAsync();
        await RemoveRefreshTokenAsync();
        await RemoveUserAsync();
        _currentUser = null;
        _getUserTask = null;
        OnAuthStateChanged?.Invoke();
    }

    public async Task<bool> IsAuthenticatedAsync()
    {
        var token = await GetTokenAsync();
        return !string.IsNullOrEmpty(token);
    }

    public async Task<string?> GetTokenAsync()
    {
        try
        {
            return await _jsRuntime.InvokeAsync<string>("localStorage.getItem", TokenKey);
        }
        catch
        {
            return null;
        }
    }

    public Task<UserDto?> GetCurrentUserAsync()
    {
        if (_currentUser != null)
            return Task.FromResult<UserDto?>(_currentUser);

        // All concurrent callers share the same Task — only one API call is made
        return _getUserTask ??= FetchCurrentUserAsync();
    }

    private async Task<UserDto?> FetchCurrentUserAsync()
    {
        try
        {
            // Deserialize from localStorage directly — no API call needed
            var userJson = await _jsRuntime.InvokeAsync<string>("localStorage.getItem", UserKey);
            if (!string.IsNullOrEmpty(userJson))
            {
                var cached = System.Text.Json.JsonSerializer.Deserialize<UserDto>(userJson);
                if (cached != null)
                {
                    _currentUser = cached;
                    return cached;
                }
            }

            // Fallback: localStorage empty/invalid — fetch from API once
            var token = await GetTokenAsync();
            if (!string.IsNullOrEmpty(token))
            {
                _httpClient.DefaultRequestHeaders.Authorization =
                    new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", token);
                var response = await _httpClient.GetFromJsonAsync<UserDto>("/api/users/me");
                if (response != null)
                {
                    _currentUser = response;
                    await _jsRuntime.InvokeVoidAsync("localStorage.setItem", UserKey,
                        System.Text.Json.JsonSerializer.Serialize(response));
                    return response;
                }
            }
        }
        catch
        {
            // Ignore
        }
        return null;
    }

    public async Task<bool> TryRefreshTokenAsync()
    {
        try
        {
            var refreshToken = await GetRefreshTokenAsync();
            if (string.IsNullOrWhiteSpace(refreshToken))
            {
                return false;
            }

            var deviceId = await EnsureDeviceIdAsync();
            var request = new RefreshTokenRequest
            {
                RefreshToken = refreshToken,
                DeviceId = deviceId
            };

            using var message = new HttpRequestMessage(HttpMethod.Post, "/api/auth/refresh")
            {
                Content = JsonContent.Create(request)
            };
            message.Options.Set(AuthRefreshHandler.SkipAuthRefresh, true);

            var response = await _httpClient.SendAsync(message);
            if (!response.IsSuccessStatusCode)
            {
                return false;
            }

            var refreshResponse = await response.Content.ReadFromJsonAsync<RefreshTokenResponse>();
            if (refreshResponse == null)
            {
                return false;
            }

            await SaveTokenAsync(refreshResponse.Token);
            await SaveRefreshTokenAsync(refreshResponse.RefreshToken);
            return true;
        }
        catch
        {
            return false;
        }
    }

    public async Task SaveTokenAsync(string token)
    {
        await _jsRuntime.InvokeVoidAsync("localStorage.setItem", TokenKey, token);
    }

    public async Task SaveRefreshTokenAsync(string refreshToken)
    {
        await _jsRuntime.InvokeVoidAsync("localStorage.setItem", RefreshTokenKey, refreshToken);
    }

    public async Task SaveUserAsync(UserDto user)
    {
        await _jsRuntime.InvokeVoidAsync("localStorage.setItem", UserKey,
            System.Text.Json.JsonSerializer.Serialize(user));
        _currentUser = user;
        _getUserTask = null;
        OnAuthStateChanged?.Invoke();
    }

    private async Task RemoveTokenAsync()
    {
        await _jsRuntime.InvokeVoidAsync("localStorage.removeItem", TokenKey);
    }

    private async Task RemoveRefreshTokenAsync()
    {
        await _jsRuntime.InvokeVoidAsync("localStorage.removeItem", RefreshTokenKey);
    }

    private async Task RemoveUserAsync()
    {
        await _jsRuntime.InvokeVoidAsync("localStorage.removeItem", UserKey);
    }

    private async Task<string?> GetRefreshTokenAsync()
    {
        try
        {
            return await _jsRuntime.InvokeAsync<string>("localStorage.getItem", RefreshTokenKey);
        }
        catch
        {
            return null;
        }
    }

    private async Task<string> EnsureDeviceIdAsync()
    {
        try
        {
            var existing = await _jsRuntime.InvokeAsync<string>("localStorage.getItem", DeviceIdKey);
            if (!string.IsNullOrWhiteSpace(existing))
            {
                return existing;
            }
        }
        catch
        {
            // Ignore, we'll regenerate below.
        }

        var deviceId = Guid.NewGuid().ToString("N");
        await _jsRuntime.InvokeVoidAsync("localStorage.setItem", DeviceIdKey, deviceId);
        return deviceId;
    }
}
