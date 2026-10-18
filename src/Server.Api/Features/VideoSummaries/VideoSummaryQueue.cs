using System.Threading.Channels;

namespace Photonne.Server.Api.Features.VideoSummaries;

/// <summary>
/// Hands requested summaries to <see cref="VideoSummaryWorker"/>. In-memory
/// only: the durable state is the <see cref="Shared.Models.VideoSummary"/> row,
/// and the worker re-queues every row still pending when the process starts.
/// </summary>
public sealed class VideoSummaryQueue
{
    private readonly Channel<Guid> _channel = Channel.CreateUnbounded<Guid>();

    public void Enqueue(Guid summaryId) => _channel.Writer.TryWrite(summaryId);

    public ChannelReader<Guid> Reader => _channel.Reader;
}
