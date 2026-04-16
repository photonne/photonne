namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Configuration for Photonne's public demo mode.
/// When enabled, a guard middleware blocks destructive endpoints, the initial
/// admin user is NOT created, and the database/assets are periodically reset
/// by <see cref="DemoResetService"/>.
/// </summary>
public class DemoModeOptions
{
    public const string SectionName = "DemoMode";

    /// <summary>
    /// Fixed in-container path where the host's seed directory must be mounted.
    /// Matches the convention used for <c>/data/assets</c> and
    /// <c>/data/thumbnails</c> — only the host side is configurable (via the
    /// volume binding in <c>docker-compose.demo.yml</c>).
    /// </summary>
    public const string SeedPath = "/data/demo-seed";

    /// <summary>
    /// Master switch. When false, demo behaviours are dormant and the app runs normally.
    /// </summary>
    public bool Enabled { get; set; } = false;

    /// <summary>
    /// Hours between automatic resets of the demo database and assets.
    /// </summary>
    public int ResetIntervalHours { get; set; } = 6;

    /// <summary>
    /// Username for the shared demo account created at seed time.
    /// </summary>
    public string DemoUsername { get; set; } = "demo";

    /// <summary>
    /// Email for the shared demo account.
    /// </summary>
    public string DemoEmail { get; set; } = "demo@photonne.local";

    /// <summary>
    /// Password for the shared demo account. Shown in the login UI.
    /// </summary>
    public string DemoPassword { get; set; } = "demo";
}
