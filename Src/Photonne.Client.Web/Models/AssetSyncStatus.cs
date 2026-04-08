namespace Photonne.Client.Web.Models;

public enum AssetSyncStatus
{
    Synced,      // Sincronizado e indexado (en BD)
    Copied,      // Copiado al directorio interno pero aun no indexado
    Pending,     // Pendiente de sincronizar (aun en el dispositivo del usuario)
    Syncing      // En proceso de sincronizacion
}
