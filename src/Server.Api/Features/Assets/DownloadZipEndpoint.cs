using System.IO.Compression;
using System.Security.Claims;
using ImageMagick;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Dtos;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.Assets;

public class DownloadZipEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapPost("/api/assets/download-zip", DownloadZip)
            .WithTags("Assets")
            .WithName("DownloadAssetsZip")
            .WithDescription("Downloads selected assets as a ZIP file")
            .Produces<Stream>(StatusCodes.Status200OK, "application/zip")
            .RequireAuthorization();
    }

    private static async Task<Results<PushStreamHttpResult, UnauthorizedHttpResult, BadRequest<ApiError>, ForbidHttpResult>> DownloadZip(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] SettingsService settingsService,
        [FromBody] DownloadZipRequest request,
        ClaimsPrincipal user,
        CancellationToken ct)
    {
        if (!TryGetUserId(user, out var userId))
            return TypedResults.Unauthorized();
        var username = user.GetUsername();
        if (string.IsNullOrEmpty(username)) return TypedResults.Unauthorized();

        if (request.AssetIds == null || request.AssetIds.Count == 0)
            return TypedResults.BadRequest(new ApiError("Debes seleccionar al menos un asset.", "no_assets_selected"));

        if (!AssetDownloadFormats.TryParse(request.Format, out var format))
            return TypedResults.BadRequest(new ApiError("format must be 'original' or 'jpeg'", "invalid_format"));

        var assets = await dbContext.Assets
            .Where(a => request.AssetIds.Contains(a.Id) && a.DeletedAt == null)
            .ToListAsync(ct);

        if (assets.Any(a => !IsAssetInUserRoot(a.FullPath, username)))
            return TypedResults.Forbid();

        var zipName = !string.IsNullOrWhiteSpace(request.FileName)
            ? $"{request.FileName}.zip"
            : "photonne_selection.zip";

        // Resolved before the first byte goes out: past that point an error
        // can no longer become a status code, only a cut connection.
        var files = new List<ZipSource>(assets.Count);
        foreach (var asset in assets)
            files.Add(new ZipSource(await settingsService.ResolvePhysicalPathAsync(asset.FullPath), asset.FileName));

        // Written into the response as it is built, so the server holds one
        // buffer instead of the whole ZIP and the download starts at once. The
        // size isn't known up front, so it goes out chunked, without a
        // Content-Length; a failure halfway aborts the connection, which the
        // clients see as a failed download rather than a short ZIP.
        return TypedResults.Stream(
            body => WriteZipAsync(body, files, format, ct),
            "application/zip",
            zipName);
    }

    /// <summary>
    /// Writes the ZIP of <paramref name="files"/> into <paramref name="output"/>,
    /// which needn't be seekable and is only written asynchronously: Kestrel
    /// refuses synchronous writes, and ZipArchive makes some even through its
    /// async API (the data descriptor that closes each entry). So the archive
    /// writes into <see cref="ForwardBuffer"/>, which is drained into the
    /// response after every chunk and holds no more than one at a time. Files
    /// gone from disk are left out.
    /// </summary>
    internal static async Task WriteZipAsync(
        Stream output,
        IReadOnlyList<ZipSource> files,
        AssetDownloadFormat format,
        CancellationToken ct)
    {
        var pending = new ForwardBuffer();
        var chunk = new byte[81920];
        using (var archive = new ZipArchive(pending, ZipArchiveMode.Create, leaveOpen: true))
        {
            var usedNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var file in files)
            {
                ct.ThrowIfCancellationRequested();

                if (!File.Exists(file.PhysicalPath))
                    continue;

                var jpegBytes = AssetDownloadFormats.ConvertsInZip(Path.GetExtension(file.PhysicalPath), format)
                    ? TryRenderJpeg(file.PhysicalPath)
                    : null;

                var entryName = GetUniqueEntryName(
                    jpegBytes != null ? AssetDownloadFormats.JpegFileName(file.FileName) : file.FileName,
                    usedNames);
                usedNames.Add(entryName);

                var entry = archive.CreateEntry(entryName, CompressionLevel.NoCompression);
                using (var entryStream = entry.Open())
                {
                    if (jpegBytes != null)
                    {
                        entryStream.Write(jpegBytes);
                    }
                    else
                    {
                        await using var fileStream = new FileStream(
                            file.PhysicalPath, FileMode.Open, FileAccess.Read, FileShare.Read,
                            bufferSize: chunk.Length, useAsync: true);
                        int read;
                        while ((read = await fileStream.ReadAsync(chunk, ct)) > 0)
                        {
                            entryStream.Write(chunk, 0, read);
                            await pending.DrainToAsync(output, ct);
                        }
                    }
                }
                await pending.DrainToAsync(output, ct);
            }
        }
        // The central directory, written when the archive closes.
        await pending.DrainToAsync(output, ct);
    }

    /// <summary>
    /// A forward-only stream that keeps what is written until
    /// <see cref="DrainToAsync"/> hands it on. Not seekable, so ZipArchive
    /// writes each entry in one pass with a data descriptor instead of
    /// seeking back to fill in sizes.
    /// </summary>
    private sealed class ForwardBuffer : Stream
    {
        private readonly MemoryStream _buffer = new();
        private long _position;

        public override bool CanRead => false;
        public override bool CanSeek => false;
        public override bool CanWrite => true;
        public override long Length => throw new NotSupportedException();
        public override long Position
        {
            get => _position;
            set => throw new NotSupportedException();
        }

        public override void Write(byte[] buffer, int offset, int count) => Write(buffer.AsSpan(offset, count));

        public override void Write(ReadOnlySpan<byte> buffer)
        {
            _buffer.Write(buffer);
            _position += buffer.Length;
        }

        public override void Flush() { }

        public async Task DrainToAsync(Stream output, CancellationToken ct)
        {
            if (_buffer.Length == 0)
                return;
            await output.WriteAsync(_buffer.GetBuffer().AsMemory(0, (int)_buffer.Length), ct);
            _buffer.SetLength(0);
        }

        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
    }

    /// <summary>
    /// Null when the file can't be converted: the ZIP then carries the
    /// original, which beats leaving the photo out.
    /// </summary>
    private static byte[]? TryRenderJpeg(string physicalPath)
    {
        try
        {
            return RawImageLoader.RenderJpeg(physicalPath);
        }
        catch (MagickException ex)
        {
            Console.WriteLine($"[ZIP] {Path.GetFileName(physicalPath)} could not be converted to JPEG, adding the original: {ex.Message}");
            return null;
        }
    }

    private static string GetUniqueEntryName(string fileName, HashSet<string> usedNames)
    {
        if (!usedNames.Contains(fileName))
            return fileName;

        var ext = Path.GetExtension(fileName);
        var name = Path.GetFileNameWithoutExtension(fileName);
        var counter = 1;
        string candidate;
        do
        {
            candidate = $"{name}_{counter++}{ext}";
        } while (usedNames.Contains(candidate));
        return candidate;
    }

    private static bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        userId = Guid.Empty;
        var claim = user.FindFirst(ClaimTypes.NameIdentifier);
        return claim != null && Guid.TryParse(claim.Value, out userId);
    }

    private static bool IsAssetInUserRoot(string assetPath, string username)
    {
        var normalized = assetPath.Replace('\\', '/');
        var virtualRoot = $"/assets/users/{username}/";
        return normalized.StartsWith(virtualRoot, StringComparison.OrdinalIgnoreCase)
            || normalized.Contains($"/users/{username}/", StringComparison.OrdinalIgnoreCase);
    }
}

/// <summary>One ZIP entry: where the file is on disk and the name it goes in under.</summary>
internal sealed record ZipSource(string PhysicalPath, string FileName);

public class DownloadZipRequest
{
    public List<Guid> AssetIds { get; set; } = new();
    public string? FileName { get; set; }

    /// <summary>
    /// "original" or "jpeg": what the RAW and HEIC/HEIF entries are stored as.
    /// Left out, every entry is the original.
    /// </summary>
    public string? Format { get; set; }
}
