using System.Collections.Concurrent;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Pgvector.EntityFrameworkCore;
using Photonne.Server.Api.Shared.Authorization;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

/// <summary>
/// Per-user face clustering. Detection (<see cref="Face"/>) is shared across
/// every user with read access to the underlying asset; identity lives in
/// <see cref="UserFaceAssignment"/> so each user grows their own
/// <see cref="Person"/> clusters over the faces they can see.
///
/// Two stages, both scoped to a single user U:
///   * Online (per upload, per visible user): a new face is auto-assigned to
///     U's nearest existing <see cref="Person"/> if cosine distance is below
///     <see cref="FaceRecognitionOptions.ClusteringThreshold"/>; in the soft-
///     match band <c>[ClusteringThreshold, SuggestionThreshold)</c> the face
///     stays orphan but is recorded as a suggestion.
///   * Batch (per user, scheduled): orphan faces visible to U are grouped via
///     single-link union-find and new Persons are created for clusters of size
///     ≥ <see cref="FaceRecognitionOptions.MinFacesForCluster"/>.
/// Manually-assigned <see cref="UserFaceAssignment"/>s are never moved. Counts
/// in <see cref="Person.FaceCount"/> are recomputed for U after every mutation.
/// </summary>
public class FaceClusteringService
{
    // Per-user cooldown shared across requests/scopes — avoids running the
    // O(n²) batch clustering on every single face detection visible to the
    // same user.
    private static readonly ConcurrentDictionary<Guid, DateTime> _lastBatchRunUtcByUser = new();
    private static readonly TimeSpan BatchCooldown = TimeSpan.FromMinutes(2);

    // Separate cooldown for the lazy whole-pass trigger (online attach + batch)
    // invoked from /people. Without it, every page refresh would re-walk all
    // unassigned faces visible to the user — even though the batch portion is
    // already cooldown'd, the online-attach portion is not.
    private static readonly ConcurrentDictionary<Guid, DateTime> _lastEnsurePassUtcByUser = new();
    private static readonly TimeSpan EnsurePassCooldown = TimeSpan.FromMinutes(2);

    public const string ClusteringThresholdKey = "FaceRecognition.ClusteringThreshold";
    public const string SuggestionThresholdKey = "FaceRecognition.SuggestionThreshold";

    private readonly ApplicationDbContext _dbContext;
    private readonly FaceRecognitionOptions _options;
    private readonly SettingsService _settings;
    private readonly AssetVisibilityService _visibility;
    private readonly ILogger<FaceClusteringService> _logger;

    public FaceClusteringService(
        ApplicationDbContext dbContext,
        IOptions<FaceRecognitionOptions> options,
        SettingsService settings,
        AssetVisibilityService visibility,
        ILogger<FaceClusteringService> logger)
    {
        _dbContext = dbContext;
        _options = options.Value;
        _settings = settings;
        _visibility = visibility;
        _logger = logger;
    }

    /// <summary>
    /// Resolves the operating thresholds, preferring runtime overrides stored in
    /// the Settings table. Falls back to the static <see cref="FaceRecognitionOptions"/>
    /// values when missing or unparseable. Suggestion threshold is clamped above the
    /// assignment threshold; otherwise the soft-match band collapses and suggestions
    /// are silently disabled.
    /// </summary>
    public async Task<(float Assign, float Suggest)> ResolveThresholdsAsync(CancellationToken cancellationToken)
    {
        var assign = await ReadFloatSettingAsync(ClusteringThresholdKey, _options.ClusteringThreshold);
        var suggest = await ReadFloatSettingAsync(SuggestionThresholdKey, _options.SuggestionThreshold);
        if (suggest < assign) suggest = assign; // disabled suggestions
        return (assign, suggest);
    }

    private async Task<float> ReadFloatSettingAsync(string key, float fallback)
    {
        var raw = await _settings.GetSettingAsync(key, Guid.Empty, string.Empty);
        if (string.IsNullOrWhiteSpace(raw)) return fallback;
        return float.TryParse(raw, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var v)
            ? v
            : fallback;
    }

    /// <summary>
    /// Online-assigns the orphan faces of a single asset for a given user U:
    /// for each face on the asset that U does not yet have an assignment for,
    /// look up U's nearest existing <see cref="Person"/>. Distance below
    /// <see cref="FaceRecognitionOptions.ClusteringThreshold"/> attaches; in
    /// the soft-match band the face gets a non-binding <c>SuggestedPersonId</c>
    /// hint instead. Caller must have already verified U has visibility on the
    /// asset.
    /// </summary>
    public async Task AssignNewFacesForUserAsync(Guid userId, Guid assetId, CancellationToken cancellationToken)
    {
        // Faces on this asset that the user has no assignment for yet.
        var newFaces = await _dbContext.Faces
            .Where(f => f.AssetId == assetId
                        && !_dbContext.UserFaceAssignments.Any(uf => uf.FaceId == f.Id && uf.UserId == userId))
            .ToListAsync(cancellationToken);

        if (newFaces.Count == 0) return;

        var hasAnyPerson = await _dbContext.People.AnyAsync(p => p.OwnerId == userId, cancellationToken);
        if (!hasAnyPerson)
        {
            _logger.LogDebug(
                "User {UserId} has no Person yet; skipping online assignment for {Count} new faces (batch clustering will create clusters)",
                userId, newFaces.Count);
            return;
        }

        var (assignThreshold, suggestThreshold) = await ResolveThresholdsAsync(cancellationToken);

        foreach (var face in newFaces)
        {
            // pgvector cosine-distance operator <=>; pick U's closest already-
            // assigned face. Pull the embedding back so we can re-check the
            // distance in memory without forcing EF to translate
            // CosineDistance twice in a single query.
            var nearest = await WithLiveAssets(_dbContext.UserFaceAssignments)
                .Where(uf => uf.UserId == userId
                             && uf.PersonId != null
                             && !uf.IsRejected
                             && uf.Person!.OwnerId == userId
                             && uf.FaceId != face.Id)
                .OrderBy(uf => uf.Face.Embedding.CosineDistance(face.Embedding))
                .Select(uf => new { uf.PersonId, Embedding = uf.Face.Embedding })
                .FirstOrDefaultAsync(cancellationToken);

            if (nearest?.PersonId == null) continue;

            var distance = CosineDistance(nearest.Embedding.ToArray(), face.Embedding.ToArray());
            if (distance < assignThreshold)
            {
                _dbContext.UserFaceAssignments.Add(new UserFaceAssignment
                {
                    FaceId = face.Id,
                    UserId = userId,
                    PersonId = nearest.PersonId.Value,
                    UpdatedAt = DateTime.UtcNow,
                });
                _logger.LogDebug("User {UserId} face {FaceId} → Person {PersonId} (dist={Dist:F3})",
                    userId, face.Id, nearest.PersonId.Value, distance);
            }
            else if (distance < suggestThreshold)
            {
                _dbContext.UserFaceAssignments.Add(new UserFaceAssignment
                {
                    FaceId = face.Id,
                    UserId = userId,
                    SuggestedPersonId = nearest.PersonId.Value,
                    SuggestedDistance = distance,
                    UpdatedAt = DateTime.UtcNow,
                });
                _logger.LogDebug("User {UserId} face {FaceId} ~? Person {PersonId} (dist={Dist:F3}) — suggestion",
                    userId, face.Id, nearest.PersonId.Value, distance);
            }
            // else: leave with no assignment row — true orphan, will be picked
            // up by the next batch run.
        }

        await _dbContext.SaveChangesAsync(cancellationToken);
        await RecomputeFaceCountsForUserAsync(userId, cancellationToken);
    }

    /// <summary>
    /// Runs <see cref="RunForUserAsync"/> for the user if there are enough
    /// orphan faces visible to them to potentially form clusters AND the per-
    /// user cooldown has elapsed. Used as the cheap "ensure up to date" hook
    /// from <c>/people</c> and from the user-scoped backfill.
    /// </summary>
    public async Task<int> MaybeRunBatchForUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var scope = await _visibility.GetScopeAsync(userId, cancellationToken);
        var orphanCount = await CountOrphansForUserAsync(userId, scope, cancellationToken);

        if (orphanCount < _options.MinFacesForCluster)
        {
            _logger.LogDebug("User {UserId}: {Orphans} orphans (< {Min}); skipping batch",
                userId, orphanCount, _options.MinFacesForCluster);
            return 0;
        }

        var now = DateTime.UtcNow;
        var last = _lastBatchRunUtcByUser.TryGetValue(userId, out var t) ? t : DateTime.MinValue;
        if (now - last < BatchCooldown)
        {
            _logger.LogDebug("User {UserId}: batch cooldown active ({Remaining:F0}s left)",
                userId, (BatchCooldown - (now - last)).TotalSeconds);
            return 0;
        }

        _lastBatchRunUtcByUser[userId] = now;
        return await RunForUserAsync(userId, cancellationToken);
    }

    /// <summary>
    /// Counts faces visible to the user that have no <see cref="UserFaceAssignment"/>
    /// for them yet (i.e. true orphans for clustering purposes).
    /// </summary>
    private async Task<int> CountOrphansForUserAsync(Guid userId, AssetVisibilityScope scope, CancellationToken cancellationToken)
    {
        var folderIds = scope.AllowedFolderIds;
        var libIds = scope.AllowedExternalLibraryIds;
        var albumIds = scope.AlbumVisibleAssetIds;

        return await _dbContext.Faces.AsNoTracking()
            .Where(f => f.Asset.DeletedAt == null && !f.Asset.IsFileMissing)
            .Where(f =>
                f.Asset.OwnerId == userId
                || (f.Asset.FolderId.HasValue && folderIds.Contains(f.Asset.FolderId.Value))
                || (f.Asset.ExternalLibraryId.HasValue && libIds.Contains(f.Asset.ExternalLibraryId.Value))
                || albumIds.Contains(f.Asset.Id))
            .Where(f => !_dbContext.UserFaceAssignments.Any(uf =>
                uf.FaceId == f.Id && uf.UserId == userId))
            .CountAsync(cancellationToken);
    }

    /// <summary>
    /// Batch clustering for a user. Loads orphan faces visible to the user
    /// (no <see cref="UserFaceAssignment"/> row for them yet), clusters via
    /// single-link union-find over cosine distance, and materializes new
    /// <see cref="Person"/>s plus their <see cref="UserFaceAssignment"/> rows
    /// for clusters that pass the size floor.
    /// </summary>
    public async Task<int> RunForUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var scope = await _visibility.GetScopeAsync(userId, cancellationToken);
        var folderIds = scope.AllowedFolderIds;
        var libIds = scope.AllowedExternalLibraryIds;
        var albumIds = scope.AlbumVisibleAssetIds;

        var orphans = await _dbContext.Faces
            .Where(f => f.Asset.DeletedAt == null && !f.Asset.IsFileMissing)
            .Where(f =>
                f.Asset.OwnerId == userId
                || (f.Asset.FolderId.HasValue && folderIds.Contains(f.Asset.FolderId.Value))
                || (f.Asset.ExternalLibraryId.HasValue && libIds.Contains(f.Asset.ExternalLibraryId.Value))
                || albumIds.Contains(f.Asset.Id))
            .Where(f => !_dbContext.UserFaceAssignments.Any(uf =>
                uf.FaceId == f.Id && uf.UserId == userId))
            .ToListAsync(cancellationToken);

        if (orphans.Count < _options.MinFacesForCluster) return 0;

        var (threshold, _) = await ResolveThresholdsAsync(cancellationToken);
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
            // nearest orphan neighbors (visible to U, no assignment yet) using
            // the HNSW index on Faces.Embedding, and add edges below the
            // cosine threshold to the union-find. Keeps batch runtime bounded
            // on large catalogs.
            var idToIndex = new Dictionary<Guid, int>(orphans.Count);
            for (int i = 0; i < orphans.Count; i++) idToIndex[orphans[i].Id] = i;

            var k = _options.KnnNeighbors;
            for (int i = 0; i < orphans.Count; i++)
            {
                var face = orphans[i];
                var embedding = face.Embedding;

                var neighbors = await _dbContext.Faces.AsNoTracking()
                    .Where(f => f.Asset.DeletedAt == null && !f.Asset.IsFileMissing)
                    .Where(f =>
                        f.Asset.OwnerId == userId
                        || (f.Asset.FolderId.HasValue && folderIds.Contains(f.Asset.FolderId.Value))
                        || (f.Asset.ExternalLibraryId.HasValue && libIds.Contains(f.Asset.ExternalLibraryId.Value))
                        || albumIds.Contains(f.Asset.Id))
                    .Where(f => !_dbContext.UserFaceAssignments.Any(uf =>
                        uf.FaceId == f.Id && uf.UserId == userId))
                    .Where(f => f.Id != face.Id)
                    .OrderBy(f => f.Embedding.CosineDistance(embedding))
                    .Take(k)
                    .Select(f => new { f.Id, Distance = f.Embedding.CosineDistance(embedding) })
                    .ToListAsync(cancellationToken);

                foreach (var n in neighbors)
                {
                    if (n.Distance >= threshold) break;
                    if (idToIndex.TryGetValue(n.Id, out var j)) Union(i, j);
                }
            }
        }
        else
        {
            // O(n²) is fine for moderate face counts per user.
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
                OwnerId = userId,
                CoverFaceId = orphans[coverIdx].Id,
                FaceCount = members.Count,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow,
            };
            _dbContext.People.Add(person);
            await _dbContext.SaveChangesAsync(cancellationToken);

            var now = DateTime.UtcNow;
            foreach (var idx in members)
            {
                _dbContext.UserFaceAssignments.Add(new UserFaceAssignment
                {
                    FaceId = orphans[idx].Id,
                    UserId = userId,
                    PersonId = person.Id,
                    UpdatedAt = now,
                });
            }
            await _dbContext.SaveChangesAsync(cancellationToken);
            personsCreated++;
        }

        _logger.LogInformation("Clustering for user {UserId}: {Created} new persons from {Orphans} orphans",
            userId, personsCreated, orphans.Count);

        // Anything still without a UserFaceAssignment after batch is a "true"
        // orphan for this user — refresh proactive suggestions so the UI can
        // surface "could this be X?" against U's existing Persons.
        var assignedFaceIds = await _dbContext.UserFaceAssignments
            .Where(uf => uf.UserId == userId)
            .Select(uf => uf.FaceId)
            .ToHashSetAsync(cancellationToken);
        var stillOrphan = orphans.Where(f => !assignedFaceIds.Contains(f.Id)).ToList();
        if (stillOrphan.Count > 0)
        {
            await RecomputeSuggestionsForUserAsync(userId, stillOrphan, cancellationToken);
        }

        return personsCreated;
    }

    /// <summary>
    /// Explicit "do everything now" path bound to user actions like the
    /// "Reagrupar caras" button: runs the online attach over every unassigned
    /// visible asset and a batch pass, bypassing the per-user cooldown. Use
    /// <see cref="EnsureUpToDateForUserAsync"/> for implicit (cheap) calls.
    /// Returns the number of new Persons created by the batch pass.
    /// </summary>
    public async Task<int> ForceRunForUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        await OnlineAttachAllUnassignedAsync(userId, cancellationToken);
        // Bypass the cooldown — explicit user action.
        _lastBatchRunUtcByUser[userId] = DateTime.UtcNow;
        return await RunForUserAsync(userId, cancellationToken);
    }

    private async Task OnlineAttachAllUnassignedAsync(Guid userId, CancellationToken cancellationToken)
    {
        var scope = await _visibility.GetScopeAsync(userId, cancellationToken);
        var folderIds = scope.AllowedFolderIds;
        var libIds = scope.AllowedExternalLibraryIds;
        var albumIds = scope.AlbumVisibleAssetIds;

        var unassignedAssetIds = await _dbContext.Faces
            .AsNoTracking()
            .Where(f => f.Asset.DeletedAt == null && !f.Asset.IsFileMissing)
            .Where(f =>
                f.Asset.OwnerId == userId
                || (f.Asset.FolderId.HasValue && folderIds.Contains(f.Asset.FolderId.Value))
                || (f.Asset.ExternalLibraryId.HasValue && libIds.Contains(f.Asset.ExternalLibraryId.Value))
                || albumIds.Contains(f.Asset.Id))
            .Where(f => !_dbContext.UserFaceAssignments.Any(uf =>
                uf.FaceId == f.Id && uf.UserId == userId))
            .Select(f => f.AssetId)
            .Distinct()
            .ToListAsync(cancellationToken);

        foreach (var assetId in unassignedAssetIds)
        {
            await AssignNewFacesForUserAsync(userId, assetId, cancellationToken);
        }
    }

    /// <summary>
    /// Lazy "ensure up to date" pass invoked when the user opens <c>/people</c>
    /// or pulls People-related data: do online attach against U's existing
    /// Persons, then run a batch if the orphan count crosses the threshold.
    /// Cooldown-gated as a whole — the online-attach portion alone can be
    /// thousands of queries for a user with shared-only access, so a single
    /// gate covers both phases. Intended to be invoked from a background
    /// worker; the request that enqueues it should not await the result.
    /// </summary>
    public async Task EnsureUpToDateForUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var now = DateTime.UtcNow;
        var last = _lastEnsurePassUtcByUser.TryGetValue(userId, out var t) ? t : DateTime.MinValue;
        if (now - last < EnsurePassCooldown)
        {
            _logger.LogDebug("EnsureUpToDate for user {UserId}: cooldown active ({Remaining:F0}s left)",
                userId, (EnsurePassCooldown - (now - last)).TotalSeconds);
            return;
        }
        // Stamp at the start so a long-running pass blocks re-entry attempts
        // for its full duration plus the cooldown afterwards. Concurrent
        // re-entry is also prevented by the queue's in-flight set.
        _lastEnsurePassUtcByUser[userId] = now;

        // Online attach catches the case where user U gained access to a
        // shared asset whose detection happened earlier — MaybeRunBatch alone
        // would not attach those to U's existing Persons because it only
        // creates new clusters from orphans.
        await OnlineAttachAllUnassignedAsync(userId, cancellationToken);
        await MaybeRunBatchForUserAsync(userId, cancellationToken);
    }

    /// <summary>
    /// For each provided orphan face, queries pgvector for U's nearest assigned
    /// face and updates the <see cref="UserFaceAssignment"/> suggestion fields:
    ///   * inside [ClusteringThreshold, SuggestionThreshold)  → set as suggestion
    ///   * outside → no row created / clear existing
    /// </summary>
    private async Task RecomputeSuggestionsForUserAsync(Guid userId, IReadOnlyList<Face> orphans, CancellationToken cancellationToken)
    {
        var (assignThreshold, suggestThreshold) = await ResolveThresholdsAsync(cancellationToken);
        if (suggestThreshold <= assignThreshold) return; // suggestions disabled

        var changed = 0;
        foreach (var face in orphans)
        {
            var nearest = await WithLiveAssets(_dbContext.UserFaceAssignments)
                .Where(uf => uf.UserId == userId
                             && uf.PersonId != null
                             && !uf.IsRejected
                             && uf.Person!.OwnerId == userId
                             && uf.FaceId != face.Id)
                .OrderBy(uf => uf.Face.Embedding.CosineDistance(face.Embedding))
                .Select(uf => new { uf.PersonId, Embedding = uf.Face.Embedding })
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

            // Upsert the user's row: create a row only when there's a real
            // suggestion to record. We never persist an "empty" row just to
            // mark "user has seen this face".
            var existing = await _dbContext.UserFaceAssignments
                .FirstOrDefaultAsync(uf => uf.FaceId == face.Id && uf.UserId == userId, cancellationToken);

            if (newSuggestion == null)
            {
                if (existing != null && existing.PersonId == null && !existing.IsManuallyAssigned && !existing.IsRejected
                    && (existing.SuggestedPersonId != null || existing.SuggestedDistance != null))
                {
                    existing.SuggestedPersonId = null;
                    existing.SuggestedDistance = null;
                    existing.UpdatedAt = DateTime.UtcNow;
                    changed++;
                }
                continue;
            }

            if (existing == null)
            {
                _dbContext.UserFaceAssignments.Add(new UserFaceAssignment
                {
                    FaceId = face.Id,
                    UserId = userId,
                    SuggestedPersonId = newSuggestion,
                    SuggestedDistance = newDistance,
                    UpdatedAt = DateTime.UtcNow,
                });
                changed++;
            }
            else if (existing.SuggestedPersonId != newSuggestion || existing.SuggestedDistance != newDistance)
            {
                existing.SuggestedPersonId = newSuggestion;
                existing.SuggestedDistance = newDistance;
                existing.UpdatedAt = DateTime.UtcNow;
                changed++;
            }
        }

        if (changed > 0)
        {
            await _dbContext.SaveChangesAsync(cancellationToken);
            _logger.LogInformation("Recomputed suggestions for user {UserId}: {Changed}/{Total} orphans updated",
                userId, changed, orphans.Count);
        }
    }

    /// <summary>
    /// Recomputes <see cref="Person.FaceCount"/> for every Person owned by U
    /// from <see cref="UserFaceAssignment"/> rows where U confirmed the face
    /// (PersonId set, not rejected) and the underlying asset is still live.
    /// Keeps the cover face honest as a side effect.
    /// </summary>
    public async Task RecomputeFaceCountsForUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var counts = await WithLiveAssets(_dbContext.UserFaceAssignments)
            .Where(uf => uf.UserId == userId
                         && uf.PersonId != null
                         && !uf.IsRejected
                         && uf.Person!.OwnerId == userId)
            .GroupBy(uf => uf.PersonId!.Value)
            .Select(g => new { PersonId = g.Key, Count = g.Count() })
            .ToListAsync(cancellationToken);

        var byId = counts.ToDictionary(c => c.PersonId, c => c.Count);

        var people = await _dbContext.People
            .Where(p => p.OwnerId == userId)
            .ToListAsync(cancellationToken);

        foreach (var p in people)
        {
            p.FaceCount = byId.TryGetValue(p.Id, out var c) ? c : 0;
            p.UpdatedAt = DateTime.UtcNow;
        }

        await _dbContext.SaveChangesAsync(cancellationToken);

        // Counts and covers are tightly coupled — repair stale covers in the
        // same pass so a Person whose only valid face just disappeared doesn't
        // keep a broken thumbnail.
        await RepairCoverFacesForUserAsync(userId, cancellationToken);
    }

    /// <summary>
    /// Re-points or clears <see cref="Person.CoverFaceId"/> when U's current
    /// cover face is invalid: deleted/missing asset, rejected, or no longer
    /// confirmed by U. Picks the highest-confidence valid replacement among
    /// faces U has confirmed.
    /// </summary>
    public async Task RepairCoverFacesForUserAsync(Guid userId, CancellationToken cancellationToken)
    {
        var brokenIds = await _dbContext.People
            .Where(p => p.OwnerId == userId && p.CoverFaceId != null)
            .Where(p => !_dbContext.UserFaceAssignments.Any(uf =>
                uf.FaceId == p.CoverFaceId
                && uf.UserId == userId
                && uf.PersonId == p.Id
                && !uf.IsRejected
                && uf.Face.Asset.DeletedAt == null
                && !uf.Face.Asset.IsFileMissing))
            .Select(p => p.Id)
            .ToListAsync(cancellationToken);

        if (brokenIds.Count == 0) return;

        var people = await _dbContext.People
            .Where(p => brokenIds.Contains(p.Id))
            .ToListAsync(cancellationToken);

        foreach (var p in people)
        {
            var replacement = await WithLiveAssets(_dbContext.UserFaceAssignments)
                .Where(uf => uf.UserId == userId
                             && uf.PersonId == p.Id
                             && !uf.IsRejected)
                .OrderByDescending(uf => uf.Face.Confidence)
                .Select(uf => (Guid?)uf.FaceId)
                .FirstOrDefaultAsync(cancellationToken);

            p.CoverFaceId = replacement;
            p.UpdatedAt = DateTime.UtcNow;
        }

        await _dbContext.SaveChangesAsync(cancellationToken);
        _logger.LogInformation("Repaired cover face for {Count} Person(s) of user {UserId}",
            people.Count, userId);
    }

    public async Task MergeAsync(Guid userId, Guid targetPersonId, Guid sourcePersonId, CancellationToken cancellationToken)
    {
        if (targetPersonId == sourcePersonId) return;

        var target = await _dbContext.People
            .FirstOrDefaultAsync(p => p.Id == targetPersonId && p.OwnerId == userId, cancellationToken)
            ?? throw new InvalidOperationException($"Target person {targetPersonId} not found");

        var source = await _dbContext.People
            .FirstOrDefaultAsync(p => p.Id == sourcePersonId && p.OwnerId == userId, cancellationToken)
            ?? throw new InvalidOperationException($"Source person {sourcePersonId} not found");

        await _dbContext.UserFaceAssignments
            .Where(uf => uf.UserId == userId && uf.PersonId == sourcePersonId)
            .ExecuteUpdateAsync(s => s.SetProperty(uf => uf.PersonId, target.Id), cancellationToken);

        // Redirect U's pending "could this be source?" suggestions to the
        // target so the user doesn't see hints pointing at a Person about to
        // disappear. The SetNull FK cascade would also clear them, but
        // redirecting preserves the hint for the consolidated cluster.
        await _dbContext.UserFaceAssignments
            .Where(uf => uf.UserId == userId && uf.SuggestedPersonId == sourcePersonId)
            .ExecuteUpdateAsync(s => s.SetProperty(uf => uf.SuggestedPersonId, (Guid?)target.Id), cancellationToken);

        // Clear source's CoverFace FK to avoid blocking the delete via SetNull cascade.
        source.CoverFaceId = null;
        await _dbContext.SaveChangesAsync(cancellationToken);

        _dbContext.People.Remove(source);
        await _dbContext.SaveChangesAsync(cancellationToken);

        await RecomputeFaceCountsForUserAsync(userId, cancellationToken);
    }

    /// <summary>
    /// Removes Persons whose <see cref="Person.FaceCount"/> has dropped to 0.
    /// Safe to call after any face mutation; callers should run
    /// <see cref="RecomputeFaceCountsForUserAsync"/> first.
    /// </summary>
    public async Task<int> CleanupEmptyPersonsAsync(Guid userId, CancellationToken cancellationToken)
    {
        var empty = await _dbContext.People
            .Where(p => p.OwnerId == userId && p.FaceCount == 0)
            .ToListAsync(cancellationToken);

        if (empty.Count == 0) return 0;

        foreach (var p in empty)
        {
            p.CoverFaceId = null;
        }
        await _dbContext.SaveChangesAsync(cancellationToken);

        _dbContext.People.RemoveRange(empty);
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Removed {Count} empty Person(s) for user {UserId}", empty.Count, userId);
        return empty.Count;
    }

    /// <summary>
    /// Restricts a Face query to faces backed by a live asset (not soft-
    /// deleted, file present).
    /// </summary>
    private static IQueryable<Face> WithLiveAssets(IQueryable<Face> faces) =>
        faces.Where(f => f.Asset.DeletedAt == null && !f.Asset.IsFileMissing);

    private static IQueryable<UserFaceAssignment> WithLiveAssets(IQueryable<UserFaceAssignment> assignments) =>
        assignments.Where(uf => uf.Face.Asset.DeletedAt == null && !uf.Face.Asset.IsFileMissing);

    private static float CosineDistance(float[] a, float[] b)
    {
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
