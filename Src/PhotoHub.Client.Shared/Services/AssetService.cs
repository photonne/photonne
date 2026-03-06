using System.Net.Http.Headers;
using System.Net.Http.Json;
using PhotoHub.Client.Shared.Models;

namespace PhotoHub.Client.Shared.Services;

public class AssetService : IAssetService
{
    private readonly HttpClient _httpClient;
    private readonly Func<Task<string?>>? _getTokenFunc;

    public AssetService(HttpClient httpClient, Func<Task<string?>>? getTokenFunc = null)
    {
        _httpClient = httpClient;
        _getTokenFunc = getTokenFunc;
    }

    private async Task SetAuthHeaderAsync()
    {
        string? token = null;
        if (_getTokenFunc != null)
        {
            token = await _getTokenFunc();
        }

        if (!string.IsNullOrEmpty(token))
        {
            _httpClient.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        }
        else
        {
            _httpClient.DefaultRequestHeaders.Authorization = null;
        }
    }

    public async Task<TimelinePageResult> GetTimelinePageAsync(DateTime? cursor = null, int pageSize = 150)
    {
        await SetAuthHeaderAsync();
        var url = $"/api/assets/timeline?pageSize={pageSize}";
        if (cursor.HasValue)
            url += $"&cursor={Uri.EscapeDataString(cursor.Value.ToUniversalTime().ToString("o"))}";
        var response = await _httpClient.GetFromJsonAsync<TimelinePageResult>(url);
        return response ?? new TimelinePageResult();
    }

    public async Task<List<TimelineItem>> GetDeviceAssetsAsync()
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.GetFromJsonAsync<List<TimelineItem>>("/api/assets/device");
        return response ?? new List<TimelineItem>();
    }

    public async Task<TimelineItem?> GetAssetByIdAsync(Guid id)
    {
        await SetAuthHeaderAsync();
        var detail = await GetAssetDetailAsync(id);
        if (detail == null) return null;
        return new TimelineItem
        {
            Id = detail.Id,
            FileName = detail.FileName,
            FullPath = detail.FullPath,
            CreatedDate = detail.CreatedDate,
            ModifiedDate = detail.ModifiedDate,
            Extension = detail.Extension,
            Type = detail.Type,
            SyncStatus = AssetSyncStatus.Synced,
            Width = detail.Exif?.Width,
            Height = detail.Exif?.Height
        };
    }

    public async Task<AssetDetail?> GetAssetDetailAsync(Guid id)
    {
        try
        {
            await SetAuthHeaderAsync();
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
            await SetAuthHeaderAsync();
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
            CreatedDate = response.CreatedDate,
            ModifiedDate = response.ModifiedDate,
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
            SyncStatus = response.SyncStatus
        };
    }

    public async Task<List<TimelineItem>> GetAssetsByFolderAsync(Guid? folderId)
    {
        try
        {
            await SetAuthHeaderAsync();
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
        await SetAuthHeaderAsync();
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
            await SetAuthHeaderAsync();
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
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/assets/delete", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task RestoreAssetsAsync(RestoreAssetsRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/assets/restore", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task PurgeAssetsAsync(PurgeAssetsRequest request)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync("/api/assets/purge", request);
        response.EnsureSuccessStatusCode();
    }

    public async Task<List<string>> AddAssetTagsAsync(Guid assetId, List<string> tags)
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsJsonAsync($"/api/assets/{assetId}/tags", new AddTagsRequest { Tags = tags });
        response.EnsureSuccessStatusCode();

        var result = await response.Content.ReadFromJsonAsync<TagUpdateResponse>();
        return result?.Tags ?? new List<string>();
    }

    public async Task<List<string>> RemoveAssetTagAsync(Guid assetId, string tag)
    {
        await SetAuthHeaderAsync();
        var encodedTag = System.Net.WebUtility.UrlEncode(tag);
        var response = await _httpClient.DeleteAsync($"/api/assets/{assetId}/tags/{encodedTag}");
        response.EnsureSuccessStatusCode();

        var result = await response.Content.ReadFromJsonAsync<TagUpdateResponse>();
        return result?.Tags ?? new List<string>();
    }

    public async Task RestoreTrashAsync()
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsync("/api/assets/trash/restore-all", null);
        response.EnsureSuccessStatusCode();
    }

    public async Task EmptyTrashAsync()
    {
        await SetAuthHeaderAsync();
        var response = await _httpClient.PostAsync("/api/assets/trash/empty", null);
        response.EnsureSuccessStatusCode();
    }
}
