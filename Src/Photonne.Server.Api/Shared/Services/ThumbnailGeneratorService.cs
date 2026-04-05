using Photonne.Server.Api.Shared.Models;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Processing;
using SixLabors.ImageSharp.Formats.Jpeg;
using SixLabors.ImageSharp.Formats.Webp;
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

    public ThumbnailGeneratorService(SettingsService settingsService)
    {
        _settingsService = settingsService;
        _thumbnailsBasePath = "/data/thumbnails";

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
                if (extension == ".heic" || extension == ".heif")
                {
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

                    Console.WriteLine($"[INFO] Extracting frame from video: {sourceFilePath} (Extension: {extension})");
                    
                    // Xabe.FFmpeg requires the output file extension to be .jpg for Snapshot
                    // Check if source file exists
                    if (!File.Exists(sourceFilePath))
                    {
                        Console.WriteLine($"[ERROR] Source video file not found: {sourceFilePath}");
                        return thumbnails;
                    }

                    // FFmpeg.Conversions.FromSnippet.Snapshot might be what I'm looking for or custom conversion
                    // If snapshot at 1s fails, try at 0s which is more likely to exist
                    IConversion conversion;
                    try 
                    {
                        Console.WriteLine($"[DEBUG] Starting FFmpeg snapshot at 1s for {assetId}: {sourceFilePath}");
                        conversion = await FFmpeg.Conversions.FromSnippet.Snapshot(sourceFilePath, tempFramePath, TimeSpan.FromSeconds(1));
                        await conversion.Start(cancellationToken);
                    }
                    catch (Exception ex1)
                    {
                        Console.WriteLine($"[WARNING] FFmpeg snapshot at 1s failed for {assetId}: {ex1.Message}. Trying at 0s");
                        try
                        {
                            conversion = await FFmpeg.Conversions.FromSnippet.Snapshot(sourceFilePath, tempFramePath, TimeSpan.FromSeconds(0));
                            await conversion.Start(cancellationToken);
                        }
                        catch (Exception ex2)
                        {
                            Console.WriteLine($"[ERROR] FFmpeg snapshot at 0s also failed for {assetId}: {ex2.Message}");
                            
                            // Fallback: try to use a more manual approach if snippet fails
                            // Sometimes snapshots fail if the video is too short or has issues at the start
                            Console.WriteLine($"[DEBUG] Trying manual FFmpeg command for {assetId}");
                            string ffmpegExe = OperatingSystem.IsWindows() ? "ffmpeg.exe" : "ffmpeg";
                            string ffmpegPath = string.IsNullOrEmpty(FFmpeg.ExecutablesPath) ? ffmpegExe : Path.Combine(FFmpeg.ExecutablesPath, ffmpegExe);
                            
                            var process = new System.Diagnostics.Process
                            {
                                StartInfo = new System.Diagnostics.ProcessStartInfo
                                {
                                    FileName = ffmpegPath,
                                    Arguments = $"-i \"{sourceFilePath}\" -ss 00:00:00.500 -vframes 1 \"{tempFramePath}\" -y",
                                    RedirectStandardOutput = true,
                                    RedirectStandardError = true,
                                    UseShellExecute = false,
                                    CreateNoWindow = true
                                }
                            };
                            process.Start();
                            await process.WaitForExitAsync(cancellationToken);
                        }
                    }
                    
                    Console.WriteLine($"[DEBUG] FFmpeg conversion finished for {assetId}");

                    if (File.Exists(tempFramePath))
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
                        Console.WriteLine($"[ERROR] Frame file was NOT created for {sourceFilePath} after FFmpeg execution. Checking FFmpeg.ExecutablesPath: {FFmpeg.ExecutablesPath}");
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[ERROR] Video snapshot failed for {sourceFilePath}: {ex.Message}");
                    Console.WriteLine($"[DEBUG] Exception Type: {ex.GetType().FullName}");
                    Console.WriteLine($"[DEBUG] StackTrace: {ex.StackTrace}");
                    if (ex.InnerException != null)
                    {
                        Console.WriteLine($"[DEBUG] Inner exception: {ex.InnerException.Message}");
                        Console.WriteLine($"[DEBUG] Inner StackTrace: {ex.InnerException.StackTrace}");
                    }
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

            return new AssetThumbnail
            {
                AssetId = assetId,
                Size = size,
                FilePath = thumbnailPath,
                Width = width,
                Height = height,
                FileSize = fileInfo.Length,
                Format = options.UseWebP ? "WebP" : "JPEG"
            };
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
        var imageExtensions = new[] { ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".gif", ".webp", ".heic", ".heif" };
        return imageExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }
    
    private bool IsVideoFile(string extension)
    {
        var videoExtensions = new[] { ".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".webm", ".m4v", ".3gp", ".mpeg", ".mpg", ".3g2", ".3gpp", ".amv", ".asf", ".f4v", ".m2v", ".mp2", ".mpe", ".mpv", ".ogv", ".qt", ".vob" };
        return videoExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }
}
