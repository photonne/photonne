namespace Photonne.Client.Web;

/// <summary>
/// Configuracion estatica de la API, inicializada al arrancar la app.
/// BaseUrl vacio = URLs relativas (comportamiento normal en Web/WASM).
/// </summary>
public static class ApiConfig
{
    public static string BaseUrl { get; set; } = string.Empty;
}
