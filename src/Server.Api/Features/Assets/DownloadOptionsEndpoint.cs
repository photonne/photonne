using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Assets;

/// <summary>
/// Tells a client, before it downloads or shares a selection, whether there is
/// anything to ask: how many of those assets are RAW or HEIC/HEIF and could
/// leave as JPEG instead. The selection screens only hold ids, so the question
/// "is any of these a RAW?" has to be answered here.
/// </summary>
public class DownloadOptionsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/download-options", Handle)
            .WithTags("Assets")
            .WithName("GetAssetsDownloadOptions")
            .WithDescription("Counts the RAW and HEIC/HEIF assets in a selection, the ones that can be downloaded as JPEG")
            .RequireAuthorization();
    }

    private static async Task<Results<Ok<DownloadOptionsResponse>, UnauthorizedHttpResult>> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] AssetVisibilityService visibility,
        [FromBody] DownloadOptionsRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        if (claim == null || !Guid.TryParse(claim.Value, out var userId))
            return TypedResults.Unauthorized();

        if (request.AssetIds == null || request.AssetIds.Count == 0)
            return TypedResults.Ok(new DownloadOptionsResponse());

        var scope = await visibility.GetScopeAsync(userId, ct);
        var fileNames = await dbContext.Assets
            .AsNoTracking()
            .Where(a => request.AssetIds.Contains(a.Id))
            .Where(scope.AssetPredicate())
            .Select(a => a.FileName)
            .ToListAsync(ct);

        return TypedResults.Ok(Summarize(fileNames));
    }

    internal static DownloadOptionsResponse Summarize(IReadOnlyCollection<string> fileNames)
    {
        var convertible = fileNames
            .Select(name => Path.GetExtension(name).ToLowerInvariant())
            .Where(AssetDownloadFormats.IsConvertible)
            .ToList();

        return new DownloadOptionsResponse
        {
            Total = fileNames.Count,
            ConvertibleCount = convertible.Count,
            Extensions = convertible
                .Select(extension => extension.TrimStart('.'))
                .Distinct()
                .Order()
                .ToList(),
        };
    }
}

public class DownloadOptionsRequest
{
    public List<Guid> AssetIds { get; set; } = new();
}

public class DownloadOptionsResponse
{
    /// <summary>Assets of the selection the user can read.</summary>
    public int Total { get; set; }

    /// <summary>How many of them are RAW or HEIC/HEIF.</summary>
    public int ConvertibleCount { get; set; }

    /// <summary>Their extensions, lower case and without the dot.</summary>
    public List<string> Extensions { get; set; } = new();
}
