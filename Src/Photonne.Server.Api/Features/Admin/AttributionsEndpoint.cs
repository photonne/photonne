using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Services.Geo;

namespace Photonne.Server.Api.Features.Admin;

/// <summary>Credit for a third-party dataset shipped inside this server image.</summary>
public class AttributionResponse
{
    public string Name { get; set; } = string.Empty;
    public string License { get; set; } = string.Empty;
    public string LicenseUrl { get; set; } = string.Empty;
    public string SourceUrl { get; set; } = string.Empty;
    /// <summary>The line a UI can show verbatim.</summary>
    public string Notice { get; set; } = string.Empty;
    /// <summary>When the copy in this image was fetched, so an operator can tell
    /// a 2024 dataset from a 2026 one. Null if unknown.</summary>
    public DateTime? DatasetDate { get; set; }
}

/// <summary>
/// Public: the third-party data this server redistributes, and under what terms.
///
/// This is not decoration. The image bakes in GeoNames' cities500, which is
/// CC BY 4.0 — redistribution is permitted *because* it's attributed, so this
/// endpoint is part of how Photonne complies with the licence, alongside the
/// credit in the README. Only datasets actually present are listed: claiming to
/// contain GeoNames data when the build couldn't download it would be its own
/// kind of wrong.
/// </summary>
public class AttributionsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/attributions", (ReverseGeocoder geocoder) =>
        {
            var items = new List<AttributionResponse>();

            if (geocoder.IsAvailable)
            {
                items.Add(new AttributionResponse
                {
                    Name = "GeoNames",
                    License = "CC BY 4.0",
                    LicenseUrl = "https://creativecommons.org/licenses/by/4.0/",
                    SourceUrl = "https://www.geonames.org/",
                    Notice = "Datos de lugares © GeoNames, bajo licencia CC BY 4.0.",
                    DatasetDate = geocoder.DatasetDate,
                });
            }

            return Results.Ok(items);
        })
        .WithTags("Version")
        .WithName("GetAttributions")
        .WithDescription("Third-party datasets bundled with this server and their licences. Public, no auth required.")
        .AllowAnonymous();
    }
}
