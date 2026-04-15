namespace Photonne.Client.Web.Services;

/// <summary>
/// Fetches <c>/api/admin/demo-info</c> once per session so both the layout banner
/// and the login page can surface demo metadata without hitting the API repeatedly.
/// </summary>
public interface IDemoInfoService
{
    /// <summary>
    /// Returns cached demo info, fetching from the API on first call.
    /// Any network error yields <see cref="DemoInfo.Disabled"/> so the UI never breaks.
    /// </summary>
    Task<DemoInfo> GetAsync();
}

public sealed record DemoInfo(
    bool Enabled,
    string? DemoUsername,
    string? DemoPassword,
    int? ResetIntervalHours,
    DateTimeOffset? NextResetAt)
{
    public static DemoInfo Disabled { get; } =
        new(false, null, null, null, null);
}
