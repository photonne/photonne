using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Mvc;
using PhotoHub.Server.Api.Shared.Interfaces;

namespace PhotoHub.Server.Api.Features.DatabaseBackup;

public class DatabaseBackupEndpoint : IEndpoint
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy    = JsonNamingPolicy.CamelCase,
        WriteIndented           = true,
        Converters              = { new JsonStringEnumConverter() },
        DefaultIgnoreCondition  = JsonIgnoreCondition.WhenWritingNull,
        ReferenceHandler        = ReferenceHandler.IgnoreCycles
    };

    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/admin/database")
            .WithTags("Admin")
            .RequireAuthorization(policy => policy.RequireRole("Admin"));

        group.MapGet("backup", ExportBackup)
            .WithName("ExportDatabaseBackup")
            .WithDescription("Exports the full database as a JSON backup file");

        group.MapPost("restore", RestoreBackup)
            .WithName("RestoreDatabaseBackup")
            .WithDescription("Restores the database from a JSON backup file, replacing all existing data")
            .DisableAntiforgery();
    }

    private static async Task<IResult> ExportBackup(
        [FromServices] DatabaseBackupService backupService,
        CancellationToken ct)
    {
        var document = await backupService.ExportAsync(ct);
        var json     = JsonSerializer.SerializeToUtf8Bytes(document, JsonOptions);
        var fileName = $"photohub_backup_{DateTime.UtcNow:yyyyMMdd_HHmmss}.json";

        return Results.File(json, "application/json", fileName);
    }

    private static async Task<IResult> RestoreBackup(
        [FromServices] DatabaseBackupService backupService,
        IFormFile file,
        CancellationToken ct)
    {
        if (file == null || file.Length == 0)
            return Results.BadRequest(new { error = "No se ha proporcionado ningún archivo de copia de seguridad." });

        if (!file.FileName.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
            return Results.BadRequest(new { error = "El archivo debe ser un backup JSON válido (.json)." });

        BackupDocument? document;
        try
        {
            await using var stream = file.OpenReadStream();
            document = await JsonSerializer.DeserializeAsync<BackupDocument>(stream, JsonOptions, ct);
        }
        catch (JsonException ex)
        {
            return Results.BadRequest(new { error = $"El archivo no es un backup JSON válido: {ex.Message}" });
        }

        if (document == null)
            return Results.BadRequest(new { error = "No se pudo leer el archivo de copia de seguridad." });

        await backupService.RestoreAsync(document, ct);

        return Results.Ok(new
        {
            message = "Base de datos restaurada correctamente.",
            stats = new
            {
                users             = document.Users.Count,
                assets            = document.Assets.Count,
                albums            = document.Albums.Count,
                folders           = document.Folders.Count,
                externalLibraries = document.ExternalLibraries.Count
            }
        });
    }
}
