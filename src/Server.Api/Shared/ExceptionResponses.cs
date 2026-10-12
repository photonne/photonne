using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Dtos;

namespace Photonne.Server.Api.Shared;

/// <summary>
/// What the global exception middleware answers. A
/// <see cref="BadHttpRequestException"/> is the client's fault and keeps its own
/// status (a body over Kestrel's <c>MaxRequestBodySize</c> is a 413 with
/// <c>upload_too_large</c>, the code the apps read as Photonne's own limit, not a
/// proxy's); anything else is a 500 ProblemDetails with a trace id.
/// </summary>
internal static class ExceptionResponses
{
    public const string UploadTooLargeCode = "upload_too_large";

    public static async Task WriteAsync(
        HttpContext context,
        Exception exception,
        long? demoMaxRequestBodySize,
        bool includeDetails,
        ILogger logger)
    {
        if (exception is BadHttpRequestException badRequest)
        {
            // Not logged as an error: the request was bad, the server is fine.
            if (badRequest.StatusCode == StatusCodes.Status413PayloadTooLarge)
            {
                await WriteRequestTooLargeAsync(context, demoMaxRequestBodySize);
                return;
            }

            context.Response.StatusCode = badRequest.StatusCode;
            await context.Response.WriteAsJsonAsync(
                new ApiError("La petición no es válida.", "bad_request"));
            return;
        }

        var problem = new ProblemDetails
        {
            Status = StatusCodes.Status500InternalServerError,
            Title = exception is DbUpdateException ? "Error de base de datos" : "Error interno del servidor"
        };
        problem.Extensions["traceId"] = Activity.Current?.Id ?? context.TraceIdentifier;

        if (includeDetails)
        {
            problem.Detail = exception is DbUpdateException dbEx ? BuildDbErrorDetail(dbEx) : exception.Message;
            problem.Extensions["stackTrace"] = exception.ToString();
        }
        else
        {
            // Exception text can carry file paths, SQL and connection details:
            // it goes to the log, and the client gets the trace id to match it.
            logger.LogError(exception, "Unhandled exception (traceId {TraceId})", problem.Extensions["traceId"]);
        }

        context.Response.StatusCode = problem.Status.Value;
        // WriteAsJsonAsync overwrites ContentType, so the problem type goes here.
        await context.Response.WriteAsJsonAsync(problem, options: null, contentType: "application/problem+json");
    }

    /// <summary>
    /// The 413 for a body over the request size cap. Also used when minimal API
    /// form binding has already swallowed Kestrel's exception and left a bare
    /// 413 without a body.
    /// </summary>
    public static Task WriteRequestTooLargeAsync(HttpContext context, long? demoMaxRequestBodySize)
    {
        var message = demoMaxRequestBodySize is { } maxBytes
            ? $"El archivo supera el tamaño máximo permitido en la demo ({maxBytes / (1024 * 1024)} MB)."
            : "El archivo supera el tamaño máximo que admite el servidor.";

        context.Response.StatusCode = StatusCodes.Status413PayloadTooLarge;
        return context.Response.WriteAsJsonAsync(new ApiError(message, UploadTooLargeCode));
    }

    private static string BuildDbErrorDetail(DbUpdateException exception)
    {
        if (exception.InnerException?.Message is { Length: > 0 } innerMessage)
        {
            return $"{exception.Message} | {innerMessage}";
        }

        return exception.Message;
    }
}
