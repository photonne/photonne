namespace Photonne.Client.Web.Services;

public interface IAuthService
{
    Task<LoginResult> LoginAsync(string username, string password);
    Task LogoutAsync();
    Task<bool> IsAuthenticatedAsync();
    Task<string?> GetTokenAsync();
    Task<UserDto?> GetCurrentUserAsync();
    Task<bool> TryRefreshTokenAsync();
    event Action? OnAuthStateChanged;
}

public enum LoginErrorKind
{
    None,
    InvalidCredentials,
    RateLimited,
    BadRequest,
    ServerError,
    NetworkError,
    Unknown
}

public sealed record LoginResult(bool Success, LoginErrorKind Error = LoginErrorKind.None, string? Message = null)
{
    public static LoginResult Ok() => new(true);
    public static LoginResult Fail(LoginErrorKind kind, string? message = null) => new(false, kind, message);
}

public class LoginRequest
{
    public string Username { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
}

public class LoginResponse
{
    public string Token { get; set; } = string.Empty;
    public string RefreshToken { get; set; } = string.Empty;
    public UserDto User { get; set; } = null!;
}

public class RefreshTokenRequest
{
    public string RefreshToken { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
}

public class RefreshTokenResponse
{
    public string Token { get; set; } = string.Empty;
    public string RefreshToken { get; set; } = string.Empty;
}
