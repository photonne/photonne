using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// Where <see cref="AssetExif.Latitude"/>/<see cref="AssetExif.Longitude"/> came
/// from. Mirrors <see cref="CaptureDateSource"/>, and for the same reason: an
/// inferred value must never be mistaken for a measured one, and a writer must
/// be able to refuse to clobber a better source with a worse one.
///
/// Values ascend by trust — the ordering IS the gate (see
/// <see cref="AssetExif.CanOverwriteLocation"/>), so never renumber them to suit
/// a reading order.
/// </summary>
public enum LocationSource
{
    /// <summary>No coordinates at all.</summary>
    None = 0,
    /// <summary>Copied from a geolocated photo taken minutes away by the same
    /// user. A good guess, not a measurement — see LocationInterpolationRunner.
    /// Ranks below Exif: a real fix always wins.</summary>
    Interpolated = 1,
    /// <summary>Read from the file's own GPS tags. The only source allowed to
    /// anchor an inference.</summary>
    Exif = 2,
    /// <summary>Placed by hand. Reserved: nothing writes this yet, but the map's
    /// eventual "fix this pin" belongs here and must outrank everything.</summary>
    Manual = 3
}

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

    /// <summary>
    /// Provenance of the coordinates above. Consumers that need a real fix — the
    /// map's pins, anything the user might act on — must filter on this;
    /// consumers working at city scale (trips, place names) can take
    /// <see cref="LocationSource.Interpolated"/> at face value.
    /// </summary>
    public LocationSource LocationSource { get; set; } = LocationSource.None;

    /// <summary>True for anything not measured by the camera. The gate that stops
    /// an inference from overwriting a real fix.</summary>
    public bool CanOverwriteLocation(LocationSource source) => source >= LocationSource;

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

