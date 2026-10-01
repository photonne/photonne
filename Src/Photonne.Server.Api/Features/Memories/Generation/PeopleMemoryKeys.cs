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
}
