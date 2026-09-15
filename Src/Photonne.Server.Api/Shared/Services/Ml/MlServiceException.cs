namespace Photonne.Server.Api.Shared.Services.Ml;

/// <summary>
/// A call to the ML service that failed, with enough detail to decide what to
/// do about it.
///
/// What came before was <c>InvalidOperationException("Face service call failed
/// after 4 attempts for asset {id}", lastException)</c>: the status code and
/// the response body were captured and then buried in the inner exception,
/// which nothing read. <see cref="EnrichmentWorker"/> stores
/// <c>ex.Message</c>, so the admin's failures registry showed the filler
/// sentence and nothing else — a timeout, a 500 from a crashed inference and a
/// corrupt JPEG all looked identical, and "retry" was a coin flip.
///
/// <see cref="Message"/> is written to be read by a person in that registry, and
/// <see cref="IsTransient"/> is what the retry policy and the UI badge use.
/// </summary>
public sealed class MlServiceException : Exception
{
    public MlServiceException(
        string capability,
        string message,
        string code,
        bool isTransient,
        int? statusCode,
        int attempts,
        Exception? innerException = null)
        : base(message, innerException)
    {
        Capability = capability;
        Code = code;
        IsTransient = isTransient;
        StatusCode = statusCode;
        Attempts = attempts;
    }

    /// <summary>Which ML capability was being asked (used in the message).</summary>
    public string Capability { get; }

    /// <summary>Stable token from the service (<c>image_unreadable</c>,
    /// <c>model_not_loaded</c>, <c>inference_failed</c>…), or one of the
    /// client-side codes in <see cref="MlErrorCodes"/> when the call never got
    /// an answer.</summary>
    public string Code { get; }

    /// <summary>Whether trying again later, with nothing changed, could
    /// plausibly work. Drives both the retry loop and what the admin sees.</summary>
    public bool IsTransient { get; }

    /// <summary>HTTP status, when there was a response at all.</summary>
    public int? StatusCode { get; }

    /// <summary>How many attempts were spent before giving up. One, for a
    /// failure the client knew was pointless to repeat.</summary>
    public int Attempts { get; }
}

/// <summary>The codes the client itself produces, for failures that never
/// reached the service or came back unintelligible. The service's own codes
/// live in <c>Src/Photonne.MlService/app/errors.py</c> and travel as-is.</summary>
public static class MlErrorCodes
{
    /// <summary>No answer: connection refused, DNS, socket reset.</summary>
    public const string Unreachable = "unreachable";

    /// <summary>The service accepted the request and never finished within
    /// <c>Ml:TimeoutSeconds</c>.</summary>
    public const string Timeout = "timeout";

    /// <summary>HTTP 200 with a body we couldn't turn into a response.</summary>
    public const string InvalidResponse = "invalid_response";

    /// <summary>An error response whose body carried no code — an older ML
    /// service, or a proxy's error page.</summary>
    public const string Unclassified = "unclassified";
}
