using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
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
    private static async Task<IResult> GetAll(
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return Results.Unauthorized();

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

        return Results.Ok(libraries);
    }

    // GET /api/libraries/{id}
    private static async Task<IResult> GetById(
        Guid id,
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return Results.Unauthorized();

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

        return library is null ? Results.NotFound() : Results.Ok(library);
    }

    // POST /api/libraries
    private static async Task<IResult> Create(
        [FromBody] CreateExternalLibraryRequest request,
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return Results.Unauthorized();

        if (!Directory.Exists(request.Path))
            return Results.BadRequest($"Directory does not exist on the server: {request.Path}");

        if (!string.IsNullOrWhiteSpace(request.CronSchedule) &&
            Shared.Services.ExternalLibrarySchedulerService.ParseCronInterval(request.CronSchedule) == null)
        {
            return Results.BadRequest(
                "Unsupported cron expression. Supported values: @hourly, @daily, @weekly, @monthly " +
                "(or their equivalent cron syntax).");
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

        return Results.Created($"/api/libraries/{library.Id}", new ExternalLibraryDto(
            library.Id, library.Name, library.Path, library.ImportSubfolders,
            library.CronSchedule, library.LastScannedAt, library.LastScanStatus.ToString(),
            library.LastScanAssetsFound, library.LastScanAssetsAdded, library.LastScanAssetsRemoved,
            0, library.CreatedAt));
    }

    // PUT /api/libraries/{id}
    private static async Task<IResult> Update(
        Guid id,
        [FromBody] UpdateExternalLibraryRequest request,
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return Results.Unauthorized();

        var library = await db.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == id && l.OwnerId == userId.Value, ct);

        if (library is null) return Results.NotFound();

        if (!Directory.Exists(request.Path))
            return Results.BadRequest($"Directory does not exist on the server: {request.Path}");

        if (!string.IsNullOrWhiteSpace(request.CronSchedule) &&
            Shared.Services.ExternalLibrarySchedulerService.ParseCronInterval(request.CronSchedule) == null)
        {
            return Results.BadRequest(
                "Unsupported cron expression. Supported values: @hourly, @daily, @weekly, @monthly.");
        }

        library.Name = request.Name.Trim();
        library.Path = request.Path.TrimEnd('/', '\\');
        library.ImportSubfolders = request.ImportSubfolders;
        library.CronSchedule = string.IsNullOrWhiteSpace(request.CronSchedule) ? null : request.CronSchedule.Trim();

        await db.SaveChangesAsync(ct);
        return Results.NoContent();
    }

    // DELETE /api/libraries/{id}
    private static async Task<IResult> Delete(
        Guid id,
        [FromServices] ApplicationDbContext db,
        HttpContext ctx,
        CancellationToken ct)
    {
        var userId = GetUserId(ctx);
        if (userId == null) return Results.Unauthorized();

        var library = await db.ExternalLibraries
            .FirstOrDefaultAsync(l => l.Id == id && l.OwnerId == userId.Value, ct);

        if (library is null) return Results.NotFound();

        // De-link assets (FK is SetNull) — the library row deletion triggers this via EF
        db.ExternalLibraries.Remove(library);
        await db.SaveChangesAsync(ct);

        return Results.NoContent();
    }

    private static Guid? GetUserId(HttpContext ctx)
    {
        var value = ctx.User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        return Guid.TryParse(value, out var id) ? id : null;
    }
}
