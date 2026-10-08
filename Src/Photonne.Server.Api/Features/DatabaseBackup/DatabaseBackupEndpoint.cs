using System.Security.Claims;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Shared.Dtos;
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
            .Produces<Stream>(StatusCodes.Status200OK, "application/json")
            .WithName("ExportDatabaseBackup")
            .WithDescription("Exports the database as a JSON backup. ?level=config (only settings/users/folders/ext-libs/permissions), essential (config + library), full (config + library + ML).");

        group.MapPost("restore", RestoreBackup)
            .WithName("RestoreDatabaseBackup")
            .WithDescription("Restores the database from a JSON backup file, replacing all existing data")
            .DisableAntiforgery()
            .WithMetadata(new DisableRequestSizeLimitAttribute())
            .WithMetadata(new RequestFormLimitsAttribute { MultipartBodyLengthLimit = long.MaxValue });
    }

    private static async Task<FileContentHttpResult> ExportBackup(
        [FromServices] DatabaseBackupService backupService,
        [FromServices] INotificationService notifications,
        [FromQuery] string? level,
        HttpContext http,
        CancellationToken ct)
    {
        var triggeredBy = GetUserId(http);
        var (selection, label) = ParseLevel(level);

        try
        {
            var document = await backupService.ExportAsync(selection, ct);
            var json     = JsonSerializer.SerializeToUtf8Bytes(document, JsonOptions);
            var fileName = $"photonne_backup_{label}_{DateTime.UtcNow:yyyyMMdd_HHmmss}.json";

            if (triggeredBy != Guid.Empty)
                await notifications.CreateAsync(triggeredBy, NotificationType.JobCompleted,
                    "Backup generado",
                    $"Se ha generado el backup ({label}, {FormatBytes(json.LongLength)}).");

            return TypedResults.File(json, "application/json", fileName);
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

    private static (BackupSelection Selection, string Label) ParseLevel(string? level)
        => (level?.ToLowerInvariant()) switch
        {
            "config"    => (BackupSelection.ConfigOnly, "config"),
            "full"      => (BackupSelection.Full,       "full"),
            _           => (BackupSelection.Essential,  "essential"),
        };

    private static async Task<Results<Ok<RestoreBackupResponse>, BadRequest<ApiError>>> RestoreBackup(
        [FromServices] DatabaseBackupService backupService,
        [FromServices] INotificationService notifications,
        IFormFile file,
        HttpContext http,
        CancellationToken ct)
    {
        var triggeredBy = GetUserId(http);

        if (file == null || file.Length == 0)
            return TypedResults.BadRequest(new ApiError("No se ha proporcionado ningún archivo de copia de seguridad.", "backup_file_missing"));

        if (!file.FileName.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
            return TypedResults.BadRequest(new ApiError("El archivo debe ser un backup JSON válido (.json).", "invalid_backup_file"));

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
            return TypedResults.BadRequest(new ApiError($"El archivo no es un backup JSON válido: {ex.Message}", "invalid_backup_file"));
        }

        if (document == null)
            return TypedResults.BadRequest(new ApiError("No se pudo leer el archivo de copia de seguridad.", "invalid_backup_file"));

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

        var includesLibrary = document.Version == "1.0" || document.IncludesLibrary;
        var includesMlData  = document.Version != "1.0" && document.IncludesLibrary && document.IncludesMlData;

        if (triggeredBy != Guid.Empty)
            await notifications.CreateAsync(triggeredBy, NotificationType.JobCompleted,
                "Restauración completada",
                $"Restauradas {document.Users.Count} cuenta(s), {document.Assets.Count} asset(s) y {document.Albums.Count} álbum(es).");

        return TypedResults.Ok(new RestoreBackupResponse(
            "Base de datos restaurada correctamente.",
            new RestoreBackupStats(
                Users:             document.Users.Count,
                Assets:            document.Assets.Count,
                Albums:            document.Albums.Count,
                Folders:           document.Folders.Count,
                ExternalLibraries: document.ExternalLibraries.Count,
                People:            document.People.Count,
                Faces:             document.Faces.Count,
                Embeddings:        document.AssetEmbeddings.Count,
                OcrLines:          document.AssetRecognizedTextLines.Count,
                IncludesConfig:    true,
                IncludesLibrary:   includesLibrary,
                IncludesMlData:    includesMlData)));
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

public sealed record RestoreBackupResponse(string Message, RestoreBackupStats Stats);

public sealed record RestoreBackupStats(
    int Users,
    int Assets,
    int Albums,
    int Folders,
    int ExternalLibraries,
    int People,
    int Faces,
    int Embeddings,
    int OcrLines,
    bool IncludesConfig,
    bool IncludesLibrary,
    bool IncludesMlData);
