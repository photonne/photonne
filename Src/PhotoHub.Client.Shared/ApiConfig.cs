namespace PhotoHub.Client.Shared;

/// <summary>
/// Configuración estática de la API, inicializada al arrancar la app.
/// En Web: BaseUrl vacío (URLs relativas) y lazy loading activo.
/// En Native (MAUI): BaseUrl con la URL del servidor, lazy loading desactivado
/// porque el IntersectionObserver no es fiable en el WebView nativo.
/// </summary>
public static class ApiConfig
{
    public static string BaseUrl { get; set; } = string.Empty;

    /// <summary>
    /// Cuando es false, las imágenes usan src directo en lugar de data-src+IntersectionObserver.
    /// </summary>
    public static bool LazyLoadImages { get; set; } = true;
}
