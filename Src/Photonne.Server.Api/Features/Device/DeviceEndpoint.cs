using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Features.Timeline;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Shared.Dtos;

namespace Photonne.Server.Api.Features.Device;

public class DeviceEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/device", Handle)
            .WithName("GetDeviceAssets")
            .WithTags("Assets")
            .WithDescription("Gets pending assets from the user's device directory")
            .RequireAuthorization()
            .AddOpenApiOperationTransformer((operation, context, ct) =>
            {
                operation.Summary = "Gets device assets";
                operation.Description = "Returns all pending assets from the user's device directory that are not yet synchronized";
                return Task.CompletedTask;
            });
    }

    private async Task<IResult> Handle(
        [FromServices] DirectoryScanner directoryScanner,
        [FromServices] SettingsService settingsService,
        ClaimsPrincipal user,
        CancellationToken cancellationToken)
    {
        try
        {
            if (!TryGetUserId(user, out var _))
            {
                return Results.Unauthorized();
            }

            var assetsPath = settingsService.GetAssetsPath();

            if (!Directory.Exists(assetsPath))
            {
                Console.WriteLine($"[DEVICE] Assets directory does not exist: {assetsPath}");
                return Results.Ok(new List<TimelineResponse>());
            }

            Console.WriteLine($"[DEVICE] Scanning assets directory: {assetsPath}");
            var userScannedFiles = (await directoryScanner.ScanDirectoryAsync(assetsPath, cancellationToken)).ToList();
            Console.WriteLine($"[DEVICE] Found {userScannedFiles.Count} files in assets directory");

            // Filter out files already in the managed library. With user + library
            // collapsed into a single path, this filter effectively returns nothing
            // — kept for explicitness / parity with the legacy behaviour.
            var indexedFileNames = new HashSet<string>(
                userScannedFiles.Select(f => f.FileName),
                StringComparer.OrdinalIgnoreCase);

            var deviceItems = new List<TimelineResponse>();
            int skippedIndexed = 0;
            
            foreach (var file in userScannedFiles)
            {
                // Solo mostrar archivos que NO están ya indexados
                if (!indexedFileNames.Contains(file.FileName))
                {
                    deviceItems.Add(new TimelineResponse
                    {
                        Id = Guid.Empty,
                        FileName = file.FileName,
                        FullPath = file.FullPath,
                        FileSize = file.FileSize,
                        FileCreatedAt = file.FileCreatedAt,
                        FileModifiedAt = file.FileModifiedAt,
                        Extension = file.Extension,
                        ScannedAt = DateTime.MinValue,
                        Type = file.AssetType.ToString(),
                        SyncStatus = AssetSyncStatus.Pending
                    });
                }
                else
                {
                    skippedIndexed++;
                }
            }

            Console.WriteLine($"[DEVICE] Returning {deviceItems.Count} pending assets (skipped {skippedIndexed} already indexed)");

            var orderedItems = deviceItems
                .OrderByDescending(a => a.FileModifiedAt)
                .ThenByDescending(a => a.FileName)
                .ToList();

            return Results.Ok(orderedItems);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[DEVICE ERROR] Error getting device assets: {ex.Message}");
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(userIdClaim?.Value, out userId);
    }
}
