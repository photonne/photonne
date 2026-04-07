using Microsoft.JSInterop;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Persiste el índice y las secciones del timeline en IndexedDB para sobrevivir recargas de página.
/// Las operaciones de escritura son fire-and-forget (no bloquean el render).
/// </summary>
public class TimelinePersistenceService
{
    private readonly IJSRuntime _js;

    public TimelinePersistenceService(IJSRuntime js)
    {
        _js = js;
    }

    /// <summary>
    /// Carga el índice guardado en IndexedDB. Devuelve null si no hay datos o falla.
    /// </summary>
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

    /// <summary>
    /// Persiste el índice en IndexedDB. No bloquea: llamar con fire-and-forget.
    /// </summary>
    public async Task SaveIndexAsync(List<TimelineIndexItem> items)
    {
        try
        {
            await _js.InvokeVoidAsync("timelineDb.saveIndex", items);
        }
        catch { }
    }

    /// <summary>
    /// Carga los items de una sección mensual desde IndexedDB. Devuelve null si no hay datos.
    /// </summary>
    public async Task<List<TimelineItem>?> LoadSectionAsync(string yearMonth)
    {
        try
        {
            return await _js.InvokeAsync<List<TimelineItem>?>("timelineDb.loadSection", yearMonth);
        }
        catch
        {
            return null;
        }
    }

    /// <summary>
    /// Persiste una sección mensual en IndexedDB. No bloquea: llamar con fire-and-forget.
    /// Elimina automáticamente secciones antiguas si se superan los 24 meses.
    /// </summary>
    public async Task SaveSectionAsync(string yearMonth, List<TimelineItem> items)
    {
        try
        {
            await _js.InvokeVoidAsync("timelineDb.saveSection", yearMonth, items);
        }
        catch { }
    }

    /// <summary>
    /// Borra todos los datos del timeline en IndexedDB. Llamar tras mutaciones (delete/archive).
    /// </summary>
    public async Task ClearAllAsync()
    {
        try
        {
            await _js.InvokeVoidAsync("timelineDb.clearAll");
        }
        catch { }
    }
}
