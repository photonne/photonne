namespace Photonne.Server.Api.Shared.Services.FaceRecognition;

public class FaceRecognitionOptions
{
    public const string SectionName = "FaceRecognition";

    public bool Enabled { get; set; } = true;

    public string ServiceUrl { get; set; } = "http://photonne-faces:8000";

    public int TimeoutSeconds { get; set; } = 120;

    public int MaxRetries { get; set; } = 3;

    // Faces below this side length (in pixels of the source image) are dropped.
    // Re-applied API-side because Python-side filtering uses thumbnail dims.
    public int MinFaceSize { get; set; } = 40;

    public float MinDetectionScore { get; set; } = 0.5f;

    // Cosine distance under which two faces of the same owner are considered the
    // same person. ArcFace cosine distance ~0.4 is a common operating point.
    public float ClusteringThreshold { get; set; } = 0.42f;

    public int MinFacesForCluster { get; set; } = 2;

    // When true, we prefer the Large (1280) thumbnail over the original asset to
    // reduce I/O and decode time. ArcFace operates on 112x112 crops anyway.
    public bool PreferThumbnailLarge { get; set; } = true;
}
