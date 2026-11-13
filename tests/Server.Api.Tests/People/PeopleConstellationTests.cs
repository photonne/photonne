using System.Net;
using System.Net.Http.Json;
using Pgvector;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.People;

/// <summary>La relación de cada persona y los datos con los que se dibuja la constelación.</summary>
public sealed class PeopleConstellationTests : IntegrationTestBase
{
    public PeopleConstellationTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record YearDto(int Year, int AssetCount);
    private sealed record StarDto(
        Guid Id, string Name, string? RelationshipCategory, string? RelationshipLabel,
        DateTime FirstCapturedAt, DateTime LastCapturedAt, int AssetCount, List<YearDto> Years,
        Guid? BranchFromPersonId);
    private sealed record Constellation(List<StarDto> People);
    private sealed record PersonBody(
        Guid Id, string? Name, string? RelationshipCategory, string? RelationshipLabel, Guid? BranchFromPersonId);
    private sealed record ErrorBody(string Error, string Code);

    private Task<Person> PersonAsync(TestUser owner, string? name, bool hidden = false) =>
        WithDbContextAsync(async db =>
        {
            var p = new Person { OwnerId = owner.Id, Name = name, IsHidden = hidden };
            db.People.Add(p);
            await db.SaveChangesAsync();
            return p;
        });

    /// <summary>Una foto de <paramref name="owner"/> con <paramref name="faces"/> caras confirmadas de la persona.</summary>
    private Task PhotoAsync(TestUser owner, Person person, DateTime capturedAt,
        int faces = 1, bool deleted = false, bool rejected = false, TestUser? assignedBy = null) =>
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
                FileCreatedAt = capturedAt,
                FileModifiedAt = capturedAt,
                CapturedAt = capturedAt,
                OwnerId = owner.Id,
                DeletedAt = deleted ? DateTime.UtcNow : null
            };
            for (var i = 0; i < faces; i++)
            {
                var face = new Face
                {
                    Asset = asset,
                    BoundingBoxX = 0.1f * i, BoundingBoxY = 0.1f, BoundingBoxW = 0.2f, BoundingBoxH = 0.2f,
                    Confidence = 0.99f,
                    Embedding = new Vector(new float[512])
                };
                db.Faces.Add(face);
                db.UserFaceAssignments.Add(new UserFaceAssignment
                {
                    Face = face,
                    UserId = (assignedBy ?? owner).Id,
                    PersonId = person.Id,
                    IsRejected = rejected
                });
            }
            await db.SaveChangesAsync();
        });

    private static DateTime Day(int year, int month, int day) => new(year, month, day, 12, 0, 0, DateTimeKind.Utc);

    [Fact]
    public async Task Constellation_GivesEachNamedPersonTheirSpanAndPhotosPerYear()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var ana = await PersonAsync(alice, "Ana");
        var luis = await PersonAsync(alice, "Luis");
        var unnamed = await PersonAsync(alice, null);
        var hidden = await PersonAsync(alice, "Oculta", hidden: true);
        var noPhotos = await PersonAsync(alice, "Sin fotos");

        await PhotoAsync(alice, ana, Day(2010, 5, 1));
        await PhotoAsync(alice, ana, Day(2010, 8, 20), faces: 2); // dos caras, una foto
        await PhotoAsync(alice, ana, Day(2014, 1, 3));
        await PhotoAsync(alice, ana, Day(2016, 6, 6), deleted: true);
        await PhotoAsync(alice, ana, Day(2017, 7, 7), rejected: true);
        await PhotoAsync(alice, luis, Day(2005, 12, 31));
        await PhotoAsync(alice, unnamed, Day(2012, 2, 2));
        await PhotoAsync(alice, hidden, Day(2012, 3, 3));

        (await client.PutAsJsonAsync($"/api/people/{ana.Id}/relationship",
            new { category = "family", label = " Madre " })).EnsureSuccessStatusCode();

        var body = await client.GetFromJsonAsync<Constellation>("/api/people/constellation");

        // Por orden de aparición; sin las personas sin nombre, ocultas o sin fotos.
        Assert.Equal(new[] { "Luis", "Ana" }, body!.People.Select(p => p.Name));
        Assert.DoesNotContain(body.People, p => p.Id == noPhotos.Id);

        var star = body.People[1];
        Assert.Equal("family", star.RelationshipCategory);
        Assert.Equal("Madre", star.RelationshipLabel);
        Assert.Equal(Day(2010, 5, 1), star.FirstCapturedAt);
        Assert.Equal(Day(2014, 1, 3), star.LastCapturedAt);
        Assert.Equal(3, star.AssetCount);
        Assert.Equal(new[] { new YearDto(2010, 2), new YearDto(2014, 1) }, star.Years);

        Assert.Null(body.People[0].RelationshipCategory);
        Assert.Equal(new[] { new YearDto(2005, 1) }, body.People[0].Years);
    }

    [Fact]
    public async Task Constellation_OnlyCountsTheCallersOwnPeople()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var ana = await PersonAsync(alice, "Ana");
        await PhotoAsync(alice, ana, Day(2020, 1, 1));

        var bobs = await bobClient.GetFromJsonAsync<Constellation>("/api/people/constellation");
        Assert.Empty(bobs!.People);

        var bobSets = await bobClient.PutAsJsonAsync($"/api/people/{ana.Id}/relationship", new { category = "friends" });
        Assert.Equal(HttpStatusCode.NotFound, bobSets.StatusCode);

        var alices = await aliceClient.GetFromJsonAsync<Constellation>("/api/people/constellation");
        Assert.Null(Assert.Single(alices!.People).RelationshipCategory);
    }

    [Fact]
    public async Task Relationship_IsValidated_ShownOnThePerson_AndCanBeCleared()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var ana = await PersonAsync(alice, "Ana");
        var url = $"/api/people/{ana.Id}/relationship";

        var unknown = await client.PutAsJsonAsync(url, new { category = "enemigos" });
        Assert.Equal(HttpStatusCode.BadRequest, unknown.StatusCode);
        Assert.Equal("invalid_relationship_category", (await unknown.Content.ReadFromJsonAsync<ErrorBody>())!.Code);

        var tooLong = await client.PutAsJsonAsync(url, new { category = "friends", label = new string('a', 101) });
        Assert.Equal(HttpStatusCode.BadRequest, tooLong.StatusCode);
        Assert.Equal("relationship_label_too_long", (await tooLong.Content.ReadFromJsonAsync<ErrorBody>())!.Code);

        Assert.Equal(HttpStatusCode.NoContent,
            (await client.PutAsJsonAsync(url, new { category = "Friends", label = "Amiga de la uni" })).StatusCode);
        var person = await client.GetFromJsonAsync<PersonBody>($"/api/people/{ana.Id}");
        Assert.Equal("friends", person!.RelationshipCategory);
        Assert.Equal("Amiga de la uni", person.RelationshipLabel);

        // Renombrar no toca la relación.
        (await client.PatchAsJsonAsync($"/api/people/{ana.Id}", new { name = "Ana María" })).EnsureSuccessStatusCode();
        person = await client.GetFromJsonAsync<PersonBody>($"/api/people/{ana.Id}");
        Assert.Equal("friends", person!.RelationshipCategory);

        // Sin categoría no queda etiqueta suelta.
        (await client.PutAsJsonAsync(url, new { category = (string?)null, label = "Amiga" })).EnsureSuccessStatusCode();
        person = await client.GetFromJsonAsync<PersonBody>($"/api/people/{ana.Id}");
        Assert.Null(person!.RelationshipCategory);
        Assert.Null(person.RelationshipLabel);
    }

    [Fact]
    public async Task Merge_KeepsTheRelationshipOfThePersonThatDisappears_WhenTheOtherHasNone()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var keep = await PersonAsync(alice, "Ana");
        var gone = await PersonAsync(alice, "Ana (duplicada)");
        await PhotoAsync(alice, keep, Day(2019, 1, 1));
        await PhotoAsync(alice, gone, Day(2021, 1, 1));
        (await client.PutAsJsonAsync($"/api/people/{gone.Id}/relationship",
            new { category = "partner", label = "Pareja" })).EnsureSuccessStatusCode();

        (await client.PostAsync($"/api/people/{keep.Id}/merge/{gone.Id}", null)).EnsureSuccessStatusCode();

        var star = Assert.Single((await client.GetFromJsonAsync<Constellation>("/api/people/constellation"))!.People);
        Assert.Equal(keep.Id, star.Id);
        Assert.Equal("partner", star.RelationshipCategory);
        Assert.Equal("Pareja", star.RelationshipLabel);
        Assert.Equal(2, star.AssetCount);
    }

    private static Task<HttpResponseMessage> BranchAsync(HttpClient client, Person person, Person? from) =>
        client.PutAsJsonAsync($"/api/people/{person.Id}/branch", new { personId = from?.Id });

    [Fact]
    public async Task Branch_SaysWhoseTrailAPersonIsBornFrom_AndCanBeRemoved()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var sister = await PersonAsync(alice, "Laia");
        var niece = await PersonAsync(alice, "Ona");
        await PhotoAsync(alice, sister, Day(2000, 1, 1));
        await PhotoAsync(alice, niece, Day(2018, 1, 1));

        Assert.Equal(HttpStatusCode.NoContent, (await BranchAsync(client, niece, sister)).StatusCode);

        var person = await client.GetFromJsonAsync<PersonBody>($"/api/people/{niece.Id}");
        Assert.Equal(sister.Id, person!.BranchFromPersonId);
        var stars = (await client.GetFromJsonAsync<Constellation>("/api/people/constellation"))!.People;
        Assert.Equal(sister.Id, stars.Single(s => s.Id == niece.Id).BranchFromPersonId);
        Assert.Null(stars.Single(s => s.Id == sister.Id).BranchFromPersonId);

        (await BranchAsync(client, niece, null)).EnsureSuccessStatusCode();
        person = await client.GetFromJsonAsync<PersonBody>($"/api/people/{niece.Id}");
        Assert.Null(person!.BranchFromPersonId);
    }

    [Fact]
    public async Task Branch_RejectsItself_Cycles_AndPeopleOfSomeoneElse()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var (bob, bobClient) = await CreateAuthenticatedUserAsync();
        var mother = await PersonAsync(alice, "Mamá");
        var sister = await PersonAsync(alice, "Laia");
        var niece = await PersonAsync(alice, "Ona");
        var bobs = await PersonAsync(bob, "Otro");
        (await BranchAsync(client, sister, mother)).EnsureSuccessStatusCode();
        (await BranchAsync(client, niece, sister)).EnsureSuccessStatusCode();

        async Task AssertRejectedAsync(Person person, Person from, string code)
        {
            var response = await BranchAsync(client, person, from);
            Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
            Assert.Equal(code, (await response.Content.ReadFromJsonAsync<ErrorBody>())!.Code);
        }

        await AssertRejectedAsync(niece, niece, "branch_same_person");
        await AssertRejectedAsync(mother, niece, "branch_cycle");
        await AssertRejectedAsync(mother, bobs, "branch_person_not_found");
        Assert.Equal(HttpStatusCode.NotFound, (await BranchAsync(bobClient, niece, bobs)).StatusCode);
    }

    [Fact]
    public async Task Merge_MovesTheBranchesToThePersonThatStays()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var mother = await PersonAsync(alice, "Mamá");
        var keep = await PersonAsync(alice, "Laia");
        var gone = await PersonAsync(alice, "Laia (duplicada)");
        var niece = await PersonAsync(alice, "Ona");
        (await BranchAsync(client, gone, mother)).EnsureSuccessStatusCode();
        (await BranchAsync(client, niece, gone)).EnsureSuccessStatusCode();

        (await client.PostAsync($"/api/people/{keep.Id}/merge/{gone.Id}", null)).EnsureSuccessStatusCode();

        var kept = await client.GetFromJsonAsync<PersonBody>($"/api/people/{keep.Id}");
        Assert.Equal(mother.Id, kept!.BranchFromPersonId);
        var child = await client.GetFromJsonAsync<PersonBody>($"/api/people/{niece.Id}");
        Assert.Equal(keep.Id, child!.BranchFromPersonId);
    }

    [Fact]
    public async Task Merge_NeverLeavesAPersonBranchingFromItself()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var keep = await PersonAsync(alice, "Laia");
        var gone = await PersonAsync(alice, "Ona");
        // La que queda nacía de la que desaparece.
        (await BranchAsync(client, keep, gone)).EnsureSuccessStatusCode();

        (await client.PostAsync($"/api/people/{keep.Id}/merge/{gone.Id}", null)).EnsureSuccessStatusCode();

        var kept = await client.GetFromJsonAsync<PersonBody>($"/api/people/{keep.Id}");
        Assert.Null(kept!.BranchFromPersonId);
    }

    [Fact]
    public async Task DeletingThePersonABranchComesFrom_LeavesTheOtherOnItsOwn()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var sister = await PersonAsync(alice, "Laia");
        var niece = await PersonAsync(alice, "Ona");
        (await BranchAsync(client, niece, sister)).EnsureSuccessStatusCode();

        await WithDbContextAsync(async db =>
        {
            db.People.Remove(await db.People.FindAsync(sister.Id) ?? throw new InvalidOperationException());
            await db.SaveChangesAsync();
        });

        var person = await client.GetFromJsonAsync<PersonBody>($"/api/people/{niece.Id}");
        Assert.Null(person!.BranchFromPersonId);
    }
}
