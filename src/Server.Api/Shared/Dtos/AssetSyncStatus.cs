namespace Photonne.Server.Api.Shared.Dtos;

public enum AssetSyncStatus
{
    Synced,      // Sincronizado e indexado (en BD)
    Copied,      // Copiado al directorio interno pero aún no indexado
    Pending,     // Pendiente de sincronizar (aún en el dispositivo del usuario)
    Syncing      // En proceso de sincronización
}
