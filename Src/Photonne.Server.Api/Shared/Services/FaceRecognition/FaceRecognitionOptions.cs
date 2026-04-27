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

    // Upper bound of the "soft match" band. A face whose nearest Person sits in
    // [ClusteringThreshold, SuggestionThreshold) is recorded as a non-binding
    // suggestion ("could this be X?") instead of being auto-assigned. Above this
    // value the face stays orphan with no hint. Keep > ClusteringThreshold or
    // suggestions are silently disabled.
    public float SuggestionThreshold { get; set; } = 0.55f;

    public int MinFacesForCluster { get; set; } = 2;

    // Above this orphan count per owner, batch clustering builds its union-find
    // graph from pgvector kNN queries (HNSW index) instead of an in-memory O(n²)
    // pairwise loop. The pairwise path is simpler and faster for small N; the
    // kNN path keeps batch runtime bounded as catalogs grow.
    public int KnnSwitchoverThreshold { get; set; } = 1500;

    // Top-k neighbors fetched per face in the kNN clustering path. Cluster
    // connectivity is transitive through union-find, so this only needs to be
    // large enough to cover dense clusters; 20 is a comfortable default at the
    // ArcFace cosine threshold.
    public int KnnNeighbors { get; set; } = 20;

    // When true, we prefer the Large (1280) thumbnail over the original asset to
    // reduce I/O and decode time. ArcFace operates on 112x112 crops anyway.
    public bool PreferThumbnailLarge { get; set; } = true;
}
