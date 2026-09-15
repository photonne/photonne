using System.Net;
using System.Text;
using Microsoft.Extensions.Logging.Abstractions;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Ml;

namespace Photonne.Server.Api.Tests.AssetEnrichment;

/// <summary>
/// What a failed ML call tells the admin afterwards.
///
/// The registry used to show "Face service call failed after 4 attempts for
/// asset {guid}" for everything, because the clients wrapped the real cause in
/// a generic exception and the worker stored only the outermost message. And
/// every client carried a comment promising to fail fast on a 4xx while
/// retrying it anyway. These tests pin both halves: the cause survives to the
/// surface, and a hopeless call is not repeated.
/// </summary>
public class MlFailureReportingTests
{
    private sealed record Probe(string Value);

    /// <summary>Answers a canned response and counts how many times it was asked.</summary>
    private sealed class StubHandler : HttpMessageHandler
    {
        private readonly HttpStatusCode _status;
        private readonly string _body;

        public StubHandler(HttpStatusCode status, string body)
        {
            _status = status;
            _body = body;
        }

        public int Calls { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult(new HttpResponseMessage(_status)
            {
                Content = new StringContent(_body, Encoding.UTF8, "application/json")
            });
        }
    }

    private static (HttpClient Client, StubHandler Handler) Stub(HttpStatusCode status, string body)
    {
        var handler = new StubHandler(status, body);
        return (new HttpClient(handler) { BaseAddress = new Uri("http://ml.test") }, handler);
    }

    private static Task<Probe> CallAsync(HttpClient client, MlOptions? options = null) =>
        MlCall.PostAsync<Probe, Probe>(
            client,
            // No backoff: these assert how many attempts happen, not how long
            // they wait, and the real schedule adds 14 s per test for nothing.
            options ?? new MlOptions { MaxRetries = 3, TimeoutSeconds = 5, RetryBaseDelaySeconds = 0 },
            NullLogger.Instance,
            "/v1/faces/detect",
            new Probe("x"),
            "Reconocimiento facial",
            "asset 1",
            CancellationToken.None);

    // ─── The cause reaches the surface ───────────────────────────────────────

    [Fact]
    public async Task StructuredError_KeepsTheServicesOwnWords()
    {
        var (client, _) = Stub(HttpStatusCode.BadRequest, """
            {"error": {"code": "image_unreadable", "transient": false,
                       "message": "no se ha podido leer la imagen",
                       "detail": "cv2.imdecode devolvió None"}}
            """);

        var ex = await Assert.ThrowsAsync<MlServiceException>(() => CallAsync(client));

        Assert.Equal("image_unreadable", ex.Code);
        Assert.False(ex.IsTransient);
        Assert.Equal(400, ex.StatusCode);
        Assert.Contains("no se ha podido leer la imagen", ex.Message);
        // The detail is the half that names the actual mechanism.
        Assert.Contains("cv2.imdecode", ex.Message);
    }

    [Fact]
    public async Task AnInferenceCrash_ArrivesAsTransient_WithTheExceptionText()
    {
        // The case that started all this: the model raised, and what reached the
        // registry was an empty 500.
        var (client, handler) = Stub(HttpStatusCode.InternalServerError, """
            {"error": {"code": "inference_failed", "transient": true,
                       "message": "la inferencia de reconocimiento facial ha fallado",
                       "detail": "RuntimeException: CUBLAS failure 3"}}
            """);

        var ex = await Assert.ThrowsAsync<MlServiceException>(() => CallAsync(client));

        Assert.Equal("inference_failed", ex.Code);
        Assert.True(ex.IsTransient);
        Assert.Contains("CUBLAS failure 3", ex.Message);
        Assert.Equal(4, handler.Calls); // retried, as a transient cause should be
    }

    // ─── Fail fast, for real this time ───────────────────────────────────────

    [Fact]
    public async Task ANonTransientFailure_IsNotRetried()
    {
        var (client, handler) = Stub(HttpStatusCode.BadRequest, """
            {"error": {"code": "image_unreadable", "transient": false, "message": "no"}}
            """);

        await Assert.ThrowsAsync<MlServiceException>(() => CallAsync(client));

        // One attempt. Four is fourteen seconds of a worker spent confirming
        // that the same bytes still don't decode.
        Assert.Equal(1, handler.Calls);
    }

    [Fact]
    public async Task ADisabledCapability_IsNotRetriedEither()
    {
        var (client, handler) = Stub(HttpStatusCode.ServiceUnavailable, """
            {"error": {"code": "capability_disabled", "transient": false,
                       "message": "el reconocimiento facial está desactivado en el servicio de ML"}}
            """);

        var ex = await Assert.ThrowsAsync<MlServiceException>(() => CallAsync(client));

        Assert.Equal(1, handler.Calls);
        Assert.Equal("capability_disabled", ex.Code);
        // A 503 would be retried on status alone — the service's own verdict
        // is what stops it.
        Assert.False(ex.IsTransient);
    }

    [Fact]
    public async Task AnOlderServiceWithoutCodes_FallsBackToTheStatus()
    {
        // The rule the clients always meant to apply: 4xx won't improve.
        var (client, handler) = Stub(HttpStatusCode.BadRequest, """{"detail": "cannot read image"}""");

        var ex = await Assert.ThrowsAsync<MlServiceException>(() => CallAsync(client));

        Assert.Equal(1, handler.Calls);
        Assert.False(ex.IsTransient);
        Assert.Contains("cannot read image", ex.Message);
    }

    [Fact]
    public async Task AnOlderServiceReturning500_IsStillRetried()
    {
        var (client, handler) = Stub(HttpStatusCode.InternalServerError, "Internal Server Error");

        var ex = await Assert.ThrowsAsync<MlServiceException>(() => CallAsync(client));

        Assert.Equal(4, handler.Calls);
        Assert.True(ex.IsTransient);
        Assert.Equal(MlErrorCodes.Unclassified, ex.Code);
    }

    [Fact]
    public async Task GivingUp_KeepsTheLastCause_NotAGenericWrapper()
    {
        var (client, _) = Stub(HttpStatusCode.ServiceUnavailable, """
            {"error": {"code": "model_not_loaded", "transient": true,
                       "message": "el modelo de detección de objetos no está cargado",
                       "detail": "FileNotFoundError: yolov8n.onnx"}}
            """);

        var ex = await Assert.ThrowsAsync<MlServiceException>(() => CallAsync(client));

        Assert.Equal("model_not_loaded", ex.Code);
        Assert.Contains("yolov8n.onnx", ex.Message);
        Assert.Contains("4 intento(s)", ex.Message);
    }

    // ─── Classification ──────────────────────────────────────────────────────

    [Theory]
    [InlineData("capability_disabled", false, EnrichmentFailureKind.NeedsAction)]
    [InlineData("model_not_loaded", true, EnrichmentFailureKind.NeedsAction)]
    [InlineData("image_unreadable", false, EnrichmentFailureKind.Permanent)]
    [InlineData("bad_request", false, EnrichmentFailureKind.Permanent)]
    [InlineData("inference_failed", true, EnrichmentFailureKind.Transient)]
    [InlineData("timeout", true, EnrichmentFailureKind.Transient)]
    [InlineData("unclassified", false, EnrichmentFailureKind.Permanent)]
    public void MlFailures_AreClassifiedByTheirCode(string code, bool transient, EnrichmentFailureKind expected)
    {
        var ex = new MlServiceException("Caras", "boom", code, transient, 500, 1);

        Assert.Equal(expected, EnrichmentFailureClassifier.Classify(ex));
    }

    [Fact]
    public void AMissingFile_IsTheAssetsProblem()
    {
        Assert.Equal(
            EnrichmentFailureKind.Permanent,
            EnrichmentFailureClassifier.Classify(new FileNotFoundException("no such file")));
    }

    [Fact]
    public void AnUnrecognisedFailure_StaysUnknownRatherThanGuessing()
    {
        Assert.Equal(
            EnrichmentFailureKind.Unknown,
            EnrichmentFailureClassifier.Classify(new InvalidOperationException("¿?")));
    }

    // ─── The message the registry stores ─────────────────────────────────────

    [Fact]
    public void Describe_WalksTheWholeChain()
    {
        // Exactly the shape that hid the cause: the useful part two levels down.
        var ex = new InvalidOperationException(
            "Face service call failed after 4 attempts",
            new HttpRequestException(
                "Face service returned 500",
                new TimeoutException("The operation timed out")));

        var text = EnrichmentFailureClassifier.Describe(ex);

        Assert.Contains("Face service call failed after 4 attempts", text);
        Assert.Contains("Face service returned 500", text);
        Assert.Contains("The operation timed out", text);
    }

    [Fact]
    public void Describe_FitsTheColumn()
    {
        var ex = new Exception(new string('x', 5000));

        Assert.True(EnrichmentFailureClassifier.Describe(ex).Length <= EnrichmentFailureClassifier.MaxMessageLength);
    }

    [Fact]
    public void Describe_DoesNotRepeatALinkThatAddsNothing()
    {
        // .NET wraps plenty of exceptions in ones whose message quotes the inner
        // one verbatim; printing both just pushes the useful part off screen.
        var ex = new Exception("outer: inner detail", new Exception("inner detail"));

        Assert.Equal("outer: inner detail", EnrichmentFailureClassifier.Describe(ex));
    }
}
