using System.IO.Compression;
using System.Security.Claims;
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

        if (request.AssetIds == null || request.AssetIds.Count == 0)
            return Results.BadRequest(new { error = "Debes seleccionar al menos un asset." });

        var isAdmin = user.IsInRole("Admin");
        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id) && a.DeletedAt == null)
            .ToListAsync(ct);

        if (!isAdmin && assets.Any(a => !IsAssetInUserRoot(a.FullPath, userId)))
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

                var entryName = GetUniqueEntryName(asset.FileName, usedNames);
                usedNames.Add(entryName);

                var entry = archive.CreateEntry(entryName, CompressionLevel.NoCompression);
                await using var entryStream = entry.Open();
                await using var fileStream = File.OpenRead(physicalPath);
                await fileStream.CopyToAsync(entryStream, ct);
            }
        }

        memoryStream.Position = 0;
        return Results.File(memoryStream, "application/zip", zipName);
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

    private static bool IsAssetInUserRoot(string assetPath, Guid userId)
    {
        var normalized = assetPath.Replace('\\', '/');
        var virtualRoot = $"/assets/users/{userId}/";
        return normalized.StartsWith(virtualRoot, StringComparison.OrdinalIgnoreCase)
            || normalized.Contains($"/users/{userId}/", StringComparison.OrdinalIgnoreCase);
    }
}

public class DownloadZipRequest
{
    public List<Guid> AssetIds { get; set; } = new();
    public string? FileName { get; set; }
}
