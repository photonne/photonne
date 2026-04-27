using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

public interface IPeopleService
{
    Task<PeoplePage> GetPeopleAsync(
        int limit = 50,
        int offset = 0,
        bool includeHidden = false,
        string? search = null,
        string? sort = null,
        string? sortDir = null,
        bool unnamedFirst = false,
        CancellationToken ct = default);
    Task<PersonSummary?> GetPersonAsync(Guid id, CancellationToken ct = default);
    Task<PersonAssetsPage> GetPersonAssetsAsync(Guid id, int limit = 100, int offset = 0, CancellationToken ct = default);
    Task RenameAsync(Guid personId, string? name, CancellationToken ct = default);
    Task HideAsync(Guid personId, CancellationToken ct = default);
    Task UnhideAsync(Guid personId, CancellationToken ct = default);

    /// <summary>Forces a batch face-clustering pass for the current user.</summary>
    Task<int> ReclusterAsync(CancellationToken ct = default);

    /// <summary>Detaches all of <paramref name="assetId"/>'s faces that are currently
    /// linked to <paramref name="personId"/>. Returns the number of faces detached.</summary>
    Task<int> UnlinkAssetFromPersonAsync(Guid personId, Guid assetId, CancellationToken ct = default);

    /// <summary>Merges <paramref name="sourceId"/> INTO <paramref name="targetId"/>:
    /// all faces are reattached to target, source is deleted.</summary>
    Task MergeAsync(Guid targetId, Guid sourceId, CancellationToken ct = default);

    /// <summary>Faces attached to a person, ordered by confidence desc — for the cover picker.</summary>
    Task<PersonFacesPage> GetPersonFacesAsync(Guid personId, int limit = 60, int offset = 0, CancellationToken ct = default);

    /// <summary>Sets a face as the person's cover image. The face must already be linked to the person.</summary>
    Task SetCoverFaceAsync(Guid personId, Guid faceId, CancellationToken ct = default);

    // Face operations
    Task<List<FaceItem>> GetFacesForAssetAsync(Guid assetId, CancellationToken ct = default);
    Task AssignFaceAsync(Guid faceId, Guid? personId, string? newPersonName, CancellationToken ct = default);
    Task UnassignFaceAsync(Guid faceId, CancellationToken ct = default);
    Task RejectFaceAsync(Guid faceId, CancellationToken ct = default);

    // Proactive suggestions
    Task<PersonSuggestionsPage> GetPersonSuggestionsAsync(Guid personId, int limit = 30, int offset = 0, CancellationToken ct = default);
    Task AcceptFaceSuggestionAsync(Guid faceId, CancellationToken ct = default);
    Task DismissFaceSuggestionAsync(Guid faceId, CancellationToken ct = default);
    Task<int> AcceptAllSuggestionsAsync(Guid personId, CancellationToken ct = default);
    Task<int> DismissAllSuggestionsAsync(Guid personId, CancellationToken ct = default);
}

public sealed record PersonSummary(
    Guid Id,
    string? Name,
    Guid? CoverFaceId,
    int FaceCount,
    bool IsHidden,
    DateTime CreatedAt,
    DateTime UpdatedAt,
    int PendingSuggestionsCount = 0);

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

public sealed record PersonFaceItem(
    Guid Id,
    Guid AssetId,
    float Confidence,
    bool IsManuallyAssigned);

public sealed record PersonFacesPage(int Total, List<PersonFaceItem> Items);

public sealed record FaceItem(
    Guid Id,
    Guid AssetId,
    Guid? PersonId,
    float BoundingBoxX,
    float BoundingBoxY,
    float BoundingBoxW,
    float BoundingBoxH,
    float Confidence,
    bool IsManuallyAssigned,
    bool IsRejected,
    Guid? SuggestedPersonId,
    float? SuggestedDistance);

public sealed record PersonSuggestionItem(
    Guid Id,
    Guid AssetId,
    float Confidence,
    float? SuggestedDistance);

public sealed record PersonSuggestionsPage(int Total, List<PersonSuggestionItem> Items);
