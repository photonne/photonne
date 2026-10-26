using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public interface IAuthService
{
    Task<string> GenerateTokenAsync(User user);
    string HashPassword(string password);
    bool VerifyPassword(string password, string hash);
    /// <summary>True when the hash is in an older format and should be replaced after a successful login.</summary>
    bool NeedsRehash(string hash);
    (bool IsValid, string? ErrorMessage) ValidatePassword(string password);
}
