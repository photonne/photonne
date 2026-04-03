using Photonne.Server.Api.Shared.Models;
using MetadataExtractor;
using MetadataExtractor.Formats.Exif;
using MetadataExtractor.Formats.Iptc;
using Xabe.FFmpeg;

namespace Photonne.Server.Api.Shared.Services;

public class ExifExtractorService
{
    /// <summary>
    /// Extracts EXIF/IPTC/XMP metadata from an image file
    /// </summary>
    public async Task<AssetExif?> ExtractExifAsync(string filePath, CancellationToken cancellationToken = default)
    {
        try
        {
            // Check if file is an image or video
            var extension = Path.GetExtension(filePath).ToLowerInvariant();
            if (!IsImageFile(extension) && !IsVideoFile(extension))
                return null;
            
            IEnumerable<MetadataExtractor.Directory> directories;
            try
            {
                directories = await Task.Run(() => ImageMetadataReader.ReadMetadata(filePath), cancellationToken);
            }
            catch (Exception ex)
            {
                // MetadataExtractor might fail for some video formats
                Console.WriteLine($"[DEBUG] Metadata extraction failed for {filePath}: {ex.Message}");
                return new AssetExif();
            }

            var exif = new AssetExif();

            foreach (var directory in directories)
            {
                ExtractDirectoryMetadata(directory, exif);
            }

            // Get image/video dimensions
            if (IsImageFile(extension))
            {
                try
                {
                    var imageInfo = await Task.Run(() => SixLabors.ImageSharp.Image.Identify(filePath), cancellationToken);
                    if (imageInfo != null)
                    {
                        exif.Width = imageInfo.Width;
                        exif.Height = imageInfo.Height;
                    }
                }
                catch
                {
                    // Ignore dimension extraction errors
                }
            }
            else if (IsVideoFile(extension))
            {
                try
                {
                    // Use FFprobe to get video dimensions
                    var mediaInfo = await FFmpeg.GetMediaInfo(filePath, cancellationToken);
                    var videoStream = mediaInfo.VideoStreams.FirstOrDefault();
                    if (videoStream != null)
                    {
                        exif.Width = videoStream.Width;
                        exif.Height = videoStream.Height;
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"[DEBUG] Video dimension extraction with FFprobe failed for {filePath}: {ex.Message}");
                }
            }

            return exif;
        }
        catch
        {
            // Return null if extraction fails
            return null;
        }
    }
    
    private bool IsVideoFile(string extension)
    {
        var videoExtensions = new[] { ".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".webm", ".m4v", ".3gp", ".mpeg", ".mpg", ".3g2", ".3gpp", ".amv", ".asf", ".f4v", ".m2v", ".mp2", ".mpe", ".mpv", ".ogv", ".qt", ".vob" };
        return videoExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }

    private void ExtractDirectoryMetadata(MetadataExtractor.Directory directory, AssetExif exif)
    {
        if (directory is ExifSubIfdDirectory exifDir)
        {
            // DateTimeOriginal (priority date)
            var dateTimeOriginal = exifDir.GetDescription(ExifDirectoryBase.TagDateTimeOriginal);
            if (!string.IsNullOrEmpty(dateTimeOriginal))
            {
                if (DateTime.TryParseExact(dateTimeOriginal, "yyyy:MM:dd HH:mm:ss", null, 
                    System.Globalization.DateTimeStyles.None, out var dateTime))
                {
                    exif.DateTimeOriginal = dateTime.ToUniversalTime();
                }
            }
            
            // Camera info
            exif.CameraMake = exifDir.GetDescription(ExifDirectoryBase.TagMake);
            exif.CameraModel = exifDir.GetDescription(ExifDirectoryBase.TagModel);
            
            // Camera settings
            var iso = exifDir.GetDescription(ExifDirectoryBase.TagIsoEquivalent);
            if (!string.IsNullOrEmpty(iso) && int.TryParse(iso, out var isoValue))
            {
                exif.Iso = isoValue;
            }
            
            var aperture = exifDir.GetDescription(ExifDirectoryBase.TagAperture);
            if (!string.IsNullOrEmpty(aperture) && double.TryParse(aperture, out var apertureValue))
            {
                exif.Aperture = apertureValue;
            }
            
            var shutterSpeed = exifDir.GetDescription(ExifDirectoryBase.TagShutterSpeed);
            if (!string.IsNullOrEmpty(shutterSpeed) && double.TryParse(shutterSpeed, out var shutterValue))
            {
                exif.ShutterSpeed = shutterValue;
            }
            
            var focalLength = exifDir.GetDescription(ExifDirectoryBase.TagFocalLength);
            if (!string.IsNullOrEmpty(focalLength) && double.TryParse(focalLength, out var focalValue))
            {
                exif.FocalLength = focalValue;
            }
            
            var orientation = exifDir.GetDescription(ExifDirectoryBase.TagOrientation);
            if (!string.IsNullOrEmpty(orientation) && int.TryParse(orientation, out var orientationValue))
            {
                exif.Orientation = orientationValue;
            }
        }
        else if (directory is GpsDirectory gpsDir)
        {
            // GPS coordinates
            if (gpsDir.TryGetGeoLocation(out var location))
            {
                exif.Latitude = location.Latitude;
                exif.Longitude = location.Longitude;
            }
            
            var altitude = gpsDir.GetDescription(GpsDirectory.TagAltitude);
            if (!string.IsNullOrEmpty(altitude) && double.TryParse(altitude, out var altitudeValue))
            {
                exif.Altitude = altitudeValue;
            }
        }
        else if (directory is IptcDirectory iptcDir)
        {
            // IPTC metadata
            exif.Description = iptcDir.GetDescription(IptcDirectory.TagCaption);
            if (!string.IsNullOrEmpty(exif.Description) && exif.Description.Length > 500)
            {
                exif.Description = exif.Description.Substring(0, 500);
            }
            
            var keywords = iptcDir.GetDescription(IptcDirectory.TagKeywords);
            if (!string.IsNullOrEmpty(keywords))
            {
                exif.Keywords = keywords;
                if (exif.Keywords.Length > 1000)
                {
                    exif.Keywords = exif.Keywords.Substring(0, 1000);
                }
            }
        }
    }
    
    private bool IsImageFile(string extension)
    {
        var imageExtensions = new[] { ".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".gif", ".webp", ".heic", ".heif" };
        return imageExtensions.Contains(extension, StringComparer.OrdinalIgnoreCase);
    }
}
