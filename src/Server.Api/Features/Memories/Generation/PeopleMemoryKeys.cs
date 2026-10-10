namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Dedupe keys of the people memories, in one place: the generators write them
/// and the feed's <c>personId</c> filter reads them back to find a person's
/// memories without a join table. Guids use the default "D" format.
/// </summary>
internal static class PeopleMemoryKeys
{
    public const string TogetherPrefix = "together:";

    /// <summary>"Martina a lo largo de los años".</summary>
    public static string Years(Guid personId) => $"person:{personId}:years";

    /// <summary>"Martina y Joan"; the pair arrives ordered, so the key is stable.</summary>
    public static string Together(Guid a, Guid b) => $"{TogetherPrefix}{a}:{b}";

    /// <summary>The other person of a pair key, seen from [personId]; null if the
    /// key isn't a pair or [personId] isn't in it.</summary>
    public static Guid? Companion(string dedupeKey, Guid personId)
    {
        if (!dedupeKey.StartsWith(TogetherPrefix, StringComparison.Ordinal)) return null;
        var parts = dedupeKey[TogetherPrefix.Length..].Split(':');
        if (parts.Length != 2 || !Guid.TryParse(parts[0], out var a) || !Guid.TryParse(parts[1], out var b))
            return null;
        if (a == personId) return b;
        if (b == personId) return a;
        return null;
    }
}
