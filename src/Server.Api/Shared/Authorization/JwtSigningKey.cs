using System.Security.Cryptography;
using System.Text;

namespace Photonne.Server.Api.Shared.Authorization;

/// <summary>
/// Resolves the key the access tokens are signed with.
///
/// <c>Jwt:Key</c> wins when it is a real secret. When it is missing, shorter
/// than the 32 bytes HMAC-SHA256 needs, or one of the placeholders that have
/// shipped in this repo (docker-compose, .env.example, appsettings), the key is
/// read from <c>Jwt:KeyFile</c> (default <c>/data/config/jwt.key</c>) or
/// generated there on first boot. A published placeholder would let anyone
/// mint an admin token.
///
/// Changing the key only invalidates access tokens: refresh tokens are opaque
/// rows in the database, so the clients just refresh.
/// </summary>
public static class JwtSigningKey
{
    public const string DefaultKeyFile = "/data/config/jwt.key";
    public const int MinKeyBytes = 32;

    private static readonly string[] KnownPlaceholders =
    [
        "TOKEN_SUPER_SECRET",
        "cambia_esto_por_clave_jwt_muy_larga_y_aleatoria",
        "dev-secret-key-change-in-production-min-32-chars",
        "clave-secreta-muy-larga-de-al-menos-32-caracteres",
    ];

    /// <summary>Why the configured key was not used, or null when it was.</summary>
    public static string? RejectionReason(string? configured)
    {
        if (string.IsNullOrWhiteSpace(configured)) return "no está configurada";
        if (KnownPlaceholders.Contains(configured.Trim(), StringComparer.Ordinal))
            return "es un valor de ejemplo público";
        if (Encoding.UTF8.GetByteCount(configured) < MinKeyBytes)
            return $"tiene menos de {MinKeyBytes} bytes";
        return null;
    }

    public static string Resolve(IConfiguration configuration, Action<string> warn)
    {
        var configured = configuration["Jwt:Key"];
        var reason = RejectionReason(configured);
        if (reason is null) return configured!;

        var keyFile = configuration["Jwt:KeyFile"] is { Length: > 0 } path ? path : DefaultKeyFile;
        try
        {
            if (File.Exists(keyFile))
            {
                var stored = File.ReadAllText(keyFile).Trim();
                if (RejectionReason(stored) is null)
                {
                    warn($"JWT_KEY {reason}: se usa la clave generada en {keyFile}.");
                    return stored;
                }
            }

            var generated = Generate();
            Directory.CreateDirectory(Path.GetDirectoryName(keyFile)!);
            File.WriteAllText(keyFile, generated);
            if (!OperatingSystem.IsWindows())
                File.SetUnixFileMode(keyFile, UnixFileMode.UserRead | UnixFileMode.UserWrite);
            warn($"JWT_KEY {reason}: se ha generado una clave aleatoria en {keyFile}.");
            return generated;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Never fall back to the placeholder: an in-memory key only costs a
            // token refresh after each restart.
            warn($"JWT_KEY {reason} y no se pudo guardar una clave en {keyFile} ({ex.Message}): " +
                 "se usa una clave temporal que cambia en cada arranque.");
            return Generate();
        }
    }

    private static string Generate() => Convert.ToBase64String(RandomNumberGenerator.GetBytes(48));
}
