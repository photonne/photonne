using System.Globalization;
using Photonne.Server.Api.Shared.Models;
using Microsoft.Extensions.Configuration;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Processing;
using SixLabors.ImageSharp.Formats.Jpeg;
using SixLabors.ImageSharp.Formats.Webp;
using SixLabors.ImageSharp.PixelFormats;
using Xabe.FFmpeg;
using ImageMagick;

namespace Photonne.Server.Api.Shared.Services;

public class ThumbnailGeneratorService
{
    private readonly string _thumbnailsBasePath;
    private readonly SettingsService _settingsService;

    public const string KeyThumbnailFormat = "TaskSettings.ThumbnailFormat";
    public const string KeyQualitySmall = "TaskSettings.ThumbnailQuality.Small";
    public const string KeyQualityMedium = "TaskSettings.ThumbnailQuality.Medium";
    public const string KeyQualityLarge = "TaskSettings.ThumbnailQuality.Large";
    public const string DefaultThumbnailsPath = "/data/thumbnails";

    private record ThumbnailOptions(bool UseWebP, int QualitySmall, int QualityMedium, int QualityLarge)
    {
        public int GetQuality(ThumbnailSize size) => size switch
        {
            ThumbnailSize.Small => QualitySmall,
            ThumbnailSize.Medium => QualityMedium,
            ThumbnailSize.Large => QualityLarge,
            _ => QualityMedium
        };
    }

    private ThumbnailOptions? _cachedOptions;
    private readonly SemaphoreSlim _optionsLock = new SemaphoreSlim(1, 1);

    private async Task<ThumbnailOptions> GetOptionsAsync()
    {
        if (_cachedOptions is not null) return _cachedOptions;
        await _optionsLock.WaitAsync();
        try
        {
            if (_cachedOptions is not null) return _cachedOptions;
            var format = await _settingsService.GetSettingAsync(KeyThumbnailFormat, Guid.Empty, "JPEG");
            _cachedOptions = new ThumbnailOptions(
                UseWebP: format.Equals("WebP", StringComparison.OrdinalIgnoreCase),
                QualitySmall: ParseQuality(await _settingsService.GetSettingAsync(KeyQualitySmall, Guid.Empty, "75"), 75),
                QualityMedium: ParseQuality(await _settingsService.GetSettingAsync(KeyQualityMedium, Guid.Empty, "80"), 80),
                QualityLarge: ParseQuality(await _settingsService.GetSettingAsync(KeyQualityLarge, Guid.Empty, "85"), 85));
            return _cachedOptions;
        }
        finally
        {
            _optionsLock.Release();
        }
    }

    public ThumbnailGeneratorService(SettingsService settingsService, IConfiguration? configuration = null)
    {
        _settingsService = settingsService;
        _thumbnailsBasePath = configuration?["ThumbnailsPath"] ?? DefaultThumbnailsPath;

        if (!Directory.Exists(_thumbnailsBasePath))
        {
            Directory.CreateDirectory(_thumbnailsBasePath);
        }
    }

    private static int ParseQuality(string value, int defaultValue)
    {
        if (!int.TryParse(value, out var n)) return defaultValue;
        return Math.Clamp(n, 1, 100);
    }
    
    /// <summary>
    /// Generates thumbnails for an asset (Small, Medium, Large)
    /// </summary>
    public async Task<List<AssetThumbnail>> GenerateThumbnailsAsync(
        string sourceFilePath,
        Guid assetId,
        CancellationToken cancellationToken = default)
    {
        var thumbnails = new List<AssetThumbnail>();

        var options = await GetOptionsAsync();

        try
        {
            var extension = Path.GetExtension(sourceFilePath).ToLowerInvariant();

            if (IsImageFile(extension))
            {
                if (IsRawOrHeicFile(extension))
                {
                    // RAW and HEIC/HEIF formats require ImageMagick (not supported by ImageSharp)
                    await GenerateHeicThumbnailsAsync(sourceFilePath, assetId, thumbnails, options, cancellationToken);
                }
                else
                {
                    using var sourceImage = await Image.LoadAsync(sourceFilePath, cancellationToken);

                    // Get EXIF orientation if available
                    var orientation = GetImageOrientation(sourceImage);

                    // Generate thumbnails for each size
                    var sizes = new[] { ThumbnailSize.Small, ThumbnailSize.Medium, ThumbnailSize.Large };

                    foreach (var size in sizes)
                    {
                        var thumbnail = await GenerateThumbnailAsync(
                            sourceImage,
                            assetId,
                            size,
                            orientation,
                            options,
                            cancellationToken);
                        
                        if (thumbnail != null)
                        {
                            thumbnails.Add(thumbnail);
                        }
                    }
                }
            }
            else if (IsVideoFile(extension))
            {
                // Generate a temporary frame from video
                var tempFramePath = Path.Combine(Path.GetTempPath(), $"{Guid.NewGuid()}.jpg");
                try
                {
                    // Ensure FFmpeg path is set if we have it in env or default
                    await EnsureFFmpegConfigured();

                    if (!File.Exists(sourceFilePath))
                    {
                        Console.WriteLine($"[ERROR] Source video file not found: {sourceFilePath}");
                        return thumbnails;
                    }

                    Console.WriteLine($"[INFO] Extracting frame from video: {sourceFilePath} (Extension: {extension})");

                    var extracted = await ExtractVideoFrameAsync(sourceFilePath, tempFramePath, assetId, cancellationToken);

                    if (extracted && File.Exists(tempFramePath))
                    {
                        var frameInfo = new FileInfo(tempFramePath);
                        Console.WriteLine($"[INFO] Frame extracted successfully, size: {frameInfo.Length} bytes, generating thumbnails for {assetId}");

                        using var sourceImage = await Image.LoadAsync(tempFramePath, cancellationToken);

                        var sizes = new[] { ThumbnailSize.Small, ThumbnailSize.Medium, ThumbnailSize.Large };
                        foreach (var size in sizes)
                        {
                            var thumbnail = await GenerateThumbnailAsync(
                                sourceImage,
                                assetId,
                                size,
                                1, // FFmpeg snapshot usually doesn't need orientation fix as it processes the stream
                                options,
                                cancellationToken);

                            if (thumbnail != null)
                            {
                                thumbnails.Add(thumbnail);
                            }
                        }
                    }
                    else
                    {
                        Console.WriteLine($"[ERROR] Could not extract any frame from video {sourceFilePath} for {assetId}. FFmpeg.ExecutablesPath: {FFmpeg.ExecutablesPath}");
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[ERROR] Video thumbnail generation failed for {sourceFilePath}: {ex.Message}");
                }
                finally
                {
                    if (File.Exists(tempFramePath))
                    {
                        File.Delete(tempFramePath);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            // Return partial results if some thumbnails failed
            Console.WriteLine($"[ERROR] Thumbnail generation failed for {sourceFilePath}: {ex.Message}");
        }
        
        return thumbnails;
    }

    private async Task EnsureFFmpegConfigured()
    {
        try 
        {
            var ffmpegPath = FFmpeg.ExecutablesPath;
            if (string.IsNullOrEmpty(ffmpegPath))
            {
                Console.WriteLine("[WARNING] FFmpeg ExecutablesPath is not set. Trying to find it in PATH.");
                // If not set, FFmpeg might rely on PATH, but we should log it
                return;
            }
            
            // Check if ffmpeg executable exists in the path
            var ffmpegExe = OperatingSystem.IsWindows() ? "ffmpeg.exe" : "ffmpeg";
            var ffprobeExe = OperatingSystem.IsWindows() ? "ffprobe.exe" : "ffprobe";
            
            var ffmpegFullPath = Path.Combine(ffmpegPath, ffmpegExe);
            var ffprobeFullPath = Path.Combine(ffmpegPath, ffprobeExe);
            
            if (!File.Exists(ffmpegFullPath))
            {
                Console.WriteLine($"[ERROR] FFmpeg executable NOT found at: {ffmpegFullPath}");
            }
            else
            {
                Console.WriteLine($"[DEBUG] FFmpeg found at: {ffmpegFullPath}");
            }

            if (!File.Exists(ffprobeFullPath))
            {
                Console.WriteLine($"[ERROR] FFprobe executable NOT found at: {ffprobeFullPath}");
            }
            else
            {
                Console.WriteLine($"[DEBUG] FFprobe found at: {ffprobeFullPath}");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] Error checking FFmpeg configuration: {ex.Message}");
        }
    }

    /// <summary>
    /// Extracts a single representative frame from a video into <paramref name="outputPath"/>.
    /// Probes the duration so the seek lands on a frame that actually exists, then walks a
    /// small ladder of timestamps — a fixed 1s offset overshoots sub-second clips and often
    /// lands on a black leader frame in others. Returns true once a non-empty frame is written.
    /// </summary>
    private async Task<bool> ExtractVideoFrameAsync(string sourceFilePath, string outputPath, Guid assetId, CancellationToken cancellationToken)
    {
        var seekPoints = await ComputeSeekPointsAsync(sourceFilePath, cancellationToken);

        foreach (var seconds in seekPoints)
        {
            var ok = await TryExtractFrameAtAsync(sourceFilePath, outputPath, seconds, cancellationToken);
            if (ok && File.Exists(outputPath) && new FileInfo(outputPath).Length > 0)
            {
                Console.WriteLine($"[DEBUG] Extracted video frame at {seconds:0.###}s for {assetId}");
                return true;
            }

            // Clear an empty/partial output before the next attempt so a stale
            // zero-byte file doesn't get mistaken for a successful extraction.
            if (File.Exists(outputPath))
            {
                File.Delete(outputPath);
            }
        }

        return false;
    }

    /// <summary>
    /// Returns candidate seek timestamps (seconds) ordered by preference. When the duration
    /// is known we prefer a frame ~10% in (skipping intros/black leaders) and widen to a few
    /// fractions so a decode failure at one point can retry elsewhere. Falls back to a fixed
    /// ladder when probing fails (e.g. ffprobe missing or an unparsable container).
    /// </summary>
    private static async Task<IReadOnlyList<double>> ComputeSeekPointsAsync(string sourceFilePath, CancellationToken cancellationToken)
    {
        try
        {
            var info = await FFmpeg.GetMediaInfo(sourceFilePath, cancellationToken);
            var duration = info.Duration.TotalSeconds;
            if (duration > 0)
            {
                var tenPercent = Math.Min(duration * 0.1, 3.0);
                return new[] { tenPercent, duration * 0.25, duration * 0.5, 0.0 }
                    .Select(s => Math.Clamp(s, 0.0, Math.Max(0.0, duration - 0.05)))
                    .Distinct()
                    .ToList();
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[WARNING] Could not probe video duration for {sourceFilePath}: {ex.Message}. Falling back to fixed seek ladder.");
        }

        return new[] { 1.0, 0.5, 0.0 };
    }

    /// <summary>
    /// Runs ffmpeg to grab a single frame at <paramref name="seconds"/>. Uses fast (keyframe)
    /// seeking with <c>-ss</c> placed before <c>-i</c>: thumbnails don't need frame-accurate
    /// seeking, and this avoids decoding the whole clip up to the offset.
    /// </summary>
    private static async Task<bool> TryExtractFrameAtAsync(string sourceFilePath, string outputPath, double seconds, CancellationToken cancellationToken)
    {
        try
        {
            var ffmpegExe = OperatingSystem.IsWindows() ? "ffmpeg.exe" : "ffmpeg";
            var ffmpegPath = string.IsNullOrEmpty(FFmpeg.ExecutablesPath)
                ? ffmpegExe
                : Path.Combine(FFmpeg.ExecutablesPath, ffmpegExe);

            var timestamp = seconds.ToString("0.000", CultureInfo.InvariantCulture);
            var arguments = $"-ss {timestamp} -i \"{sourceFilePath}\" -frames:v 1 -an -y \"{outputPath}\"";

            using var process = new System.Diagnostics.Process
            {
                StartInfo = new System.Diagnostics.ProcessStartInfo
                {
                    FileName = ffmpegPath,
                    Arguments = arguments,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                }
            };

            process.Start();
            // Drain stderr so a chatty ffmpeg can't deadlock on a full pipe buffer.
            var stderr = process.StandardError.ReadToEndAsync(cancellationToken);
            await process.WaitForExitAsync(cancellationToken);
            await stderr;

            if (process.ExitCode != 0)
            {
                Console.WriteLine($"[WARNING] ffmpeg exited {process.ExitCode} extracting frame at {seconds:0.###}s from {sourceFilePath}");
                return false;
            }

            return true;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[WARNING] ffmpeg frame extraction at {seconds:0.###}s failed for {sourceFilePath}: {ex.Message}");
            return false;
        }
    }

    private async Task GenerateHeicThumbnailsAsync(string sourceFilePath, Guid assetId, List<AssetThumbnail> thumbnails, ThumbnailOptions options, CancellationToken cancellationToken)
    {
        try
        {
            using var image = new MagickImage(sourceFilePath);

            var sizes = new[] { ThumbnailSize.Small, ThumbnailSize.Medium, ThumbnailSize.Large };
            foreach (var size in sizes)
            {
                var targetSize = (int)size;
                var thumbnailPath = GetThumbnailPath(assetId, size, options.UseWebP);
                var thumbnailDir = Path.GetDirectoryName(thumbnailPath);

                if (!string.IsNullOrEmpty(thumbnailDir) && !Directory.Exists(thumbnailDir))
                {
                    Directory.CreateDirectory(thumbnailDir);
                }

                using var thumb = image.Clone();
                thumb.AutoOrient();
                thumb.Thumbnail((uint)targetSize, (uint)targetSize);
                thumb.Format = options.UseWebP ? MagickFormat.WebP : MagickFormat.Jpeg;
                thumb.Quality = (uint)options.GetQuality(size);

                await thumb.WriteAsync(thumbnailPath, cancellationToken);

                var fileInfo = new FileInfo(thumbnailPath);
                thumbnails.Add(new AssetThumbnail
                {
                    AssetId = assetId,
                    Size = size,
                    FilePath = thumbnailPath,
                    Width = (int)thumb.Width,
                    Height = (int)thumb.Height,
                    FileSize = fileInfo.Length,
                    Format = options.UseWebP ? "WebP" : "JPEG"
                });
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[ERROR] HEIC thumbnail generation failed for {sourceFilePath}: {ex.Message}");
        }
    }
    
    private async Task<AssetThumbnail?> GenerateThumbnailAsync(
        Image sourceImage,
        Guid assetId,
        ThumbnailSize size,
        int orientation,
        ThumbnailOptions options,
        CancellationToken cancellationToken)
    {
        try
        {
            var targetSize = (int)size;

            // Calculate dimensions maintaining aspect ratio
            var (width, height) = CalculateThumbnailDimensions(
                sourceImage.Width,
                sourceImage.Height,
                targetSize,
                orientation);

            // Create thumbnail
            using var thumbnail = sourceImage.Clone(ctx =>
            {
                // Apply orientation transformation if needed
                if (orientation != 1)
                {
                    ApplyOrientation(ctx, orientation);
                }

                // Resize maintaining aspect ratio
                ctx.Resize(new ResizeOptions
                {
                    Size = new Size(width, height),
                    Mode = ResizeMode.Max,
                    Sampler = KnownResamplers.Lanczos3
                });
            });

            // Save thumbnail
            var thumbnailPath = GetThumbnailPath(assetId, size, options.UseWebP);
            var thumbnailDir = Path.GetDirectoryName(thumbnailPath);
            if (!string.IsNullOrEmpty(thumbnailDir) && !Directory.Exists(thumbnailDir))
            {
                Directory.CreateDirectory(thumbnailDir);
            }

            var quality = options.GetQuality(size);
            if (options.UseWebP)
                await thumbnail.SaveAsync(thumbnailPath, new WebpEncoder { Quality = quality }, cancellationToken);
            else
                await thumbnail.SaveAsync(thumbnailPath, new JpegEncoder { Quality = quality }, cancellationToken);

            var fileInfo = new FileInfo(thumbnailPath);

            // Extract dominant color from the small thumbnail — fast 16×16 pixel average
            string? dominantColor = null;
            if (size == ThumbnailSize.Small)
                dominantColor = ExtractDominantColor(thumbnail);

            return new AssetThumbnail
            {
                AssetId = assetId,
                Size = size,
                FilePath = thumbnailPath,
                Width = width,
                Height = height,
                FileSize = fileInfo.Length,
                Format = options.UseWebP ? "WebP" : "JPEG",
                DominantColor = dominantColor
            };
        }
        catch
        {
            return null;
        }
    }
    
    /// <summary>
    /// Computes the dominant (average) color of an image by downsampling to 16×16 and averaging all pixels.
    /// Returns a CSS hex string like "#a3b2c1".
    /// </summary>
    private static string? ExtractDominantColor(Image image)
    {
        try
        {
            using var small = image.CloneAs<Rgba32>();
            small.Mutate(ctx => ctx.Resize(16, 16, KnownResamplers.Box));

            long r = 0, g = 0, b = 0, count = 0;
            small.ProcessPixelRows(accessor =>
            {
                for (int y = 0; y < accessor.Height; y++)
                {
                    var row = accessor.GetRowSpan(y);
                    for (int x = 0; x < row.Length; x++)
                    {
                        r += row[x].R;
                        g += row[x].G;
                        b += row[x].B;
                        count++;
                    }
                }
            });

            if (count == 0) return null;
            return $"#{(byte)(r / count):X2}{(byte)(g / count):X2}{(byte)(b / count):X2}";
        }
        catch
        {
            return null;
        }
    }

    private (int width, int height) CalculateThumbnailDimensions(
        int originalWidth, 
        int originalHeight, 
        int targetSize,
        int orientation)
    {
        // Handle orientation (swap dimensions if rotated)
        var width = originalWidth;
        var height = originalHeight;
        
        if (orientation >= 5 && orientation <= 8)
        {
            // 90 or 270 degree rotation - swap dimensions
            (width, height) = (height, width);
        }
        
        // Calculate aspect ratio
        var aspectRatio = (double)width / height;
        
        int newWidth, newHeight;
        
        if (width > height)
        {
            newWidth = targetSize;
            newHeight = (int)(targetSize / aspectRatio);
        }
        else
        {
            newHeight = targetSize;
            newWidth = (int)(targetSize * aspectRatio);
        }
        
        return (newWidth, newHeight);
    }
    
    private int GetImageOrientation(Image image)
    {
        try
        {
            // Try to get orientation from EXIF metadata
            if (image.Metadata.ExifProfile != null)
            {
                var orientationTag = image.Metadata.ExifProfile.Values
                    .FirstOrDefault(v => v.Tag == SixLabors.ImageSharp.Metadata.Profiles.Exif.ExifTag.Orientation);
                
                if (orientationTag != null && orientationTag.GetValue() is ushort orientationValue)
                {
                    return orientationValue;
                }
            }
        }
        catch
        {
            // Ignore errors
        }
        
        return 1; // Default: no rotation
    }
    
    private void ApplyOrientation(IImageProcessingContext ctx, int orientation)
    {
        // Apply rotation/transformation based on EXIF orientation
        switch (orientation)
        {
            case 3: // 180 degrees
                ctx.Rotate(180);
                break;
            case 6: // 90 degrees clockwise
                ctx.Rotate(90);
                break;
            case 8: // 90 degrees counter-clockwise
                ctx.Rotate(-90);
                break;
            case 2: // Flip horizontal
                ctx.Flip(FlipMode.Horizontal);
                break;
            case 4: // Flip vertical
                ctx.Flip(FlipMode.Vertical);
                break;
            case 5: // Rotate 90 CW and flip horizontal
                ctx.Rotate(90).Flip(FlipMode.Horizontal);
                break;
            case 7: // Rotate 90 CCW and flip horizontal
                ctx.Rotate(-90).Flip(FlipMode.Horizontal);
                break;
        }
    }
    
    private string GetThumbnailPath(Guid assetId, ThumbnailSize size, bool useWebP = false)
    {
        var sizeName = size.ToString().ToLowerInvariant();
        var ext = useWebP ? "webp" : "jpg";
        return Path.Combine(_thumbnailsBasePath, assetId.ToString(), $"{sizeName}.{ext}");
    }

    /// <summary>
    /// Checks if a thumbnail file exists physically on disk (either JPEG or WebP)
    /// </summary>
    public bool ThumbnailExists(Guid assetId, ThumbnailSize size)
    {
        return File.Exists(GetThumbnailPath(assetId, size, false))
            || File.Exists(GetThumbnailPath(assetId, size, true));
    }
    
    /// <summary>
    /// Verifies all thumbnails for an asset exist, returns missing sizes
    /// </summary>
    public List<ThumbnailSize> GetMissingThumbnailSizes(Guid assetId)
    {
        var sizes = new[] { ThumbnailSize.Small, ThumbnailSize.Medium, ThumbnailSize.Large };
        var missing = new List<ThumbnailSize>();
        
        foreach (var size in sizes)
        {
            if (!ThumbnailExists(assetId, size))
            {
                missing.Add(size);
            }
        }
        
        return missing;
    }
    
    private bool IsImageFile(string extension)
    {
        var imageExtensions = new[] { ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".gif", ".webp", ".heic", ".heif",
                                      ".raw", ".cr2", ".cr3", ".nef", ".arw", ".dng", ".orf", ".rw2", ".pef", ".raf", ".srw" };
        return imageExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }

    private static bool IsRawOrHeicFile(string extension)
    {
        var rawExtensions = new[] { ".heic", ".heif",
                                    ".raw", ".cr2", ".cr3", ".nef", ".arw", ".dng", ".orf", ".rw2", ".pef", ".raf", ".srw" };
        return rawExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }
    
    private bool IsVideoFile(string extension)
    {
        var videoExtensions = new[] { ".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".webm", ".m4v", ".3gp", ".mpeg", ".mpg", ".3g2", ".3gpp", ".amv", ".asf", ".f4v", ".m2v", ".mp2", ".mpe", ".mpv", ".ogv", ".qt", ".vob" };
        return videoExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }
}
