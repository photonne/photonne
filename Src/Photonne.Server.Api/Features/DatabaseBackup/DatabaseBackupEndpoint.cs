using System.Security.Claims;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.DatabaseBackup;

public class DatabaseBackupEndpoint : IEndpoint
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy    = JsonNamingPolicy.CamelCase,
        WriteIndented           = true,
        Converters              = { new JsonStringEnumConverter(), new VectorJsonConverter() },
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
            .WithDescription("Exports the full database as a JSON backup file. Pass ?includeMl=false to skip ML output (faces, embeddings, OCR, scenes, objects).");

        group.MapPost("restore", RestoreBackup)
            .WithName("RestoreDatabaseBackup")
            .WithDescription("Restores the database from a JSON backup file, replacing all existing data")
            .DisableAntiforgery()
            .WithMetadata(new DisableRequestSizeLimitAttribute())
            .WithMetadata(new RequestFormLimitsAttribute { MultipartBodyLengthLimit = long.MaxValue });
    }

    private static async Task<IResult> ExportBackup(
        [FromServices] DatabaseBackupService backupService,
        [FromServices] INotificationService notifications,
        [FromQuery] bool includeMl,
        HttpContext http,
        CancellationToken ct)
    {
        var triggeredBy = GetUserId(http);
        try
        {
            var document = await backupService.ExportAsync(includeMl, ct);
            var json     = JsonSerializer.SerializeToUtf8Bytes(document, JsonOptions);
            var suffix   = includeMl ? "full" : "essential";
            var fileName = $"photonne_backup_{suffix}_{DateTime.UtcNow:yyyyMMdd_HHmmss}.json";

            if (triggeredBy != Guid.Empty)
                await notifications.CreateAsync(triggeredBy, NotificationType.JobCompleted,
                    "Backup generado",
                    $"Se ha generado el backup ({suffix}, {FormatBytes(json.LongLength)}).");

            return Results.File(json, "application/json", fileName);
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            if (triggeredBy != Guid.Empty)
                await notifications.CreateAsync(triggeredBy, NotificationType.JobFailed,
                    "Error al generar backup",
                    $"No se pudo exportar la base de datos: {Truncate(ex.Message, 200)}");
            throw;
        }
    }

    private static async Task<IResult> RestoreBackup(
        [FromServices] DatabaseBackupService backupService,
        [FromServices] INotificationService notifications,
        IFormFile file,
        HttpContext http,
        CancellationToken ct)
    {
        var triggeredBy = GetUserId(http);

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
            if (triggeredBy != Guid.Empty)
                await notifications.CreateAsync(triggeredBy, NotificationType.JobFailed,
                    "Restauración fallida",
                    $"El archivo de backup no es válido: {Truncate(ex.Message, 200)}");
            return Results.BadRequest(new { error = $"El archivo no es un backup JSON válido: {ex.Message}" });
        }

        if (document == null)
            return Results.BadRequest(new { error = "No se pudo leer el archivo de copia de seguridad." });

        try
        {
            await backupService.RestoreAsync(document, ct);
        }
        catch (OperationCanceledException) { throw; }
        catch (Exception ex)
        {
            if (triggeredBy != Guid.Empty)
                await notifications.CreateAsync(triggeredBy, NotificationType.JobFailed,
                    "Restauración fallida",
                    $"Error durante la restauración: {Truncate(ex.Message, 200)}");
            throw;
        }

        if (triggeredBy != Guid.Empty)
            await notifications.CreateAsync(triggeredBy, NotificationType.JobCompleted,
                "Restauración completada",
                $"Restauradas {document.Users.Count} cuenta(s), {document.Assets.Count} asset(s) y {document.Albums.Count} álbum(es).");

        return Results.Ok(new
        {
            message = "Base de datos restaurada correctamente.",
            stats = new
            {
                users             = document.Users.Count,
                assets            = document.Assets.Count,
                albums            = document.Albums.Count,
                folders           = document.Folders.Count,
                externalLibraries = document.ExternalLibraries.Count,
                people            = document.People.Count,
                faces             = document.Faces.Count,
                embeddings        = document.AssetEmbeddings.Count,
                ocrLines          = document.AssetRecognizedTextLines.Count,
                includesMlData    = document.IncludesMlData && document.Version != "1.0",
            }
        });
    }

    private static Guid GetUserId(HttpContext http)
        => Guid.TryParse(http.User.FindFirst(ClaimTypes.NameIdentifier)?.Value, out var id) ? id : Guid.Empty;

    private static string Truncate(string s, int max)
        => string.IsNullOrEmpty(s) ? string.Empty : (s.Length <= max ? s : s[..max] + "…");

    private static string FormatBytes(long bytes)
    {
        if (bytes >= 1_073_741_824) return $"{bytes / 1_073_741_824.0:F1} GB";
        if (bytes >= 1_048_576) return $"{bytes / 1_048_576.0:F1} MB";
        if (bytes >= 1_024) return $"{bytes / 1_024.0:F1} KB";
        return $"{bytes} B";
    }
}
