using System.Net;
using System.Net.Http.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Infrastructure;

namespace Photonne.Server.Api.Tests.Assets;

/// <summary>
/// The "Archivos no compatibles" catalogue: what the listing tells the client
/// it may delete, the delete itself (permanent, on disk — there is no trash for
/// these), and the purge that stops a row outliving its file.
/// </summary>
public sealed class UnsupportedFilesTests : IntegrationTestBase
{
    public UnsupportedFilesTests(PhotonneApiFactory factory) : base(factory) { }

    private sealed record FileBody(Guid Id, string FileName, string FullPath, bool CanDelete);
    private sealed record PageBody(List<FileBody> Items, bool HasMore);

    /// <summary>Writes a non-media file into the user's personal space and returns what a scan would report.</summary>
    private ScannedFile WriteFile(string username, string fileName)
    {
        var directory = Path.Combine(
            Factory.InternalAssetsPath, "users", username, "t" + Guid.NewGuid().ToString("N")[..8]);
        Directory.CreateDirectory(directory);
        var physicalPath = Path.Combine(directory, fileName);
        File.WriteAllText(physicalPath, "not a photo");
        var info = new FileInfo(physicalPath);
        return new ScannedFile
        {
            FileName = fileName,
            FullPath = physicalPath,
            FileSize = info.Length,
            FileCreatedAt = info.CreationTimeUtc,
            FileModifiedAt = info.LastWriteTimeUtc,
            Extension = Path.GetExtension(fileName)
        };
    }

    private async Task<Guid> CatalogueAsync(ScannedFile file)
    {
        using var scope = Factory.Services.CreateScope();
        var indexer = scope.ServiceProvider.GetRequiredService<UnsupportedFileIndexingService>();
        var row = await indexer.IndexUnsupportedFileAsync(file, Guid.Empty, CancellationToken.None);
        Assert.NotNull(row);
        return row!.Id;
    }

    private Task<bool> RowExistsAsync(Guid id) =>
        WithDbContextAsync(db => db.UnsupportedFiles.AsNoTracking().AnyAsync(u => u.Id == id));

    [Fact]
    public async Task List_ShowsPathAndAllowsDelete_OnOwnFile()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var id = await CatalogueAsync(WriteFile(alice.Username, "notes.txt"));

        var page = await aliceClient.GetFromJsonAsync<PageBody>("/api/unsupported-files?pageSize=50");

        var item = Assert.Single(page!.Items, i => i.Id == id);
        Assert.EndsWith("/notes.txt", item.FullPath);
        Assert.StartsWith($"/assets/users/{alice.Username}/", item.FullPath);
        Assert.True(item.CanDelete);
    }

    [Fact]
    public async Task Delete_RemovesFileFromDiskAndCatalogue()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var file = WriteFile(alice.Username, "notes.txt");
        var id = await CatalogueAsync(file);

        var response = await aliceClient.DeleteAsync($"/api/unsupported-files/{id}");

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        Assert.False(File.Exists(file.FullPath));
        Assert.False(await RowExistsAsync(id));
    }

    [Fact]
    public async Task Delete_ClearsTheRow_WhenTheFileIsAlreadyGone()
    {
        var (alice, aliceClient) = await CreateAuthenticatedUserAsync();
        var file = WriteFile(alice.Username, "notes.txt");
        var id = await CatalogueAsync(file);
        File.Delete(file.FullPath);

        var response = await aliceClient.DeleteAsync($"/api/unsupported-files/{id}");

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        Assert.False(await RowExistsAsync(id));
    }

    [Fact]
    public async Task Delete_IsForbidden_OnAnotherUsersFile()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var (_, bobClient) = await CreateAuthenticatedUserAsync();
        var file = WriteFile(alice.Username, "notes.txt");
        var id = await CatalogueAsync(file);

        var response = await bobClient.DeleteAsync($"/api/unsupported-files/{id}");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.True(File.Exists(file.FullPath));
        Assert.True(await RowExistsAsync(id));
    }

    [Fact]
    public async Task Prune_DropsRowsTheScanNoLongerReports_AndKeepsTheRest()
    {
        var (alice, _) = await CreateAuthenticatedUserAsync();
        var kept = WriteFile(alice.Username, "kept.txt");
        var deleted = WriteFile(alice.Username, "deleted.txt");
        var keptId = await CatalogueAsync(kept);
        var deletedId = await CatalogueAsync(deleted);
        File.Delete(deleted.FullPath);

        int purged;
        using (var scope = Factory.Services.CreateScope())
        {
            var indexer = scope.ServiceProvider.GetRequiredService<UnsupportedFileIndexingService>();
            // What the next scan reports: only the file still on disk.
            purged = await indexer.PruneStaleAsync(new[] { kept }, externalLibraryId: null, CancellationToken.None);
        }

        Assert.Equal(1, purged);
        Assert.True(await RowExistsAsync(keptId));
        Assert.False(await RowExistsAsync(deletedId));
    }
}
