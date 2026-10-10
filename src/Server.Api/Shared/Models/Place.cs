using System.ComponentModel.DataAnnotations;

namespace Photonne.Server.Api.Shared.Models;

/// <summary>
/// A populated place from the GeoNames cities500 dataset (CC BY 4.0 — see
/// VersionEndpoint for the attribution we ship). Rows are created on demand, the
/// first time a photo is geocoded near one, so the table only ever holds places
/// the library has actually been to.
///
/// Deliberately a table rather than city/country strings on AssetExif: photos
/// group by place for free, renaming stays impossible to get inconsistent, and
/// the trip generator can count assets per place with a join instead of a
/// string comparison.
/// </summary>
public class Place
{
    public Guid Id { get; set; } = Guid.NewGuid();

    /// <summary>The GeoNames id. Stable across dataset releases, which makes it
    /// the natural key: re-importing a newer cities500 updates places in place
    /// instead of duplicating them.</summary>
    public int GeonameId { get; set; }

    [Required]
    [MaxLength(200)]
    public string Name { get; set; } = string.Empty;

    /// <summary>
    /// ISO 3166-1 alpha-2. The country's NAME is not stored, and — contrary to
    /// what an earlier version of this comment claimed — .NET cannot render it:
    /// RegionInfo.DisplayName ignores CurrentUICulture and falls back to the
    /// NATIVE name, so "JP" comes out as 日本 and "MA" as المغرب regardless of
    /// the culture you ask in. ("IT" → "Italia" only works by coincidence.)
    ///
    /// So nothing user-facing may derive a country name from this code without
    /// bringing its own translations. Trip titles are built from city names for
    /// exactly this reason. The code is still worth keeping: it disambiguates
    /// same-named cities and groups places by country for free.
    /// </summary>
    [Required]
    [MaxLength(2)]
    public string CountryCode { get; set; } = string.Empty;

    public double Latitude { get; set; }
    public double Longitude { get; set; }
}
