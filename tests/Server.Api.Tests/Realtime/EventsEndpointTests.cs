using System.Net;
using System.Net.Http.Json;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.Realtime;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Realtime;

/// <summary>
/// The event stream says "library" when the caller's photos change, whoever
/// changed them, and nothing to anybody else.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class EventsEndpointTests : IntegrationTestBase
{
    public EventsEndpointTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record DeleteRequest(List<Guid> AssetIds);

    private Task<Guid> CreateAssetAsync(TestUser owner) => WithDbContextAsync(async db =>
    {
        var asset = new Asset
        {
            FileName = "photo.jpg",
            FullPath = $"/assets/users/{owner.Username}/{Guid.NewGuid():N}.jpg",
            FileSize = 1024,
            Checksum = Guid.NewGuid().ToString("N"),
            Type = AssetType.Image,
            Extension = "jpg",
            FileCreatedAt = DateTime.UtcNow,
            FileModifiedAt = DateTime.UtcNow,
            OwnerId = owner.Id
        };
        db.Assets.Add(asset);
        await db.SaveChangesAsync();
        return asset.Id;
    });

    [Fact]
    public async Task Requires_a_session()
    {
        var response = await CreateClient().GetAsync("/api/events");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Trashing_a_photo_tells_its_owner_to_reload_the_library()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateAssetAsync(alice);

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(20));
        using var response = await aliceClient.GetAsync("/api/events", HttpCompletionOption.ResponseHeadersRead, cts.Token);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("text/event-stream", response.Content.Headers.ContentType?.MediaType);
        using var stream = new StreamReader(await response.Content.ReadAsStreamAsync(cts.Token));
        Assert.Equal(": connected", await stream.ReadLineAsync(cts.Token));

        await aliceClient.PostAsJsonAsync("/api/assets/delete", new DeleteRequest([assetId]), cts.Token);

        string? line;
        do line = await stream.ReadLineAsync(cts.Token);
        while (line is not null && !line.StartsWith("event: "));
        Assert.Equal("event: library", line);
    }

    [Fact]
    public async Task Changes_reach_only_their_owner_and_only_what_shows()
    {
        var bus = Factory.Services.GetRequiredService<UserEventBus>();
        var alice = await CreateUserAsync();
        var bob = await CreateUserAsync();
        using var aliceEvents = bus.Subscribe(alice.Id);
        using var bobEvents = bus.Subscribe(bob.Id);
        var assetId = await CreateAssetAsync(alice);
        Assert.True(aliceEvents.Reader.TryRead(out var added));
        Assert.Equal(UserEventTypes.Library, added);

        // Background bookkeeping (a scan date) changes nothing on screen.
        await WithDbContextAsync(async db =>
        {
            var asset = await db.Assets.FindAsync(assetId);
            asset!.ScannedAt = DateTime.UtcNow;
            await db.SaveChangesAsync();
            return 0;
        });

        Assert.False(aliceEvents.Reader.TryRead(out _));
        Assert.False(bobEvents.Reader.TryRead(out _));
    }
}
