using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Formats.Jpeg;
using SixLabors.ImageSharp.Processing;

namespace Photonne.Server.Api.Features.People;

/// <summary>Returns a JPEG crop of the face's bounding box. Cached on disk under
/// /data/thumbnails/faces/{faceId}.jpg. Always validates that the requester owns
/// the underlying asset.</summary>
public class FaceThumbnailEndpoint : IEndpoint
{
    private const int OutputSize = 220;

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/faces/{id:guid}/thumbnail", Handle)
            .WithTags("Faces")
            .RequireAuthorization();
    }

    private static async Task<IResult> Handle(
        [FromServices] ApplicationDbContext db,
        [FromServices] SettingsService settings,
        [FromServices] IConfiguration configuration,
        Guid id,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!ListPeopleEndpoint.TryGetUserId(user, out var userId)) return Results.Unauthorized();

        var face = await db.Faces.AsNoTracking()
            .Include(f => f.Asset).ThenInclude(a => a.Thumbnails)
            .FirstOrDefaultAsync(f => f.Id == id && f.Asset.OwnerId == userId, ct);
        if (face == null) return Results.NotFound();

        var cacheDir = Path.Combine(
            configuration["ThumbnailsPath"] ?? "/data/thumbnails", "faces");
        Directory.CreateDirectory(cacheDir);
        var cachedPath = Path.Combine(cacheDir, $"{id}.jpg");

        if (!File.Exists(cachedPath))
        {
            var sourcePath = await ResolveSourceImageAsync(face, settings);
            if (sourcePath == null || !File.Exists(sourcePath))
                return Results.NotFound();

            using var image = await Image.LoadAsync(sourcePath, ct);
            var cropRect = ComputeCropRect(face, image.Width, image.Height);
            if (cropRect.Width <= 1 || cropRect.Height <= 1)
                return Results.NotFound();

            image.Mutate(x => x
                .Crop(cropRect)
                .Resize(new ResizeOptions { Size = new Size(OutputSize, OutputSize), Mode = ResizeMode.Crop }));

            await image.SaveAsync(cachedPath, new JpegEncoder { Quality = 85 }, ct);
        }

        var bytes = await File.ReadAllBytesAsync(cachedPath, ct);
        return Results.File(bytes, "image/jpeg",
            lastModified: File.GetLastWriteTimeUtc(cachedPath),
            entityTag: new Microsoft.Net.Http.Headers.EntityTagHeaderValue($"\"{id:N}\""));
    }

    private static async Task<string?> ResolveSourceImageAsync(Face face, SettingsService settings)
    {
        var large = face.Asset.Thumbnails.FirstOrDefault(t => t.Size == ThumbnailSize.Large);
        if (large != null && File.Exists(large.FilePath)) return large.FilePath;
        return await settings.ResolvePhysicalPathAsync(face.Asset.FullPath);
    }

    private static Rectangle ComputeCropRect(Face face, int width, int height)
    {
        // Bounding box is normalized [0,1]; expand 20% for context, then clamp.
        var pad = 0.2f;
        var x0 = Math.Max(0, (int)((face.BoundingBoxX - face.BoundingBoxW * pad) * width));
        var y0 = Math.Max(0, (int)((face.BoundingBoxY - face.BoundingBoxH * pad) * height));
        var x1 = Math.Min(width, (int)((face.BoundingBoxX + face.BoundingBoxW * (1 + pad)) * width));
        var y1 = Math.Min(height, (int)((face.BoundingBoxY + face.BoundingBoxH * (1 + pad)) * height));
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }
}
