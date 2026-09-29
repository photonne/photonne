using System.IO.Compression;
using System.Security.Claims;
using ImageMagick;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Assets;

public class DownloadZipEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/download-zip", DownloadZip)
            .WithTags("Assets")
            .WithName("DownloadAssetsZip")
            .WithDescription("Downloads selected assets as a ZIP file")
            .RequireAuthorization();
    }

    private static async Task<IResult> DownloadZip(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromBody] DownloadZipRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
            return Results.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return Results.Unauthorized();

        if (request.AssetIds == null || request.AssetIds.Count == 0)
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });

        if (!AssetDownloadFormats.TryParse(request.Format, out var format))
            return Results.BadRequest(new { error = "format must be 'original' or 'jpeg'" });

        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id) && a.DeletedAt == null)
            .ToListAsync(ct);

        if (assets.Any(a => !IsAssetInUserRoot(a.FullPath, username)))
            return Results.Forbid();

        var zipName = !string.IsNullOrWhiteSpace(request.FileName)
            ? $"{request.FileName}.zip"
            : "photonne_selection.zip";

        var memoryStream = new MemoryStream();
        using (var archive = new ZipArchive(memoryStream, ZipArchiveMode.Create, leaveOpen: true))
        {
            var usedNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var asset in assets)
            {
                ct.ThrowIfCancellationRequested();

                var physicalPath = await settingsService.ResolvePhysicalPathAsync(asset.FullPath);
                if (!File.Exists(physicalPath))
                    continue;

                var jpegBytes = AssetDownloadFormats.ConvertsInZip(Path.GetExtension(physicalPath), format)
                    ? TryRenderJpeg(physicalPath)
                    : null;

                var entryName = GetUniqueEntryName(
                    jpegBytes != null ? AssetDownloadFormats.JpegFileName(asset.FileName) : asset.FileName,
                    usedNames);
                usedNames.Add(entryName);

                var entry = archive.CreateEntry(entryName, CompressionLevel.NoCompression);
                await using var entryStream = entry.Open();
                if (jpegBytes != null)
                {
                    await entryStream.WriteAsync(jpegBytes, ct);
                    continue;
                }
                await using var fileStream = File.OpenRead(physicalPath);
                await fileStream.CopyToAsync(entryStream, ct);
            }
        }

        memoryStream.Position = 0;
        return Results.File(memoryStream, "application/zip", zipName);
    }

    /// <summary>
    /// Null when the file can't be converted: the ZIP then carries the
    /// original, which beats leaving the photo out.
    /// </summary>
    private static byte[]? TryRenderJpeg(string physicalPath)
    {
        try
        {
            return RawImageLoader.RenderJpeg(physicalPath);
        }
        catch (MagickException ex)
        {
            Console.WriteLine($"[ZIP] {Path.GetFileName(physicalPath)} could not be converted to JPEG, adding the original: {ex.Message}");
            return null;
        }
    }

    private static string GetUniqueEntryName(string fileName, HashSet<string> usedNames)
    {
        if (!usedNames.Contains(fileName))
            return fileName;

        var ext = Path.GetExtension(fileName);
        var name = Path.GetFileNameWithoutExtension(fileName);
        var counter = 1;
        string candidate;
        do
        {
            candidate = $"{name}_{counter++}{ext}";
        } while (usedNames.Contains(candidate));
        return candidate;
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        userId = Guid.Empty;
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return claim != null && Guid.TryParse(claim.Value, out userId);
    }

    private static bool IsAssetInUserRoot(string assetPath, string username)
    {
        var normalized = assetPath.Replace('\\', '/');
        var virtualRoot = $"/assets/users/{username}/";
        return normalized.StartsWith(virtualRoot, StringComparison.OrdinalIgnoreCase)
            || normalized.Contains($"/users/{username}/", StringComparison.OrdinalIgnoreCase);
    }
}

public class DownloadZipRequest
{
    public List<Guid> AssetIds { get; set; } = new();
    public string? FileName { get; set; }

    /// <summary>
    /// "original" or "jpeg": what the RAW and HEIC/HEIF entries are stored as.
    /// Left out, every entry is the original.
    /// </summary>
    public string? Format { get; set; }
}
