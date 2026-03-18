using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PhotoHub.Server.Api.Shared.Data;
using PhotoHub.Server.Api.Shared.Interfaces;
using PhotoHub.Client.Shared.Models;

namespace PhotoHub.Server.Api.Features.Duplicates;

public class DeletePhysicalDuplicatesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/duplicates/physical/delete",
            async (
                [FromBody] List<PhysicalFileDeleteRequest> files,
                [FromServices] ApplicationDbContext dbContext,
                CancellationToken cancellationToken) =>
            {
                var result = new PhysicalDeleteResult();

                foreach (var file in files)
                {
                    var name = Path.GetFileName(file.PhysicalPath);
                    try
                    {
                        // 1. Delete physical file FIRST — if this fails the DB record is left intact
                        if (File.Exists(file.PhysicalPath))
                        {
                            try
                            {
                                File.Delete(file.PhysicalPath);
                            }
                            catch (Exception ex)
                            {
                                result.Errors.Add($"{name}: {ex.Message}");
                                Console.WriteLine($"[DELETE-DUPLICATES] No se pudo eliminar el archivo físico {file.PhysicalPath}: {ex.Message}");
                                continue; // Skip DB cleanup — the file still exists on disk
                            }
                        }

                        // 2. Physical file is gone (or never existed) — now remove DB records
                        if (file.AssetId.HasValue)
                        {
                            var asset = await dbContext.Assets
                                .FindAsync([file.AssetId.Value], cancellationToken);

                            if (asset != null)
                            {
                                var thumbnails = await dbContext.AssetThumbnails
                                    .Where(t => t.AssetId == file.AssetId.Value)
                                    .ToListAsync(cancellationToken);
                                dbContext.AssetThumbnails.RemoveRange(thumbnails);

                                var exif = await dbContext.AssetExifs
                                    .FirstOrDefaultAsync(e => e.AssetId == file.AssetId.Value, cancellationToken);
                                if (exif != null)
                                    dbContext.AssetExifs.Remove(exif);

                                dbContext.Assets.Remove(asset);
                                await dbContext.SaveChangesAsync(cancellationToken);
                            }

                            // 3. Delete thumbnail files on disk
                            var thumbnailDir = GetThumbnailDirectory(file.AssetId.Value);
                            if (Directory.Exists(thumbnailDir))
                                Directory.Delete(thumbnailDir, recursive: true);
                        }

                        result.Deleted++;
                    }
                    catch (Exception ex)
                    {
                        result.Errors.Add($"{name}: {ex.Message}");
                        Console.WriteLine($"[DELETE-DUPLICATES] Error eliminando {file.PhysicalPath}: {ex.Message}");
                    }
                }

                return Results.Ok(result);
            })
        .WithName("DeletePhysicalDuplicates")
        .WithTags("Assets")
        .WithDescription("Deletes physical duplicate files and their DB records.")
        .RequireAuthorization(policy => policy.RequireRole("Admin"));
    }

    private static string GetThumbnailDirectory(Guid assetId)
    {
        var basePath = Directory.Exists("/app/thumbnails")
            ? "/app/thumbnails"
            : Path.Combine(Directory.GetCurrentDirectory(), "thumbnails");
        return Path.Combine(basePath, assetId.ToString());
    }
}
