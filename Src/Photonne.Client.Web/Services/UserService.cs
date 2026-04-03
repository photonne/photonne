using System.Net.Http.Json;
using System.Net.Http.Headers;

namespace Photonne.Client.Web.Services;

public class UserService : IUserService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public UserService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
    {
        _httpClient = httpClient;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthHeaderAsync()
    {
        string? token = null;
        if (_getTokenFunc != null)
        {
            token = await _getTokenFunc();
        }

        if (!string.IsNullOrEmpty(token))
        {
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }
        else
        {
            _httpClient.DefaultRequestHeaders.Authorization = null;
        }
    }

    public async Task<List<UserDto>> GetUsersAsync()
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.GetFromJsonAsync<List<UserDto>>("/api/users");
        return response ?? new List<UserDto>();
    }

    public async Task<List<ShareableUserDto>> GetShareableUsersAsync()
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.GetFromJsonAsync<List<ShareableUserDto>>("/api/users/shareable");
        return response ?? new List<ShareableUserDto>();
    }

    public async Task<UserDto> GetUserAsync(Guid id)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.GetFromJsonAsync<UserDto>($"/api/users/{id}");
        return response ?? throw new Exception("User not found");
    }

    public async Task<UserDto> GetCurrentUserAsync()
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.GetFromJsonAsync<UserDto>("/api/users/me");
        return response ?? throw new Exception("User not found");
    }

    public async Task<UserDto> CreateUserAsync(CreateUserRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/users", request);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<UserDto>() ?? throw new Exception("Failed to create user");
    }

    public async Task<UserDto> UpdateUserAsync(Guid id, UpdateUserRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PutAsJsonAsync($"/api/users/{id}", request);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<UserDto>() ?? throw new Exception("Failed to update user");
    }

    public async Task DeleteUserAsync(Guid id)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.DeleteAsync($"/api/users/{id}");
        response.EnsureSuccessStatusCode();
    }

    public async Task ResetPasswordAsync(Guid id, string newPassword)
    {
        await SetAuthHeaderAsync();
        var request = new ResetPasswordRequest { NewPassword = newPassword };
        var response = await _httpClient.PostAsJsonAsync($"/api/users/{id}/reset-password", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task<UserDto> UpdateProfileAsync(UpdateProfileRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PutAsJsonAsync("/api/users/me", request);
        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadFromJsonAsync<ErrorResponse>();
            throw new Exception(error?.Error ?? "Error al actualizar el perfil");
        }
        return await response.Content.ReadFromJsonAsync<UserDto>() ?? throw new Exception("Error al actualizar el perfil");
    }

    public async Task ChangePasswordAsync(ChangePasswordRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/users/me/change-password", request);
        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadFromJsonAsync<ErrorResponse>();
            throw new Exception(error?.Error ?? "Error al cambiar la contraseña");
        }
    }

    private class ErrorResponse
    {
        public string? Error { get; set; }
    }
}
