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

    public AuthService(IConfiguration configuration)
    {
        _configuration = configuration;
    }

    public async Task<string> GenerateTokenAsync(User user)
    {
        var claims = new List<Claim>
        {
            new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()),
            new Claim(ClaimTypes.Name, user.Username),
            new Claim(ClaimTypes.Email, user.Email),
            new Claim(ClaimTypes.Role, user.Role)
        };

        var key = new SymmetricSecurityKey(
            Encoding.UTF8.GetBytes(_configuration["Jwt:Key"] ?? throw new InvalidOperationException("JWT Key not configured")));
        
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);
        
        var token = new JwtSecurityToken(
            issuer: _configuration["Jwt:Issuer"],
            audience: _configuration["Jwt:Audience"],
            claims: claims,
            expires: DateTime.UtcNow.AddHours(24),
            signingCredentials: creds
        );

        return await Task.FromResult(new JwtSecurityTokenHandler().WriteToken(token));
    }

    public string HashPassword(string password)
    {
        using var sha256 = SHA256.Create();
        var hashedBytes = sha256.ComputeHash(Encoding.UTF8.GetBytes(password));
        return Convert.ToBase64String(hashedBytes);
    }

    public bool VerifyPassword(string password, string hash)
    {
        var passwordHash = HashPassword(password);
        return passwordHash == hash;
    }

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
