using System.Text.Json;

namespace Photonne.Client.Web.Services;

public sealed class ApiErrorInfo
{
    public string Title { get; init; } = "Error de API";
    public string? Message { get; init; }
    public int? StatusCode { get; init; }
    public string? ReasonPhrase { get; init; }
    public string? Url { get; init; }
    public string? Method { get; init; }
    public string? RawContent { get; init; }
    public string? TraceId { get; init; }
    public DateTimeOffset Timestamp { get; init; } = DateTimeOffset.Now;

    public string ToJson()
        => JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
}

public sealed class ApiErrorNotifier
{
    private bool _hasShown;
    public event Action<ApiErrorInfo>? OnError;

    public void Notify(ApiErrorInfo error)
    {
        if (_hasShown)
        {
            return;
        }

        _hasShown = true;
        OnError?.Invoke(error);
    }
}
