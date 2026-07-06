namespace Photonne.Server.Api.Shared.Services.Ml;

/// <summary>
/// Maps the admin-facing per-task compute-device choice (stored as a
/// <c>*.Provider</c> setting) to the ONNX Runtime provider spec the ML service
/// understands, and the setting key to the ML task name its /v1/config endpoint
/// expects.
/// </summary>
public static class MlProviders
{
    // Admin device values persisted under the *.Provider setting keys.
    public const string DeviceAuto = "auto";
    public const string DeviceGpu = "cuda";
    public const string DeviceCpu = "cpu";

    // The five per-task provider setting keys, each under an already-global
    // prefix (see SettingsEndpoint.IsGlobalKey), mapped to the ML task name.
    public static readonly IReadOnlyDictionary<string, string> KeyToTask =
        new Dictionary<string, string>(StringComparer.Ordinal)
        {
            ["FaceRecognition.Provider"] = "faces",
            ["ObjectDetection.Provider"] = "objects",
            ["SceneClassification.Provider"] = "scenes",
            ["TextRecognition.Provider"] = "text",
            ["Embedding.Provider"] = "embeddings",
        };

    /// <summary>
    /// Translates an admin device value into the ONNX provider spec sent to the
    /// ML service. "auto" reloads the task on its container env default (the
    /// ML side treats "auto" as "reset to default"); "cuda" prefers the GPU
    /// with a CPU fallback; anything else pins the task to CPU.
    /// </summary>
    public static string DeviceToProviderSpec(string? device) => (device ?? DeviceAuto).Trim().ToLowerInvariant() switch
    {
        DeviceAuto => "auto",
        DeviceGpu => "CUDAExecutionProvider,CPUExecutionProvider",
        _ => "CPUExecutionProvider",
    };
}
