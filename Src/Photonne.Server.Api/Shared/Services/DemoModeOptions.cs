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
    /// Master switch. When false, demo behaviours are dormant and the app runs normally.
    /// </summary>
    public bool Enabled { get; set; } = false;

    /// <summary>
    /// Hours between automatic resets of the demo database and assets.
    /// </summary>
    public int ResetIntervalHours { get; set; } = 6;

    /// <summary>
    /// Host-side directory containing the seed images used to populate the demo.
    /// Must be readable by the app. Files are copied (not moved) into the assets volume.
    /// </summary>
    public string SeedPath { get; set; } = "/data/demo-seed";

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
