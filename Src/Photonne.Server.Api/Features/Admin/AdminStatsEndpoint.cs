using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Dtos;

namespace Photonne.Server.Api.Features.Admin;

public class AdminStatsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("stats", GetStats)
            .WithName("GetAdminStats")
            .WithDescription("Gets usage statistics for the admin dashboard");
    }

    private static async Task<IResult> GetStats(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        CancellationToken ct)
    {
        const string cacheKey = "admin:stats";
        if (cache.TryGetValue(cacheKey, out AdminStatsResponse? cached))
            return Results.Ok(cached);

        var assetStats = await dbContext.Assets
            .AsNoTracking()
            .Where(a => a.DeletedAt == null)
            .GroupBy(a => new { a.OwnerId, a.Type })
            .Select(g => new
            {
                g.Key.OwnerId,
                g.Key.Type,
                Count = g.Count(),
                Bytes = g.Sum(a => a.FileSize)
            })
            .ToListAsync(ct);

        var users = await dbContext.Users
            .AsNoTracking()
            .Select(u => new
            {
                u.Id,
                u.Username,
                u.FirstName,
                u.LastName,
                u.Email
            })
            .ToListAsync(ct);

        var userLookup = users.ToDictionary(u => u.Id, u => u);

        var userUsage = assetStats
            .GroupBy(a => a.OwnerId)
            .Select(group =>
            {
                var ownerId = group.Key;
                var photoCount = group.Where(a => a.Type == AssetType.IMAGE).Sum(a => a.Count);
                var videoCount = group.Where(a => a.Type == AssetType.VIDEO).Sum(a => a.Count);
                var photoBytes = group.Where(a => a.Type == AssetType.IMAGE).Sum(a => a.Bytes);
                var videoBytes = group.Where(a => a.Type == AssetType.VIDEO).Sum(a => a.Bytes);

                if (ownerId.HasValue && userLookup.TryGetValue(ownerId.Value, out var user))
                {
                    return new AdminUserUsage
                    {
                        UserId = user.Id,
                        DisplayName = BuildDisplayName(user.Username, user.FirstName, user.LastName, user.Email),
                        Email = user.Email,
                        Photos = photoCount,
                        Videos = videoCount,
                        PhotoBytes = photoBytes,
                        VideoBytes = videoBytes
                    };
                }

                return new AdminUserUsage
                {
                    UserId = Guid.Empty,
                    DisplayName = "Sin propietario",
                    Email = null,
                    Photos = photoCount,
                    Videos = videoCount,
                    PhotoBytes = photoBytes,
                    VideoBytes = videoBytes
                };
            })
            .OrderByDescending(u => u.PhotoBytes + u.VideoBytes)
            .ToList();

        var response = new AdminStatsResponse
        {
            TotalPhotos = assetStats.Where(a => a.Type == AssetType.IMAGE).Sum(a => a.Count),
            TotalVideos = assetStats.Where(a => a.Type == AssetType.VIDEO).Sum(a => a.Count),
            TotalBytes = assetStats.Sum(a => a.Bytes),
            Users = userUsage
        };

        cache.Set(cacheKey, response, TimeSpan.FromMinutes(15));
        return Results.Ok(response);
    }

    private static string BuildDisplayName(string username, string? firstName, string? lastName, string? email)
    {
        var parts = new[] { firstName, lastName }
            .Where(s => !string.IsNullOrWhiteSpace(s))
            .ToArray();

        if (parts.Length > 0)
        {
            return string.Join(" ", parts);
        }

        if (!string.IsNullOrWhiteSpace(username))
        {
            return username;
        }

        return email ?? "Usuario";
    }
}
