using System.Security.Cryptography;
using System.Text;

namespace Photonne.Server.Api.Shared.Services.Auth;

/// <summary>
/// Time-based one-time passwords (RFC 6238: HMAC-SHA1, 30 s, 6 digits), the
/// codes authenticator apps show. A code is accepted one step either side of
/// now, for clocks a little off, and never twice (<c>lastStep</c>).
/// </summary>
public static class Totp
{
    public const int Digits = 6;
    public const int StepSeconds = 30;
    private const string Base32Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /// <summary>A new random secret, Base32 (160 bits, what the apps expect).</summary>
    public static string NewSecret() => Base32Encode(RandomNumberGenerator.GetBytes(20));

    public static long StepAt(DateTimeOffset time) => time.ToUnixTimeSeconds() / StepSeconds;

    /// <summary>The code of <paramref name="secret"/> for time step <paramref name="step"/>.</summary>
    public static string Code(string secret, long step)
    {
        Span<byte> counter = stackalloc byte[8];
        for (var i = 7; i >= 0; i--)
        {
            counter[i] = (byte)(step & 0xFF);
            step >>= 8;
        }
        var hash = HMACSHA1.HashData(Base32Decode(secret), counter);
        var offset = hash[^1] & 0x0F;
        var binary = ((hash[offset] & 0x7F) << 24) | (hash[offset + 1] << 16) | (hash[offset + 2] << 8) | hash[offset + 3];
        return (binary % 1_000_000).ToString("D6");
    }

    /// <summary>
    /// The time step <paramref name="code"/> belongs to (now, or one either
    /// side), or null when it's wrong or that step was already used.
    /// </summary>
    public static long? Verify(string secret, string? code, DateTimeOffset now, long? lastStep)
    {
        var digits = new string((code ?? string.Empty).Where(char.IsDigit).ToArray());
        if (digits.Length != Digits) return null;
        var current = StepAt(now);
        for (var step = current - 1; step <= current + 1; step++)
        {
            if (lastStep is not null && step <= lastStep) continue;
            if (CryptographicOperations.FixedTimeEquals(Encoding.ASCII.GetBytes(Code(secret, step)), Encoding.ASCII.GetBytes(digits)))
                return step;
        }
        return null;
    }

    /// <summary>The otpauth:// link the authenticator apps read from a QR code.</summary>
    public static string OtpAuthUri(string issuer, string account, string secret) =>
        $"otpauth://totp/{Uri.EscapeDataString(issuer)}:{Uri.EscapeDataString(account)}" +
        $"?secret={secret}&issuer={Uri.EscapeDataString(issuer)}&algorithm=SHA1&digits={Digits}&period={StepSeconds}";

    public static string Base32Encode(ReadOnlySpan<byte> data)
    {
        var output = new StringBuilder((data.Length * 8 + 4) / 5);
        int buffer = 0, bits = 0;
        foreach (var b in data)
        {
            buffer = (buffer << 8) | b;
            bits += 8;
            while (bits >= 5)
            {
                output.Append(Base32Alphabet[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) output.Append(Base32Alphabet[(buffer << (5 - bits)) & 31]);
        return output.ToString();
    }

    public static byte[] Base32Decode(string text)
    {
        var clean = text.Trim().TrimEnd('=').Replace(" ", "").ToUpperInvariant();
        var output = new List<byte>(clean.Length * 5 / 8);
        int buffer = 0, bits = 0;
        foreach (var c in clean)
        {
            var value = Base32Alphabet.IndexOf(c);
            if (value < 0) throw new FormatException($"'{c}' is not Base32");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8)
            {
                output.Add((byte)((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        return output.ToArray();
    }
}

/// <summary>
/// One-use codes for getting in without the phone. Shown once when two-step
/// verification is turned on; only their hashes are kept.
/// </summary>
public static class RecoveryCodes
{
    private const string Alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
    public const int Count = 10;

    public static List<string> Generate() => Enumerable.Range(0, Count).Select(_ =>
    {
        var chars = RandomNumberGenerator.GetItems<char>(Alphabet, 8);
        return $"{new string(chars, 0, 4)}-{new string(chars, 4, 4)}";
    }).ToList();

    public static string Hash(string code) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(Normalize(code))));

    /// <summary>The stored list without <paramref name="code"/>, or null when it isn't one of them.</summary>
    public static string? Consume(string? stored, string? code)
    {
        if (string.IsNullOrEmpty(stored) || string.IsNullOrWhiteSpace(code)) return null;
        var hashes = stored.Split(';', StringSplitOptions.RemoveEmptyEntries).ToList();
        var hash = Hash(code);
        if (!hashes.Remove(hash)) return null;
        return string.Join(';', hashes);
    }

    public static int Left(string? stored) =>
        string.IsNullOrEmpty(stored) ? 0 : stored.Split(';', StringSplitOptions.RemoveEmptyEntries).Length;

    private static string Normalize(string code) => code.Trim().ToLowerInvariant().Replace("-", "").Replace(" ", "");
}
