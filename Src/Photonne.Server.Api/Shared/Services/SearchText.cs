using System.Globalization;
using System.Text;

namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// Plegado de texto para BUSCAR: mismas reglas a los dos lados de la comparación,
/// para que "jose", "José" y "JOSÉ" sean el mismo término mires donde mires
/// (personas, etiquetas, escenas, objetos, nombres de archivo, OCR…).
///
/// Hay dos caras del mismo plegado y tienen que coincidir:
/// <list type="bullet">
/// <item><see cref="Unaccent"/> es la que se aplica a una COLUMNA dentro de una
/// consulta: no se ejecuta en .NET, la traduce EF a la función SQL
/// <c>photonne_unaccent</c> (ver <c>ApplicationDbContext.OnModelCreating</c>), que
/// envuelve la extensión <c>unaccent</c> de Postgres.</item>
/// <item><see cref="Fold"/> es la de MEMORIA, para plegar el término escrito o una
/// lista de etiquetas antes de mandarla a la consulta.</item>
/// </list>
///
/// No se usa para IDENTIDAD (nombres de usuario al iniciar sesión, clave única de
/// las etiquetas): ahí "José" y "Jose" siguen siendo cosas distintas. Esto solo
/// decide qué ENCUENTRA una búsqueda.
/// </summary>
public static class SearchText
{
    /// <summary>
    /// Quita los acentos de una columna, dentro de una consulta. Solo tiene sentido
    /// dentro de un árbol de expresión que traduzca EF: llamarla desde .NET lanza.
    /// </summary>
    public static string Unaccent(string value) =>
        throw new InvalidOperationException(
            "SearchText.Unaccent solo existe para que EF la traduzca a photonne_unaccent(); " +
            "para plegar en memoria usa SearchText.Fold().");

    /// <summary>
    /// Pliega un término en memoria: recorta, pasa a minúsculas y quita los
    /// diacríticos descomponiendo en NFD y tirando las marcas combinantes, con un
    /// puñado de casos que NO se descomponen (ø, đ, ł…) mapeados a mano para no
    /// separarnos de lo que hace <c>unaccent</c> en Postgres.
    /// </summary>
    public static string Fold(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return string.Empty;

        var decomposed = value.Trim().ToLowerInvariant().Normalize(NormalizationForm.FormD);
        var builder = new StringBuilder(decomposed.Length);
        foreach (var ch in decomposed)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(ch) == UnicodeCategory.NonSpacingMark) continue;
            builder.Append(ch switch
            {
                'ø' => 'o',
                'đ' or 'ð' => 'd',
                'ł' => 'l',
                'ħ' => 'h',
                'ŧ' => 't',
                'þ' => 'p',
                _ => ch,
            });
        }
        return builder.ToString().Normalize(NormalizationForm.FormC);
    }

    /// <summary>
    /// Patrón <c>ILIKE</c> de "contiene", ya plegado. Escapa los comodines del
    /// propio LIKE para que un término con <c>%</c> o <c>_</c> se busque literal en
    /// vez de convertirse en "cualquier cosa".
    /// </summary>
    public static string ContainsPattern(string? value) =>
        "%" + Fold(value).Replace("\\", "\\\\").Replace("%", "\\%").Replace("_", "\\_") + "%";
}
