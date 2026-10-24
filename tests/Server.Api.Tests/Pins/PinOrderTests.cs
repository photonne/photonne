using System.Net;
using System.Net.Http.Json;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Pins;

/// <summary>
/// "Fijados" keeps one order per user across album and folder pins: a new pin
/// goes on top, and PUT /api/pins/order rewrites it without touching anyone else's.
/// </summary>
public sealed class PinOrderTests : IntegrationTestBase
{
    public PinOrderTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record ItemDto(Guid Id, bool IsPinned, int? PinPosition);
    private sealed record CreateAlbum(string Name);
    private sealed record PinRef(string Kind, Guid Id);
    private sealed record ReorderRequest(List<PinRef> Pins);

    private static async Task<Guid> CreateAlbumAsync(HttpClient client, string name)
    {
        var response = await client.PostAsJsonAsync("/api/albums", new CreateAlbum(name));
        response.EnsureSuccessStatusCode();
        return (await response.Content.ReadFromJsonAsync<ItemDto>())!.Id;
    }

    private Task<Guid> CreateFolderAsync(string path) =>
        WithDbContextAsync(async db =>
        {
            var folder = new Folder { Path = path, Name = path.Split('/').Last() };
            db.Folders.Add(folder);
            await db.SaveChangesAsync();
            return folder.Id;
        });

    /// <summary>The user's pins as "kind:id", in their "Fijados" order.</summary>
    private static async Task<List<string>> PinnedOrderAsync(HttpClient client)
    {
        var albums = await client.GetFromJsonAsync<List<ItemDto>>("/api/albums");
        var folders = await client.GetFromJsonAsync<List<ItemDto>>("/api/folders");
        return albums!.Where(a => a.IsPinned).Select(a => (a.PinPosition, Key: $"album:{a.Id}"))
            .Concat(folders!.Where(f => f.IsPinned).Select(f => (f.PinPosition, Key: $"folder:{f.Id}")))
            .OrderBy(p => p.PinPosition)
            .Select(p => p.Key)
            .ToList();
    }

    [Fact]
    public async Task NewPins_GoOnTop_AcrossAlbumsAndFolders()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var viaje = await CreateAlbumAsync(client, "Viaje");
        var camara = await CreateFolderAsync($"/assets/users/{alice.Username}/Camara");
        var perros = await CreateAlbumAsync(client, "Perros");

        (await client.PutAsync($"/api/albums/{viaje}/pin", null)).EnsureSuccessStatusCode();
        (await client.PutAsync($"/api/folders/{camara}/pin", null)).EnsureSuccessStatusCode();
        (await client.PutAsync($"/api/albums/{perros}/pin", null)).EnsureSuccessStatusCode();

        Assert.Equal([$"album:{perros}", $"folder:{camara}", $"album:{viaje}"], await PinnedOrderAsync(client));
        var detail = await client.GetFromJsonAsync<ItemDto>($"/api/folders/{camara}");
        Assert.NotNull(detail!.PinPosition);
    }

    [Fact]
    public async Task Reorder_SetsTheOrder_AndKeepsUnmentionedPinsAfter()
    {
        var (alice, client) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();
        var viaje = await CreateAlbumAsync(client, "Viaje");
        var perros = await CreateAlbumAsync(client, "Perros");
        var suelto = await CreateAlbumAsync(client, "Suelto");
        var camara = await CreateFolderAsync($"/assets/users/{alice.Username}/Camara");
        var bobs = await CreateAlbumAsync(bobClient, "Bob");
        (await bobClient.PutAsync($"/api/albums/{bobs}/pin", null)).EnsureSuccessStatusCode();

        (await client.PutAsync($"/api/albums/{viaje}/pin", null)).EnsureSuccessStatusCode();
        (await client.PutAsync($"/api/folders/{camara}/pin", null)).EnsureSuccessStatusCode();
        (await client.PutAsync($"/api/albums/{perros}/pin", null)).EnsureSuccessStatusCode();
        // Now: perros, camara, viaje.

        // Viaje first, then camara; perros isn't mentioned. An album that isn't
        // pinned, another user's pin and a repeat are ignored.
        var response = await client.PutAsJsonAsync("/api/pins/order", new ReorderRequest(
        [
            new("album", viaje), new("album", suelto), new("album", bobs),
            new("folder", camara), new("album", viaje)
        ]));
        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);

        Assert.Equal([$"album:{viaje}", $"folder:{camara}", $"album:{perros}"], await PinnedOrderAsync(client));
        Assert.Equal([$"album:{bobs}"], await PinnedOrderAsync(bobClient));

        // A later pin still lands on top of the new order.
        (await client.PutAsync($"/api/albums/{suelto}/pin", null)).EnsureSuccessStatusCode();
        Assert.Equal(
            [$"album:{suelto}", $"album:{viaje}", $"folder:{camara}", $"album:{perros}"],
            await PinnedOrderAsync(client));
    }

    [Fact]
    public async Task Reorder_RejectsAnUnknownKind()
    {
        var (_, client) = await CreateAuthenticatedUserAsync();

        var response = await client.PutAsJsonAsync("/api/pins/order",
            new ReorderRequest([new("tag", Guid.NewGuid())]));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }
}
