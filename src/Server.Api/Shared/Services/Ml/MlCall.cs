using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace Photonne.Server.Api.Shared.Services.Ml;

/// <summary>
/// One POST-to-the-ML-service-with-retries, shared by the five capability
/// clients.
///
/// It used to be copy-pasted per client, which is where the retry rule rotted:
/// every copy carried a comment saying a 4xx is not transient and must fail
/// fast, and every copy then threw an <c>HttpRequestException</c> that its own
/// <c>catch</c> clause picked up and retried. A corrupt JPEG therefore burned
/// four attempts and fourteen seconds of a worker before failing, and reached
/// the admin registry indistinguishable from the ML container being down.
///
/// Here the decision is made once, off the service's own error code.
/// </summary>
internal static class MlCall
{
    public static async Task<TResponse> PostAsync<TRequest, TResponse>(
        HttpClient http,
        MlOptions options,
        ILogger logger,
        string route,
        TRequest request,
        string capability,
        string target,
        CancellationToken ct)
        where TResponse : class
    {
        var attempts = Math.Max(1, options.MaxRetries + 1);
        MlServiceException? last = null;

        for (var attempt = 1; attempt <= attempts; attempt++)
        {
            MlServiceException failure;
            try
            {
                using var response = await http.PostAsJsonAsync(route, request, ct);

                if (response.IsSuccessStatusCode)
                {
                    var dto = await response.Content.ReadFromJsonAsync<TResponse>(cancellationToken: ct);
                    if (dto is not null) return dto;

                    failure = new MlServiceException(
                        capability,
                        $"{capability}: el servicio de ML respondió 200 con un cuerpo vacío ({target})",
                        MlErrorCodes.InvalidResponse,
                        isTransient: true,
                        statusCode: (int)response.StatusCode,
                        attempts: attempt);
                }
                else
                {
                    failure = await FromErrorResponseAsync(response, capability, target, attempt, ct);
                }
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                // The server is shutting down or the task was cancelled. Not a
                // failure of the asset — let the worker leave the row alone.
                throw;
            }
            catch (TaskCanceledException ex)
            {
                // Not our cancellation token, so it's HttpClient.Timeout.
                failure = new MlServiceException(
                    capability,
                    $"{capability}: el servicio de ML no respondió en {options.TimeoutSeconds} s ({target})",
                    MlErrorCodes.Timeout,
                    isTransient: true,
                    statusCode: null,
                    attempts: attempt,
                    innerException: ex);
            }
            catch (HttpRequestException ex)
            {
                failure = new MlServiceException(
                    capability,
                    $"{capability}: no se ha podido contactar con el servicio de ML en {options.ServiceUrl} ({ex.Message})",
                    MlErrorCodes.Unreachable,
                    isTransient: true,
                    statusCode: ex.StatusCode is { } s ? (int)s : null,
                    attempts: attempt,
                    innerException: ex);
            }
            catch (JsonException ex)
            {
                failure = new MlServiceException(
                    capability,
                    $"{capability}: el servicio de ML devolvió una respuesta ilegible ({target})",
                    MlErrorCodes.InvalidResponse,
                    isTransient: true,
                    statusCode: null,
                    attempts: attempt,
                    innerException: ex);
            }

            // The fail-fast the old comment promised: repeating a call the
            // service already told us is hopeless only wastes a worker.
            if (!failure.IsTransient) throw failure;

            last = failure;
            if (attempt >= attempts) break;

            var delay = TimeSpan.FromSeconds(options.RetryBaseDelaySeconds * Math.Pow(2, attempt - 1));
            if (delay <= TimeSpan.Zero) continue;
            logger.LogWarning(
                "{Capability} failed for {Target} (attempt {Attempt}/{Total}, code {Code}); retrying in {Delay}s: {Message}",
                capability, target, attempt, attempts, failure.Code, delay.TotalSeconds, failure.Message);
            await Task.Delay(delay, ct);
        }

        // Keep the last failure's own wording and code — wrapping it in a
        // generic "failed after N attempts" is what hid the cause before.
        throw new MlServiceException(
            capability,
            $"{last!.Message} — {attempts} intento(s)",
            last.Code,
            last.IsTransient,
            last.StatusCode,
            attempts,
            last.InnerException);
    }

    /// <summary>
    /// Reads the structured body the ML service sends
    /// (<c>{"error": {"code", "transient", "message", "detail"}}</c>) and falls
    /// back to the status code when it isn't there — an older service, or an
    /// error page from something in between.
    /// </summary>
    private static async Task<MlServiceException> FromErrorResponseAsync(
        HttpResponseMessage response,
        string capability,
        string target,
        int attempt,
        CancellationToken ct)
    {
        var status = (int)response.StatusCode;
        var raw = await SafeReadAsync(response, ct);

        string? code = null;
        bool? transient = null;
        string? message = null;
        string? detail = null;

        if (!string.IsNullOrWhiteSpace(raw))
        {
            try
            {
                using var doc = JsonDocument.Parse(raw);
                if (doc.RootElement.ValueKind == JsonValueKind.Object
                    && doc.RootElement.TryGetProperty("error", out var err)
                    && err.ValueKind == JsonValueKind.Object)
                {
                    code = ReadString(err, "code");
                    message = ReadString(err, "message");
                    detail = ReadString(err, "detail");
                    if (err.TryGetProperty("transient", out var t)
                        && (t.ValueKind == JsonValueKind.True || t.ValueKind == JsonValueKind.False))
                    {
                        transient = t.GetBoolean();
                    }
                }
                else if (doc.RootElement.ValueKind == JsonValueKind.Object)
                {
                    // Older service: {"detail": "..."} (or a validation array).
                    message = ReadString(doc.RootElement, "detail");
                }
            }
            catch (JsonException)
            {
                // Not JSON — keep the raw text, it's still better than nothing.
            }
        }

        // Without a code from the service, fall back to the same rule the
        // clients always meant to apply: 4xx is the caller's (or the asset's)
        // fault and won't improve; 5xx, 408 and 429 might.
        transient ??= status >= 500 || status is 408 or 429;

        var text = message ?? Truncate(raw, 500) ?? $"HTTP {status}";
        var full = detail is { Length: > 0 } ? $"{text} — {Truncate(detail, 500)}" : text;

        return new MlServiceException(
            capability,
            $"{capability}: {full} (HTTP {status}, {target})",
            code ?? MlErrorCodes.Unclassified,
            transient.Value,
            status,
            attempt);
    }

    private static string? ReadString(JsonElement obj, string name) =>
        obj.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String
            ? v.GetString()
            : null;

    private static async Task<string?> SafeReadAsync(HttpResponseMessage response, CancellationToken ct)
    {
        try { return await response.Content.ReadAsStringAsync(ct); }
        catch { return null; }
    }

    private static string? Truncate(string? text, int max) =>
        text is null ? null : text.Length <= max ? text : text[..max] + "…";
}
