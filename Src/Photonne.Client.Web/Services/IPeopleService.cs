namespace Photonne.Client.Web.Services;

public interface IPeopleService
{
    Task<PeoplePage> GetPeopleAsync(int limit = 50, int offset = 0, bool includeHidden = false, CancellationToken ct = default);
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
