using System.Threading.Channels;
using Photonne.Server.Api.Shared.Models;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// In-memory dispatcher that routes newly-created ML jobs to per-type
/// <see cref="Channel{T}"/> readers consumed by <see cref="MlJobProcessorService"/>.
///
/// Replaces the previous 30-second polling model: producers (the API endpoints
/// that call <c>EnqueueMlJobAsync</c>) push the job id to the channel as soon
/// as the row is committed, so consumers wake up immediately without scanning
/// the database.
///
/// The queue is process-local. Jobs that are created while the worker is down,
/// or that linger in <c>Pending</c> after a crash, are recovered on startup by
/// <see cref="MlJobProcessorService"/>, which re-pushes them into the channels.
///
/// Channels are unbounded: producers never block. The volume is bounded in
/// practice by the number of pending rows (one entry per row), and a single
/// <see cref="Guid"/> per entry keeps memory negligible even at hundreds of
/// thousands of jobs.
/// </summary>
public class MlJobQueue
{
    private readonly Channel<Guid> _faceRecognition = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _objectDetection = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _sceneClassification = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _textRecognition = Channel.CreateUnbounded<Guid>();
    private readonly Channel<Guid> _imageEmbedding = Channel.CreateUnbounded<Guid>();

    /// <summary>Push a job id into the channel for its type. The processor's
    /// per-type workers will pick it up. Safe to call from any thread.</summary>
    public ValueTask EnqueueAsync(MlJobType type, Guid jobId, CancellationToken ct = default) =>
        GetChannel(type).Writer.WriteAsync(jobId, ct);

    /// <summary>Reader for the channel of the given type. Used by the workers
    /// in <see cref="MlJobProcessorService"/> via <c>ReadAllAsync</c>.</summary>
    public ChannelReader<Guid> Reader(MlJobType type) => GetChannel(type).Reader;

    private Channel<Guid> GetChannel(MlJobType type) => type switch
    {
        MlJobType.FaceRecognition     => _faceRecognition,
        MlJobType.ObjectDetection     => _objectDetection,
        MlJobType.SceneClassification => _sceneClassification,
        MlJobType.TextRecognition     => _textRecognition,
        MlJobType.ImageEmbedding      => _imageEmbedding,
        _ => throw new ArgumentOutOfRangeException(nameof(type), type, "Unknown ML job type"),
    };
}
