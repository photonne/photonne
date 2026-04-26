using System.Collections.Concurrent;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Pgvector.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

/// <summary>
/// Two-stage clustering for face embeddings:
///   * Online (per upload): each new face is attached to the closest existing
///     Person of the same owner via cosine distance, when below the configured
///     threshold. Otherwise it stays orphan (PersonId = null).
///   * Batch (per owner, scheduled): scans orphan faces and groups them into
///     new Persons using a simple union-find driven by pairwise distance under
///     the same threshold (single-link, equivalent to DBSCAN with eps=threshold
///     and min_samples=MinFacesForCluster on a graph cut).
/// Manually-assigned faces are never moved. Face counts are kept consistent in
/// Person.FaceCount.
/// </summary>
public class FaceClusteringService
{
    // Per-owner cooldown shared across requests/scopes — avoids running the
    // O(n²) batch clustering on every single face detection.
    private static readonly ConcurrentDictionary<Guid, DateTime> _lastBatchRunUtc = new();
    private static readonly TimeSpan BatchCooldown = TimeSpan.FromMinutes(2);

    private readonly ApplicationDbContext _dbContext;
    private readonly FaceRecognitionOptions _options;
    private readonly ILogger<FaceClusteringService> _logger;

    public FaceClusteringService(
        ApplicationDbContext dbContext,
        IOptions<FaceRecognitionOptions> options,
        ILogger<FaceClusteringService> logger)
    {
        _dbContext = dbContext;
        _options = options.Value;
        _logger = logger;
    }

    /// <summary>
    /// Online-assigns the orphan faces of a single asset (just detected) to the
    /// nearest existing Person within ClusteringThreshold.
    /// </summary>
    public async Task AssignNewFacesAsync(Guid ownerId, Guid assetId, CancellationToken cancellationToken)
    {
        var newFaces = await _dbContext.Faces
            .Where(f => f.AssetId == assetId
                        && f.PersonId == null
                        && !f.IsManuallyAssigned
                        && !f.IsRejected)
            .ToListAsync(cancellationToken);

        if (newFaces.Count == 0) return;

        var hasAnyPerson = await _dbContext.People.AnyAsync(p => p.OwnerId == ownerId, cancellationToken);
        if (!hasAnyPerson)
        {
            _logger.LogDebug(
                "Owner {OwnerId} has no Person yet; skipping online assignment for {Count} new faces (batch clustering will create clusters)",
                ownerId, newFaces.Count);
            return;
        }

        var threshold = _options.ClusteringThreshold;

        foreach (var face in newFaces)
        {
            // Pgvector cosine-distance operator <=>; pick the closest assigned face
            // belonging to a Person of the same owner. Pull the embedding back so we
            // can re-check the distance in memory without forcing EF to translate
            // CosineDistance twice in a single query.
            var nearest = await _dbContext.Faces
                .Where(f => f.PersonId != null
                            && !f.IsRejected
                            && f.Person!.OwnerId == ownerId
                            && f.Id != face.Id)
                .OrderBy(f => f.Embedding.CosineDistance(face.Embedding))
                .Select(f => new { f.PersonId, f.Embedding })
                .FirstOrDefaultAsync(cancellationToken);

            if (nearest?.PersonId == null) continue;

            var distance = CosineDistance(nearest.Embedding.ToArray(), face.Embedding.ToArray());
            if (distance < threshold)
            {
                face.PersonId = nearest.PersonId.Value;
                _logger.LogDebug("Face {FaceId} → Person {PersonId} (dist={Dist:F3})",
                    face.Id, nearest.PersonId.Value, distance);
            }
        }

        await _dbContext.SaveChangesAsync(cancellationToken);
        await RecomputeFaceCountsAsync(ownerId, cancellationToken);
    }

    /// <summary>
    /// Runs <see cref="RunForOwnerAsync"/> for the owner if there are enough
    /// orphan faces to potentially form clusters AND the per-owner cooldown
    /// has elapsed. This is the hook called after each face detection to
    /// keep the People table populated without requiring a manual trigger.
    /// </summary>
    public async Task<int> MaybeRunBatchAsync(Guid ownerId, CancellationToken cancellationToken)
    {
        var orphanCount = await _dbContext.Faces
            .CountAsync(f => f.Asset.OwnerId == ownerId
                             && f.PersonId == null
                             && !f.IsManuallyAssigned
                             && !f.IsRejected, cancellationToken);

        if (orphanCount < _options.MinFacesForCluster)
        {
            _logger.LogDebug("Owner {OwnerId}: {Orphans} orphans (< {Min}); skipping batch",
                ownerId, orphanCount, _options.MinFacesForCluster);
            return 0;
        }

        var now = DateTime.UtcNow;
        var last = _lastBatchRunUtc.TryGetValue(ownerId, out var t) ? t : DateTime.MinValue;
        if (now - last < BatchCooldown)
        {
            _logger.LogDebug("Owner {OwnerId}: batch cooldown active ({Remaining:F0}s left)",
                ownerId, (BatchCooldown - (now - last)).TotalSeconds);
            return 0;
        }

        _lastBatchRunUtc[ownerId] = now;
        return await RunForOwnerAsync(ownerId, cancellationToken);
    }

    /// <summary>
    /// Batch clustering for orphan faces of a given owner. Creates new Persons
    /// for clusters of size >= MinFacesForCluster.
    /// </summary>
    public async Task<int> RunForOwnerAsync(Guid ownerId, CancellationToken cancellationToken)
    {
        var orphans = await _dbContext.Faces
            .Where(f => f.Asset.OwnerId == ownerId
                        && f.PersonId == null
                        && !f.IsManuallyAssigned
                        && !f.IsRejected)
            .ToListAsync(cancellationToken);

        if (orphans.Count < _options.MinFacesForCluster) return 0;

        var threshold = _options.ClusteringThreshold;
        var parent = Enumerable.Range(0, orphans.Count).ToArray();

        int Find(int x)
        {
            while (parent[x] != x)
            {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        void Union(int a, int b)
        {
            var ra = Find(a);
            var rb = Find(b);
            if (ra != rb) parent[ra] = rb;
        }

        // O(n²) is fine for moderate face counts per owner. For >5000 faces per
        // owner we should switch to pgvector kNN-based neighbor lookup.
        for (int i = 0; i < orphans.Count; i++)
        {
            var ai = orphans[i].Embedding.ToArray();
            for (int j = i + 1; j < orphans.Count; j++)
            {
                var aj = orphans[j].Embedding.ToArray();
                var d = CosineDistance(ai, aj);
                if (d < threshold) Union(i, j);
            }
        }

        var clusters = new Dictionary<int, List<int>>();
        for (int i = 0; i < orphans.Count; i++)
        {
            var root = Find(i);
            if (!clusters.TryGetValue(root, out var list))
            {
                list = new List<int>();
                clusters[root] = list;
            }
            list.Add(i);
        }

        var personsCreated = 0;
        foreach (var (_, members) in clusters)
        {
            if (members.Count < _options.MinFacesForCluster) continue;

            // Cover face = highest-confidence member.
            var coverIdx = members.OrderByDescending(idx => orphans[idx].Confidence).First();
            var person = new Person
            {
                OwnerId = ownerId,
                CoverFaceId = orphans[coverIdx].Id,
                FaceCount = members.Count,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow,
            };
            _dbContext.People.Add(person);
            await _dbContext.SaveChangesAsync(cancellationToken);

            foreach (var idx in members)
            {
                orphans[idx].PersonId = person.Id;
            }
            await _dbContext.SaveChangesAsync(cancellationToken);
            personsCreated++;
        }

        _logger.LogInformation("Clustering for owner {OwnerId}: {Created} new persons from {Orphans} orphans",
            ownerId, personsCreated, orphans.Count);

        return personsCreated;
    }

    public async Task RecomputeFaceCountsAsync(Guid ownerId, CancellationToken cancellationToken)
    {
        var counts = await _dbContext.Faces
            .Where(f => f.PersonId != null
                        && !f.IsRejected
                        && f.Person!.OwnerId == ownerId)
            .GroupBy(f => f.PersonId!.Value)
            .Select(g => new { PersonId = g.Key, Count = g.Count() })
            .ToListAsync(cancellationToken);

        var byId = counts.ToDictionary(c => c.PersonId, c => c.Count);

        var people = await _dbContext.People
            .Where(p => p.OwnerId == ownerId)
            .ToListAsync(cancellationToken);

        foreach (var p in people)
        {
            p.FaceCount = byId.TryGetValue(p.Id, out var c) ? c : 0;
            p.UpdatedAt = DateTime.UtcNow;
        }

        await _dbContext.SaveChangesAsync(cancellationToken);
    }

    public async Task MergeAsync(Guid ownerId, Guid targetPersonId, Guid sourcePersonId, CancellationToken cancellationToken)
    {
        if (targetPersonId == sourcePersonId) return;

        var target = await _dbContext.People
            .FirstOrDefaultAsync(p => p.Id == targetPersonId && p.OwnerId == ownerId, cancellationToken)
            ?? throw new InvalidOperationException($"Target person {targetPersonId} not found");

        var source = await _dbContext.People
            .FirstOrDefaultAsync(p => p.Id == sourcePersonId && p.OwnerId == ownerId, cancellationToken)
            ?? throw new InvalidOperationException($"Source person {sourcePersonId} not found");

        await _dbContext.Faces
            .Where(f => f.PersonId == sourcePersonId)
            .ExecuteUpdateAsync(s => s.SetProperty(f => f.PersonId, target.Id), cancellationToken);

        // Clear source's CoverFace FK to avoid blocking the delete via SetNull cascade.
        source.CoverFaceId = null;
        await _dbContext.SaveChangesAsync(cancellationToken);

        _dbContext.People.Remove(source);
        await _dbContext.SaveChangesAsync(cancellationToken);

        await RecomputeFaceCountsAsync(ownerId, cancellationToken);
    }

    private static float CosineDistance(float[] a, float[] b)
    {
        // ArcFace embeddings come L2-normalized from InsightFace, but defend against
        // unnormalized inputs to keep the distance well-defined.
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.Length; i++)
        {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        var denom = Math.Sqrt(na) * Math.Sqrt(nb);
        if (denom <= 0) return 1.0f;
        var cos = dot / denom;
        return (float)(1.0 - cos);
    }
}
