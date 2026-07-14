using System.Net;
using System.Text.Json;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Maintenance;

/// <summary>
/// Regression coverage for the streaming maintenance endpoint
/// (GET /api/admin/maintenance/{kind}/stream). It must emit newline-delimited
/// JSON (one object per line) — NOT a single JSON array — so the Native
/// line-based client can read live progress incrementally, and each task must
/// finish without a fatal "Error:" event.
/// </summary>
public sealed class MaintenanceStreamTests : IntegrationTestBase
{
    public MaintenanceStreamTests(PhotonneApiFactory factory) : base(factory) { }

    private async Task<HttpClient> LoginAsAdminAsync()
    {
        var admin = new TestUser(
            Guid.Empty,
            PhotonneApiFactory.AdminUsername,
            PhotonneApiFactory.AdminPassword,
            "Admin");
        return await LoginAsClientAsync(admin);
    }

    private static List<JsonElement> ParseNdjson(string body) =>
        body.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Select(l => JsonDocument.Parse(l).RootElement.Clone())
            .ToList();

    [Fact]
    public async Task Stream_EmitsNdjsonLines_NotAJsonArray()
    {
        var adminClient = await LoginAsAdminAsync();

        var response = await adminClient.GetAsync(
            "/api/admin/maintenance/missing-files/stream",
            HttpCompletionOption.ResponseHeadersRead);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadAsStringAsync();

        // A JSON array response would start with '[' and fail to parse per line.
        Assert.DoesNotContain('[', body);
        var events = ParseNdjson(body);
        Assert.NotEmpty(events);
        foreach (var e in events)
            Assert.True(e.TryGetProperty("message", out _));

        // First event carries the task id immediately (fire-and-forget trigger),
        // last event is the completion.
        Assert.True(events[0].TryGetProperty("taskId", out var tid) && tid.GetString() is { Length: > 0 });
        Assert.True(events[^1].GetProperty("isCompleted").GetBoolean());
    }

    [Theory]
    [InlineData("missing-files")]
    [InlineData("recalculate-sizes")]
    [InlineData("orphan-thumbnails")]
    [InlineData("empty-trash")]
    [InlineData("purge-missing")]
    public async Task Stream_Task_CompletesWithoutError(string kind)
    {
        var adminClient = await LoginAsAdminAsync();

        var response = await adminClient.GetAsync(
            $"/api/admin/maintenance/{kind}/stream",
            HttpCompletionOption.ResponseHeadersRead);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadAsStringAsync();
        var last = ParseNdjson(body).Last();

        Assert.True(last.GetProperty("isCompleted").GetBoolean());
        Assert.False(last.GetProperty("message").GetString()!.StartsWith("Error:"),
            $"{kind}: {last.GetProperty("message").GetString()}");
    }

    [Fact]
    public async Task Stream_UnknownKind_FinishesWithError()
    {
        var adminClient = await LoginAsAdminAsync();

        var response = await adminClient.GetAsync(
            "/api/admin/maintenance/not-a-real-task/stream",
            HttpCompletionOption.ResponseHeadersRead);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadAsStringAsync();
        var last = ParseNdjson(body).Last();
        Assert.True(last.GetProperty("isCompleted").GetBoolean());
    }
}
