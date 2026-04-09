using Microsoft.JSInterop;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Persiste el índice y el grid del timeline en IndexedDB para sobrevivir recargas de página.
/// Las operaciones de escritura son fire-and-forget (no bloquean el render).
/// </summary>
public class TimelinePersistenceService
{
    private readonly IJSRuntime _js;

    public TimelinePersistenceService(IJSRuntime js)
    {
        _js = js;
    }

    public async Task<List<TimelineIndexItem>?> LoadIndexAsync()
    {
        try
        {
            return await _js.InvokeAsync<List<TimelineIndexItem>?>("timelineDb.loadIndex");
        }
        catch
        {
            return null;
        }
    }

    public async Task SaveIndexAsync(List<TimelineIndexItem> items)
    {
        try
        {
            await _js.InvokeVoidAsync("timelineDb.saveIndex", items);
        }
        catch { }
    }

    public async Task<List<TimelineGridSection>?> LoadGridAsync()
    {
        try
        {
            return await _js.InvokeAsync<List<TimelineGridSection>?>("timelineDb.loadGrid");
        }
        catch
        {
            return null;
        }
    }

    public async Task SaveGridAsync(List<TimelineGridSection> grid)
    {
        try
        {
            await _js.InvokeVoidAsync("timelineDb.saveGrid", grid);
        }
        catch { }
    }

    public async Task ClearAllAsync()
    {
        try
        {
            await _js.InvokeVoidAsync("timelineDb.clearAll");
        }
        catch { }
    }
}
