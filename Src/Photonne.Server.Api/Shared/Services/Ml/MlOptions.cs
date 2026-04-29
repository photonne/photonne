namespace Photonne.Server.Api.Shared.Services.Ml;

/// <summary>
/// Shared transport-level settings for the Photonne ML microservice. The
/// per-capability options (FaceRecognition, ObjectDetection, ...) live under
/// the same "Ml" section and only carry capability-specific tunables.
/// </summary>
public class MlOptions
{
    public const string SectionName = "Ml";

    public string ServiceUrl { get; set; } = "http://photonne-ml:8000";

    public int TimeoutSeconds { get; set; } = 120;

    public int MaxRetries { get; set; } = 3;
}
