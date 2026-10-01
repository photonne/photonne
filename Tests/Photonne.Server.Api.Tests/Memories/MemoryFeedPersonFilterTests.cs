using System.Net.Http.Json;
using Photonne.Server.Api.Features.Memories.Generation;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Memories;

/// <summary>
/// GET /api/memories?personId= returns only the people memories that person is
/// in — the "Recuerdos con…" row of their page — and still only the caller's.
/// </summary>
[Collection(IntegrationCollection.Name)]
public class MemoryFeedPersonFilterTests : IntegrationTestBase
{
    public MemoryFeedPersonFilterTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record FeedItem(Guid Id, string Kind, string Title);

    private Task AddMemoryAsync(Guid ownerId, MemoryKind kind, string dedupeKey, string title) =>
        WithDbContextAsync(async db =>
        {
            db.Memories.Add(new Memory
            {
                OwnerId = ownerId,
                Kind = kind,
                Title = title,
                ThemeKey = kind is MemoryKind.PersonThroughYears or MemoryKind.PeopleTogether ? "people" : "onthisday",
                GroupTitle = "Personas",
                DedupeKey = dedupeKey,
                WindowStart = new DateTime(2020, 1, 1),
                WindowEnd = new DateTime(2024, 1, 1),
                Score = 0.5,
            });
            await db.SaveChangesAsync();
        });

    [Fact]
    public async Task PersonFilter_ReturnsTheirYearsAndPairs_Only()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var martina = Guid.NewGuid();
        var joan = Guid.NewGuid();
        var pau = Guid.NewGuid();
        var (first, second) = martina.CompareTo(joan) < 0 ? (martina, joan) : (joan, martina);

        await AddMemoryAsync(alice.Id, MemoryKind.PersonThroughYears, PeopleMemoryKeys.Years(martina), "Martina a lo largo de los años");
        await AddMemoryAsync(alice.Id, MemoryKind.PeopleTogether, PeopleMemoryKeys.Together(first, second), "Martina y Joan");
        await AddMemoryAsync(alice.Id, MemoryKind.PersonThroughYears, PeopleMemoryKeys.Years(pau), "Pau a lo largo de los años");
        await AddMemoryAsync(alice.Id, MemoryKind.OnThisDay, "onthisday:2020-10-01", "Hace 4 años");
        // Same person id, another owner: must never leak in.
        await AddMemoryAsync(bob.Id, MemoryKind.PersonThroughYears, PeopleMemoryKeys.Years(martina), "Ajena");

        var forMartina = await client.GetFromJsonAsync<List<FeedItem>>($"/api/memories?personId={martina}");
        Assert.Equal(
            new[] { "Martina a lo largo de los años", "Martina y Joan" },
            forMartina!.Select(m => m.Title).OrderBy(t => t).ToArray());

        var forJoan = await client.GetFromJsonAsync<List<FeedItem>>($"/api/memories?personId={joan}");
        Assert.Equal("Martina y Joan", Assert.Single(forJoan!).Title);

        var unfiltered = await client.GetFromJsonAsync<List<FeedItem>>("/api/memories");
        Assert.Equal(4, unfiltered!.Count);

        var bobForMartina = await bobClient.GetFromJsonAsync<List<FeedItem>>($"/api/memories?personId={martina}");
        Assert.Equal("Ajena", Assert.Single(bobForMartina!).Title);
    }
}
