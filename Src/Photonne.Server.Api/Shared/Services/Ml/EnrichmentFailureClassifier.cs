using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.Ml;

/// <summary>
/// Turns the exception that ended an enrichment attempt into the two things the
/// admin screen needs: a sentence that names the actual cause, and a kind that
/// says whether retrying is worth anything.
///
/// Both used to be lost at the same spot. <see cref="EnrichmentWorker"/> stored
/// <c>ex.Message</c> and dropped the inner exceptions, and the ML clients put
/// everything informative in exactly those inner exceptions, so the registry
/// filled up with "… call failed after 4 attempts for asset {guid}".
/// </summary>
public static class EnrichmentFailureClassifier
{
    /// <summary>Longest message we keep — matches the column.</summary>
    public const int MaxMessageLength = 2000;

    public static EnrichmentFailureKind Classify(Exception exception) => exception switch
    {
        MlServiceException ml => FromCode(ml),

        // The generator's wrapper carries the message; the cause underneath
        // is what decides whether retrying can help.
        ThumbnailGenerationException { InnerException: { } cause } => Classify(cause),

        // The file the asset points at isn't on disk. Usually the photo really
        // is gone (that's what the missing-files maintenance is for); when it's
        // a volume that didn't mount, the path in the message makes that
        // obvious at a glance across every row.
        FileNotFoundException => EnrichmentFailureKind.Permanent,
        DirectoryNotFoundException => EnrichmentFailureKind.Permanent,

        // The file is there and we can't open it: permissions on the volume,
        // which no amount of retrying will negotiate.
        UnauthorizedAccessException => EnrichmentFailureKind.NeedsAction,

        // Postgres down, connection pool exhausted, a transaction deadlock.
        Npgsql.NpgsqlException => EnrichmentFailureKind.Transient,
        TimeoutException => EnrichmentFailureKind.Transient,
        HttpRequestException => EnrichmentFailureKind.Transient,

        _ => EnrichmentFailureKind.Unknown,
    };

    private static EnrichmentFailureKind FromCode(MlServiceException ml) => ml.Code switch
    {
        // Somebody has to turn it back on or fix the model; the queue draining
        // into failures meanwhile is noise, not information.
        "capability_disabled" => EnrichmentFailureKind.NeedsAction,
        "model_not_loaded" => EnrichmentFailureKind.NeedsAction,

        // The bytes are the problem and they aren't going to change.
        "image_unreadable" => EnrichmentFailureKind.Permanent,
        "bad_request" => EnrichmentFailureKind.Permanent,

        // Everything else follows what the service said about itself.
        _ => ml.IsTransient ? EnrichmentFailureKind.Transient : EnrichmentFailureKind.Permanent,
    };

    public static string? CodeOf(Exception exception) =>
        exception is MlServiceException ml ? ml.Code : null;

    /// <summary>
    /// The whole cause chain on one line. The outermost message alone is
    /// routinely the least informative part — "call failed after 4 attempts" —
    /// while the status code, the response body or the socket error sit two
    /// levels down.
    /// </summary>
    public static string Describe(Exception exception)
    {
        var parts = new List<string>();
        var seen = 0;
        for (Exception? e = exception; e is not null && seen < 5; e = e.InnerException, seen++)
        {
            var text = e.Message?.Trim();
            if (string.IsNullOrEmpty(text)) continue;
            // Skip a link that only repeats what we already said.
            if (parts.Count > 0 && parts[^1].Contains(text, StringComparison.Ordinal)) continue;
            parts.Add(text);
        }

        if (parts.Count == 0) parts.Add(exception.GetType().Name);

        var joined = string.Join(" ← ", parts);
        return joined.Length <= MaxMessageLength ? joined : joined[..MaxMessageLength];
    }
}
