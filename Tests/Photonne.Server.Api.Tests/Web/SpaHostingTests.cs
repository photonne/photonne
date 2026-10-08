using System.Net;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Web;

/// <summary>
/// The server hosts the web client: app routes get its index.html, its
/// hashed build files are cached for good, unknown API paths stay API 404s,
/// and public share links get a link preview without counting a view.
/// </summary>
public sealed class SpaHostingTests : IntegrationTestBase
{
    public SpaHostingTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record ErrorBody(string Error, string Code);

    [Fact]
    public async Task AppRoute_GetsTheIndex_RevalidatedEveryTime()
    {
        var response = await CreateClient().GetAsync("/albums/123");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("text/html", response.Content.Headers.ContentType?.MediaType);
        Assert.Contains(PhotonneApiFactory.IndexInlineScript, await response.Content.ReadAsStringAsync());
        Assert.True(response.Headers.CacheControl?.NoCache);
    }

    [Fact]
    public async Task HashedBuildFiles_AreCachedForGood()
    {
        var response = await CreateClient().GetAsync("/_app/immutable/start.abc123.js");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("public, max-age=31536000, immutable", response.Headers.CacheControl?.ToString());
    }

    [Fact]
    public async Task UnknownApiPath_IsAnApi404_NotTheIndex()
    {
        var response = await CreateClient().GetAsync("/api/does-not-exist");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        var body = await response.Content.ReadFromJsonAsync<ErrorBody>();
        Assert.Equal("endpoint_not_found", body!.Code);
    }

    [Fact]
    public async Task Csp_AllowsTheIndexInlineScriptsByHash_Only()
    {
        var response = await CreateClient().GetAsync("/");

        var csp = string.Join(';', response.Headers.GetValues("Content-Security-Policy"));
        var scriptSrc = csp.Split(';').Select(d => d.Trim()).Single(d => d.StartsWith("script-src "));
        var hash = Convert.ToBase64String(SHA256.HashData(Encoding.UTF8.GetBytes(PhotonneApiFactory.IndexInlineScript)));
        Assert.Contains($"'sha256-{hash}'", scriptSrc);
        Assert.DoesNotContain("unsafe-inline", scriptSrc);
        Assert.DoesNotContain("unsafe-eval", scriptSrc);
    }

    [Fact]
    public async Task OpenShareLink_GetsAPreview_WithoutCountingAView()
    {
        var token = await CreateShareLinkAsync("Verano <2026>", password: null);

        var html = await CreateClient().GetStringAsync($"/share/{token}");

        Assert.Contains("<meta property=\"og:title\" content=\"Verano &lt;2026&gt;\" />", html);
        Assert.Contains($"/api/share/{token}/asset/", html);
        Assert.Contains("<title>Verano &lt;2026&gt; · Photonne</title>", html);
        Assert.Contains(PhotonneApiFactory.IndexInlineScript, html);
        var views = await WithDbContextAsync(db => Task.FromResult(db.SharedLinks.Single(l => l.Token == token).ViewCount));
        Assert.Equal(0, views);
    }

    [Fact]
    public async Task PasswordShareLink_ShowsNothingOfTheAlbum()
    {
        var token = await CreateShareLinkAsync("Privado", password: "secreto");

        var html = await CreateClient().GetStringAsync($"/share/{token}");

        Assert.DoesNotContain("og:", html);
        Assert.DoesNotContain("Privado", html);
    }

    private async Task<string> CreateShareLinkAsync(string albumName, string? password)
    {
        var owner = await CreateUserAsync();
        var token = Guid.NewGuid().ToString("N");
        await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                OwnerId = owner.Id,
                FileName = "a.jpg",
                FullPath = "/assets/a.jpg",
                Checksum = Guid.NewGuid().ToString("N")
            };
            var album = new Album { Name = albumName, OwnerId = owner.Id };
            album.AlbumAssets.Add(new AlbumAsset { Asset = asset });
            db.Albums.Add(album);
            db.SharedLinks.Add(new SharedLink
            {
                Token = token,
                Album = album,
                CreatedById = owner.Id,
                PasswordHash = password != null ? SharePasswordHasher.Hash(password) : null
            });
            await db.SaveChangesAsync();
        });
        return token;
    }
}
