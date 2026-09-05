using Microsoft.AspNetCore.Components.Forms;
using Photonne.Client.Web.Models;

namespace Photonne.Client.Web.Services;

/// <summary>
/// Acceso público (sin autenticación) al contenido compartido mediante enlaces
/// con token. La gestión de enlaces (crear, editar, revocar) vive en las apps
/// nativas; el panel web solo necesita renderizar el contenido compartido y,
/// en enlaces con solicitud de fotos, subir las aportaciones de los invitados.
/// </summary>
public interface IShareService
{
    Task<SharedContentResponse?> GetSharedContentAsync(string token, string? password = null);

    /// <summary>Sube una foto de un invitado a través de un enlace con AllowUpload.</summary>
    Task<bool> UploadAssetAsync(string token, IBrowserFile file, string? uploaderName, string? password);
}
