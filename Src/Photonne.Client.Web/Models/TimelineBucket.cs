namespace Photonne.Client.Web.Models;

/// <summary>
/// Un mes del esqueleto del timeline: clave "yyyy-MM" y número EXACTO de
/// assets visibles. El contrato del servidor garantiza que el endpoint de
/// contenido del bucket devuelve exactamente <see cref="Count"/> items, así
/// que la rejilla puede reservar altura de scroll antes de hidratar.
/// </summary>
public class TimelineBucket
{
    public string Key { get; set; } = string.Empty;
    public int Count { get; set; }
}
