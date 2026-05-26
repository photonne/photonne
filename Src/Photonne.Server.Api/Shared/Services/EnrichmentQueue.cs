using System.Threading.Channels;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// In-memory dispatcher that routes newly-created enrichment tasks to per-type
/// <see cref="Channel{T}"/> readers consumed by <see cref="EnrichmentWorker"/>.
///
/// Producers (the upload/sync endpoints, the nightly backfill, etc.) push the
/// task id to the channel as soon as the row is committed, so consumers wake up
/// immediately without scanning the database.
///
/// The queue is process-local. Tasks created while the worker is down, or that
/// linger in <c>Pending</c> after a crash, are recovered on startup by the
/// worker, which re-pushes them into the channels.
///
/// Channels are unbounded: producers never block. The volume is bounded in
/// practice by the number of pending rows; a single <see cref="Guid"/> per
/// entry keeps memory negligible even at hundreds of thousands of tasks.
/// </summary>
public class EnrichmentQueue
{
    // Fast tasks (sub-second to seconds).
    private readonly Channel<Guid> _exif = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _thumbnails = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _mediaRecognition = Channel.CreateUnbounded<Guid>();

    // ML tasks (heavy, model-bound).
    private readonly Channel<Guid> _faceRecognition = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _objectDetection = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _sceneClassification = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _textRecognition = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _imageEmbedding = Channel.CreateUnbounded<Guid>();

    /// <summary>Push a task id into the channel for its type. The worker's
    /// per-type readers will pick it up. Safe to call from any thread.</summary>
    public ValueTask EnqueueAsync(AssetEnrichmentType type, Guid taskId, CancellationToken ct = default) =>
        GetChannel(type).Writer.WriteAsync(taskId, ct);

    /// <summary>Reader for the channel of the given type. Used by the worker
    /// via <c>ReadAllAsync</c>.</summary>
    public ChannelReader<Guid> Reader(AssetEnrichmentType type) => GetChannel(type).Reader;

    private Channel<Guid> GetChannel(AssetEnrichmentType type) => type switch
    {
        AssetEnrichmentType.Exif                => _exif,
        AssetEnrichmentType.Thumbnails          => _thumbnails,
        AssetEnrichmentType.MediaRecognition    => _mediaRecognition,
        AssetEnrichmentType.FaceRecognition     => _faceRecognition,
        AssetEnrichmentType.ObjectDetection     => _objectDetection,
        AssetEnrichmentType.SceneClassification => _sceneClassification,
        AssetEnrichmentType.TextRecognition     => _textRecognition,
        AssetEnrichmentType.ImageEmbedding      => _imageEmbedding,
        _ => throw new ArgumentOutOfRangeException(nameof(type), type, "Unknown enrichment task type"),
    };
}
