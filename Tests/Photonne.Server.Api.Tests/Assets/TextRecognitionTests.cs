using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Assets;

/// <summary>
/// Exercises the OCR feature surface end-to-end against a real Postgres:
///  - GET /api/assets/{id}/text returns persisted lines in reading order
///    and enforces ownership.
///  - GET /api/assets/search?q=... matches inside ExtractedTexts so the
///    existing search bar finds the photo of a receipt or a screenshot.
/// The Python ML service is not invoked here; rows are seeded directly via
/// the DbContext so the test is hermetic.
/// </summary>
public sealed class TextRecognitionTests : IntegrationTestBase
{
    public TextRecognitionTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record ExtractedTextLineDto(
        Guid Id,
        string Text,
        float Confidence,
        float BBoxX,
        float BBoxY,
        float BBoxWidth,
        float BBoxHeight,
        int LineIndex);

    private sealed record SearchResponse(List<SearchItem> Items, bool HasMore);
    private sealed record SearchItem(Guid Id, string FileName);

    private async Task<Guid> CreateImageForUserAsync(Guid userId, string fileName)
    {
        return await WithDbContextAsync(async db =>
        {
            var asset = new Asset
            {
                FileName = fileName,
                FullPath = $"/assets/users/{userId}/{fileName}",
                FileSize = 1024,
                Checksum = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N"),
                Type = AssetType.Image,
                Extension = Path.GetExtension(fileName).TrimStart('.'),
                FileCreatedAt = DateTime.UtcNow,
                FileModifiedAt = DateTime.UtcNow,
                OwnerId = userId
            };
            db.Assets.Add(asset);
            await db.SaveChangesAsync();
            return asset.Id;
        });
    }

    private async Task SeedTextLinesAsync(Guid assetId, params string[] lines)
    {
        await WithDbContextAsync(async db =>
        {
            for (var i = 0; i < lines.Length; i++)
            {
                db.ExtractedTexts.Add(new ExtractedText
                {
                    AssetId = assetId,
                    Text = lines[i],
                    Confidence = 0.95f,
                    BBoxX = 0.1f,
                    BBoxY = 0.1f + i * 0.05f,
                    BBoxWidth = 0.4f,
                    BBoxHeight = 0.04f,
                    LineIndex = i,
                });
            }
            await db.SaveChangesAsync();
        });
    }

    [Fact]
    public async Task GetText_ReturnsLines_OrderedByLineIndex()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var assetId = await CreateImageForUserAsync(alice.Id, "receipt.jpg");
        await SeedTextLinesAsync(assetId, "CAFÉ CENTRAL", "Total 12.50 EUR", "Gracias");

        var response = await aliceClient.GetAsync($"/api/assets/{assetId}/text");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<List<ExtractedTextLineDto>>();
        Assert.NotNull(body);
        Assert.Equal(3, body!.Count);
        Assert.Equal("CAFÉ CENTRAL", body[0].Text);
        Assert.Equal(0, body[0].LineIndex);
        Assert.Equal("Gracias", body[2].Text);
    }

    [Fact]
    public async Task GetText_ReturnsNotFound_WhenAssetBelongsToAnotherUser()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();

        var aliceAssetId = await CreateImageForUserAsync(alice.Id, "private.jpg");
        await SeedTextLinesAsync(aliceAssetId, "secret note");

        var response = await bobClient.GetAsync($"/api/assets/{aliceAssetId}/text");
        // 404, not 403 — we don't disclose the existence of foreign assets.
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task Search_ByOcrText_MatchesViaQ()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var receiptId = await CreateImageForUserAsync(alice.Id, "img_001.jpg");
        var unrelatedId = await CreateImageForUserAsync(alice.Id, "img_002.jpg");
        await SeedTextLinesAsync(receiptId, "CARREFOUR EXPRESS", "Total 23.40 EUR");
        await SeedTextLinesAsync(unrelatedId, "Hello World");

        var response = await aliceClient.GetAsync("/api/assets/search?q=carrefour");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<SearchResponse>();
        Assert.NotNull(body);
        Assert.Contains(body!.Items, i => i.Id == receiptId);
        Assert.DoesNotContain(body.Items, i => i.Id == unrelatedId);
    }

    [Fact]
    public async Task Search_ByTextQuery_UsesFullTextIndex()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var menuId = await CreateImageForUserAsync(alice.Id, "menu.jpg");
        var otherId = await CreateImageForUserAsync(alice.Id, "other.jpg");
        await SeedTextLinesAsync(menuId, "Pizza margherita", "Spaghetti carbonara");
        await SeedTextLinesAsync(otherId, "shopping list");

        var response = await aliceClient.GetAsync("/api/assets/search?textQuery=carbonara");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        var body = await response.Content.ReadFromJsonAsync<SearchResponse>();
        Assert.NotNull(body);
        Assert.Contains(body!.Items, i => i.Id == menuId);
        Assert.DoesNotContain(body.Items, i => i.Id == otherId);
    }
}
