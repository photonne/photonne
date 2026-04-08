using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

public class AssetExif
{
    public Guid Id { get; set; } = Guid.NewGuid();
    
    public Guid AssetId { get; set; }
    public Asset Asset { get; set; } = null!;
    
    // DateTimeOriginal (prioritario para fecha de toma)
    public DateTime? DateTimeOriginal { get; set; }
    
    // GPS coordinates
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public double? Altitude { get; set; }
    
    // Camera info
    [MaxLength(200)]
    public string? CameraMake { get; set; }
    
    [MaxLength(200)]
    public string? CameraModel { get; set; }
    
    // Image properties
    public int? Width { get; set; }
    public int? Height { get; set; }
    public int? Orientation { get; set; }
    
    // Other metadata
    [MaxLength(500)]
    public string? Description { get; set; }
    
    [MaxLength(1000)]
    public string? Keywords { get; set; }
    
    // ISO, Aperture, Shutter Speed
    public int? Iso { get; set; }
    public double? Aperture { get; set; }
    public double? ShutterSpeed { get; set; }
    public double? FocalLength { get; set; }
    
    public DateTime ExtractedAt { get; set; } = DateTime.UtcNow;
}

