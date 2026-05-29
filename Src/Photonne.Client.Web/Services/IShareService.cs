using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Acceso público (sin autenticación) al contenido compartido mediante enlaces
/// con token. La gestión de enlaces (crear, editar, revocar) vive en las apps
/// nativas; el panel web solo necesita renderizar el contenido compartido.
/// </summary>
public interface IShareService
{
    Task<SharedContentResponse?> GetSharedContentAsync(string token, string? password = null);
}
