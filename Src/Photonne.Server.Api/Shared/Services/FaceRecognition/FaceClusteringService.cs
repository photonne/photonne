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
    /// Online-assigns the orphan faces of a single asset (just detected): if the
    /// nearest existing Person sits within <see cref="FaceRecognitionOptions.ClusteringThreshold"/>
    /// the face is attached automatically; if it sits in the soft-match band
    /// [ClusteringThreshold, SuggestionThreshold) the face stays orphan but gets a
    /// non-binding <see cref="Face.SuggestedPersonId"/> hint for the UI to surface.
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

        var assignThreshold = _options.ClusteringThreshold;
        var suggestThreshold = _options.SuggestionThreshold;

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

            if (nearest?.PersonId == null)
            {
                ClearSuggestion(face);
                continue;
            }

            var distance = CosineDistance(nearest.Embedding.ToArray(), face.Embedding.ToArray());
            if (distance < assignThreshold)
            {
                face.PersonId = nearest.PersonId.Value;
                ClearSuggestion(face);
                _logger.LogDebug("Face {FaceId} → Person {PersonId} (dist={Dist:F3})",
                    face.Id, nearest.PersonId.Value, distance);
            }
            else if (distance < suggestThreshold)
            {
                face.SuggestedPersonId = nearest.PersonId.Value;
                face.SuggestedDistance = distance;
                _logger.LogDebug("Face {FaceId} ~? Person {PersonId} (dist={Dist:F3}) — suggestion",
                    face.Id, nearest.PersonId.Value, distance);
            }
            else
            {
                ClearSuggestion(face);
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

        if (orphans.Count > _options.KnnSwitchoverThreshold)
        {
            // pgvector kNN path: for each orphan, ask Postgres for its top-k
            // nearest orphan neighbors using the HNSW index on Faces.Embedding,
            // and add edges below the cosine threshold to the union-find. This
            // turns the O(n²) pairwise loop into O(n · k · log n) plus n round
            // trips, which keeps batch runtime bounded on large catalogs.
            var idToIndex = new Dictionary<Guid, int>(orphans.Count);
            for (int i = 0; i < orphans.Count; i++) idToIndex[orphans[i].Id] = i;

            var k = _options.KnnNeighbors;
            for (int i = 0; i < orphans.Count; i++)
            {
                var face = orphans[i];
                var embedding = face.Embedding;

                // Filter by orphan-ness so the candidate set matches the
                // pairwise path; pgvector ≥0.8 will iterate the HNSW graph
                // until k candidates pass the WHERE filter.
                var neighbors = await _dbContext.Faces
                    .AsNoTracking()
                    .Where(f => f.Asset.OwnerId == ownerId
                                && f.PersonId == null
                                && !f.IsManuallyAssigned
                                && !f.IsRejected
                                && f.Id != face.Id)
                    .OrderBy(f => f.Embedding.CosineDistance(embedding))
                    .Take(k)
                    .Select(f => new { f.Id, Distance = f.Embedding.CosineDistance(embedding) })
                    .ToListAsync(cancellationToken);

                foreach (var n in neighbors)
                {
                    // Neighbors come back ordered by ascending distance, so
                    // once we cross the threshold the rest are out too.
                    if (n.Distance >= threshold) break;
                    if (idToIndex.TryGetValue(n.Id, out var j)) Union(i, j);
                }
            }
        }
        else
        {
            // O(n²) is fine for moderate face counts per owner.
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

        // After cluster creation any face still without a Person is a "true" orphan
        // for this owner — refresh its proactive suggestion so the UI can offer
        // "could this be X?" against existing Persons. Re-suggesting is intentional:
        // dismiss is non-sticky, the user just confirms or ignores again next time.
        var stillOrphan = orphans.Where(f => f.PersonId == null).ToList();
        if (stillOrphan.Count > 0)
        {
            await RecomputeSuggestionsForAsync(ownerId, stillOrphan, cancellationToken);
        }

        return personsCreated;
    }

    /// <summary>
    /// For each provided orphan face, queries pgvector for the nearest assigned face
    /// of the same owner and updates <see cref="Face.SuggestedPersonId"/>/<see cref="Face.SuggestedDistance"/>:
    ///   * inside [ClusteringThreshold, SuggestionThreshold)  → set as suggestion
    ///   * outside → clear (orphan, no hint)
    /// Faces that should auto-assign instead (distance &lt; ClusteringThreshold) are
    /// left untouched: a stale orphan in that band is rare here because RunForOwnerAsync
    /// already grouped near-duplicates, and AssignNewFacesAsync handles the online path.
    /// </summary>
    private async Task RecomputeSuggestionsForAsync(Guid ownerId, IReadOnlyList<Face> orphans, CancellationToken cancellationToken)
    {
        var assignThreshold = _options.ClusteringThreshold;
        var suggestThreshold = _options.SuggestionThreshold;
        if (suggestThreshold <= assignThreshold) return; // suggestions disabled

        var changed = 0;
        foreach (var face in orphans)
        {
            var nearest = await _dbContext.Faces
                .Where(f => f.PersonId != null
                            && !f.IsRejected
                            && f.Person!.OwnerId == ownerId
                            && f.Id != face.Id)
                .OrderBy(f => f.Embedding.CosineDistance(face.Embedding))
                .Select(f => new { f.PersonId, f.Embedding })
                .FirstOrDefaultAsync(cancellationToken);

            Guid? newSuggestion = null;
            float? newDistance = null;
            if (nearest?.PersonId != null)
            {
                var distance = CosineDistance(nearest.Embedding.ToArray(), face.Embedding.ToArray());
                if (distance >= assignThreshold && distance < suggestThreshold)
                {
                    newSuggestion = nearest.PersonId.Value;
                    newDistance = distance;
                }
            }

            if (face.SuggestedPersonId != newSuggestion || face.SuggestedDistance != newDistance)
            {
                face.SuggestedPersonId = newSuggestion;
                face.SuggestedDistance = newDistance;
                changed++;
            }
        }

        if (changed > 0)
        {
            await _dbContext.SaveChangesAsync(cancellationToken);
            _logger.LogInformation("Recomputed suggestions for owner {OwnerId}: {Changed}/{Total} orphans updated",
                ownerId, changed, orphans.Count);
        }
    }

    private static void ClearSuggestion(Face face)
    {
        face.SuggestedPersonId = null;
        face.SuggestedDistance = null;
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

        // Redirect any pending "could this be source?" suggestions to the merged
        // target so the user doesn't see hints pointing at a Person that's about
        // to disappear. The SetNull FK cascade would also clear them, but redirect
        // preserves the hint for the consolidated cluster.
        await _dbContext.Faces
            .Where(f => f.SuggestedPersonId == sourcePersonId)
            .ExecuteUpdateAsync(s => s.SetProperty(f => f.SuggestedPersonId, (Guid?)target.Id), cancellationToken);

        // Clear source's CoverFace FK to avoid blocking the delete via SetNull cascade.
        source.CoverFaceId = null;
        await _dbContext.SaveChangesAsync(cancellationToken);

        _dbContext.People.Remove(source);
        await _dbContext.SaveChangesAsync(cancellationToken);

        await RecomputeFaceCountsAsync(ownerId, cancellationToken);
    }

    /// <summary>
    /// Removes Persons whose FaceCount has dropped to 0 (e.g., the user
    /// rejected or unassigned every face that was clustered into them).
    /// Safe to call after any face mutation. Does not touch Persons that
    /// the user is still actively populating, because callers always run
    /// <see cref="RecomputeFaceCountsAsync"/> first to settle counts.
    /// </summary>
    public async Task<int> CleanupEmptyPersonsAsync(Guid ownerId, CancellationToken cancellationToken)
    {
        var empty = await _dbContext.People
            .Where(p => p.OwnerId == ownerId && p.FaceCount == 0)
            .ToListAsync(cancellationToken);

        if (empty.Count == 0) return 0;

        // CoverFaceId may still point at a rejected/now-orphan face — break the
        // FK before delete to keep the SetNull cascade simple.
        foreach (var p in empty)
        {
            p.CoverFaceId = null;
        }
        await _dbContext.SaveChangesAsync(cancellationToken);

        _dbContext.People.RemoveRange(empty);
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Removed {Count} empty Person(s) for owner {OwnerId}", empty.Count, ownerId);
        return empty.Count;
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
