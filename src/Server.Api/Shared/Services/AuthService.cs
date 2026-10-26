using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using Microsoft.IdentityModel.Tokens;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

public class AuthService : IAuthService
{
    private readonly IConfiguration _configuration;
    private readonly SettingsService _settingsService;

    // Default: 24 h (1440 min) — used when the admin setting is missing or invalid.
    private const int DefaultSessionMinutes = 1440;
    // Clamp to a sane range [5 min .. 30 days] regardless of what is stored.
    private const int MinSessionMinutes = 5;
    private const int MaxSessionMinutes = 60 * 24 * 30;

    public AuthService(IConfiguration configuration, SettingsService settingsService)
    {
        _configuration = configuration;
        _settingsService = settingsService;
        _pbkdf2Iterations = int.TryParse(configuration["Auth:PasswordHashIterations"], out var iterations) && iterations > 0
            ? iterations
            : DefaultPbkdf2Iterations;
    }

    public async Task<string> GenerateTokenAsync(User user)
    {
        var claims = new List<Claim>
        {
            new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()),
            new Claim(ClaimTypes.Name, user.Username),
            // Custom "username" claim — read by endpoints to build
            // /assets/users/{username} paths without an extra DB lookup.
            new Claim("username", user.Username),
            new Claim(ClaimTypes.Email, user.Email),
            new Claim(ClaimTypes.Role, user.Role)
        };

        var key = new SymmetricSecurityKey(
            Encoding.UTF8.GetBytes(_configuration["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured")));

        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var sessionMinutes = await GetSessionTimeoutMinutesAsync();

        var token = new JwtSecurityToken(
            issuer: _configuration["Jwt:Issuer"],
            audience: _configuration["Jwt:Audience"],
            claims: claims,
            expires: DateTime.UtcNow.AddMinutes(sessionMinutes),
            signingCredentials: creds
        );

        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    private async Task<int> GetSessionTimeoutMinutesAsync()
    {
        var raw = await _settingsService.GetSettingAsync(
            "ServerSettings.SessionTimeoutMinutes", Guid.Empty, DefaultSessionMinutes.ToString());

        if (!int.TryParse(raw, out var minutes) || minutes <= 0)
            return DefaultSessionMinutes;

        return Math.Clamp(minutes, MinSessionMinutes, MaxSessionMinutes);
    }

    // PBKDF2-HMAC-SHA512 with the OWASP iteration count, stored as
    // "pbkdf2-sha512$<iterations>$<salt>$<hash>" so the count can grow later.
    // Auth:PasswordHashIterations only exists so the test suite, which logs in
    // hundreds of times, doesn't spend minutes hashing.
    private const string Pbkdf2Prefix = "pbkdf2-sha512$";
    private const int DefaultPbkdf2Iterations = 210_000;
    private readonly int _pbkdf2Iterations;
    private const int SaltBytes = 16;
    private const int HashBytes = 32;

    public string HashPassword(string password)
    {
        var salt = RandomNumberGenerator.GetBytes(SaltBytes);
        var hash = Pbkdf2(password, salt, _pbkdf2Iterations);
        return $"{Pbkdf2Prefix}{_pbkdf2Iterations}${Convert.ToBase64String(salt)}${Convert.ToBase64String(hash)}";
    }

    public bool VerifyPassword(string password, string hash)
    {
        if (string.IsNullOrEmpty(hash)) return false;

        if (!hash.StartsWith(Pbkdf2Prefix, StringComparison.Ordinal))
        {
            // Accounts from before PBKDF2: unsalted SHA-256. They move to the
            // new format on their next login (NeedsRehash).
            var legacy = SHA256.HashData(Encoding.UTF8.GetBytes(password));
            try
            {
                return CryptographicOperations.FixedTimeEquals(legacy, Convert.FromBase64String(hash));
            }
            catch (FormatException)
            {
                return false;
            }
        }

        var parts = hash[Pbkdf2Prefix.Length..].Split('$');
        if (parts.Length != 3 || !int.TryParse(parts[0], out var iterations) || iterations <= 0)
            return false;
        try
        {
            var salt = Convert.FromBase64String(parts[1]);
            var expected = Convert.FromBase64String(parts[2]);
            return CryptographicOperations.FixedTimeEquals(Pbkdf2(password, salt, iterations, expected.Length), expected);
        }
        catch (FormatException)
        {
            return false;
        }
    }

    public bool NeedsRehash(string hash) =>
        !hash.StartsWith($"{Pbkdf2Prefix}{_pbkdf2Iterations}$", StringComparison.Ordinal);

    private static byte[] Pbkdf2(string password, byte[] salt, int iterations, int length = HashBytes) =>
        Rfc2898DeriveBytes.Pbkdf2(Encoding.UTF8.GetBytes(password), salt, iterations, HashAlgorithmName.SHA512, length);

    public (bool IsValid, string? ErrorMessage) ValidatePassword(string password)
    {
        if (string.IsNullOrWhiteSpace(password))
        {
            return (false, "La contraseña no puede estar vacía");
        }

        if (password.Length < 8)
        {
            return (false, "La contraseña debe tener al menos 8 caracteres");
        }

        if (password.Length > 128)
        {
            return (false, "La contraseña no puede tener más de 128 caracteres");
        }

        // Verificar que tenga al menos una letra mayúscula
        if (!password.Any(char.IsUpper))
        {
            return (false, "La contraseña debe contener al menos una letra mayúscula");
        }

        // Verificar que tenga al menos una letra minúscula
        if (!password.Any(char.IsLower))
        {
            return (false, "La contraseña debe contener al menos una letra minúscula");
        }

        // Verificar que tenga al menos un número
        if (!password.Any(char.IsDigit))
        {
            return (false, "La contraseña debe contener al menos un número");
        }

        // Verificar que tenga al menos un carácter especial
        if (!password.Any(ch => !char.IsLetterOrDigit(ch)))
        {
            return (false, "La contraseña debe contener al menos un carácter especial");
        }

        return (true, null);
    }
}
