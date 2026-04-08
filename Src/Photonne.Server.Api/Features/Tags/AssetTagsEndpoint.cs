using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.Tags;

public class AssetTagsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/assets")
            .WithTags("Assets")
            .RequireAuthorization();

        group.MapPost("{assetId}/tags", AddTagsAsync)
            .WithName("AddAssetTags")
            .WithDescription("Adds user tags to an asset.");

        group.MapDelete("{assetId}/tags/{tag}", RemoveTagAsync)
            .WithName("RemoveAssetTag")
            .WithDescription("Removes a user tag from an asset.");

        app.MapGet("/api/tags", GetTagsAsync)
            .WithTags("Assets")
            .RequireAuthorization()
            .WithName("GetUserTags")
            .WithDescription("Gets user tags, optionally filtered by query.");
    }

    private static async Task<IResult> AddTagsAsync(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] Guid assetId,
        [FromBody] AddTagsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        if (request.Tags == null || request.Tags.Count == 0)
        {
            return Results.BadRequest(new { error = "Debes proporcionar al menos una etiqueta." });
        }

        var asset = await dbContext.Assets
            .Include(a => a.UserTags)
            .ThenInclude(ut => ut.UserTag)
            .Include(a => a.Tags)
            .FirstOrDefaultAsync(a => a.Id == assetId, ct);

        if (asset == null)
        {
            return Results.NotFound(new { error = "Asset no encontrado." });
        }

        var isAdmin = user.IsInRole("Admin");
        if (!isAdmin && !IsAssetInUserRoot(asset.FullPath, userId))
        {
            return Results.Forbid();
        }

        var normalizedInputs = request.Tags
            .Select(NormalizeTag)
            .Where(t => !string.IsNullOrWhiteSpace(t))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToList();

        if (normalizedInputs.Count == 0)
        {
            return Results.BadRequest(new { error = "Las etiquetas no pueden estar vacías." });
        }

        var existingTags = await dbContext.UserTags
            .Where(t => t.OwnerId == userId && normalizedInputs.Contains(t.NormalizedName))
            .ToListAsync(ct);

        foreach (var normalized in normalizedInputs)
        {
            var existing = existingTags.FirstOrDefault(t => t.NormalizedName == normalized);
            if (existing == null)
            {
                existing = new UserTag
                {
                    OwnerId = userId,
                    Name = TrimToMaxLength(request.Tags.First(t => NormalizeTag(t) == normalized).Trim(), 80),
                    NormalizedName = normalized
                };
                dbContext.UserTags.Add(existing);
                existingTags.Add(existing);
            }

            var alreadyLinked = asset.UserTags.Any(ut => ut.UserTag.NormalizedName == normalized);
            if (!alreadyLinked)
            {
                asset.UserTags.Add(new AssetUserTag
                {
                    AssetId = asset.Id,
                    UserTag = existing
                });
            }
        }

        await dbContext.SaveChangesAsync(ct);

        var tags = BuildTagList(asset);
        return Results.Ok(new { tags });
    }

    private static async Task<IResult> RemoveTagAsync(
        [FromServices] ApplicationDbContext dbContext,
        [FromRoute] Guid assetId,
        [FromRoute] string tag,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var asset = await dbContext.Assets
            .Include(a => a.UserTags)
            .ThenInclude(ut => ut.UserTag)
            .Include(a => a.Tags)
            .FirstOrDefaultAsync(a => a.Id == assetId, ct);

        if (asset == null)
        {
            return Results.NotFound(new { error = "Asset no encontrado." });
        }

        var isAdmin = user.IsInRole("Admin");
        if (!isAdmin && !IsAssetInUserRoot(asset.FullPath, userId))
        {
            return Results.Forbid();
        }

        var normalized = NormalizeTag(tag);
        if (string.IsNullOrWhiteSpace(normalized))
        {
            return Results.BadRequest(new { error = "Etiqueta inválida." });
        }

        var link = asset.UserTags.FirstOrDefault(ut => ut.UserTag.NormalizedName == normalized);
        if (link == null)
        {
            return Results.NotFound(new { error = "Etiqueta no encontrada en el asset." });
        }

        asset.UserTags.Remove(link);
        dbContext.AssetUserTags.Remove(link);

        await dbContext.SaveChangesAsync(ct);

        var remainingLinks = await dbContext.AssetUserTags
            .AnyAsync(ut => ut.UserTagId == link.UserTagId, ct);
        if (!remainingLinks)
        {
            dbContext.UserTags.Remove(link.UserTag);
            await dbContext.SaveChangesAsync(ct);
        }

        var tags = BuildTagList(asset);
        return Results.Ok(new { tags });
    }

    private static async Task<IResult> GetTagsAsync(
        [FromServices] ApplicationDbContext dbContext,
        [FromQuery] string? query,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
        {
            return Results.Unauthorized();
        }

        var normalizedQuery = NormalizeTag(query ?? string.Empty);

        var tagsQuery = dbContext.UserTags
            .Where(t => t.OwnerId == userId);

        if (!string.IsNullOrWhiteSpace(normalizedQuery))
        {
            tagsQuery = tagsQuery.Where(t => t.NormalizedName.Contains(normalizedQuery));
        }

        var tags = await tagsQuery
            .OrderBy(t => t.Name)
            .Select(t => t.Name)
            .ToListAsync(ct);

        return Results.Ok(tags);
    }

    private static List<string> BuildTagList(Asset asset)
    {
        var autoTags = asset.Tags.Select(t => t.TagType.ToString());
        var userTags = asset.UserTags.Select(t => t.UserTag.Name);
        return autoTags.Concat(userTags)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(t => t)
            .ToList();
    }

    private static string NormalizeTag(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return string.Empty;
        }

        var trimmed = value.Trim().ToLowerInvariant();
        var collapsed = string.Join(' ', trimmed.Split(' ', StringSplitOptions.RemoveEmptyEntries));
        return TrimToMaxLength(collapsed, 80);
    }

    private static string TrimToMaxLength(string value, int maxLength)
    {
        if (value.Length <= maxLength)
        {
            return value;
        }

        return value[..maxLength];
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        userId = Guid.Empty;
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return userIdClaim != null && Guid.TryParse(userIdClaim.Value, out userId);
    }

    private static bool IsAssetInUserRoot(string assetPath, Guid userId)
    {
        var normalized = assetPath.Replace('\\', '/');
        var virtualRoot = $"/assets/users/{userId}/";
        if (normalized.StartsWith(virtualRoot, StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        return normalized.Contains($"/users/{userId}/", StringComparison.OrdinalIgnoreCase);
    }
}

public class AddTagsRequest
{
    public List<string> Tags { get; set; } = new();
}
