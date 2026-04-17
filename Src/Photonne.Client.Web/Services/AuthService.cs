using System.Net;
using System.Net.Http.Json;
using Microsoft.Extensions.Logging;
using Microsoft.JSInterop;

namespace Photonne.Client.Web.Services;

public class AuthService : IAuthService
{
    private readonly HttpClient _httpClient;
    private readonly IJSRuntime _jsRuntime;
    private readonly ILogger<AuthService> _logger;
    private const string TokenKey = "authToken";
    private const string RefreshTokenKey = "refreshToken";
    private const string UserKey = "authUser";
    private const string DeviceIdKey = "deviceId";
    private UserDto? _currentUser;
    private Task<UserDto?>? _getUserTask;

    public event Action? OnAuthStateChanged;

    public AuthService(HttpClient httpClient, IJSRuntime jsRuntime, ILogger<AuthService> logger)
    {
        _httpClient = httpClient;
        _jsRuntime = jsRuntime;
        _logger = logger;
    }

    public async Task<LoginResult> LoginAsync(string username, string password)
    {
        HttpResponseMessage? response;
        try
        {
            var deviceId = await EnsureDeviceIdAsync();
            var request = new LoginRequest { Username = username, Password = password, DeviceId = deviceId };
            response = await _httpClient.PostAsJsonAsync("/api/auth/login", request);
        }
        catch (HttpRequestException ex)
        {
            _logger.LogWarning(ex, "Login failed: network error");
            return LoginResult.Fail(LoginErrorKind.NetworkError);
        }
        catch (TaskCanceledException ex)
        {
            _logger.LogWarning(ex, "Login failed: request timed out");
            return LoginResult.Fail(LoginErrorKind.NetworkError);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Login failed: unexpected error");
            return LoginResult.Fail(LoginErrorKind.Unknown);
        }

        if (response.IsSuccessStatusCode)
        {
            LoginResponse? loginResponse;
            try
            {
                loginResponse = await response.Content.ReadFromJsonAsync<LoginResponse>();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Login response could not be parsed");
                return LoginResult.Fail(LoginErrorKind.ServerError);
            }

            if (loginResponse == null)
            {
                _logger.LogError("Login response was empty");
                return LoginResult.Fail(LoginErrorKind.ServerError);
            }

            await SaveTokenAsync(loginResponse.Token);
            await SaveRefreshTokenAsync(loginResponse.RefreshToken);
            await SaveUserAsync(loginResponse.User);
            _currentUser = loginResponse.User;
            OnAuthStateChanged?.Invoke();
            return LoginResult.Ok();
        }

        return response.StatusCode switch
        {
            HttpStatusCode.Unauthorized => LoginResult.Fail(LoginErrorKind.InvalidCredentials),
            HttpStatusCode.BadRequest => LoginResult.Fail(LoginErrorKind.BadRequest),
            HttpStatusCode.TooManyRequests => LoginResult.Fail(LoginErrorKind.RateLimited),
            var s when (int)s >= 500 => LoginResult.Fail(LoginErrorKind.ServerError),
            _ => LoginResult.Fail(LoginErrorKind.Unknown)
        };
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
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to read auth token from localStorage");
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
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to fetch current user");
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
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Refresh token request failed");
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
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to read refresh token from localStorage");
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
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to read device id from localStorage, regenerating");
        }

        var deviceId = Guid.NewGuid().ToString("N");
        await _jsRuntime.InvokeVoidAsync("localStorage.setItem", DeviceIdKey, deviceId);
        return deviceId;
    }
}
