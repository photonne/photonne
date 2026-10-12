using System.Text.Json;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging.Abstractions;
using Photonne.Server.Api.Shared;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Shared;

/// <summary>
/// The global exception middleware's answers. TestServer doesn't apply
/// Kestrel's MaxRequestBodySize, so the demo's 413 is checked here on the
/// conversion itself: the apps only take a 413 as Photonne's own when the body
/// carries <c>upload_too_large</c>.
/// </summary>
public sealed class ExceptionResponsesTests
{
    private static DefaultHttpContext NewContext()
    {
        var context = new DefaultHttpContext();
        context.Response.Body = new MemoryStream();
        return context;
    }

    private static JsonElement ReadBody(HttpContext context)
    {
        context.Response.Body.Position = 0;
        return JsonDocument.Parse(context.Response.Body).RootElement;
    }

    [Fact]
    public async Task RequestBodyTooLarge_Is413WithUploadTooLargeAndTheDemoCap()
    {
        var context = NewContext();
        var exception = new BadHttpRequestException("Request body too large.", StatusCodes.Status413PayloadTooLarge);

        await ExceptionResponses.WriteAsync(
            context, exception, DemoModeOptions.MaxRequestBodyBytes, includeDetails: false, NullLogger.Instance);

        Assert.Equal(StatusCodes.Status413PayloadTooLarge, context.Response.StatusCode);
        Assert.StartsWith("application/json", context.Response.ContentType);
        var body = ReadBody(context);
        Assert.Equal("upload_too_large", body.GetProperty("code").GetString());
        Assert.Equal(
            "El archivo supera el tamaño máximo permitido en la demo (25 MB).",
            body.GetProperty("error").GetString());
    }

    [Fact]
    public async Task OtherBadRequest_KeepsItsStatusCode()
    {
        var context = NewContext();
        var exception = new BadHttpRequestException("Unexpected end of request content.", StatusCodes.Status400BadRequest);

        await ExceptionResponses.WriteAsync(context, exception, null, includeDetails: false, NullLogger.Instance);

        Assert.Equal(StatusCodes.Status400BadRequest, context.Response.StatusCode);
        Assert.Equal("bad_request", ReadBody(context).GetProperty("code").GetString());
    }

    [Fact]
    public async Task AnyOtherException_IsStill500ProblemDetails()
    {
        var context = NewContext();

        await ExceptionResponses.WriteAsync(
            context, new InvalidOperationException("boom"), DemoModeOptions.MaxRequestBodyBytes,
            includeDetails: false, NullLogger.Instance);

        Assert.Equal(StatusCodes.Status500InternalServerError, context.Response.StatusCode);
        Assert.Equal("application/problem+json", context.Response.ContentType);
        var body = ReadBody(context);
        Assert.Equal("Error interno del servidor", body.GetProperty("title").GetString());
        Assert.False(body.TryGetProperty("detail", out _));
    }
}
