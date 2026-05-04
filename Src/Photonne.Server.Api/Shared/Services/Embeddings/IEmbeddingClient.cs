namespace Photonne.Server.Api.Shared.Services.Embeddings;

public interface IEmbeddingClient
{
    Task<EmbeddingResponseDto> EmbedImageAsync(string imagePath, Guid assetId, CancellationToken cancellationToken = default);

    Task<EmbeddingResponseDto> EmbedTextAsync(string text, CancellationToken cancellationToken = default);
}
