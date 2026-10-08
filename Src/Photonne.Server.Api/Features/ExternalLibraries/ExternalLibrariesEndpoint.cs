using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Features.ExternalLibraries;

// ─── DTOs ────────────────────────────────────────────────────────────────────

public record CreateExternalLibraryRequest(
    string Name,
    string Path,
    bool ImportSubfolders = true,
    string? CronSchedule = null);

public record UpdateExternalLibraryRequest(
    string Name,
    string Path,
    bool ImportSubfolders,
    string? CronSchedule);

public record ExternalLibraryDto(
    Guid Id,
    string Name,
    string Path,
    bool ImportSubfolders,
    string? CronSchedule,
    DateTime? LastScannedAt,
    string LastScanStatus,
    int? LastScanAssetsFound,
    int? LastScanAssetsAdded,
    int? LastScanAssetsRemoved,
    int AssetCount,
    DateTime CreatedAt);

// ─── Endpoint ────────────────────────────────────────────────────────────────

public class ExternalLibrariesEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var readGroup = app.MapGroup("/api/libraries")
            .WithTags("External Libraries")
            .RequireAuthorization();

        readGroup.MapGet("", GetAll)
            .WithName("GetExternalLibraries")
            .WithDescription("Lists all external libraries visible to the current user");

        readGroup.MapGet("{id:guid}", GetById)
            .WithName("GetExternalLibrary")
            .WithDescription("Gets a single external library by ID if the user has access");

        var adminGroup = app.MapGroup("/api/libraries")
            .WithTags("External Libraries")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        adminGroup.MapPost("", Create)
            .WithName("CreateExternalLibrary")
            .WithDescription("Creates a new external library pointing to a server-side directory");

        adminGroup.MapPut("{id:guid}", Update)
            .WithName("UpdateExternalLibrary")
            .WithDescription("Updates an existing external library");

        adminGroup.MapDelete("{id:guid}", Delete)
            .WithName("DeleteExternalLibrary")
            .WithDescription("Deletes an external library. Assets are de-linked but NOT deleted from disk.");
    }

    // GET /api/libraries
    private static async Task<Results<Ok<List<ExternalLibraryDto>>, UnauthorizedHttpResult>> GetAll(
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return TypedResults.Unauthorized();

        var libraries = await db.ExternalLibraries
            .Where(l => l.OwnerId == userId.Value
                     || l.Permissions.Any(p => p.UserId == userId.Value && p.CanRead))
            .Select(l => new ExternalLibraryDto(
                l.Id,
                l.Name,
                l.Path,
                l.ImportSubfolders,
                l.CronSchedule,
                l.LastScannedAt,
                l.LastScanStatus.ToString(),
                l.LastScanAssetsFound,
                l.LastScanAssetsAdded,
                l.LastScanAssetsRemoved,
                l.Assets.Count(a => a.DeletedAt == null),
                l.CreatedAt))
            .ToListAsync(ct);

        return TypedResults.Ok(libraries);
    }

    // GET /api/libraries/{id}
    private static async Task<Results<Ok<ExternalLibraryDto>, NotFound<ApiError>, UnauthorizedHttpResult>> GetById(
        Guid id,
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return TypedResults.Unauthorized();

        var library = await db.ExternalLibraries
            .Where(l => l.Id == id
                && (l.OwnerId == userId.Value
                    || l.Permissions.Any(p => p.UserId == userId.Value && p.CanRead)))
            .Select(l => new ExternalLibraryDto(
                l.Id,
                l.Name,
                l.Path,
                l.ImportSubfolders,
                l.CronSchedule,
                l.LastScannedAt,
                l.LastScanStatus.ToString(),
                l.LastScanAssetsFound,
                l.LastScanAssetsAdded,
                l.LastScanAssetsRemoved,
                l.Assets.Count(a => a.DeletedAt == null),
                l.CreatedAt))
            .FirstOrDefaultAsync(ct);

        if (library is null) return TypedResults.NotFound(new ApiError($"External library {id} not found", "library_not_found"));
        return TypedResults.Ok(library);
    }

    // POST /api/libraries
    private static async Task<Results<Created<ExternalLibraryDto>, BadRequest<ApiError>, UnauthorizedHttpResult>> Create(
        [FromBody] CreateExternalLibraryRequest request,
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return TypedResults.Unauthorized();

        if (!Directory.Exists(request.Path))
            return TypedResults.BadRequest(new ApiError($"Directory does not exist on the server: {request.Path}", "directory_not_found"));

        if (!string.IsNullOrWhiteSpace(request.CronSchedule) &&
            Shared.Services.ExternalLibrarySchedulerService.ParseCronInterval(request.CronSchedule) == null)
        {
            return TypedResults.BadRequest(new ApiError(
                "Unsupported cron expression. Supported values: @hourly, @daily, @weekly, @monthly " +
                "(or their equivalent cron syntax).",
                "invalid_cron_schedule"));
        }

        var library = new ExternalLibrary
        {
            Name = request.Name.Trim(),
            Path = request.Path.TrimEnd('/', '\\'),
            ImportSubfolders = request.ImportSubfolders,
            CronSchedule = string.IsNullOrWhiteSpace(request.CronSchedule) ? null : request.CronSchedule.Trim(),
            OwnerId = userId.Value,
        };

        db.ExternalLibraries.Add(library);
        await db.SaveChangesAsync(ct);

        return TypedResults.Created($"/api/libraries/{library.Id}", new ExternalLibraryDto(
            library.Id, library.Name, library.Path, library.ImportSubfolders,
            library.CronSchedule, library.LastScannedAt, library.LastScanStatus.ToString(),
            library.LastScanAssetsFound, library.LastScanAssetsAdded, library.LastScanAssetsRemoved,
            0, library.CreatedAt));
    }

    // PUT /api/libraries/{id}
    private static async Task<Results<NoContent, BadRequest<ApiError>, NotFound<ApiError>, UnauthorizedHttpResult>> Update(
        Guid id,
        [FromBody] UpdateExternalLibraryRequest request,
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return TypedResults.Unauthorized();

        var library = await db.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == id && l.OwnerId == userId.Value, ct);

        if (library is null) return TypedResults.NotFound(new ApiError($"External library {id} not found", "library_not_found"));

        if (!Directory.Exists(request.Path))
            return TypedResults.BadRequest(new ApiError($"Directory does not exist on the server: {request.Path}", "directory_not_found"));

        if (!string.IsNullOrWhiteSpace(request.CronSchedule) &&
            Shared.Services.ExternalLibrarySchedulerService.ParseCronInterval(request.CronSchedule) == null)
        {
            return TypedResults.BadRequest(new ApiError(
                "Unsupported cron expression. Supported values: @hourly, @daily, @weekly, @monthly.",
                "invalid_cron_schedule"));
        }

        library.Name = request.Name.Trim();
        library.Path = request.Path.TrimEnd('/', '\\');
        library.ImportSubfolders = request.ImportSubfolders;
        library.CronSchedule = string.IsNullOrWhiteSpace(request.CronSchedule) ? null : request.CronSchedule.Trim();

        await db.SaveChangesAsync(ct);
        return TypedResults.NoContent();
    }

    // DELETE /api/libraries/{id}
    private static async Task<Results<NoContent, NotFound<ApiError>, UnauthorizedHttpResult>> Delete(
        Guid id,
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return TypedResults.Unauthorized();

        var library = await db.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == id && l.OwnerId == userId.Value, ct);

        if (library is null) return TypedResults.NotFound(new ApiError($"External library {id} not found", "library_not_found"));

        // De-link assets (FK is SetNull) — the library row deletion triggers this via EF
        db.ExternalLibraries.Remove(library);
        await db.SaveChangesAsync(ct);

        return TypedResults.NoContent();
    }

    private static Guid? GetUserId(HttpContext ctx)
    {
        var value = ctx.User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return Guid.TryParse(value, out var id) ? id : null;
    }
}
