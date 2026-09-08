using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Runtime.CompilerServices;
using System.Text.Json;
using Microsoft.AspNetCore.Components.WebAssembly.Http;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public class AssetService : IAssetService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public AssetService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
    {
        _httpClient = httpClient;
        _getTokenFunc = getTokenFunc;
    }


    public async Task<List<TimelineBucket>> GetTimelineBucketsAsync(CancellationToken cancellationToken = default)
    {
        var response = await _httpClient.GetFromJsonAsync<List<TimelineBucket>>(
            "/api/assets/timeline/buckets", cancellationToken);
        return response ?? new List<TimelineBucket>();
    }

    public async Task<List<TimelineItem>> GetBucketItemsAsync(string yearMonth, CancellationToken cancellationToken = default)
    {
        var response = await _httpClient.GetFromJsonAsync<List<TimelineItem>>(
            $"/api/assets/timeline/buckets/{Uri.EscapeDataString(yearMonth)}", cancellationToken);
        return response ?? new List<TimelineItem>();
    }

    public async Task<List<YearBreakdownGroup>> GetYearBreakdownAsync(IReadOnlyCollection<Guid> assetIds)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/assets/year-breakdown", new { assetIds });
        response.EnsureSuccessStatusCode();
        var payload = await response.Content.ReadFromJsonAsync<YearBreakdownResponse>();
        return payload?.Groups ?? new List<YearBreakdownGroup>();
    }

    private sealed class YearBreakdownResponse
    {
        public List<YearBreakdownGroup> Groups { get; set; } = new();
    }

    public async Task<List<UserDuplicateGroup>> GetMyDuplicatesAsync()
    {
        var response = await _httpClient.GetFromJsonAsync<List<UserDuplicateGroup>>("/api/utilities/duplicates");
        return response ?? new List<UserDuplicateGroup>();
    }

    public async Task<TimelinePageResult> GetTrashPageAsync(DateTime? cursor = null, int pageSize = 150)
    {
        var url = $"/api/assets/trash?pageSize={pageSize}";
        if (cursor.HasValue)
            url += $"&cursor={Uri.EscapeDataString(cursor.Value.ToUniversalTime().ToString("o"))}";
        var response = await _httpClient.GetFromJsonAsync<TimelinePageResult>(url);
        return response ?? new TimelinePageResult();
    }

    public async Task<TimelinePageResult> GetTimelinePageAsync(DateTime? cursor = null, int pageSize = 150)
    {
        var url = $"/api/assets/timeline?pageSize={pageSize}";
        if (cursor.HasValue)
            url += $"&cursor={Uri.EscapeDataString(cursor.Value.ToUniversalTime().ToString("o"))}";
        var response = await _httpClient.GetFromJsonAsync<TimelinePageResult>(url);
        return response ?? new TimelinePageResult();
    }

    public async Task<TimelinePageResult> GetTimelineSectionAsync(DateTime from, DateTime to, int pageSize = 500, CancellationToken cancellationToken = default)
    {
        var url = $"/api/assets/timeline" +
                  $"?from={Uri.EscapeDataString(from.ToUniversalTime().ToString("o"))}" +
                  $"&cursor={Uri.EscapeDataString(to.ToUniversalTime().ToString("o"))}" +
                  $"&pageSize={pageSize}";
        var response = await _httpClient.GetFromJsonAsync<TimelinePageResult>(url, cancellationToken);
        return response ?? new TimelinePageResult();
    }

    public async Task<List<TimelineIndexItem>> GetTimelineIndexAsync()
    {
        var response = await _httpClient.GetFromJsonAsync<List<TimelineIndexItem>>("/api/assets/timeline/index");
        return response ?? new List<TimelineIndexItem>();
    }

    public async Task<List<TimelineItem>> GetDeviceAssetsAsync()
    {
        var response = await _httpClient.GetFromJsonAsync<List<TimelineItem>>("/api/assets/device");
        return response ?? new List<TimelineItem>();
    }

    public async Task<TimelineItem?> GetAssetByIdAsync(Guid id)
    {
        var detail = await GetAssetDetailAsync(id);
        if (detail == null) return null;
        return new TimelineItem
        {
            Id = detail.Id,
            FileName = detail.FileName,
            FullPath = detail.FullPath,
            FileCreatedAt = detail.FileCreatedAt,
            FileModifiedAt = detail.FileModifiedAt,
            Extension = detail.Extension,
            Type = detail.Type,
            SyncStatus = AssetSyncStatus.Synced,
            Width = detail.Exif?.Width,
            Height = detail.Exif?.Height,
            IsFileMissing = detail.IsFileMissing
        };
    }

    public async Task<AssetDetail?> GetAssetDetailAsync(Guid id)
    {
        try
        {
                var response = await _httpClient.GetFromJsonAsync<AssetDetailResponse>($"/api/assets/{id}");
            return MapResponseToDetail(response);
        }
        catch
        {
            return null;
        }
    }

    public async Task<AssetDetail?> GetPendingAssetDetailAsync(string path)
    {
        try
        {
                var response = await _httpClient.GetFromJsonAsync<AssetDetailResponse>($"/api/assets/pending/detail?path={System.Net.WebUtility.UrlEncode(path)}");
            return MapResponseToDetail(response);
        }
        catch
        {
            return null;
        }
    }

    private AssetDetail? MapResponseToDetail(AssetDetailResponse? response)
    {
        if (response == null)
            return null;

        // Mapear de AssetDetailResponse a AssetDetail
        return new AssetDetail
        {
            Id = response.Id,
            FileName = response.FileName,
            FullPath = response.FullPath,
            FileSize = response.FileSize,
            FileCreatedAt = response.FileCreatedAt,
            FileModifiedAt = response.FileModifiedAt,
            Extension = response.Extension,
            ScannedAt = response.ScannedAt,
            Type = response.Type,
            Checksum = response.Checksum,
            HasExif = response.HasExif,
            HasThumbnails = response.HasThumbnails,
            FolderId = response.FolderId,
            FolderPath = response.FolderPath,
            Exif = response.Exif != null ? new ExifData
            {
                DateTaken = response.Exif.DateTaken,
                CameraMake = response.Exif.CameraMake,
                CameraModel = response.Exif.CameraModel,
                Width = response.Exif.Width,
                Height = response.Exif.Height,
                Orientation = response.Exif.Orientation,
                Latitude = response.Exif.Latitude,
                Longitude = response.Exif.Longitude,
                Altitude = response.Exif.Altitude,
                Iso = response.Exif.Iso,
                Aperture = response.Exif.Aperture,
                ShutterSpeed = response.Exif.ShutterSpeed,
                FocalLength = response.Exif.FocalLength,
                Description = response.Exif.Description,
                Keywords = response.Exif.Keywords,
                Software = response.Exif.Software
            } : null,
            Thumbnails = response.Thumbnails.Select(t => new ThumbnailInfo
            {
                Id = t.Id,
                Size = t.Size,
                Width = t.Width,
                Height = t.Height,
                AssetId = t.AssetId
            }).ToList(),
            Tags = response.Tags,
            UserTags = response.UserTags,
            AutoTags = response.AutoTags,
            SyncStatus = response.SyncStatus,
            IsFavorite = response.IsFavorite,
            IsArchived = response.IsArchived,
            IsFileMissing = response.IsFileMissing,
            Caption = response.Caption,
            AiDescription = response.AiDescription,
            IsReadOnly = response.IsReadOnly
        };
    }

    public async Task<List<TimelineItem>> GetAssetsByFolderAsync(Guid? folderId)
    {
        try
        {
                var url = folderId.HasValue 
                ? $"/api/folders/{folderId}/assets" 
                : "/api/assets/timeline";
            var response = await _httpClient.GetFromJsonAsync<List<TimelineItem>>(url);
            return response ?? new List<TimelineItem>();
        }
        catch
        {
            return new List<TimelineItem>();
        }
    }

    public async Task<UploadResponse?> UploadAssetAsync(string fileName, Stream content, CancellationToken cancellationToken = default)
    {
        using var multipartContent = new MultipartFormDataContent();
        using var streamContent = new StreamContent(content);
        multipartContent.Add(streamContent, "file", fileName);

        var response = await _httpClient.PostAsync("/api/assets/upload", multipartContent, cancellationToken);
        
        if (response.IsSuccessStatusCode)
        {
            return await response.Content.ReadFromJsonAsync<UploadResponse>(cancellationToken: cancellationToken);
        }

        return null;
    }

    public async Task<SyncAssetResponse?> SyncAssetAsync(string path, CancellationToken cancellationToken = default)
    {
        try
        {
                var response = await _httpClient.PostAsync($"/api/assets/sync?path={System.Net.WebUtility.UrlEncode(path)}", null, cancellationToken);
            if (response.IsSuccessStatusCode)
            {
                return await response.Content.ReadFromJsonAsync<SyncAssetResponse>(cancellationToken: cancellationToken);
            }
        }
        catch
        {
            // Ignore
        }
        return null;
    }

    public async IAsyncEnumerable<SyncProgressUpdate> SyncMultipleAssetsAsync(
        IEnumerable<string> paths, 
        [System.Runtime.CompilerServices.EnumeratorCancellation] CancellationToken cancellationToken = default)
    {
        var pathsList = paths.ToList();
        var total = pathsList.Count;
        var processed = 0;
        var successful = 0;
        var failed = 0;

        foreach (var path in pathsList)
        {
            cancellationToken.ThrowIfCancellationRequested();

            var result = await SyncAssetAsync(path, cancellationToken);
            processed++;

            if (result != null && !string.IsNullOrEmpty(result.Message))
            {
                successful++;
                yield return new SyncProgressUpdate
                {
                    Current = processed,
                    Total = total,
                    Successful = successful,
                    Failed = failed,
                    CurrentPath = path,
                    Message = $"Sincronizado: {Path.GetFileName(path)}",
                    IsCompleted = processed == total
                };
            }
            else
            {
                failed++;
                yield return new SyncProgressUpdate
                {
                    Current = processed,
                    Total = total,
                    Successful = successful,
                    Failed = failed,
                    CurrentPath = path,
                    Message = $"Error al sincronizar: {Path.GetFileName(path)}",
                    IsCompleted = processed == total
                };
            }
        }
    }

    public async Task DeleteAssetsAsync(DeleteAssetsRequest request)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/assets/delete", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task RestoreAssetsAsync(RestoreAssetsRequest request)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/assets/restore", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task PurgeAssetsAsync(PurgeAssetsRequest request)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/assets/purge", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task<List<string>> AddAssetTagsAsync(Guid assetId, List<string> tags)
    {
        var response = await _httpClient.PostAsJsonAsync($"/api/assets/{assetId}/tags", new AddTagsRequest { Tags = tags });
        response.EnsureSuccessStatusCode();

        var result = await response.Content.ReadFromJsonAsync<TagUpdateResponse>();
        return result?.Tags ?? new List<string>();
    }

    public async Task<List<string>> RemoveAssetTagAsync(Guid assetId, string tag)
    {
        var encodedTag = System.Net.WebUtility.UrlEncode(tag);
        var response = await _httpClient.DeleteAsync($"/api/assets/{assetId}/tags/{encodedTag}");
        response.EnsureSuccessStatusCode();

        var result = await response.Content.ReadFromJsonAsync<TagUpdateResponse>();
        return result?.Tags ?? new List<string>();
    }

    public async Task<(List<TimelineItem> Items, bool HasMore)> SearchAssetsAsync(
        string? q, DateTime? from, DateTime? to, string? folder, int pageSize = 100, IReadOnlyCollection<Guid>? personIds = null, IReadOnlyCollection<string>? objectLabels = null, IReadOnlyCollection<string>? sceneLabels = null, string? textQuery = null, int offset = 0)
    {
        var url = $"/api/assets/search?pageSize={pageSize}&offset={offset}";
        if (!string.IsNullOrWhiteSpace(q))
            url += $"&q={Uri.EscapeDataString(q)}";
        if (!string.IsNullOrWhiteSpace(textQuery))
            url += $"&textQuery={Uri.EscapeDataString(textQuery)}";
        if (from.HasValue)
            url += $"&from={Uri.EscapeDataString(from.Value.ToString("o"))}";
        if (to.HasValue)
            url += $"&to={Uri.EscapeDataString(to.Value.ToString("o"))}";
        if (!string.IsNullOrWhiteSpace(folder))
            url += $"&folder={Uri.EscapeDataString(folder)}";
        if (personIds != null)
        {
            foreach (var pid in personIds)
                url += $"&personId={pid}";
        }
        if (objectLabels != null)
        {
            foreach (var label in objectLabels)
            {
                if (string.IsNullOrWhiteSpace(label)) continue;
                url += $"&objectLabel={Uri.EscapeDataString(label)}";
            }
        }
        if (sceneLabels != null)
        {
            foreach (var label in sceneLabels)
            {
                if (string.IsNullOrWhiteSpace(label)) continue;
                url += $"&sceneLabel={Uri.EscapeDataString(label)}";
            }
        }

        var response = await _httpClient.GetFromJsonAsync<SearchResult>(url);
        return (response?.Items ?? new(), response?.HasMore ?? false);
    }

    public async Task<List<TimelineItem>> SemanticSearchAsync(string query, int limit = 50, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(query)) return new();
        var url = $"/api/assets/search/semantic?q={Uri.EscapeDataString(query)}&limit={limit}";
        var response = await _httpClient.GetFromJsonAsync<SemanticSearchResult>(url, ct);
        return response?.Items.Select(i => i.Asset).ToList() ?? new();
    }

    public async Task<List<TimelineItem>> GetMemoriesAsync(bool test = false)
    {
        try
        {
                var url = test ? "/api/assets/memories?test=true" : "/api/assets/memories";
            var response = await _httpClient.GetFromJsonAsync<List<TimelineItem>>(url);
            return response ?? new();
        }
        catch
        {
            return new();
        }
    }

    public async Task<bool> ToggleFavoriteAsync(Guid assetId)
    {
        var response = await _httpClient.PostAsync($"/api/assets/{assetId}/favorite", null);
        response.EnsureSuccessStatusCode();
        var result = await response.Content.ReadFromJsonAsync<FavoriteToggleResult>();
        return result?.IsFavorite ?? false;
    }

    public async Task<TimelinePageResult> GetFavoritesPageAsync(DateTime? cursor = null, int pageSize = 150)
    {
        var url = $"/api/assets/favorites?pageSize={pageSize}";
        if (cursor.HasValue)
            url += $"&cursor={Uri.EscapeDataString(cursor.Value.ToUniversalTime().ToString("o"))}";
        var response = await _httpClient.GetFromJsonAsync<TimelinePageResult>(url);
        return response ?? new TimelinePageResult();
    }

    public async Task<byte[]?> DownloadZipAsync(List<Guid> assetIds, string? fileName = null)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/assets/download-zip",
            new DownloadZipRequest { AssetIds = assetIds, FileName = fileName });
        if (!response.IsSuccessStatusCode)
            return null;
        return await response.Content.ReadAsByteArrayAsync();
    }

    public async Task<TimelinePageResult> GetArchivedPageAsync(DateTime? cursor = null, int pageSize = 150)
    {
        var url = $"/api/assets/archived?pageSize={pageSize}";
        if (cursor.HasValue)
            url += $"&cursor={Uri.EscapeDataString(cursor.Value.ToUniversalTime().ToString("o"))}";
        var response = await _httpClient.GetFromJsonAsync<TimelinePageResult>(url);
        return response ?? new TimelinePageResult();
    }

    public async Task ArchiveAssetsAsync(ArchiveAssetsRequest request)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/assets/archive", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task UnarchiveAssetsAsync(UnarchiveAssetsRequest request)
    {
        var response = await _httpClient.PostAsJsonAsync("/api/assets/unarchive", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task UnarchiveAllAsync()
    {
        var response = await _httpClient.PostAsync("/api/assets/archive/unarchive-all", null);
        response.EnsureSuccessStatusCode();
    }

    public async Task RestoreTrashAsync()
    {
        var response = await _httpClient.PostAsync("/api/assets/trash/restore-all", null);
        response.EnsureSuccessStatusCode();
    }

    public async Task EmptyTrashAsync()
    {
        var response = await _httpClient.PostAsync("/api/assets/trash/empty", null);
        response.EnsureSuccessStatusCode();
    }

    public async Task<SharedTrashPage> GetSharedTrashAsync(DateTime? cursor = null, int pageSize = 150)
    {
        var url = $"/api/assets/shared-trash?pageSize={pageSize}";
        if (cursor.HasValue)
            url += $"&cursor={Uri.EscapeDataString(cursor.Value.ToUniversalTime().ToString("o"))}";
        var response = await _httpClient.GetFromJsonAsync<SharedTrashPage>(url);
        return response ?? new SharedTrashPage();
    }

    public async Task RestoreSharedTrashAsync(IEnumerable<Guid> assetIds)
    {
        var response = await _httpClient.PostAsJsonAsync(
            "/api/assets/shared-trash/restore", new { assetIds = assetIds.ToList() });
        response.EnsureSuccessStatusCode();
    }

    public async Task PurgeSharedTrashAsync(IEnumerable<Guid> assetIds)
    {
        var response = await _httpClient.PostAsJsonAsync(
            "/api/assets/shared-trash/purge", new { assetIds = assetIds.ToList() });
        response.EnsureSuccessStatusCode();
    }

    public async Task<List<TimelineItem>> GetLargeFilesAsync(int count = 50)
    {
        var result = await _httpClient.GetFromJsonAsync<List<TimelineItem>>($"/api/utilities/large-files?count={count}");
        return result ?? new List<TimelineItem>();
    }

    public async Task<string?> UpdateDescriptionAsync(Guid assetId, string? caption)
    {
        var response = await _httpClient.PatchAsJsonAsync($"/api/assets/{assetId}/description", new { caption });
        response.EnsureSuccessStatusCode();
        var result = await response.Content.ReadFromJsonAsync<DescriptionUpdateResult>();
        return result?.Caption;
    }

    public async Task<CaptureDateUpdateResult?> UpdateCaptureDateAsync(Guid assetId, DateTimeOffset dateTaken, bool writeToFile)
    {
        var response = await _httpClient.PatchAsJsonAsync(
            $"/api/assets/{assetId}/date", new { dateTaken, writeToFile });
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<CaptureDateUpdateResult>();
    }

    public async Task<TimelineNeighborsResult> GetTimelineNeighborsAsync(Guid assetId, int before = 50, int after = 50)
    {
        var url = $"/api/assets/{assetId}/timeline-neighbors?before={before}&after={after}";
        var response = await _httpClient.GetFromJsonAsync<TimelineNeighborsResult>(url);
        return response ?? new TimelineNeighborsResult();
    }

    public async Task<HashSet<string>> CheckExistingAsync(
        IEnumerable<(string Name, long Size)> files,
        CancellationToken cancellationToken = default)
    {
        try
        {
                var body = new
            {
                files = files.Select(f => new { name = f.Name, size = f.Size }).ToList()
            };
            var response = await _httpClient.PostAsJsonAsync("/api/assets/check-existing", body, cancellationToken);
            if (!response.IsSuccessStatusCode) return [];
            var result = await response.Content.ReadFromJsonAsync<CheckExistingResult>(cancellationToken: cancellationToken);
            return result?.ExistingKeys ?? [];
        }
        catch
        {
            return [];
        }
    }

    public async Task<Guid?> ExistsByChecksumAsync(string checksum, CancellationToken cancellationToken = default)
    {
        try
        {
                var response = await _httpClient.GetAsync($"/api/assets/exists/{checksum}", cancellationToken);
            if (response.StatusCode == System.Net.HttpStatusCode.NotFound) return null;
            if (!response.IsSuccessStatusCode) return null;
            var result = await response.Content.ReadFromJsonAsync<ChecksumExistsResult>(cancellationToken: cancellationToken);
            return result?.AssetId;
        }
        catch
        {
            return null;
        }
    }
}

file class DescriptionUpdateResult
{
    public string? Caption { get; set; }
}

public class CaptureDateUpdateResult
{
    public DateTime DateTaken { get; set; }
    public DateTime CapturedAt { get; set; }
    public bool FileWritten { get; set; }
    public string? Reason { get; set; }
}

file class CheckExistingResult
{
    public HashSet<string> ExistingKeys { get; set; } = [];
}

file class ChecksumExistsResult
{
    public Guid? AssetId { get; set; }
}
