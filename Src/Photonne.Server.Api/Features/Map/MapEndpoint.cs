using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Scalar.AspNetCore;

namespace Photonne.Server.Api.Features.Map;

public class MapAssetsEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        app.MapGet("/api/assets/map", Handle)
            .CodeSample(
                codeSample: "curl -X GET \"http://localhost:5000/api/assets/map?zoom=10&bounds=40.0,-3.0,41.0,-2.0\" -H \"Accept: application/json\"",
                label: "cURL Example")
            .WithName("GetMapAssets")
            .WithTags("Assets")
            .WithDescription("Gets assets with GPS coordinates for map visualization")
            .AddOpenApiOperationTransformer((operation, context, ct) =>
            {
                operation.Summary = "Get map assets";
                operation.Description = "Returns assets with GPS coordinates, optionally filtered by zoom level and map bounds for clustering.";
                return Task.CompletedTask;
            });
    }

    private async Task<IResult> Handle(
        [FromServices] ApplicationDbContext dbContext,
        [FromServices] IMemoryCache cache,
        ClaimsPrincipal user,
        [FromQuery] int? zoom,
        [FromQuery] double? minLat,
        [FromQuery] double? minLng,
        [FromQuery] double? maxLat,
        [FromQuery] double? maxLng,
        CancellationToken cancellationToken)
    {
        try
        {
            if (!TryGetUserId(user, out var userId))
            {
                return Results.Unauthorized();
            }

            var isAdmin = user.IsInRole("Admin");
            var userRootPath = GetUserRootPath(userId);

            // Obtener assets GPS — cachear todos los del usuario, filtrar bounds en memoria
            var cacheKey = $"map:assets:{userId}";
            if (!cache.TryGetValue(cacheKey, out List<AssetLocation>? allAssets) || allAssets == null)
            {
                var query = dbContext.Assets
                    .Include(a => a.Exif)
                    .Include(a => a.Thumbnails)
                    .Where(a => a.DeletedAt == null &&
                               a.Exif != null &&
                               a.Exif.Latitude.HasValue &&
                               a.Exif.Longitude.HasValue);

                if (!isAdmin)
                {
                    var allowedFolderIds = await GetAllowedFolderIdsForUserAsync(dbContext, userId, userRootPath, cancellationToken);
                    query = query.Where(a => a.FolderId.HasValue && allowedFolderIds.Contains(a.FolderId.Value));
                }

                var dbAssets = await query.ToListAsync(cancellationToken);
                allAssets = dbAssets.Select(a => new AssetLocation
                {
                    Id = a.Id,
                    FileCreatedAt = a.FileCreatedAt,
                    Latitude = a.Exif!.Latitude!.Value,
                    Longitude = a.Exif.Longitude!.Value,
                    HasThumbnails = a.Thumbnails.Any()
                }).ToList();

                cache.Set(cacheKey, allAssets, TimeSpan.FromMinutes(5));
            }

            // Filtrar por bounds en memoria (evita múltiples consultas a BD para distintos viewports)
            var assets = allAssets;
            if (minLat.HasValue && minLng.HasValue && maxLat.HasValue && maxLng.HasValue)
            {
                bool isGlobalView = Math.Abs(maxLng.Value - minLng.Value) > 350;
                if (!isGlobalView)
                {
                    assets = allAssets.Where(a =>
                        a.Latitude >= minLat.Value && a.Latitude <= maxLat.Value &&
                        a.Longitude >= minLng.Value && a.Longitude <= maxLng.Value).ToList();
                }
            }

            // Agrupar assets en clusters basados en zoom level
            var currentZoom = zoom ?? 10;
            var clusterDistance = GetClusterDistance(currentZoom);
            
            // Si el zoom es alto, reducimos el área de búsqueda de clustering para evitar que assets lejanos 
            // "roben" assets que deberían estar en clusters separados y visibles al hacer zoom.
            var clusters = CreateClusters(assets, clusterDistance);
            
            // Validación inicial: asegurar que no haya assets duplicados antes de procesar
            clusters = ValidateNoDuplicateAssets(clusters);
            
            // Eliminar duplicados ANTES de separar para evitar crear más duplicados
            clusters = DeduplicateClusters(clusters);
            
            // Separar clusters que se superponen visualmente
            clusters = SeparateOverlappingClusters(clusters, currentZoom, clusterDistance);
            
            // Eliminar duplicados DESPUÉS de separar (por si la separación creó algún problema)
            clusters = DeduplicateClusters(clusters);
            
            // Validación final: asegurar que no haya assets duplicados
            clusters = ValidateNoDuplicateAssets(clusters);
            
            // Validación final agresiva: asegurar que no haya assets duplicados
            var totalAssets = clusters.Sum(c => c.Count);
            var uniqueAssets = clusters.SelectMany(c => c.AssetIds).Distinct().Count();
            if (totalAssets != uniqueAssets)
            {
                // Hay duplicados, forzar limpieza agresiva
                clusters = ForceDeduplicate(clusters);
                
                // Verificar nuevamente después de la limpieza
                totalAssets = clusters.Sum(c => c.Count);
                uniqueAssets = clusters.SelectMany(c => c.AssetIds).Distinct().Count();
                if (totalAssets != uniqueAssets)
                {
                    // Si aún hay duplicados después de la limpieza, usar método más agresivo
                    clusters = AggressiveDeduplicate(clusters);
                }
            }

            // Usar los HasThumbnails ya cargados en memoria (evita segunda consulta a BD)
            var assetThumbLookup = allAssets.ToDictionary(a => a.Id, a => a.HasThumbnails);

            var response = clusters.Select(c =>
            {
                var firstAssetId = c.AssetIds.FirstOrDefault();
                var clusterId = $"{c.Latitude:F6}_{c.Longitude:F6}_{c.Count}_{c.EarliestDate:yyyyMMddHHmmss}";

                return new MapClusterResponse
                {
                    Id = clusterId,
                    Latitude = c.Latitude,
                    Longitude = c.Longitude,
                    Count = c.Count,
                    AssetIds = c.AssetIds,
                    EarliestDate = c.EarliestDate,
                    LatestDate = c.LatestDate,
                    FirstAssetId = firstAssetId,
                    HasThumbnail = firstAssetId != Guid.Empty &&
                                   assetThumbLookup.TryGetValue(firstAssetId, out var hasThumbs) && hasThumbs
                };
            }).ToList();

            return Results.Ok(response);
        }
        catch (Exception ex)
        {
            return Results.Problem(
                detail: ex.Message,
                statusCode: StatusCodes.Status500InternalServerError
            );
        }
    }

    private bool TryGetUserId(ClaimsPrincipal user, out Guid userId)
    {
        var userIdClaim = user.FindFirst(ClaimTypes.NameIdentifier);
        return Guid.TryParse(userIdClaim?.Value, out userId);
    }

    private string GetUserRootPath(Guid userId)
    {
        return $"/assets/users/{userId}";
    }

    private async Task<HashSet<Guid>> GetAllowedFolderIdsForUserAsync(
        ApplicationDbContext dbContext,
        Guid userId,
        string userRootPath,
        CancellationToken ct)
    {
        var allFolders = await dbContext.Folders.ToListAsync(ct);
        var permissions = await dbContext.FolderPermissions
            .Where(p => p.UserId == userId && p.CanRead)
            .ToListAsync(ct);

        var foldersWithPermissions = await dbContext.FolderPermissions
            .Select(p => p.FolderId)
            .Distinct()
            .ToListAsync(ct);

        var foldersWithPermissionsSet = foldersWithPermissions.ToHashSet();
        var allowedIds = permissions.Select(p => p.FolderId).ToHashSet();

        foreach (var folder in allFolders)
        {
            if (!foldersWithPermissionsSet.Contains(folder.Id))
            {
                if (folder.Path.Replace('\\', '/').StartsWith(userRootPath, StringComparison.OrdinalIgnoreCase))
                {
                    allowedIds.Add(folder.Id);
                }
            }
        }

        return allowedIds;
    }

    private double GetClusterDistance(int zoom)
    {
        // Distancia de clustering basada en zoom level (en kilómetros)
        // Zoom más alto = menor distancia = más clusters
        // Zoom más bajo = mayor distancia = menos clusters
        if (zoom <= 3)
            return 100.0; 
        if (zoom <= 6)
            return 20.0;
        if (zoom <= 10)
            return 5.0; 
        if (zoom <= 13)
            return 0.5; // 500m
        if (zoom <= 15)
            return 0.1; // 100m
        return 0.02; // 20m para zoom muy alto
    }

    private List<MapCluster> CreateClusters(
        List<AssetLocation> assets, 
        double clusterDistance)
    {
        if (!assets.Any())
            return new List<MapCluster>();

        var clusters = new List<MapCluster>();
        var processed = new HashSet<Guid>();

        // Ordenar assets por ID para hacer el algoritmo determinístico
        var sortedAssets = assets.OrderBy(a => a.Id).ToList();

        foreach (var asset in sortedAssets)
        {
            if (processed.Contains(asset.Id))
                continue;

            var cluster = new MapCluster
            {
                Latitude = asset.Latitude,
                Longitude = asset.Longitude,
                Count = 1,
                AssetIds = new List<Guid> { asset.Id },
                EarliestDate = asset.FileCreatedAt,
                LatestDate = asset.FileCreatedAt,
                HasThumbnail = asset.HasThumbnails
            };

            processed.Add(asset.Id);

            // Usar un enfoque iterativo: agregar assets cercanos al cluster actual
            // y luego buscar assets cercanos a cualquier asset del cluster
            var clusterAssets = new List<AssetLocation> { asset };
            bool foundNewAssets = true;
            int maxIterations = 10; // Limitar iteraciones para evitar bucles infinitos
            int iteration = 0;

            while (foundNewAssets && iteration < maxIterations)
            {
                foundNewAssets = false;
                iteration++;

                var newAssets = new List<AssetLocation>();

                // Para cada asset en el cluster actual, buscar assets cercanos
                foreach (var clusterAsset in clusterAssets)
                {
                    var nearbyAssets = sortedAssets
                        .Where(a => !processed.Contains(a.Id) && a.Id != clusterAsset.Id)
                        .Select(a => new
                        {
                            Asset = a,
                            Distance = CalculateDistance(
                                clusterAsset.Latitude, clusterAsset.Longitude,
                                a.Latitude, a.Longitude)
                        })
                        .Where(x => x.Distance <= clusterDistance)
                        .OrderBy(x => x.Distance)
                        .ThenBy(x => x.Asset.Id)
                        .Select(x => x.Asset)
                        .ToList();

                    foreach (var nearby in nearbyAssets)
                    {
                        if (!newAssets.Any(a => a.Id == nearby.Id) && !clusterAssets.Any(a => a.Id == nearby.Id))
                        {
                            newAssets.Add(nearby);
                            foundNewAssets = true;
                        }
                    }
                }

                // Agregar nuevos assets al cluster
                foreach (var newAsset in newAssets)
                {
                    cluster.Count++;
                    cluster.AssetIds.Add(newAsset.Id);
                    processed.Add(newAsset.Id);
                    clusterAssets.Add(newAsset);

                    // Actualizar centro del cluster (promedio ponderado)
                    var totalCount = cluster.Count;
                    cluster.Latitude = (cluster.Latitude * (totalCount - 1) + newAsset.Latitude) / totalCount;
                    cluster.Longitude = (cluster.Longitude * (totalCount - 1) + newAsset.Longitude) / totalCount;

                    // Actualizar fechas
                    if (newAsset.FileCreatedAt < cluster.EarliestDate)
                        cluster.EarliestDate = newAsset.FileCreatedAt;
                    if (newAsset.FileCreatedAt > cluster.LatestDate)
                        cluster.LatestDate = newAsset.FileCreatedAt;
                }
            }

            // Ordenar AssetIds para consistencia
            cluster.AssetIds = cluster.AssetIds.OrderBy(id => id).ToList();
            clusters.Add(cluster);
        }

        return clusters;
    }

    private double CalculateDistance(double lat1, double lon1, double lat2, double lon2)
    {
        // Fórmula de Haversine para calcular distancia entre dos puntos GPS
        const double R = 6371.0; // Radio de la Tierra en km
        var dLat = ToRadians(lat2 - lat1);
        var dLon = ToRadians(lon2 - lon1);
        var a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2) +
                Math.Cos(ToRadians(lat1)) * Math.Cos(ToRadians(lat2)) *
                Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
        var c = 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));
        return R * c;
    }

    private double ToRadians(double degrees)
    {
        return degrees * Math.PI / 180.0;
    }

    /// <summary>
    /// Calcula la distancia en kilómetros que corresponde a un número de píxeles en el mapa según el nivel de zoom.
    /// </summary>
    private double PixelsToKilometers(int pixels, int zoom, double latitude)
    {
        // Aproximación: en el ecuador, 1 grado de longitud ≈ 111.32 km
        // La escala del mapa depende del zoom: scale = 256 * 2^zoom metros por 256 píxeles
        // 1 píxel = (256 * 2^zoom) / 256 metros = 2^zoom metros
        
        // Ajustar por latitud (cos(lat) para longitud)
        var metersPerPixel = (156543.03392 * Math.Cos(ToRadians(latitude))) / Math.Pow(2, zoom);
        var kilometersPerPixel = metersPerPixel / 1000.0;
        
        return kilometersPerPixel * pixels;
    }

    /// <summary>
    /// Obtiene el radio visual de un círculo en píxeles según el número de assets.
    /// </summary>
    private int GetCircleRadiusPixels(int count)
    {
        // El tamaño del círculo es: Math.max(40, Math.min(80, 35 + count * 2))
        // Radio mínimo = 40px, radio máximo = 80px
        return Math.Max(40, Math.Min(80, 35 + count * 2));
    }

    /// <summary>
    /// Separa clusters que se superponen visualmente ajustando sus posiciones.
    /// </summary>
    private List<MapCluster> SeparateOverlappingClusters(List<MapCluster> clusters, int zoom, double clusterDistance)
    {
        if (clusters.Count <= 1)
            return clusters;

        var separated = new List<MapCluster>(clusters);
        var maxIterations = 50;
        var iteration = 0;
        var hasOverlaps = true;

        while (hasOverlaps && iteration < maxIterations)
        {
            hasOverlaps = false;
            iteration++;

            for (int i = 0; i < separated.Count; i++)
            {
                var cluster1 = separated[i];
                var radius1Pixels = GetCircleRadiusPixels(cluster1.Count);
                var radius1Km = PixelsToKilometers(radius1Pixels, zoom, cluster1.Latitude);

                for (int j = i + 1; j < separated.Count; j++)
                {
                    var cluster2 = separated[j];
                    var radius2Pixels = GetCircleRadiusPixels(cluster2.Count);
                    var radius2Km = PixelsToKilometers(radius2Pixels, zoom, cluster2.Latitude);

                    // Distancia mínima necesaria para evitar superposición (suma de radios + pequeño margen)
                    var minRequiredDistance = radius1Km + radius2Km + (Math.Min(radius1Km, radius2Km) * 0.1); // 10% de margen
                    
                    var actualDistance = CalculateDistance(
                        cluster1.Latitude, cluster1.Longitude,
                        cluster2.Latitude, cluster2.Longitude);

                    if (actualDistance < minRequiredDistance)
                    {
                        hasOverlaps = true;
                        
                        // Calcular dirección de separación
                        var bearing = CalculateBearing(
                            cluster1.Latitude, cluster1.Longitude,
                            cluster2.Latitude, cluster2.Longitude);

                        // Calcular cuánto necesitamos separar
                        var separationNeeded = minRequiredDistance - actualDistance;
                        // Limitar la separación máxima para evitar mover clusters demasiado lejos
                        var maxSeparationKm = Math.Min(separationNeeded / 2.0, clusterDistance * 0.5);
                        var separationKm = Math.Min(separationNeeded / 2.0, maxSeparationKm);

                        // Mover cluster1 alejándolo de cluster2
                        var newPos1 = MovePoint(cluster1.Latitude, cluster1.Longitude, 
                            bearing + 180, separationKm);
                        separated[i] = new MapCluster
                        {
                            Latitude = newPos1.Lat,
                            Longitude = newPos1.Lon,
                            Count = cluster1.Count,
                            AssetIds = cluster1.AssetIds,
                            EarliestDate = cluster1.EarliestDate,
                            LatestDate = cluster1.LatestDate,
                            HasThumbnail = cluster1.HasThumbnail
                        };

                        // Mover cluster2 alejándolo de cluster1
                        var newPos2 = MovePoint(cluster2.Latitude, cluster2.Longitude, 
                            bearing, separationKm);
                        separated[j] = new MapCluster
                        {
                            Latitude = newPos2.Lat,
                            Longitude = newPos2.Lon,
                            Count = cluster2.Count,
                            AssetIds = cluster2.AssetIds,
                            EarliestDate = cluster2.EarliestDate,
                            LatestDate = cluster2.LatestDate,
                            HasThumbnail = cluster2.HasThumbnail
                        };
                    }
                }
            }
        }

        return separated;
    }

    /// <summary>
    /// Calcula el bearing (dirección) entre dos puntos en grados.
    /// </summary>
    private double CalculateBearing(double lat1, double lon1, double lat2, double lon2)
    {
        var dLon = ToRadians(lon2 - lon1);
        var lat1Rad = ToRadians(lat1);
        var lat2Rad = ToRadians(lat2);

        var y = Math.Sin(dLon) * Math.Cos(lat2Rad);
        var x = Math.Cos(lat1Rad) * Math.Sin(lat2Rad) - 
                Math.Sin(lat1Rad) * Math.Cos(lat2Rad) * Math.Cos(dLon);

        var bearing = Math.Atan2(y, x);
        return (ToDegrees(bearing) + 360) % 360;
    }

    private double ToDegrees(double radians)
    {
        return radians * 180.0 / Math.PI;
    }

    /// <summary>
    /// Mueve un punto una distancia específica en una dirección específica.
    /// </summary>
    private (double Lat, double Lon) MovePoint(double lat, double lon, double bearing, double distanceKm)
    {
        const double R = 6371.0; // Radio de la Tierra en km
        var bearingRad = ToRadians(bearing);
        var latRad = ToRadians(lat);
        var lonRad = ToRadians(lon);

        var newLatRad = Math.Asin(
            Math.Sin(latRad) * Math.Cos(distanceKm / R) +
            Math.Cos(latRad) * Math.Sin(distanceKm / R) * Math.Cos(bearingRad));

        var newLonRad = lonRad + Math.Atan2(
            Math.Sin(bearingRad) * Math.Sin(distanceKm / R) * Math.Cos(latRad),
            Math.Cos(distanceKm / R) - Math.Sin(latRad) * Math.Sin(newLatRad));

        return (ToDegrees(newLatRad), ToDegrees(newLonRad));
    }

    /// <summary>
    /// Elimina clusters duplicados que contengan los mismos assets.
    /// Garantiza que cada asset aparezca solo en un cluster, priorizando clusters más grandes.
    /// </summary>
    private List<MapCluster> DeduplicateClusters(List<MapCluster> clusters)
    {
        if (clusters.Count <= 1)
            return clusters;

        // Primero, eliminar clusters con exactamente los mismos AssetIds
        var clustersByAssetSet = new Dictionary<string, MapCluster>();
        foreach (var cluster in clusters)
        {
            var assetKey = string.Join(",", cluster.AssetIds.OrderBy(id => id));
            if (!clustersByAssetSet.ContainsKey(assetKey))
            {
                clustersByAssetSet[assetKey] = cluster;
            }
            else
            {
                // Si hay duplicado exacto, mantener el primero (ya está ordenado)
                var existing = clustersByAssetSet[assetKey];
                if (cluster.Count > existing.Count)
                {
                    clustersByAssetSet[assetKey] = cluster;
                }
            }
        }

        var deduplicated = clustersByAssetSet.Values.ToList();

        // Ahora eliminar clusters que tienen assets solapados
        // Ordenar por tamaño (más grandes primero) para priorizar clusters más completos
        var sortedClusters = deduplicated.OrderByDescending(c => c.Count).ToList();
        var finalClusters = new List<MapCluster>();
        var usedAssetIds = new HashSet<Guid>();

        foreach (var cluster in sortedClusters)
        {
            var clusterAssetIds = new HashSet<Guid>(cluster.AssetIds);
            
            // Verificar si este cluster tiene assets que ya están en otro cluster
            var hasOverlap = clusterAssetIds.Any(id => usedAssetIds.Contains(id));
            
            if (!hasOverlap)
            {
                // No hay solapamiento, agregar este cluster
                finalClusters.Add(cluster);
                foreach (var assetId in clusterAssetIds)
                {
                    usedAssetIds.Add(assetId);
                }
            }
            // Si hay solapamiento, simplemente descartar este cluster
            // porque ya tenemos uno más grande que contiene algunos de sus assets
        }

        return finalClusters;
    }

    /// <summary>
    /// Validación final para asegurar que ningún asset aparezca en múltiples clusters.
    /// Si se encuentra un asset duplicado, se mantiene solo en el cluster más grande.
    /// </summary>
    private List<MapCluster> ValidateNoDuplicateAssets(List<MapCluster> clusters)
    {
        if (clusters.Count <= 1)
            return clusters;

        var assetToCluster = new Dictionary<Guid, MapCluster>();
        var clustersToRemove = new HashSet<MapCluster>();

        // Primera pasada: identificar duplicados
        foreach (var cluster in clusters)
        {
            foreach (var assetId in cluster.AssetIds)
            {
                if (assetToCluster.ContainsKey(assetId))
                {
                    // Asset duplicado encontrado
                    var existingCluster = assetToCluster[assetId];
                    // Mantener el cluster más grande
                    if (cluster.Count > existingCluster.Count)
                    {
                        clustersToRemove.Add(existingCluster);
                        assetToCluster[assetId] = cluster;
                    }
                    else
                    {
                        clustersToRemove.Add(cluster);
                    }
                }
                else
                {
                    assetToCluster[assetId] = cluster;
                }
            }
        }

        // Segunda pasada: limpiar clusters que tienen assets duplicados
        var validClusters = new List<MapCluster>();
        var usedAssets = new HashSet<Guid>();

        foreach (var cluster in clusters.OrderByDescending(c => c.Count))
        {
            if (clustersToRemove.Contains(cluster))
                continue;

            var clusterAssets = cluster.AssetIds.ToList();
            var hasDuplicates = clusterAssets.Any(id => usedAssets.Contains(id));

            if (!hasDuplicates)
            {
                validClusters.Add(cluster);
                foreach (var assetId in clusterAssets)
                {
                    usedAssets.Add(assetId);
                }
            }
        }

        return validClusters;
    }

    /// <summary>
    /// Fuerza la deduplicación eliminando cualquier asset duplicado, manteniendo solo el cluster más grande para cada asset.
    /// </summary>
    private List<MapCluster> ForceDeduplicate(List<MapCluster> clusters)
    {
        if (clusters.Count <= 1)
            return clusters;

        // Crear un diccionario que mapea cada asset ID al mejor cluster que lo contiene
        var assetToBestCluster = new Dictionary<Guid, MapCluster>();
        
        foreach (var cluster in clusters.OrderByDescending(c => c.Count))
        {
            foreach (var assetId in cluster.AssetIds)
            {
                if (!assetToBestCluster.ContainsKey(assetId))
                {
                    assetToBestCluster[assetId] = cluster;
                }
                else
                {
                    // Si este cluster es más grande, reemplazar
                    var existingCluster = assetToBestCluster[assetId];
                    if (cluster.Count > existingCluster.Count)
                    {
                        assetToBestCluster[assetId] = cluster;
                    }
                }
            }
        }

        // Obtener los clusters únicos (sin duplicados)
        var uniqueClusters = assetToBestCluster.Values
            .GroupBy(c => string.Join(",", c.AssetIds.OrderBy(id => id)))
            .Select(g => g.First())
            .ToList();

        // Reconstruir clusters asegurando que cada asset aparezca solo una vez
        var finalClusters = new List<MapCluster>();
        var usedAssets = new HashSet<Guid>();

        foreach (var cluster in uniqueClusters.OrderByDescending(c => c.Count))
        {
            // Filtrar assets que ya están en otros clusters
            var availableAssets = cluster.AssetIds.Where(id => !usedAssets.Contains(id)).ToList();
            
            if (availableAssets.Any())
            {
                // Crear un nuevo cluster solo con los assets disponibles
                var newCluster = new MapCluster
                {
                    AssetIds = availableAssets,
                    Count = availableAssets.Count,
                    Latitude = cluster.Latitude,
                    Longitude = cluster.Longitude,
                    EarliestDate = cluster.EarliestDate,
                    LatestDate = cluster.LatestDate,
                    HasThumbnail = cluster.HasThumbnail
                };

                finalClusters.Add(newCluster);
                foreach (var assetId in availableAssets)
                {
                    usedAssets.Add(assetId);
                }
            }
        }

        return finalClusters;
    }

    /// <summary>
    /// Deduplicación agresiva: asigna cada asset al cluster más cercano y elimina duplicados.
    /// </summary>
    private List<MapCluster> AggressiveDeduplicate(List<MapCluster> clusters)
    {
        if (clusters.Count <= 1)
            return clusters;

        // Obtener todos los assets únicos
        var allAssetIds = clusters.SelectMany(c => c.AssetIds).Distinct().ToList();
        
        // Para cada asset, encontrar el mejor cluster (más grande)
        var assetToCluster = new Dictionary<Guid, MapCluster>();
        
        foreach (var assetId in allAssetIds)
        {
            var clustersWithAsset = clusters
                .Where(c => c.AssetIds.Contains(assetId))
                .OrderByDescending(c => c.Count)
                .ThenBy(c => c.AssetIds.Count)
                .ToList();
            
            if (clustersWithAsset.Any())
            {
                assetToCluster[assetId] = clustersWithAsset.First();
            }
        }

        // Agrupar assets por cluster
        var clusterGroups = assetToCluster
            .GroupBy(kvp => kvp.Value, new ClusterEqualityComparer())
            .ToList();

        // Crear nuevos clusters sin duplicados
        var finalClusters = new List<MapCluster>();
        foreach (var group in clusterGroups)
        {
            var originalCluster = group.Key;
            var assetIds = group.Select(kvp => kvp.Key).OrderBy(id => id).ToList();
            
            var newCluster = new MapCluster
            {
                AssetIds = assetIds,
                Count = assetIds.Count,
                Latitude = originalCluster.Latitude,
                Longitude = originalCluster.Longitude,
                EarliestDate = originalCluster.EarliestDate,
                LatestDate = originalCluster.LatestDate,
                HasThumbnail = originalCluster.HasThumbnail
            };
            
            finalClusters.Add(newCluster);
        }

        return finalClusters;
    }

    private class ClusterEqualityComparer : IEqualityComparer<MapCluster>
    {
        public bool Equals(MapCluster? x, MapCluster? y)
        {
            if (x == null || y == null) return false;
            if (ReferenceEquals(x, y)) return true;
            
            var xIds = new HashSet<Guid>(x.AssetIds.OrderBy(id => id));
            var yIds = new HashSet<Guid>(y.AssetIds.OrderBy(id => id));
            
            return xIds.SetEquals(yIds);
        }

        public int GetHashCode(MapCluster obj)
        {
            if (obj == null) return 0;
            var sortedIds = obj.AssetIds.OrderBy(id => id).ToList();
            return string.Join(",", sortedIds).GetHashCode();
        }
    }

    private class AssetLocation
    {
        public Guid Id { get; set; }
        public DateTime FileCreatedAt { get; set; }
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public bool HasThumbnails { get; set; }
    }

    private class MapCluster
    {
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public int Count { get; set; }
        public List<Guid> AssetIds { get; set; } = new();
        public DateTime EarliestDate { get; set; }
        public DateTime LatestDate { get; set; }
        public bool HasThumbnail { get; set; }
    }
}
