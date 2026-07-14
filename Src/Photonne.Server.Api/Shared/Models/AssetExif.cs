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

    /// <summary>Nearest populated place to <see cref="Latitude"/>/<see cref="Longitude"/>,
    /// resolved offline at index time. Null when the photo has no GPS, when the
    /// dataset isn't baked into the image, or when the nearest city is further
    /// away than the lookup's cap (mid-ocean, deep desert).</summary>
    public Guid? PlaceId { get; set; }
    public Place? Place { get; set; }

    /// <summary>When the reverse-geocode ran. Null means "not attempted yet" and
    /// is what makes the backfill resumable — it is set even when the lookup
    /// finds nothing, so a fruitless coordinate isn't retried forever.</summary>
    public DateTime? GeocodedAt { get; set; }

    /// <summary>Distance to <see cref="Place"/> in metres. Kept because "Girona"
    /// means something different at 2 km than at 60 km, and a future UI may want
    /// to say "cerca de" — or drop the label entirely past a threshold.</summary>
    public int? GeocodeDistanceMeters { get; set; }
    
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

