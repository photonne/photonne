using System.Net;
using System.Net.Http.Json;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.UploadAssets;

/// <summary>
/// /api/assets/sync applies the same limits as /upload, with the same
/// ApiError: the clients only take a 413 as Photonne's own when its body
/// carries <c>upload_too_large</c>; any other 413 reads as a proxy's.
/// </summary>
public sealed class SyncAssetLimitsTests : IntegrationTestBase
{
    public SyncAssetLimitsTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record ErrorDto(string Error, string Code);

    [Fact]
    public async Task Sync_FileOverMaxUploadSize_Returns413WithUploadTooLarge()
    {
        var (user, client) = await CreateAuthenticatedUserAsync();

        using (var scope = Factory.Services.CreateScope())
        {
            var settings = scope.ServiceProvider.GetRequiredService<SettingsService>();
            await settings.SetSettingAsync("ServerSettings.MaxUploadSizeMb", "1", Guid.Empty);
        }

        // The source has to live in the user's own subtree of the assets root.
        var sourceDir = Path.Combine(Factory.InternalAssetsPath, "users", user.Username, "Inbox");
        Directory.CreateDirectory(sourceDir);
        var sourcePath = Path.Combine(sourceDir, $"{Guid.NewGuid():N}.jpg");
        await File.WriteAllBytesAsync(sourcePath, new byte[1024 * 1024 + 1]);

        try
        {
            var response = await client.PostAsync(
                $"/api/assets/sync?path={Uri.EscapeDataString(sourcePath)}", null);

            Assert.Equal(HttpStatusCode.RequestEntityTooLarge, response.StatusCode);
            var body = await response.Content.ReadFromJsonAsync<ErrorDto>();
            Assert.NotNull(body);
            Assert.Equal("upload_too_large", body!.Code);
            Assert.Equal("El archivo supera el tamaño máximo permitido (1 MB).", body.Error);
        }
        finally
        {
            File.Delete(sourcePath);
        }
    }
}
