using System.Linq.Expressions;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Where an asset's bytes live. Only <see cref="Personal"/> counts towards the
/// owner's quota: the shared space (<c>/assets/shared</c>) belongs to the house,
/// usually filled by admins, and an external library is another system's disk.
/// Both are still measured and attributed to the owner, so the numbers stay
/// available if the quota rule changes later.
/// </summary>
public enum StorageSpace
{
    Personal = 0,
    Shared = 1,
    Library = 2
}

public static class StorageSpaces
{
    /// <summary>Prefix of every asset inside a share (<c>/assets/shared/{name}/...</c>).</summary>
    public const string SharedPathPrefix = "/assets/shared/";

    /// <summary>
    /// Projects each asset onto the fields the storage breakdowns group by,
    /// with its <see cref="StorageSpace"/> computed in SQL. Libraries win over
    /// the path: a library's files never sit under the shared root anyway.
    /// </summary>
    public static IQueryable<SpacedAsset> WithSpace(this IQueryable<Asset> assets) =>
        assets.Select(a => new SpacedAsset
        {
            OwnerId = a.OwnerId,
            Type = a.Type,
            ExternalLibraryId = a.ExternalLibraryId,
            FileSize = a.FileSize,
            Space = a.ExternalLibraryId != null
                ? StorageSpace.Library
                : a.FullPath.StartsWith(SharedPathPrefix)
                    ? StorageSpace.Shared
                    : StorageSpace.Personal
        });

    /// <summary>The assets that count towards the owner's storage quota.</summary>
    public static readonly Expression<Func<Asset, bool>> CountsTowardsQuota = a =>
        a.ExternalLibraryId == null && !a.FullPath.StartsWith(SharedPathPrefix);
}

public sealed class SpacedAsset
{
    public Guid? OwnerId { get; init; }
    public AssetType Type { get; init; }
    public Guid? ExternalLibraryId { get; init; }
    public long FileSize { get; init; }
    public StorageSpace Space { get; init; }
}
