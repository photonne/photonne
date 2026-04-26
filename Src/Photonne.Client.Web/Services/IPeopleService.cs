using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IPeopleService
{
    Task<PeoplePage> GetPeopleAsync(int limit = 50, int offset = 0, bool includeHidden = false, CancellationToken ct = default);
    Task<PersonSummary?> GetPersonAsync(Guid id, CancellationToken ct = default);
    Task<PersonAssetsPage> GetPersonAssetsAsync(Guid id, int limit = 100, int offset = 0, CancellationToken ct = default);
    Task RenameAsync(Guid personId, string? name, CancellationToken ct = default);
    Task HideAsync(Guid personId, CancellationToken ct = default);
}

public sealed record PersonSummary(
    Guid Id,
    string? Name,
    Guid? CoverFaceId,
    int FaceCount,
    bool IsHidden,
    DateTime CreatedAt,
    DateTime UpdatedAt);

public sealed record PeoplePage(int Total, List<PersonSummary> Items);

public sealed record PersonAssetItem(
    Guid Id,
    string FileName,
    string Type,
    DateTime FileCreatedAt,
    bool HasThumbnails,
    string? DominantColor)
{
    /// <summary>Adapt the DTO to a TimelineItem so AssetCard can render it.</summary>
    public TimelineItem ToTimelineItem() => new()
    {
        Id = Id,
        FileName = FileName,
        FileCreatedAt = FileCreatedAt,
        Type = Type,
        HasThumbnails = HasThumbnails,
        DominantColor = DominantColor,
    };
}

public sealed record PersonAssetsPage(int Total, List<PersonAssetItem> Items);
