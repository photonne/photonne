using System.Net.Http.Json;
using Pgvector;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.People;

/// <summary>The "¿Quién es?" queue: every pending suggestion, whoever it points at.</summary>
public sealed class FaceSuggestionsQueueTests : IntegrationTestBase
{
    public FaceSuggestionsQueueTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record Item(Guid Id, Guid AssetId, float? SuggestedDistance, Guid PersonId, string? PersonName);
    private sealed record Page(int Total, List<Item> Items);

    /// <summary>A face on a new photo of <paramref name="owner"/>, suggested as <paramref name="person"/>.</summary>
    private Task<Guid> SuggestAsync(TestUser owner, Person? person, float distance, bool deleted = false) =>
        WithDbContextAsync(async db =>
        {
            var name = $"{Guid.NewGuid():N}.jpg";
            var asset = new Asset
            {
                FileName = name,
                FullPath = $"/assets/users/{owner.Username}/{name}",
                FileSize = 1,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = "jpg",
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                CapturedAt = DateTime.UtcNow,
                OwnerId = owner.Id,
                DeletedAt = deleted ? DateTime.UtcNow : null
            };
            var face = new Face
            {
                Asset = asset,
                BoundingBoxX = 0.1f, BoundingBoxY = 0.1f, BoundingBoxW = 0.2f, BoundingBoxH = 0.2f,
                Confidence = 0.99f,
                Embedding = new Vector(new float[512])
            };
            db.Faces.Add(face);
            if (person != null && db.Entry(person).State == Microsoft.EntityFrameworkCore.EntityState.Detached)
                db.People.Attach(person);
            db.UserFaceAssignments.Add(new UserFaceAssignment
            {
                Face = face,
                UserId = owner.Id,
                SuggestedPerson = person,
                SuggestedDistance = person == null ? null : distance
            });
            await db.SaveChangesAsync();
            return face.Id;
        });

    [Fact]
    public async Task ListsEverySuggestion_StrongestFirst_WithoutHiddenPeopleOrDeletedPhotos()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var (ana, luis, hidden) = await WithDbContextAsync(async db =>
        {
            var a = new Person { OwnerId = alice.Id, Name = "Ana" };
            var l = new Person { OwnerId = alice.Id, Name = "Luis" };
            var h = new Person { OwnerId = alice.Id, Name = "Oculta", IsHidden = true };
            db.People.AddRange(a, l, h);
            await db.SaveChangesAsync();
            return (a, l, h);
        });

        var weakAna = await SuggestAsync(alice, ana, 0.50f);
        var strongLuis = await SuggestAsync(alice, luis, 0.45f);
        await SuggestAsync(alice, hidden, 0.40f);
        await SuggestAsync(alice, ana, 0.41f, deleted: true);
        await SuggestAsync(alice, person: null, 0f);

        var page = await client.GetFromJsonAsync<Page>("/api/people/suggestions");

        Assert.Equal(2, page!.Total);
        Assert.Equal(new[] { strongLuis, weakAna }, page.Items.Select(i => i.Id));
        Assert.Equal(new[] { "Luis", "Ana" }, page.Items.Select(i => i.PersonName));

        // Accepting one takes it out of the queue.
        (await client.PostAsync($"/api/faces/{strongLuis}/accept-suggestion", null)).EnsureSuccessStatusCode();
        var after = await client.GetFromJsonAsync<Page>("/api/people/suggestions");
        Assert.Equal(new[] { weakAna }, after!.Items.Select(i => i.Id));
    }
}
